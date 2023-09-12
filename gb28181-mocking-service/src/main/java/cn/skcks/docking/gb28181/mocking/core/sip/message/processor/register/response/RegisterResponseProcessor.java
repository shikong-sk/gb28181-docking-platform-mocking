package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.register.response;

import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.processor.MessageProcessor;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import javax.sip.address.Address;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import java.util.EventObject;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class RegisterResponseProcessor implements MessageProcessor {
    private final SipListener sipListener;
    private final SipSubscribe subscribe;

    @PostConstruct
    @Override
    public void init() {
        sipListener.addResponseProcessor(Request.REGISTER, this);
    }

    @Override
    public void process(EventObject event) {
        ResponseEvent requestEvent = (ResponseEvent) event;
        SIPResponse response = (SIPResponse)requestEvent.getResponse();
        ToHeader toHeader = response.getTo();
        Address address = toHeader.getAddress();
        CallIdHeader callIdHeader = response.getCallIdHeader();
        SipUri uri = (SipUri)address.getURI();
        String deviceId = uri.getUser();
        String key = GenericSubscribe.Helper.getKey(Request.REGISTER, deviceId, callIdHeader.getCallId());
        Optional.ofNullable(subscribe.getRegisterSubscribe().getPublisher(key)).ifPresent(publisher->publisher.submit(response));
    }
}
