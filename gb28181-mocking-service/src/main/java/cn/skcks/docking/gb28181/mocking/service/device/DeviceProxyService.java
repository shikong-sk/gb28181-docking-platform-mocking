package cn.skcks.docking.gb28181.mocking.service.device;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.URLUtil;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.ffmpeg.FfmpegSupportService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.Executor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
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

    private final ConcurrentHashMap<String, Executor> task = new ConcurrentHashMap<>();

    private final SipSender sender;

    private final FfmpegSupportService ffmpegSupportService;

    public synchronized void proxyVideo2Rtp(String callId, MockingDevice device, Date startTime, Date endTime, String rtpAddr, int rtpPort) {
        Optional.ofNullable(task.get(device.getDeviceCode())).ifPresent(task->{
            task.getWatchdog().destroyProcess();
        });
        String fromUrl = URLUtil.completeUrl(proxyConfig.getUrl(), "/video");
        HashMap<String, String> map = new HashMap<>(3);
        String deviceCode = device.getDeviceCode();
        map.put("device_id", deviceCode);
        map.put("begin_time", DateUtil.format(startTime, DatePattern.PURE_DATETIME_FORMAT));
        map.put("end_time", DateUtil.format(endTime, DatePattern.PURE_DATETIME_FORMAT));
        String query = URLUtil.buildQuery(map, StandardCharsets.UTF_8);
        fromUrl = StringUtils.joinWith("?", fromUrl, query);
        log.info("设备: {} 视频 url: {}", deviceCode, fromUrl);
        String toUrl = StringUtils.joinWith("", "rtp://", rtpAddr, ":", rtpPort);
        long time = DateUtil.between(startTime, endTime, DateUnit.SECOND);

        String key = GenericSubscribe.Helper.getKey(Request.BYE, callId);
        subscribe.getByeSubscribe().addPublisher(key);
        Flow.Subscriber<SIPRequest> subscriber = new Flow.Subscriber<>() {

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
        subscribe.getByeSubscribe().addSubscribe(key, subscriber);
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        task.put(device.getDeviceCode(), pushRtpTask( fromUrl,  toUrl,  time + 60));
        scheduledExecutorService.schedule(subscriber::onComplete, time + 60, TimeUnit.SECONDS);
    }

    @SneakyThrows
    public Executor pushRtpTask(String fromUrl, String toUrl, long time){
        return ffmpegSupportService.pushToRtp(fromUrl, toUrl, time, TimeUnit.SECONDS);
    }
}
