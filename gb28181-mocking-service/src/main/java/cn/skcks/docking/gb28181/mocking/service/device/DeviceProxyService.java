package cn.skcks.docking.gb28181.mocking.service.device;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.URLUtil;
import cn.skcks.docking.gb28181.common.json.JsonResponse;
import cn.skcks.docking.gb28181.common.redis.RedisUtil;
import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.core.sip.gb28181.cache.CacheUtil;
import cn.skcks.docking.gb28181.core.sip.gb28181.constant.GB28181Constant;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.media.config.ZlmMediaConfig;
import cn.skcks.docking.gb28181.media.dto.proxy.AddFFmpegSource;
import cn.skcks.docking.gb28181.media.dto.proxy.AddFFmpegSourceResp;
import cn.skcks.docking.gb28181.media.dto.proxy.AddStreamProxy;
import cn.skcks.docking.gb28181.media.dto.proxy.AddStreamProxyResp;
import cn.skcks.docking.gb28181.media.dto.response.ZlmResponse;
import cn.skcks.docking.gb28181.media.dto.rtp.StartSendRtp;
import cn.skcks.docking.gb28181.media.dto.rtp.StartSendRtpResp;
import cn.skcks.docking.gb28181.media.dto.rtp.StopSendRtp;
import cn.skcks.docking.gb28181.media.dto.status.ResponseStatus;
import cn.skcks.docking.gb28181.media.proxy.ZlmMediaService;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.config.sip.FfmpegConfig;
import cn.skcks.docking.gb28181.mocking.config.sip.ZlmHookConfig;
import cn.skcks.docking.gb28181.mocking.config.sip.ZlmRtmpConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.executor.MockingExecutor;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.notify.dto.MediaStatusRequestDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.core.sip.service.VideoCacheManager;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.ffmpeg.FfmpegSupportService;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmPublishHookService;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmStreamChangeHookService;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmStreamNoneReaderHookService;
import cn.skcks.docking.gb28181.sdp.GB28181Description;
import cn.skcks.docking.gb28181.sdp.parser.GB28181DescriptionParser;
import cn.skcks.docking.gb28181.sip.method.invite.response.InviteResponseBuilder;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import gov.nist.javax.sdp.MediaDescriptionImpl;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PreDestroy;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.Executor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    private final VideoCacheManager videoCacheManager;

    private final String DEFAULT_ZLM_APP = "live";
    private final String ZLM_FFMPEG_PROXY_APP = "ffmpeg_proxy";

    private final ZlmStreamNoneReaderHookService zlmStreamNoneReaderHookService;

    private final ZlmPublishHookService zlmPublishHookService;

    private final FfmpegConfig ffmpegConfig;

    private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(128);

    @Qualifier(MockingExecutor.EXECUTOR_BEAN_NAME)
    private final java.util.concurrent.Executor executor;

    public interface TaskProcessor {
        void process(Runnable sendOkResponse,SIPRequest request,String callId,String fromUrl, String toAddr,int toPort, MockingDevice device, String key, long time,String ssrc);
    }

    private void requestZlmPushStream(SIPRequest request, String callId, String fromUrl, String toAddr, int toPort, MockingDevice device, String key, long time, String ssrc) throws Exception{
        GB28181Description gb28181Description = new GB28181DescriptionParser(new String(request.getRawContent())).parse();
        MediaDescription mediaDescription = (MediaDescription)gb28181Description.getMediaDescriptions(true).get(0);
        boolean tcp = StringUtils.containsIgnoreCase(mediaDescription.getMedia().getProtocol(), "TCP");
//        zlmStreamChangeHookService.getRegistHandler(DEFAULT_ZLM_APP).put(callId,()->{
        AtomicReference<String> failMag = new AtomicReference<>("");
        Retryer<StartSendRtpResp> retryer = RetryerBuilder.<StartSendRtpResp>newBuilder()
                .retryIfResult(resp -> {
                    if(resp != null){
                        failMag.set(resp.toString());
                    }
                    return resp.getLocalPort() == null || resp.getLocalPort() <= 0;
                })
                .retryIfException()
                .retryIfRuntimeException()
                // 重试间隔
                .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.MILLISECONDS))
                // 重试次数
                .withStopStrategy(StopStrategies.stopAfterAttempt(10 * 1000))
                .build();
        zlmPublishHookService.getHandler(DEFAULT_ZLM_APP).put(callId,()->{
            executor.execute(()->{
                try {
                    StartSendRtp startSendRtp  = new StartSendRtp();
                    StartSendRtpResp sendRtpResp = retryer.call(() -> {
                        startSendRtp.setApp(DEFAULT_ZLM_APP);
                        startSendRtp.setStream(callId);
                        startSendRtp.setSsrc(ssrc);
                        startSendRtp.setDstUrl(toAddr);
                        startSendRtp.setDstPort(toPort);
                        startSendRtp.setUdp(!tcp);
//                    log.debug("startSendRtp {}",startSendRtp);
                        StartSendRtpResp startSendRtpResp = zlmMediaService.startSendRtp(startSendRtp);
//                    log.debug("startSendRtpResp {}",startSendRtpResp);
                        return startSendRtpResp;
                    });

                    log.info("sendRtp 推流成功 {} {}, req => {}, resp => {}", device.getDeviceCode(),device.getGbChannelId(), startSendRtp, sendRtpResp);
                } catch (Exception e) {
                    log.error("zlm rtp 推流失败, {}, {} {} {}, {}", failMag.get(), device.getDeviceCode(),device.getGbChannelId(), callId, e.getMessage());
                    Optional.ofNullable(zlmStreamChangeHookService.getUnregistHandler(DEFAULT_ZLM_APP).remove(callId))
                            .ifPresent(ZlmStreamChangeHookService.ZlmStreamChangeHookHandler::handler);
                }
            });
        });

        zlmStreamRegistHookEvent(DEFAULT_ZLM_APP, callId, ssrc);
        zlmStreamNoneReaderHookService.getHandler(DEFAULT_ZLM_APP).put(callId,()->{
            sendBye(request,device,key);
        });
    }

    private void zlmStreamRegistHookEvent(String app, String callId, String ssrc){
        zlmStreamChangeHookService.getUnregistHandler(app).put(callId,()->{
            executor.execute(()->{
                ScheduledFuture<?> schedule = scheduledExecutorService.schedule(() -> {
                    StopSendRtp stopSendRtp = new StopSendRtp();
                    stopSendRtp.setApp(app);
                    stopSendRtp.setStream(callId);
                    stopSendRtp.setSsrc(ssrc);

                    log.info("结束 zlm rtp 推流, app {}, stream {}, ssrc {}", app, callId, ssrc);
                    zlmMediaService.stopSendRtp(stopSendRtp);
                }, 10, TimeUnit.SECONDS);

                // 如果 流 在 10秒内 重新注册, 则 取消停止RTP推流
                zlmStreamChangeHookService.getRegistHandler(app).put(callId,()->{
                    schedule.cancel(true);
                    zlmStreamRegistHookEvent(app, callId, ssrc);
                });

                // 如果 注销 后 10.5 秒内 没有再注册, 就彻底取消相关事件的订阅
                scheduledExecutorService.schedule(()->{
                    zlmStreamChangeHookService.getRegistHandler(app).remove(callId);
                },10500, TimeUnit.MILLISECONDS);
            });
        });
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
        return (Runnable sendOkResponse, SIPRequest request,String callId,String fromUrl, String toAddr,int toPort, MockingDevice device, String key, long time,String ssrc) -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                log.error("{}", e.getMessage());
            }

            String ackKey = GenericSubscribe.Helper.getKey(Request.ACK, callId);
            subscribe.getAckSubscribe().addPublisher(ackKey, 1, TimeUnit.MINUTES);
            subscribe.getAckSubscribe().addSubscribe(ackKey, new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(SIPRequest item) {
                    subscribe.getAckSubscribe().delPublisher(ackKey);
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                    Flow.Subscriber<SIPRequest> task = ffmpegTask(request, callbackTask, callId, key, device);
                    try {
                        String zlmRtpUrl = getZlmRtmpUrl(DEFAULT_ZLM_APP, callId);
                        requestZlmPushStream(request, callId, fromUrl, toAddr, toPort, device, key, time, ssrc);
                        FfmpegExecuteResultHandler executeResultHandler = mediaStatus(request, device, key);
                        Executor executor = pushRtpTask(fromUrl, zlmRtpUrl, time + 60, executeResultHandler);
                        scheduledExecutorService.schedule(task::onComplete, time + 60, TimeUnit.SECONDS);
                        callbackTask.put(device.getDeviceCode(), executor);
                        executeResultHandler.waitFor();
                    } catch (Exception e) {
                        sendBye(request, device, "");
                        log.error("{}", e.getMessage());
                    }
                }
            });
            trying(request);
            sendOkResponse.run();
        };
    }

    public TaskProcessor downloadTask(){
        return (Runnable sendOkResponse,SIPRequest request,String callId,String fromUrl, String toAddr,int toPort, MockingDevice device, String key, long time,String ssrc)->{
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                log.error("{}", e.getMessage());
            }
            String ackKey = GenericSubscribe.Helper.getKey(Request.ACK, callId);
            subscribe.getAckSubscribe().addPublisher(ackKey, 1, TimeUnit.MINUTES);
            subscribe.getAckSubscribe().addSubscribe(ackKey, new Flow.Subscriber<>() {
                private SIPRequest ackRequest;
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(SIPRequest item) {
                    ackRequest = item;
                    subscribe.getAckSubscribe().delPublisher(ackKey);
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                    Flow.Subscriber<SIPRequest> task = ffmpegTask(request, downloadTask, callId, key, device);
                    try {
                        if(ackRequest != null){
                            FfmpegExecuteResultHandler executeResultHandler = mediaStatus(request, device, key);
                            if (!ffmpegConfig.getRtp().getUseRtpToDownload()) {
                                String zlmRtpUrl = getZlmRtmpUrl(DEFAULT_ZLM_APP, callId);
                                executor.execute(()->{
                                    try {
                                        requestZlmPushStream(request, callId, fromUrl, toAddr, toPort, device, key, time, ssrc);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });

                                Executor executor = pushDownload2RtpTask(fromUrl, zlmRtpUrl, time, executeResultHandler);
                                scheduledExecutorService.schedule(task::onComplete, time + 60, TimeUnit.SECONDS);
                                downloadTask.put(device.getDeviceCode(), executor);
                            } else {
                                String rtpUrl = getRtpUrl(request);
                                Executor executor = pushDownload2RtpTask(fromUrl, rtpUrl, time, executeResultHandler);
                                scheduledExecutorService.schedule(task::onComplete, time + 60, TimeUnit.SECONDS);
                                downloadTask.put(device.getDeviceCode(), executor);
                            }
                            executeResultHandler.waitFor();
                        }
                    } catch (Exception e) {
                        sendBye(request, device, "");
                        log.error("{}", e.getMessage());
                    }
                }
            });
            trying(request);
            sendOkResponse.run();
        };
    }

    private static String getRtpUrl(SIPRequest request) throws ParseException, SdpException {
        String contentString = new String(request.getRawContent());
        GB28181DescriptionParser gb28181DescriptionParser = new GB28181DescriptionParser(contentString);
        GB28181Description sdp = gb28181DescriptionParser.parse();
        String rtpIp = sdp.getConnection().getAddress();
        MediaDescriptionImpl media = (MediaDescriptionImpl) sdp.getMediaDescriptions(true).get(0);
        return "rtp://" + rtpIp + ":" + media.getMedia().getMediaPort();
    }

    private String getZlmRtmpUrl(String app, String streamId){
        return "rtmp://" + zlmMediaConfig.getIp() + ":" + zlmRtmpConfig.getPort() + "/" + app +"/" + streamId;
    }

    private void trying(SIPRequest request){
        InviteResponseBuilder inviteRequestBuilder = InviteResponseBuilder.builder().build();
        Response tryingInviteResponse = inviteRequestBuilder.createTryingInviteResponse(request);
        String ip = request.getLocalAddress().getHostAddress();
        String transPort = request.getTopmostViaHeader().getTransport();
        sender.sendResponse(ip, transPort, ((provider, ip1, port) -> tryingInviteResponse));
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

    public Flow.Subscriber<SIPRequest> zlmFfmpegByeSubscriber(String key, SIPRequest inviteRequest,MockingDevice device){
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

                String cacheKey = CacheUtil.getKey("ZLM","FFMPEG", "PROXY", key);
                String proxyKey = RedisUtil.StringOps.get(cacheKey);
                log.info("关闭拉流代理 {}", zlmMediaService.delFfmpegSource(proxyKey));
                RedisUtil.KeyOps.delete(cacheKey);
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

    public void pullStreamByZlmFfmpegSource(SIPRequest request,String callId, MockingDevice device, Date start, Date stop,String rtpAddr, int rtpPort, String ssrc){
        Retryer<ZlmResponse<AddFFmpegSourceResp>> retryer = RetryerBuilder.<ZlmResponse<AddFFmpegSourceResp>>newBuilder()
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

        String toUrl = "rtmp://" + zlmMediaConfig.getIp() + ":" + zlmRtmpConfig.getPort() + "/" + ZLM_FFMPEG_PROXY_APP +"/" + callId;
        String key = GenericSubscribe.Helper.getKey(Request.BYE, callId);
        try {
            ZlmResponse<AddFFmpegSourceResp> sourceResp = retryer.call(() -> zlmMediaService.addFfmpegSource(AddFFmpegSource.builder()
                    .srcUrl(getProxyUrl(device,start,stop))
                    .dstUrl(toUrl)
                    .enableHls(false)
                    .enableMp4(false)
                    .timeoutMs(Duration.ofSeconds(30).toMillis())
                    .build()));
            String proxyKey = sourceResp.getData().getKey();
            String cacheKey = CacheUtil.getKey("ZLM","FFMPEG", "PROXY", key);
            RedisUtil.StringOps.set(cacheKey, proxyKey);

            GB28181Description gb28181Description = new GB28181DescriptionParser(new String(request.getRawContent())).parse();
            MediaDescription mediaDescription = (MediaDescription)gb28181Description.getMediaDescriptions(true).get(0);
            boolean tcp = StringUtils.containsIgnoreCase(mediaDescription.getMedia().getProtocol(), "TCP");

            Retryer<StartSendRtpResp> rtpRetryer = rtpRetryer();
            zlmStreamChangeHookService.getRegistHandler(ZLM_FFMPEG_PROXY_APP).put(callId,()->{
                try {
                    rtpRetryer.call(()->{
                        StartSendRtp startSendRtp = new StartSendRtp();
                        startSendRtp.setApp(DEFAULT_ZLM_APP);
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
                    log.error("zlm rtp 推流失败, {} {} {}, {}", device.getDeviceCode(),device.getGbChannelId(), callId, e.getMessage());
                    sendBye(request, device, "");
                }
            });

            zlmStreamChangeHookService.getUnregistHandler(ZLM_FFMPEG_PROXY_APP).put(callId, ()->{
                StopSendRtp stopSendRtp = new StopSendRtp();
                stopSendRtp.setApp(DEFAULT_ZLM_APP);
                stopSendRtp.setStream(callId);
                stopSendRtp.setSsrc(ssrc);

                log.info("结束 zlm rtp 推流, app {}, stream {}, ssrc {}", ZLM_FFMPEG_PROXY_APP, callId, ssrc);
                zlmMediaService.stopSendRtp(stopSendRtp);
            });

            Flow.Subscriber<SIPRequest> subscriber = zlmFfmpegByeSubscriber(key,request,device);
            subscribe.getByeSubscribe().addPublisher(key);
            subscribe.getByeSubscribe().addSubscribe(key, subscriber);
        }catch (Exception e){
            log.error("zlm ffmpeg 拉/推流失败",e);
            sendBye(request, device, "");
        }
    }

    @SneakyThrows
    public void pullLiveStream2Rtp(SIPRequest request,Runnable sendOkResponse,String callId, MockingDevice device, String rtpAddr, int rtpPort, String ssrc){
        String liveCacheKey = CacheUtil.getKey("INVITE", "LIVE", device.getGbDeviceId());
        String liveCache;
        if(liveCacheKey != null){
            liveCache = RedisUtil.StringOps.get(liveCacheKey);
            if(liveCache != null){
                RedisUtil.KeyOps.delete(liveCache);
                // 关闭已存在的实时流 bye 订阅（如果存在）
                subscribe.getByeSubscribe().delPublisher(liveCache);
            }
        }

        trying(request);
        sendOkResponse.run();
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
                    .app(DEFAULT_ZLM_APP)
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

            ScheduledFuture<?> schedule = scheduledExecutorService.schedule(() -> {
                log.warn("到达最长播放时间 {}, 强制关闭实时视频播放 {} {}", proxyConfig.getProxyTimeRange(), device.getGbChannelId(), callId);
                sendBye(request, device, "");
                log.info("关闭拉流代理 {}", zlmMediaService.delStreamProxy(proxyKey));
                RedisUtil.KeyOps.delete(cacheKey);

                StopSendRtp stopSendRtp = new StopSendRtp();
                stopSendRtp.setApp(DEFAULT_ZLM_APP);
                stopSendRtp.setStream(callId);
                stopSendRtp.setSsrc(ssrc);

                log.info("结束 zlm rtp 推流, app {}, stream {}, ssrc {}", DEFAULT_ZLM_APP, callId, ssrc);
                zlmMediaService.stopSendRtp(stopSendRtp);
            }, proxyConfig.getRealTimeVideoMaxPlayTime().toMillis(), TimeUnit.MILLISECONDS);

            Retryer<StartSendRtpResp> rtpRetryer = rtpRetryer();
            zlmStreamChangeHookService.getRegistHandler(DEFAULT_ZLM_APP).put(callId,()->{
                try {
                    rtpRetryer.call(()->{
                        StartSendRtp startSendRtp = new StartSendRtp();
                        startSendRtp.setApp(DEFAULT_ZLM_APP);
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
                    log.error("zlm rtp 推流失败, {} {} {}, {}", device.getDeviceCode(),device.getGbChannelId(), callId, e.getMessage());
                    sendBye(request, device, "");
                    log.info("关闭拉流代理 {}", zlmMediaService.delStreamProxy(proxyKey));
                    RedisUtil.KeyOps.delete(cacheKey);
                    schedule.cancel(true);
                }
            });

            zlmStreamChangeHookService.getUnregistHandler(DEFAULT_ZLM_APP).put(callId,()-> {
                StopSendRtp stopSendRtp = new StopSendRtp();
                stopSendRtp.setApp(DEFAULT_ZLM_APP);
                stopSendRtp.setStream(callId);
                stopSendRtp.setSsrc(ssrc);

                log.info("结束 zlm rtp 推流, app {}, stream {}, ssrc {}", DEFAULT_ZLM_APP, callId, ssrc);
                zlmMediaService.stopSendRtp(stopSendRtp);
                log.info("关闭拉流代理 {}", zlmMediaService.delStreamProxy(proxyKey));
                RedisUtil.KeyOps.delete(cacheKey);
            });

            Flow.Subscriber<SIPRequest> subscriber = zlmByeSubscriber(key,request,device);
            liveCache = CacheUtil.getKey("INVITE", "LIVE", device.getGbDeviceId());
            RedisUtil.StringOps.set(liveCache, key);
            subscribe.getByeSubscribe().addPublisher(key);
            subscribe.getByeSubscribe().addSubscribe(key, subscriber);
        } catch (Exception e) {
            log.error("zlm 代理拉流失败",e);
            sendBye(request, device, "");
        }
    }

    @SneakyThrows
    private String getProxyUrl(MockingDevice device, Date startTime, Date endTime){
        if(proxyConfig.getPreDownloadForRecordInfo().getEnable() && !ffmpegConfig.getUseZlmFfmpeg()){
            CompletableFuture<JsonResponse<String>> task = videoCacheManager.get(device.getDeviceCode(), startTime, endTime);
            if(task != null){
                if(task.isDone()){
                    String filePath = task.get().getData();
                    File file = new File(filePath);
                    if(StringUtils.isNotBlank(filePath) && file.exists()){
                        log.info("本地视频已缓存, 将从本地缓存推流, 缓存文件 => {}", filePath);
                        return filePath;
                    } else {
                        log.info("缓存已失效, 将直接使用 http代理 推流 并 重新缓存, 缓存文件 => {}", filePath);
                        videoCacheManager.addTask(device.getDeviceCode(), startTime, endTime);
                    }
                }
            }
        }

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
        return fromUrl;
    }

    public void proxyVideo2Rtp(Runnable sendOkResponse,SIPRequest request, String callId, MockingDevice device, Date startTime, Date endTime, String rtpAddr, int rtpPort, String ssrc, TaskProcessor taskProcessor) {
        String fromUrl = getProxyUrl(device, startTime, endTime);
        String key = GenericSubscribe.Helper.getKey(Request.BYE, callId);
        subscribe.getByeSubscribe().addPublisher(key);
        long time = DateUtil.between(startTime, endTime, DateUnit.SECOND);
        taskProcessor.process(sendOkResponse,request, callId,fromUrl,rtpAddr, rtpPort,device,key,time, ssrc);
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

        private void close(){
            CallIdHeader requestCallId = request.getCallId();
            String callId = requestCallId.getCallId();
            callbackTask.remove(callId);
            Optional<ZlmStreamChangeHookService.ZlmStreamChangeHookHandler> optionalZlmStreamChangeHookHandler =
                    Optional.ofNullable(zlmStreamChangeHookService.getUnregistHandler(DEFAULT_ZLM_APP).remove(callId));
            Optional<ZlmStreamNoneReaderHookService.ZlmStreamNoneReaderHookHandler> optionalZlmStreamNoneReaderHandler =
                    Optional.ofNullable(zlmStreamNoneReaderHookService.getHandler(DEFAULT_ZLM_APP).remove(callId));
            // 如果取消注册已完成就直接结束, 否则发送 bye请求 结束
            if(optionalZlmStreamChangeHookHandler.isEmpty() && optionalZlmStreamNoneReaderHandler.isEmpty()){
                return;
            }

            optionalZlmStreamChangeHookHandler.ifPresent(handler -> {
                log.warn("流改变事件未结束 ZlmStreamChange {} {}, 强制结束", DEFAULT_ZLM_APP,callId);
            });
            optionalZlmStreamNoneReaderHandler.ifPresent(handler -> {
                log.warn("流无人观看事件未结束 ZlmStreamNoneReader {} {}, 强制结束", DEFAULT_ZLM_APP, callId);
            });
            sendBye(request,device,key);
        }

        @SneakyThrows
        private void mediaStatus(){
            int num = taskNum.decrementAndGet();
            log.info("当前任务数 {}", num);
            // 等待zlm推流结束, 如果 ffmpeg 结束 3分钟内 未能推流完成就主动结束
            scheduledExecutorService.schedule(this::close,5,TimeUnit.MINUTES);
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
            int num = taskNum.decrementAndGet();
            log.info("当前任务数 {}", num);
            log.error("ffmpeg 执行失败", e);
            close();
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

    private Retryer<StartSendRtpResp> rtpRetryer(){
         return RetryerBuilder.<StartSendRtpResp>newBuilder()
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
    }
}
