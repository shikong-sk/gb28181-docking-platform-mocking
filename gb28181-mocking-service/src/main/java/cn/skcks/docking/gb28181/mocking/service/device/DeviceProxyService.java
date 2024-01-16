package cn.skcks.docking.gb28181.mocking.service.device;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.URLUtil;
import cn.skcks.docking.gb28181.common.redis.RedisUtil;
import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.core.sip.gb28181.cache.CacheUtil;
import cn.skcks.docking.gb28181.core.sip.gb28181.constant.GB28181Constant;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.media.config.ZlmMediaConfig;
import cn.skcks.docking.gb28181.media.dto.proxy.AddStreamProxy;
import cn.skcks.docking.gb28181.media.dto.proxy.AddStreamProxyResp;
import cn.skcks.docking.gb28181.media.dto.response.ZlmResponse;
import cn.skcks.docking.gb28181.media.dto.rtp.StartSendRtp;
import cn.skcks.docking.gb28181.media.dto.rtp.StartSendRtpResp;
import cn.skcks.docking.gb28181.media.dto.status.ResponseStatus;
import cn.skcks.docking.gb28181.media.proxy.ZlmMediaService;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.config.sip.ZlmHookConfig;
import cn.skcks.docking.gb28181.mocking.config.sip.ZlmRtmpConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.notify.dto.MediaStatusRequestDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.ffmpeg.FfmpegSupportService;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmStreamChangeHookService;
import cn.skcks.docking.gb28181.sdp.GB28181Description;
import cn.skcks.docking.gb28181.sdp.parser.GB28181DescriptionParser;
import cn.skcks.docking.gb28181.sip.method.invite.response.InviteResponseBuilder;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PreDestroy;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.Executor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProxyService {
    private final ZlmHookConfig zlmHookConfig;
    private final DeviceProxyConfig proxyConfig;

    private final SipSubscribe subscribe;

    private static final ConcurrentHashMap<String, Executor> callbackTask = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Executor> downloadTask = new ConcurrentHashMap<>();

    @Getter
    private static final AtomicInteger taskNum = new AtomicInteger(0);

    private final SipSender sender;

    private final FfmpegSupportService ffmpegSupportService;
    private final ZlmMediaService zlmMediaService;
    private final ZlmMediaConfig zlmMediaConfig;
    private final ZlmStreamChangeHookService zlmStreamChangeHookService;
    private final ZlmRtmpConfig zlmRtmpConfig;
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public interface TaskProcessor {
        void process(SIPRequest request,String callId,String fromUrl, String toAddr,int toPort, MockingDevice device, String key, long time,String ssrc);
    }

    private String requestZlmPushStream(ScheduledFuture<?> schedule, SIPRequest request, String callId, String fromUrl, String toAddr, int toPort, MockingDevice device, String key, long time, String ssrc) throws Exception{
        GB28181Description gb28181Description = new GB28181DescriptionParser(new String(request.getRawContent())).parse();
        MediaDescription mediaDescription = (MediaDescription)gb28181Description.getMediaDescriptions(true).get(0);
        boolean tcp = StringUtils.containsIgnoreCase(mediaDescription.getMedia().getProtocol(), "TCP");
        zlmStreamChangeHookService.getRegistHandler().put(callId,()->{
            schedule.cancel(false);
            Retryer<StartSendRtpResp> retryer = RetryerBuilder.<StartSendRtpResp>newBuilder()
                    .retryIfResult(resp -> resp.getLocalPort() == null || resp.getLocalPort() <= 0)
                    .retryIfException()
                    .retryIfRuntimeException()
                    // 重试间隔
                    .withWaitStrategy(WaitStrategies.fixedWait(3, TimeUnit.SECONDS))
                    // 重试次数
                    .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                    .build();
            try {
                retryer.call(()->{
                    StartSendRtp startSendRtp = new StartSendRtp();
                    startSendRtp.setApp("live");
                    startSendRtp.setStream(callId);
                    startSendRtp.setSsrc(ssrc);
                    startSendRtp.setDstUrl(toAddr);
                    startSendRtp.setDstPort(toPort);
                    startSendRtp.setUdp(!tcp);
                    log.info("startSendRtp {}",startSendRtp);
                    StartSendRtpResp startSendRtpResp = zlmMediaService.startSendRtp(startSendRtp);
                    log.info("startSendRtpResp {}",startSendRtpResp);
                    return startSendRtpResp;
                });
            } catch (Exception e) {
                schedule.cancel(true);
                Optional.ofNullable(zlmStreamChangeHookService.getUnregistHandler().remove(callId))
                        .ifPresent(ZlmStreamChangeHookService.ZlmStreamChangeHookHandler::handler);
                throw new RuntimeException(e);
            }
        });
        zlmStreamChangeHookService.getUnregistHandler().put(callId,()->{
            sendBye(request,device,key);
        });
        return "rtmp://" + zlmMediaConfig.getIp() + ":" + zlmRtmpConfig.getPort() + "/live/" + callId;
    }

    private Flow.Subscriber<SIPRequest> ffmpegTask(SIPRequest request,ConcurrentHashMap<String, Executor> tasks, String callId, String key, MockingDevice device){
        Optional.ofNullable(tasks.get(callId)).ifPresent(task->{
            task.getWatchdog().destroyProcess();
        });
        Flow.Subscriber<SIPRequest> subscriber = ffmpegByeSubscriber(request, key, device, tasks);
        subscribe.getByeSubscribe().addSubscribe(key, subscriber);
        int num = taskNum.incrementAndGet();
        log.info("当前任务数 {}", num);
        return subscriber;
    }

    public TaskProcessor playbackTask(){
        return (SIPRequest request,String callId,String fromUrl, String toAddr,int toPort, MockingDevice device, String key, long time,String ssrc) -> {
            Flow.Subscriber<SIPRequest> task = ffmpegTask(request, callbackTask, callId, key, device);
            ScheduledFuture<?> schedule = trying(request);
            try {
                String zlmRtpUrl = requestZlmPushStream(schedule, request, callId, fromUrl, toAddr, toPort, device, key, time, ssrc);
                FfmpegExecuteResultHandler executeResultHandler = mediaStatus(request, device, key);
                Executor executor = pushRtpTask(fromUrl, zlmRtpUrl, time + 60, executeResultHandler);
                scheduledExecutorService.schedule(task::onComplete, time + 60, TimeUnit.SECONDS);
                callbackTask.put(device.getDeviceCode(), executor);
                executeResultHandler.waitFor();
            } catch (Exception e) {
                schedule.cancel(true);
                sendBye(request,device,"");
                throw new RuntimeException(e);
            }
        };
    }

    public TaskProcessor downloadTask(){
        return (SIPRequest request,String callId,String fromUrl, String toAddr,int toPort, MockingDevice device, String key, long time,String ssrc)->{
            Flow.Subscriber<SIPRequest> task = ffmpegTask(request, downloadTask, callId, key, device);
            ScheduledFuture<?> schedule = trying(request);
            try {
                String zlmRtpUrl = requestZlmPushStream(schedule, request, callId, fromUrl, toAddr, toPort, device, key, time, ssrc);
                FfmpegExecuteResultHandler executeResultHandler = mediaStatus(request, device, key);
                Executor executor = pushDownload2RtpTask(fromUrl, zlmRtpUrl, time + 60, executeResultHandler);
                scheduledExecutorService.schedule(task::onComplete, time + 60, TimeUnit.SECONDS);
                downloadTask.put(device.getDeviceCode(), executor);
                executeResultHandler.waitFor();
            } catch (Exception e) {
                schedule.cancel(true);
                sendBye(request,device,"");
                throw new RuntimeException(e);
            }
        };
    }

    private ScheduledFuture<?> trying(SIPRequest request){
        return scheduledExecutorService.schedule(() -> {
            InviteResponseBuilder inviteRequestBuilder = InviteResponseBuilder.builder().build();
            Response tryingInviteResponse = inviteRequestBuilder.createTryingInviteResponse(request);
            String ip = request.getLocalAddress().getHostAddress();
            String transPort = request.getTopmostViaHeader().getTransport();
            sender.sendResponse(ip, transPort, ((provider, ip1, port) -> tryingInviteResponse));
        }, 200, TimeUnit.MILLISECONDS);
    }

    public Flow.Subscriber<SIPRequest> ffmpegByeSubscriber(SIPRequest inviteRequest,String key, MockingDevice device, ConcurrentHashMap<String, Executor> task){
        return new Flow.Subscriber<>() {
            SIPRequest request;
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                log.info("订阅 bye {}", key);
                subscription.request(1);
            }

            @Override
            public void onNext(SIPRequest item) {
                request = item;
                onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                onComplete();
            }

            @Override
            public void onComplete() {
                log.info("bye 订阅结束 {}", key);
                if(request == null){
                    sendBye(inviteRequest,device,"");
                } else {
                    String ip = request.getLocalAddress().getHostAddress();
                    String transPort = request.getTopmostViaHeader().getTransport();
                    sender.sendResponse(ip, transPort, ((provider, ip1, port) ->
                            SipResponseBuilder.response(request, Response.OK, "OK")));
                }

                subscribe.getByeSubscribe().delPublisher(key);
                Optional.ofNullable(task.get(device.getDeviceCode())).ifPresent(task -> {
                    task.getWatchdog().destroyProcess();
                });
                task.remove(device.getDeviceCode());
            }
        };
    }

    public Flow.Subscriber<SIPRequest> zlmByeSubscriber(String key, SIPRequest inviteRequest,MockingDevice device){
        return new Flow.Subscriber<>() {
            private SIPRequest request;
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                log.info("订阅 bye {}", key);
                subscription.request(1);
            }

            @Override
            public void onNext(SIPRequest item) {
                request = item;
                subscribe.getByeSubscribe().delPublisher(key);
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {
                log.info("bye 订阅结束 {}", key);
                if(request == null){
                    sendBye(inviteRequest,device,"");
                } else {
                    String ip = request.getLocalAddress().getHostAddress();
                    String transPort = request.getTopmostViaHeader().getTransport();
                    sender.sendResponse(ip, transPort, ((provider, ip1, port) ->
                            SipResponseBuilder.response(request, Response.OK, "OK")));
                }

                String cacheKey = CacheUtil.getKey("INVITE", "PROXY", key);
                String proxyKey = RedisUtil.StringOps.get(cacheKey);
                log.info("关闭拉流代理 {}", zlmMediaService.delStreamProxy(proxyKey));
                RedisUtil.KeyOps.delete(cacheKey);
            }
        };
    }

    @SneakyThrows
    public void pullLiveStream2Rtp(SIPRequest request,String callId, MockingDevice device, String rtpAddr, int rtpPort, String ssrc){
        Retryer<ZlmResponse<AddStreamProxyResp>> retryer = RetryerBuilder.<ZlmResponse<AddStreamProxyResp>>newBuilder()
                .retryIfResult(resp -> {
                    log.info("resp {}", resp);
                    return !resp.getCode().equals(ResponseStatus.Success);
                })
                .retryIfException()
                .retryIfRuntimeException()
                // 重试间隔
                .withWaitStrategy(WaitStrategies.fixedWait(3, TimeUnit.SECONDS))
                // 重试次数
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        String liveUrl = device.getLiveStream();

        try {
            ZlmResponse<AddStreamProxyResp> proxy = retryer.call(() -> zlmMediaService.addStreamProxy(AddStreamProxy.builder()
                    .url(liveUrl)
                    .app("live")
                    .stream(callId)
                    .build()));

            log.info("使用 zlm 代理拉流 {}", proxy);
            String proxyKey = proxy.getData().getKey();
            String key = GenericSubscribe.Helper.getKey(Request.BYE, callId);
            String cacheKey = CacheUtil.getKey("INVITE", "PROXY", key);
            RedisUtil.StringOps.set(cacheKey, proxyKey);

            GB28181Description gb28181Description = new GB28181DescriptionParser(new String(request.getRawContent())).parse();
            MediaDescription mediaDescription = (MediaDescription)gb28181Description.getMediaDescriptions(true).get(0);
            boolean tcp = StringUtils.containsIgnoreCase(mediaDescription.getMedia().getProtocol(), "TCP");

            Retryer<StartSendRtpResp> rtpRetryer = RetryerBuilder.<StartSendRtpResp>newBuilder()
                    .retryIfResult(resp -> {
                        log.info("resp {}", resp);
                        return resp.getLocalPort() == null || resp.getLocalPort() <= 0;
                    })
                    .retryIfException()
                    .retryIfRuntimeException()
                    // 重试间隔
                    .withWaitStrategy(WaitStrategies.fixedWait(3, TimeUnit.SECONDS))
                    // 重试次数
                    .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                    .build();

            zlmStreamChangeHookService.getRegistHandler().put(callId,()->{
                try {
                    rtpRetryer.call(()->{
                        StartSendRtp startSendRtp = new StartSendRtp();
                        startSendRtp.setApp("live");
                        startSendRtp.setStream(callId);
                        startSendRtp.setSsrc(ssrc);
                        startSendRtp.setDstUrl(rtpAddr);
                        startSendRtp.setDstPort(rtpPort);
                        startSendRtp.setUdp(!tcp);
                        log.info("startSendRtp {}",startSendRtp);
                        StartSendRtpResp startSendRtpResp = zlmMediaService.startSendRtp(startSendRtp);
                        log.info("startSendRtpResp {}",startSendRtpResp);
                        return startSendRtpResp;
                    });
                } catch (Exception e){
                    log.error("zlm rtp 推流失败",e);
                    sendBye(request, device, "");
                }
            });

            // zlmStreamChangeHookService.getUnregistHandler().put(callId,()-> sendBye(request,device,key));
            Flow.Subscriber<SIPRequest> subscriber = zlmByeSubscriber(key,request,device);
            subscribe.getByeSubscribe().addPublisher(key);
            subscribe.getByeSubscribe().addSubscribe(key, subscriber);
        } catch (Exception e) {
            log.error("zlm 代理拉流失败",e);
            sendBye(request, device, "");
        }
    }

    public void proxyVideo2Rtp(SIPRequest request,String callId, MockingDevice device, Date startTime, Date endTime, String rtpAddr, int rtpPort, String ssrc, TaskProcessor taskProcessor) {
        String fromUrl = URLUtil.completeUrl(proxyConfig.getUrl(), "/video");
        HashMap<String, String> map = new HashMap<>(3);
        String deviceCode = device.getDeviceCode();
        map.put("device_id", deviceCode);
        LocalDateTime fixedBeginTime = LocalDateTimeUtil.of(startTime.toInstant(), ZoneId.of(GB28181Constant.TIME_ZONE)).minus(zlmHookConfig.getFixed());
        map.put("begin_time",DateUtil.format(fixedBeginTime, DatePattern.PURE_DATETIME_PATTERN) );
        map.put("end_time", DateUtil.format(LocalDateTimeUtil.of(endTime.toInstant(), ZoneId.of(GB28181Constant.TIME_ZONE)), DatePattern.PURE_DATETIME_PATTERN));
        map.put("useDownload", String.valueOf(proxyConfig.getUseDownloadToPlayback()));
        String query = URLUtil.buildQuery(map, StandardCharsets.UTF_8);
        fromUrl = StringUtils.joinWith("?", fromUrl, query);
        log.info("设备: {} 视频 url: {}", deviceCode, fromUrl);
        long time = DateUtil.between(startTime, endTime, DateUnit.SECOND);

        String key = GenericSubscribe.Helper.getKey(Request.BYE, callId);
        subscribe.getByeSubscribe().addPublisher(key);
        taskProcessor.process(request, callId,fromUrl,rtpAddr, rtpPort,device,key,time, ssrc);
    }

    @SneakyThrows
    public Executor pushRtpTask(String fromUrl, String toUrl, long time, ExecuteResultHandler resultHandler) {
        return ffmpegSupportService.pushToRtp(fromUrl, toUrl, time, TimeUnit.SECONDS, resultHandler);
    }

    @SneakyThrows
    public Executor pushDownload2RtpTask(String fromUrl, String toUrl, long time, ExecuteResultHandler resultHandler) {
        return ffmpegSupportService.pushDownload2Rtp(fromUrl, toUrl, time, TimeUnit.SECONDS, resultHandler);
    }

    @RequiredArgsConstructor
    public class FfmpegExecuteResultHandler implements ExecuteResultHandler {
        private final static long SLEEP_TIME_MS = 50;
        @Setter(AccessLevel.PRIVATE)
        private boolean hasResult = false;

        private final SIPRequest request;
        private final MockingDevice device;
        private final String key;

        @SneakyThrows
        private void mediaStatus(){
            int num = taskNum.decrementAndGet();
            log.info("当前任务数 {}", num);
            // 等待zlm推流结束, 如果 ffmpeg 结束 30秒 未能推流完成就主动结束
            scheduledExecutorService.schedule(()->{
                CallIdHeader requestCallId = request.getCallId();
                String callId = requestCallId.getCallId();
                callbackTask.remove(callId);
                Optional<ZlmStreamChangeHookService.ZlmStreamChangeHookHandler> optionalZlmStreamChangeHookHandler =
                        Optional.ofNullable(zlmStreamChangeHookService.getUnregistHandler().remove(callId));
                // 如果取消注册已完成就直接结束, 否则发送 bye请求 结束
                if(optionalZlmStreamChangeHookHandler.isEmpty()){
                    return;
                }
                sendBye(request,device,key);
            },30,TimeUnit.SECONDS);
        }

        public boolean hasResult() {
            return hasResult;
        }

        @SneakyThrows
        public void waitFor() {
            while (!hasResult()) {
                Thread.sleep(SLEEP_TIME_MS);
            }
        }

        @Override
        public void onProcessComplete(int exitValue) {
            hasResult = true;
            mediaStatus();
        }

        @Override
        public void onProcessFailed(ExecuteException e) {
            hasResult = true;
            mediaStatus();
        }
    }

    public FfmpegExecuteResultHandler mediaStatus(SIPRequest request, MockingDevice device,String key){
        return new FfmpegExecuteResultHandler(request,device,key);
    }

    /**
     * 程序退出时全部销毁
     */
    @PreDestroy
    private void destroy(){
        callbackTask.values().parallelStream().forEach(executor -> executor.getWatchdog().destroyProcess());
        downloadTask.values().parallelStream().forEach(executor -> executor.getWatchdog().destroyProcess());
    }

    private void sendBye(SIPRequest request, MockingDevice device, String key){
        CallIdHeader requestCallId = request.getCallId();
        log.info("{} 推流结束, 发送媒体通知", key);
        MediaStatusRequestDTO mediaStatusRequestDTO = MediaStatusRequestDTO.builder()
                .sn(String.valueOf((int) ((Math.random() * 9 + 1) * 100000)))
                .deviceId(device.getGbChannelId())
                .build();

        String tag = request.getFromHeader().getTag();
        sender.sendRequest(((provider, ip, port) -> SipRequestBuilder.createMessageRequest(device,
                ip, port, 1, XmlUtils.toXml(mediaStatusRequestDTO), SipUtil.generateViaTag(), tag, requestCallId)));

        String ip = request.getLocalAddress().getHostAddress();
        SipURI targetUri = (SipURI) request.getFromHeader().getAddress().getURI();
        String targetId = targetUri.getUser();
        String targetIp = request.getRemoteAddress().getHostAddress();
        int targetPort = request.getTopmostViaHeader().getPort();
        String transport = request.getTopmostViaHeader().getTransport();
        long seqNumber = request.getCSeq().getSeqNumber() + 1;
        SipProvider provider = sender.getProvider(transport, ip);
        CallIdHeader newCallId = request.getCallId();
        Request byeRequest = SipRequestBuilder.createByeRequest(targetIp, targetPort, seqNumber, targetId, SipUtil.generateFromTag(), null, newCallId.getCallId());
        try{
            provider.sendRequest(byeRequest);
        }catch (Exception e){
            log.error("bye 请求发送失败 {}",e.getMessage());
        }
    }
}
