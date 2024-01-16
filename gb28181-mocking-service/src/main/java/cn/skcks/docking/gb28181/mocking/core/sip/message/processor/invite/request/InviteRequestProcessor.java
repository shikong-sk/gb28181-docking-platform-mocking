package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.invite.request;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.processor.MessageProcessor;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.config.sip.FfmpegConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceProxyService;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceService;
import cn.skcks.docking.gb28181.sdp.GB28181Description;
import cn.skcks.docking.gb28181.sdp.GB28181SDPBuilder;
import cn.skcks.docking.gb28181.sdp.media.MediaStreamMode;
import cn.skcks.docking.gb28181.sdp.parser.GB28181DescriptionParser;
import gov.nist.javax.sdp.TimeDescriptionImpl;
import gov.nist.javax.sdp.fields.TimeField;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import javax.sdp.*;
import javax.sip.RequestEvent;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.time.Duration;
import java.util.Date;
import java.util.EventObject;
import java.util.Vector;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
@Component
@SuppressWarnings("Duplicates")
public class InviteRequestProcessor implements MessageProcessor, SmartLifecycle {
    private final SipListener sipListener;

    private final SipSender sender;

    private final DeviceProxyService deviceProxyService;

    private final DeviceService deviceService;

    private final SipSubscribe subscribe;

    private final FfmpegConfig ffmpegConfig;

    private final DeviceProxyConfig deviceProxyConfig;

    private boolean running;

    @PostConstruct
    @Override
    public void init() {
        sipListener.addRequestProcessor(Request.INVITE, this);
    }

    @SuppressWarnings("Duplicates")
    @SneakyThrows
    @Override
    public void process(EventObject eventObject) {
        RequestEvent requestEvent = (RequestEvent) eventObject;
        SIPRequest request = (SIPRequest) requestEvent.getRequest();
        String senderIp = request.getLocalAddress().getHostAddress();
        String transport = request.getTopmostViaHeader().getTransport();
        String content = new String(request.getRawContent());
        GB28181Description gb28181Description = new GB28181DescriptionParser(content).parse();
        log.info("解析的 sdp信息: \n{}", gb28181Description);
        String id = gb28181Description.getOrigin().getUsername();
        MockingDevice device = deviceService.getDeviceByGbChannelId(id).orElse(null);
        if (device == null) {
            log.error("未能找到 deviceId: {} 的相关信息", id);
            sender.sendResponse(senderIp, transport, notFound(request));
            return;
        }
        Vector<?> mediaDescriptions = gb28181Description.getMediaDescriptions(true);
        log.info("mediaDescriptions {}", mediaDescriptions);
        mediaDescriptions.stream().filter(item -> {
            MediaDescription mediaDescription = (MediaDescription) item;
            Media media = mediaDescription.getMedia();
            try {
                Vector<?> mediaFormats = media.getMediaFormats(false);
                return mediaFormats.contains("98");
            } catch (SdpParseException e) {
                log.error("sdp media 解析异常: {}", e.getMessage());
                return false;
            }
        }).findFirst().ifPresentOrElse((item) -> {
            SessionName sessionName = gb28181Description.getSessionName();
            try {
                String type = sessionName.getValue();
                log.info("type {}", type);
                if (StringUtils.equalsAnyIgnoreCase(type, "Play", "PlayBack")) {
                    log.info("点播/回放请求");
                    if (StringUtils.equalsIgnoreCase(type, "Play")) {
                        play(request, device, gb28181Description, (MediaDescription) item);
                    } else {
                        playback(request, device, gb28181Description, (MediaDescription) item);
                    }
                } else if (StringUtils.equalsIgnoreCase(type, "Download")) {
                    log.info("下载请求");
                    download(request, device, gb28181Description, (MediaDescription) item);
                } else {
                    log.error("未知请求类型: {}", type);
                    sender.sendResponse(senderIp, transport, unsupported(request));
                }
            } catch (SdpParseException e) {
                log.error("sdp 解析异常: {}", e.getMessage());
            }
        }, () -> {
            log.info("未找到支持的媒体类型");
            sender.sendResponse(senderIp, transport, unsupported(request));
        });
    }

    private SipSender.SendResponse notFound(SIPRequest request) {
        return (provider, ip, port) -> SipResponseBuilder.response(request, Response.NOT_FOUND,
                "Not Found");
    }

