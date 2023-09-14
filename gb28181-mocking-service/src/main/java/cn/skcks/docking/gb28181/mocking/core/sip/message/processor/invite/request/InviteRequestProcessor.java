package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.invite.request;

import cn.skcks.docking.gb28181.core.sip.gb28181.sdp.GB28181Description;
import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.processor.MessageProcessor;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.core.sip.gb28181.sdp.GB28181DescriptionParser;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sdp.Media;
import javax.sdp.MediaDescription;
import javax.sdp.SdpParseException;
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
        }).findFirst().ifPresentOrElse((ignore)->{
            log.info("{}", ignore);
        },()->{
            log.info("未找到支持的媒体类型");
            sender.sendResponse(senderIp,transport, ((provider, ip, port) ->
                    SipResponseBuilder.response(request,
                            Response.UNSUPPORTED_MEDIA_TYPE,
                            "Unsupported Media Type")));
        });
    }
}
