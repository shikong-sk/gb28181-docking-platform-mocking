package cn.skcks.docking.gb28181.mocking.core.sip.response;

import cn.skcks.docking.gb28181.core.sip.message.MessageHelper;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.sip.message.MessageFactory;
import javax.sip.message.Response;

@Slf4j
public class SipResponseBuilder {
    @SneakyThrows
    public static Response response(SIPRequest request, int status, String message){
        if (request.getToHeader().getTag() == null) {
            request.getToHeader().setTag(SipUtil.generateTag());
        }

        MessageFactory messageFactory = MessageHelper.getSipFactory().createMessageFactory();
        SIPResponse response = (SIPResponse)messageFactory.createResponse(status, request);
        if (message != null) {
            response.setReasonPhrase(message);
        }
        return response;
    }
}