    private SipSender.SendResponse unsupported(SIPRequest request) {
        return (provider, ip, port) -> SipResponseBuilder.response(request, Response.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Media Type");
    }

    private SipSender.SendResponse forbidden(SIPRequest request) {
        return (provider, ip, port) -> SipResponseBuilder.response(request, Response.FORBIDDEN,
                "Only support playback with " + deviceProxyConfig.getProxyTimeRange().getSeconds() + " secs video");
    }

    /**
     * 视频点播
     *
     * @param gb28181Description gb28181 sdp
     * @param mediaDescription   媒体描述符
     */
    @SneakyThrows
    private void play(SIPRequest request, MockingDevice device, GB28181Description gb28181Description, MediaDescription mediaDescription) {
        TimeField timeField = new TimeField();
        timeField.setZero();
        SdpFactory.getInstance().createTimeDescription(timeField);

        String channelId = gb28181Description.getOrigin().getUsername();
        log.info("通道id: {}", channelId);
        String address = gb28181Description.getOrigin().getAddress();
        log.info("目标地址: {}", address);
        Media media = mediaDescription.getMedia();
        int port = media.getMediaPort();
        log.info("目标端口号: {}", port);

        String senderIp = request.getLocalAddress().getHostAddress();
        String transport = request.getTopmostViaHeader().getTransport();
        if(StringUtils.isBlank(device.getLiveStream())){
            log.warn("设备({} => {}) 无可用实时流地址, 返回 418", device.getGbDeviceId(), channelId);
            sender.sendResponse(senderIp, transport, unsupported(request));
            return;
        }

        String ssrc = gb28181Description.getSsrcField().getSsrc();
        GB28181Description sdp = GB28181SDPBuilder.Sender.build(GB28181SDPBuilder.Action.PLAY,
                device.getGbDeviceId(),
                channelId, Connection.IP4, address, port,
                ssrc,
                MediaStreamMode.of(((MediaDescription) gb28181Description.getMediaDescriptions(true).get(0)).getMedia().getProtocol()),
                SdpFactory.getInstance().createTimeDescription(timeField));
        // playback(request, device, gb28181Description, mediaDescription, time);
        String callId = request.getCallId().getCallId();
        String key = GenericSubscribe.Helper.getKey(Request.ACK, callId);
        subscribe.getAckSubscribe().addPublisher(key);
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        final ScheduledFuture<?>[] schedule = new ScheduledFuture<?>[1];
        Flow.Subscriber<SIPRequest> subscriber = playSubscriber(request,callId,device,address,port,key,ssrc,schedule);
        // 60秒超时计时器
        schedule[0] = scheduledExecutorService.schedule(subscriber::onComplete, 60 , TimeUnit.SECONDS);
        // 推流 ack 事件订阅
        subscribe.getAckSubscribe().addSubscribe(key, subscriber);

        scheduledExecutorService.schedule(()->{
            // 发送 sdp 响应
            sender.sendResponse(senderIp, transport, (ignore, ignore2, ignore3) -> SipResponseBuilder.responseSdp(request, sdp));
        }, 1,TimeUnit.SECONDS);
    }

    /**
     * 模拟设备 录像回放 当前小时至当前时间录像
     *
     * @param gb28181Description gb28181 sdp
     * @param mediaDescription   媒体描述符
     */
    @SneakyThrows
    private void playback(SIPRequest request, MockingDevice device, GB28181Description gb28181Description, MediaDescription mediaDescription) {
        TimeDescriptionImpl timeDescription = (TimeDescriptionImpl) gb28181Description.getTimeDescriptions(true).get(0);
        TimeField time = (TimeField) timeDescription.getTime();
        playback(request, device, gb28181Description, mediaDescription, time);
    }

    @SneakyThrows
    private void playback(SIPRequest request, MockingDevice device, GB28181Description gb28181Description, MediaDescription mediaDescription, TimeField time) {
        playback(request, device, gb28181Description, mediaDescription, time, false);
    }

    @SneakyThrows
    private void download(SIPRequest request, MockingDevice device, GB28181Description gb28181Description, MediaDescription mediaDescription) {
        TimeDescriptionImpl timeDescription = (TimeDescriptionImpl) gb28181Description.getTimeDescriptions(true).get(0);
        TimeField time = (TimeField) timeDescription.getTime();
        playback(request, device, gb28181Description, mediaDescription, time, true);
    }

    @SneakyThrows
    private void playback(SIPRequest request, MockingDevice device, GB28181Description gb28181Description, MediaDescription mediaDescription, TimeField time, boolean isDownload) {
        Date start = new Date(time.getStartTime() * 1000);
        Date stop = new Date(time.getStopTime() * 1000);
        log.info("{} ~ {}", start, stop);

        String senderIp = request.getLocalAddress().getHostAddress();
        String transport = request.getTopmostViaHeader().getTransport();
        if(deviceProxyConfig.getProxyVideoInTimeRange()){
            long between = DateUtil.between(start, stop, DateUnit.SECOND);
            long seconds = deviceProxyConfig.getProxyTimeRange().getSeconds();
            if( between > seconds ){
                log.info("请求回放/下载时长 {} 大于所设定 支持时长范围 {}",between, seconds);
                sender.sendResponse(senderIp, transport, forbidden(request));
                return;
            }
        }

        String channelId = gb28181Description.getOrigin().getUsername();
        log.info("通道id: {}", channelId);
        String address = gb28181Description.getOrigin().getAddress();
        log.info("目标地址: {}", address);
        Media media = mediaDescription.getMedia();
        int port = media.getMediaPort();
        log.info("目标端口号: {}", port);
        int taskNum = DeviceProxyService.getTaskNum().get();
        log.info("当前任务数 {}", taskNum);
        if(ffmpegConfig.getTask().getMax() > 0 && taskNum >= ffmpegConfig.getTask().getMax()){
            log.warn("任务数过多 性能受限, 返回 486");
            // 发送 sdp 响应
            sender.sendResponse(senderIp, transport, (ignore, ignore2, ignore3) -> SipResponseBuilder.response(request, Response.BUSY_HERE, "BUSY_HERE"));
            return;
        }

        GB28181SDPBuilder.Action action = isDownload ? GB28181SDPBuilder.Action.DOWNLOAD : GB28181SDPBuilder.Action.PLAY_BACK;
        TimeField timeField = new TimeField();
        timeField.setZero();

        String ssrc = gb28181Description.getSsrcField().getSsrc();
        GB28181Description sdp = GB28181SDPBuilder.Sender.build(action,
                device.getGbDeviceId(),
                channelId, Connection.IP4, address, port,
                ssrc,
                MediaStreamMode.of(((MediaDescription) gb28181Description.getMediaDescriptions(true).get(0)).getMedia().getProtocol()),
                SdpFactory.getInstance().createTimeDescription(timeField));

        String callId = request.getCallId().getCallId();
        String key = GenericSubscribe.Helper.getKey(Request.ACK, callId);
        subscribe.getAckSubscribe().addPublisher(key);
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        final ScheduledFuture<?>[] schedule = new ScheduledFuture<?>[1];
        Flow.Subscriber<SIPRequest> subscriber;
        if(!isDownload){
            subscriber = placbackSubscriber(request, callId,device,start,stop,address,port,key,ssrc,schedule);
        } else {
            subscriber = downloadSubscriber(request, callId,device,start,stop,address,port,key,ssrc,schedule);
        }
        // 60秒超时计时器
        schedule[0] = scheduledExecutorService.schedule(subscriber::onComplete, 60 , TimeUnit.SECONDS);
        // 推流 ack 事件订阅
        subscribe.getAckSubscribe().addSubscribe(key, subscriber);

        scheduledExecutorService.schedule(()->{
            // 发送 sdp 响应
            sender.sendResponse(senderIp, transport, (ignore, ignore2, ignore3) -> SipResponseBuilder.responseSdp(request, sdp));
        }, 1,TimeUnit.SECONDS);
    }

    public Flow.Subscriber<SIPRequest> playSubscriber(SIPRequest request,String callId,MockingDevice device,String address,int port,String key, String ssrc,ScheduledFuture<?>[] scheduledFuture){
        return new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                log.info("创建 ack 订阅 {}", key);
                subscription.request(1);
            }

            @Override
            public void onNext(SIPRequest item) {
                log.info("收到 ack 确认请求: {} 开始推流",key);
                // RTP 推流
                deviceProxyService.pullLiveStream2Rtp(request, callId, device, address, port,ssrc);
                onComplete();
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {
                subscribe.getAckSubscribe().delPublisher(key);
                scheduledFuture[0].cancel(true);
            }
        };
    }

