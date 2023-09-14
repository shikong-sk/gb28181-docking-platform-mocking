package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.invite.request;

import cn.hutool.core.date.DateUtil;
import cn.skcks.docking.gb28181.core.sip.gb28181.sdp.GB28181Description;
import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.processor.MessageProcessor;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.core.sip.gb28181.sdp.GB28181DescriptionParser;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.mapper.MockingDeviceMapper;
import gov.nist.javax.sdp.TimeDescriptionImpl;
import gov.nist.javax.sdp.fields.TimeField;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.sdp.Media;
import javax.sdp.MediaDescription;
import javax.sdp.SdpParseException;
import javax.sdp.SessionName;
import javax.sip.RequestEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.EventObject;
import java.util.Vector;

@Slf4j
@RequiredArgsConstructor
@Component
public class InviteRequestProcessor implements MessageProcessor {
    private final SipListener sipListener;

    private final SipSender sender;

    private final MockingDeviceMapper mockingDeviceMapper;

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
        SIPRequest request = (SIPRequest)requestEvent.getRequest();
        String deviceId = SipUtil.getUserIdFromFromHeader(request);
        CallIdHeader callIdHeader = request.getCallIdHeader();
        String senderIp = request.getLocalAddress().getHostAddress();
        String transport = request.getTopmostViaHeader().getTransport();
        String content = new String(request.getRawContent());
        GB28181Description gb28181Description = new GB28181DescriptionParser(content).parse();
        log.info("解析的 sdp信息: \n{}", gb28181Description);
        Vector<?> mediaDescriptions = gb28181Description.getMediaDescriptions(true);
        log.info("mediaDescriptions {}",mediaDescriptions);
        mediaDescriptions.stream().filter(item->{
            MediaDescription mediaDescription = (MediaDescription)item;
            Media media = mediaDescription.getMedia();
            try {
                Vector<?> mediaFormats = media.getMediaFormats(false);
                return mediaFormats.contains("98");
            } catch (SdpParseException e) {
                log.error("sdp media 解析异常: {}", e.getMessage());
                return false;
            }
        }).findFirst().ifPresentOrElse((item)->{
            SessionName sessionName = gb28181Description.getSessionName();
            try {
                String type = sessionName.getValue();
                log.info("type {}", type);
                if(StringUtils.equalsAnyIgnoreCase(type,"Play","PlayBack")){
                    log.info("点播/回放请求");
                    if(StringUtils.equalsIgnoreCase(type,"Play")){
                        play(gb28181Description, (MediaDescription) item);
                    } else {
                        playback(gb28181Description, (MediaDescription) item);
                    }
                } else if(StringUtils.equalsIgnoreCase(type,"Download")){
                    log.info("下载请求");
                } else {
                    log.error("未知请求类型: {}", type);
                    sender.sendResponse(senderIp, transport, unsupported(request));
                }
            } catch (SdpParseException e) {
                log.error("sdp 解析异常: {}", e.getMessage());
            }
        },()->{
            log.info("未找到支持的媒体类型");
            sender.sendResponse(senderIp, transport, unsupported(request));
        });
    }

    private SipSender.SendResponse unsupported(SIPRequest request) {
        return (provider, ip, port) -> SipResponseBuilder.response(request, Response.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Media Type");
    }

    /**
     * 模拟设备不支持实时 故直接回放 最近15分钟 至 当前时间录像
     * @param gb28181Description gb28181 sdp
     * @param mediaDescription 媒体描述符
     */
    @SneakyThrows
    private void play(GB28181Description gb28181Description, MediaDescription mediaDescription){
        TimeField time = new TimeField();
        time.setStart(DateUtil.offsetMinute(DateUtil.date(), -15));
        time.setStop(DateUtil.date());
        playback(gb28181Description, mediaDescription, time);
    }

    /**
     * 模拟设备 录像回放 当前小时至当前时间录像
     * @param gb28181Description gb28181 sdp
     * @param mediaDescription 媒体描述符
     */
    @SneakyThrows
    private void playback(GB28181Description gb28181Description, MediaDescription mediaDescription) {
        TimeDescriptionImpl timeDescription = (TimeDescriptionImpl) gb28181Description.getTimeDescriptions(true).get(0);
        TimeField time = (TimeField) timeDescription.getTime();
        playback(gb28181Description, mediaDescription, time);
    }

    @SneakyThrows
    private void playback(GB28181Description gb28181Description, MediaDescription mediaDescription, TimeField time){
        Media media = mediaDescription.getMedia();
        int port = media.getMediaPort();
        log.info("目标端口号: {}", port);
        log.info("{} ~ {}", time.getStart(), time.getStop());

        String channelId = gb28181Description.getOrigin().getUsername();
        log.info("通道id: {}", channelId);
        String address = gb28181Description.getOrigin().getAddress();
        log.info("目标地址: {}", address);

    }
}
