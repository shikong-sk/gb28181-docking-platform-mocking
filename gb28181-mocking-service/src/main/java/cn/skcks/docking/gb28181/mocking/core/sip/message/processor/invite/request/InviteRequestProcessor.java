package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.invite.request;

import cn.hutool.core.date.DateUtil;
import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.processor.MessageProcessor;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
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
import org.springframework.stereotype.Component;

import javax.sdp.*;
import javax.sip.RequestEvent;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.Date;
import java.util.EventObject;
import java.util.Vector;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class InviteRequestProcessor implements MessageProcessor {
    private final SipListener sipListener;

    private final SipSender sender;

    private final DeviceProxyService deviceProxyService;

    private final DeviceService deviceService;

    private final SipSubscribe subscribe;

    private final FfmpegConfig ffmpegConfig;

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
                        // 暂不支持实时
//                        sender.sendResponse(senderIp, transport, unsupported(request));
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

    /**
     * 模拟设备不支持实时 故直接回放 最近15分钟 至 当前时间录像
     *
     * @param gb28181Description gb28181 sdp
     * @param mediaDescription   媒体描述符
     */
    @SneakyThrows
    private void play(SIPRequest request, MockingDevice device, GB28181Description gb28181Description, MediaDescription mediaDescription) {
        TimeField time = new TimeField();
        time.setStart(DateUtil.offsetMinute(DateUtil.date(), -5));
        time.setStop(DateUtil.date());
        playback(request, device, gb28181Description, mediaDescription, time);
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
        String channelId = gb28181Description.getOrigin().getUsername();
        log.info("通道id: {}", channelId);
        String address = gb28181Description.getOrigin().getAddress();
        log.info("目标地址: {}", address);
        Media media = mediaDescription.getMedia();
        int port = media.getMediaPort();
        log.info("目标端口号: {}", port);

        String senderIp = request.getLocalAddress().getHostAddress();
        String transport = request.getTopmostViaHeader().getTransport();
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
        GB28181Description sdp = GB28181SDPBuilder.Sender.build(action,
                device.getGbDeviceId(),
                channelId, Connection.IP4, address, port,
                gb28181Description.getSsrcField().getSsrc(),
                MediaStreamMode.of(((MediaDescription) gb28181Description.getMediaDescriptions(true).get(0)).getMedia().getProtocol()),
                SdpFactory.getInstance().createTimeDescription(timeField));

        String ssrc = gb28181Description.getSsrcField().getSsrc();
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
                // RTP 推流
                deviceProxyService.proxyVideo2Rtp(request, callId, device, start, stop, address, port,ssrc, deviceProxyService.playbackTask());
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
}
