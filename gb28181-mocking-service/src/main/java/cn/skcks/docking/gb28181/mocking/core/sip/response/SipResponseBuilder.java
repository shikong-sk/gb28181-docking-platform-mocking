package cn.skcks.docking.gb28181.mocking.core.sip.response;

import cn.skcks.docking.gb28181.core.sip.gb28181.constant.GB28181Constant;
import cn.skcks.docking.gb28181.core.sip.gb28181.sdp.GB28181Description;
import cn.skcks.docking.gb28181.core.sip.message.MessageHelper;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import gov.nist.javax.sip.message.MessageFactoryImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.ContentTypeHeader;
import javax.sip.message.Response;

@Slf4j
public class SipResponseBuilder {
    @SneakyThrows
    public static Response response(SIPRequest request, int status, String message){
        if (request.getToHeader().getTag() == null) {
            request.getToHeader().setTag(SipUtil.generateTag());
        }

        MessageFactoryImpl messageFactory = (MessageFactoryImpl)MessageHelper.getSipFactory().createMessageFactory();
        // 使用 GB28181 默认编码 否则中文将会乱码
        messageFactory.setDefaultContentEncodingCharset(GB28181Constant.CHARSET);
        SIPResponse response = (SIPResponse)messageFactory.createResponse(status, request);
        if (message != null) {
            response.setReasonPhrase(message);
        }
        return response;
    }

    @SneakyThrows
    public static Response responseSdp(SIPRequest request, GB28181Description sdp) {
        MessageFactoryImpl messageFactory = (MessageFactoryImpl)MessageHelper.getSipFactory().createMessageFactory();
        // 使用 GB28181 默认编码 否则中文将会乱码
        messageFactory.setDefaultContentEncodingCharset(GB28181Constant.CHARSET);
        SIPResponse response = (SIPResponse)messageFactory.createResponse(Response.OK, request);
        SipFactory sipFactory = SipFactory.getInstance();
        ContentTypeHeader contentTypeHeader = sipFactory.createHeaderFactory().createContentTypeHeader("APPLICATION", "SDP");
        response.setContent(sdp.toString(), contentTypeHeader);
        SipURI sipURI = (SipURI) request.getRequestURI();
        SipURI uri = MessageHelper.createSipURI(sipURI.getUser(), StringUtils.joinWith(":", sipURI.getHost() + ":" + sipURI.getPort()));
        Address concatAddress = sipFactory.createAddressFactory().createAddress(uri);
        response.addHeader(sipFactory.createHeaderFactory().createContactHeader(concatAddress));
        return response;
    }
}