    public Flow.Subscriber<SIPRequest> placbackSubscriber(SIPRequest request,String callId,MockingDevice device,Date start,Date stop,String address,int port,String key, String ssrc,ScheduledFuture<?>[] scheduledFuture){
        return new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                log.info("创建 ack 订阅 {}", key);
                subscription.request(1);
            }

            @Override
            public void onNext(SIPRequest item) {
                log.info("收到 ack 确认请求: {} 开始推流",key);
                if(ffmpegConfig.getUseZlmFfmpeg()){
                    deviceProxyService.pullStreamByZlmFfmpegSource(request,callId,device, start, stop, address, port,ssrc);
                } else {
                    // RTP 推流
                    deviceProxyService.proxyVideo2Rtp(request, callId, device, start, stop, address, port,ssrc, deviceProxyService.playbackTask());
                }
                onComplete();
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {
                subscribe.getAckSubscribe().delPublisher(key);
                scheduledFuture[0].cancel(true);
            }
        };
    }

    public Flow.Subscriber<SIPRequest> downloadSubscriber(SIPRequest request,String callId,MockingDevice device,Date start,Date stop,String address,int port,String key,String ssrc,ScheduledFuture<?>[] scheduledFuture){
        return new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                log.info("创建 ack 订阅 {}", key);
                subscription.request(1);
            }

            @Override
            public void onNext(SIPRequest item) {
                log.info("收到 ack 确认请求: {} 开始推流",key);
                // RTP 推流
                deviceProxyService.proxyVideo2Rtp(request, callId, device, start, stop, address, port, ssrc,deviceProxyService.downloadTask());
                onComplete();
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {
                subscribe.getAckSubscribe().delPublisher(key);
                scheduledFuture[0].cancel(true);
            }
        };
    }

    @Override
    public void start() {
        running = true;
    }

    @SneakyThrows
    @Override
    public void stop() {
        subscribe.getAckSubscribe().close();
        subscribe.getByeSubscribe().close();
        log.info("关闭所有 ack 和 bye 订阅 并 关闭所有推流");
        Thread.sleep(Duration.ofSeconds(5).toMillis());
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
