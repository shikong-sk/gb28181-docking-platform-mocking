package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.ack.request;

import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.processor.MessageProcessor;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Request;
import java.util.EventObject;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class AckRequestProcessor implements MessageProcessor {
    private final SipListener sipListener;
    private final SipSubscribe subscribe;

    @PostConstruct
    @Override
    public void init() {
        sipListener.addRequestProcessor(Request.ACK, this);
    }

    @Override
    public void process(EventObject eventObject) {
        RequestEvent requestEvent = (RequestEvent) eventObject;
        SIPRequest request = (SIPRequest) requestEvent.getRequest();
        String callId = request.getCallId().getCallId();
        String key = GenericSubscribe.Helper.getKey(Request.ACK, callId);
        Optional.ofNullable(subscribe.getAckSubscribe().getPublisher(key))
                .ifPresent(publisher -> publisher.submit(request));
    }
}
