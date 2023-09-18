package cn.skcks.docking.gb28181.mocking.service.device;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.URLUtil;
import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.core.sip.gb28181.constant.GB28181Constant;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.notify.dto.MediaStatusRequestDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.ffmpeg.FfmpegSupportService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.Executor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProxyService {
    private final DeviceProxyConfig proxyConfig;

    private final SipSubscribe subscribe;

    private final ConcurrentHashMap<String, Executor> callbackTask = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Executor> downloadTask = new ConcurrentHashMap<>();

    private final SipSender sender;

    private final FfmpegSupportService ffmpegSupportService;

    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public interface TaskProcessor {
        void process(SIPRequest request,String callId,String fromUrl, String toUrl, MockingDevice device, String key, long time);
    }

    public TaskProcessor playbackTask(){
        return (SIPRequest request,String callId,String fromUrl, String toUrl, MockingDevice device, String key, long time) -> {
            Optional.ofNullable(callbackTask.get(callId)).ifPresent(task->{
                task.getWatchdog().destroyProcess();
            });
            Flow.Subscriber<SIPRequest> subscriber = byeSubscriber(key, device, callbackTask);
            subscribe.getByeSubscribe().addSubscribe(key, subscriber);
            callbackTask.put(device.getDeviceCode(), pushRtpTask(fromUrl,  toUrl,  time + 60, mediaStatus(request, device, key)));
            scheduledExecutorService.schedule(subscriber::onComplete, time + 60, TimeUnit.SECONDS);
        };
    }

    public TaskProcessor downloadTask(){
        return (SIPRequest request,String callId,String fromUrl, String toUrl, MockingDevice device, String key, long time)->{
            Optional.ofNullable(downloadTask.get(callId)).ifPresent(task->{
                task.getWatchdog().destroyProcess();
            });
            Flow.Subscriber<SIPRequest> subscriber = byeSubscriber(key, device, downloadTask);
            subscribe.getByeSubscribe().addSubscribe(key, subscriber);
            downloadTask.put(device.getDeviceCode(), pushDownload2RtpTask( fromUrl,  toUrl,  time + 60, mediaStatus(request,device,key)));
            scheduledExecutorService.schedule(subscriber::onComplete, time + 60, TimeUnit.SECONDS);
        };
    }

    public Flow.Subscriber<SIPRequest> byeSubscriber(String key, MockingDevice device, ConcurrentHashMap<String, Executor> task){
        return new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                log.info("订阅 bye {}", key);
                subscription.request(1);
            }

            @Override
            public void onNext(SIPRequest item) {
                String ip = item.getLocalAddress().getHostAddress();
                String transPort = item.getTopmostViaHeader().getTransport();
                sender.sendResponse(ip, transPort, ((provider, ip1, port) ->
                        SipResponseBuilder.response(item, Response.OK, "OK")));
                onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                onComplete();
            }

            @Override
            public void onComplete() {
                log.info("bye 订阅结束 {}", key);
                subscribe.getByeSubscribe().delPublisher(key);
                Optional.ofNullable(task.get(device.getDeviceCode())).ifPresent(task -> {
                    task.getWatchdog().destroyProcess();
                });
                task.remove(device.getDeviceCode());
            }
        };
    }

    public synchronized void proxyVideo2Rtp(SIPRequest request,String callId, MockingDevice device, Date startTime, Date endTime, String rtpAddr, int rtpPort, TaskProcessor taskProcessor) {
        String fromUrl = URLUtil.completeUrl(proxyConfig.getUrl(), "/video");
        HashMap<String, String> map = new HashMap<>(3);
        String deviceCode = device.getDeviceCode();
        map.put("device_id", deviceCode);
        map.put("begin_time",DateUtil.format(LocalDateTimeUtil.of(startTime.toInstant(), ZoneId.of(GB28181Constant.TIME_ZONE)), DatePattern.PURE_DATETIME_PATTERN) );
        map.put("end_time", DateUtil.format(LocalDateTimeUtil.of(endTime.toInstant(), ZoneId.of(GB28181Constant.TIME_ZONE)), DatePattern.PURE_DATETIME_PATTERN));
        String query = URLUtil.buildQuery(map, StandardCharsets.UTF_8);
        fromUrl = StringUtils.joinWith("?", fromUrl, query);
        log.info("设备: {} 视频 url: {}", deviceCode, fromUrl);
        String toUrl = StringUtils.joinWith("", "rtp://", rtpAddr, ":", rtpPort);
        long time = DateUtil.between(startTime, endTime, DateUnit.SECOND);

        String key = GenericSubscribe.Helper.getKey(Request.BYE, callId);
        subscribe.getByeSubscribe().addPublisher(key);
        taskProcessor.process(request, callId,fromUrl,toUrl,device,key,time);
    }

    @SneakyThrows
    public Executor pushRtpTask(String fromUrl, String toUrl, long time, ExecuteResultHandler resultHandler) {
        return ffmpegSupportService.pushToRtp(fromUrl, toUrl, time, TimeUnit.SECONDS, resultHandler);
    }

    @SneakyThrows
    public Executor pushDownload2RtpTask(String fromUrl, String toUrl, long time, ExecuteResultHandler resultHandler) {
        return ffmpegSupportService.pushDownload2Rtp(fromUrl, toUrl, time, TimeUnit.SECONDS, resultHandler);
    }

    public ExecuteResultHandler mediaStatus(SIPRequest request, MockingDevice device,String key){
        return new ExecuteResultHandler() {
            private void mediaStatus(){
                CallIdHeader requestCallId = request.getCallId();
                String callId = requestCallId.getCallId();
                callbackTask.remove(callId);
                log.info("{} 推流结束, 发送媒体通知", key);
                MediaStatusRequestDTO mediaStatusRequestDTO = MediaStatusRequestDTO.builder()
                        .sn(String.valueOf((int) ((Math.random() * 9 + 1) * 100000)))
                        .deviceId(device.getGbChannelId())
                        .build();

                String tag = request.getFromHeader().getTag();
                sender.sendRequest(((provider, ip, port) -> SipRequestBuilder.createMessageRequest(device,
                        ip, port, 1, XmlUtils.toXml(mediaStatusRequestDTO), SipUtil.generateViaTag(), tag, requestCallId)));
            }

            @Override
            public void onProcessComplete(int exitValue) {
                mediaStatus();
            }

            @Override
            public void onProcessFailed(ExecuteException e) {
                mediaStatus();
            }
        };
    }
}
