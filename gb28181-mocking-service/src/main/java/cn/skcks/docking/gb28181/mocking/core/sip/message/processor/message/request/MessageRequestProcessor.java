package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request;

import cn.skcks.docking.gb28181.common.json.ResponseStatus;
import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.core.sip.gb28181.constant.CmdType;
import cn.skcks.docking.gb28181.core.sip.gb28181.constant.GB28181Constant;
import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.processor.MessageProcessor;
import cn.skcks.docking.gb28181.core.sip.message.processor.message.request.dto.MessageDTO;
import cn.skcks.docking.gb28181.core.sip.message.sender.SipMessageSender;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.CatalogCmdProcessor;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.EventObject;

@Slf4j
@RequiredArgsConstructor
@Component
public class MessageRequestProcessor implements MessageProcessor {
    private final SipListener sipListener;

    private final SipSubscribe subscribe;

    private final SipMessageSender sender;

    private final CatalogCmdProcessor catalogCmdProcessor;

    private Response okResponse(SIPRequest request){
        return SipResponseBuilder.response(request, Response.OK, "OK");
    }

    @PostConstruct
    @Override
    public void init() {
        sipListener.addRequestProcessor(Request.MESSAGE, this);
    }

    @Override
    public void process(EventObject eventObject) {
        RequestEvent requestEvent = (RequestEvent) eventObject;
        SIPRequest request = (SIPRequest)requestEvent.getRequest();
        String deviceId = SipUtil.getUserIdFromFromHeader(request);
        CallIdHeader callIdHeader = request.getCallIdHeader();
        String senderIp = request.getLocalAddress().getHostAddress();
        byte[] content = request.getRawContent();
        MessageDTO messageDto = XmlUtils.parse(content, MessageDTO.class, GB28181Constant.CHARSET);
        log.debug("deviceId:{}, 接收到的消息 => {}", deviceId, messageDto);

        if(messageDto.getCmdType().equalsIgnoreCase(CmdType.CATALOG)) {
            sender.send(senderIp, okResponse(request));
            catalogCmdProcessor.process(request, content);
        } else {
            Response response = SipResponseBuilder.response(request, Response.NOT_IMPLEMENTED, ResponseStatus.NOT_IMPLEMENTED.getMessage());
            sender.send(senderIp, response);
        }
    }
}
