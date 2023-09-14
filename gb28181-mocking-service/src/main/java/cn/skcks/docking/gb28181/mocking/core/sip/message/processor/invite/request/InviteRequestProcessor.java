package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.invite.request;

import cn.skcks.docking.gb28181.core.sip.gb28181.sdp.GB28181Description;
import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.processor.MessageProcessor;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.core.sip.gb28181.sdp.GB28181DescriptionParser;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import java.util.EventObject;

@Slf4j
@RequiredArgsConstructor
@Component
public class InviteRequestProcessor implements MessageProcessor {
    private final SipListener sipListener;
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
        String content = new String(request.getRawContent());
        log.info("{}", content);

        GB28181Description gb28181Description = new GB28181DescriptionParser(content).parse();
        log.info("{}", gb28181Description);
    }
}
