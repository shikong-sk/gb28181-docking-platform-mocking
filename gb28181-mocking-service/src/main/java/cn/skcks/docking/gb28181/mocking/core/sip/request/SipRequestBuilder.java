package cn.skcks.docking.gb28181.mocking.core.sip.request;

import cn.skcks.docking.gb28181.core.sip.gb28181.constant.GB28181Constant;
import cn.skcks.docking.gb28181.core.sip.message.MessageHelper;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.config.sip.ServerConfig;
import cn.skcks.docking.gb28181.mocking.config.sip.SipConfig;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import gov.nist.javax.sip.message.MessageFactoryImpl;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@DependsOn("serverConfig")
@Component
public class SipRequestBuilder implements ApplicationContextAware {
    private static ServerConfig serverConfig;

    private static SipConfig sipConfig;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        serverConfig = applicationContext.getBean(ServerConfig.class);
        sipConfig = applicationContext.getBean(SipConfig.class);
    }

    private static SipFactory getSipFactory(){
        return SipFactory.getInstance();
    }

    @SneakyThrows
    private static List<ViaHeader> getViaHeaders(String ip,int port, String transport, String viaTag){
        ViaHeader viaHeader = getSipFactory().createHeaderFactory().createViaHeader(ip, port, transport, viaTag);
        viaHeader.setRPort();
        return Collections.singletonList(viaHeader);
    }

    @SneakyThrows
    private static CSeqHeader getCSeqHeader(long cSeq, String method){
        return getSipFactory().createHeaderFactory().createCSeqHeader(cSeq, method);
    }

    @SneakyThrows
    public static Request createRegisterRequest(MockingDevice device, String ip, int port, long cSeq, String fromTag, String viaTag, CallIdHeader callIdHeader) {
        String target = StringUtils.joinWith(":", serverConfig.getIp(), serverConfig.getPort());
        SipURI requestURI = MessageHelper.createSipURI(serverConfig.getId(), target);
        // via
        List<ViaHeader> viaHeaders = getViaHeaders(serverConfig.getIp(), serverConfig.getPort(), sipConfig.getTransport(), viaTag);

        // from
        String from = StringUtils.joinWith(":", ip, port);
        SipURI fromSipURI = MessageHelper.createSipURI(device.getGbDeviceId(), from);
        Address fromAddress = MessageHelper.createAddress(fromSipURI);
        FromHeader fromHeader = MessageHelper.createFromHeader(fromAddress, fromTag);

        // to
        ToHeader toHeader = MessageHelper.createToHeader(fromAddress, null);

        // forwards
        MaxForwardsHeader maxForwardsHeader = MessageHelper.createMaxForwardsHeader(70);

        // ceq
        CSeqHeader cSeqHeader = getCSeqHeader(cSeq, Request.REGISTER);

        SipFactory sipFactory = getSipFactory();
        Request request = sipFactory.createMessageFactory().createRequest(requestURI, Request.REGISTER, callIdHeader,
                cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);
        Address concatAddress = sipFactory.createAddressFactory().createAddress(sipFactory.createAddressFactory()
                .createSipURI(device.getGbDeviceId(), from));
        request.addHeader(sipFactory.createHeaderFactory().createContactHeader(concatAddress));
        ExpiresHeader expires = sipFactory.createHeaderFactory().createExpiresHeader(sipConfig.getExpire());
        request.addHeader(expires);
        UserAgentHeader userAgentHeader = SipUtil.createUserAgentHeader();
        request.addHeader(userAgentHeader);
        return request;
    }

    @SneakyThrows
    public static Request createRegisterRequestWithAuthorization(MockingDevice device, String ip, int port, long cSeq, String fromTag, String viaTag, CallIdHeader callIdHeader, WWWAuthenticateHeader www) {
        Request request = createRegisterRequest(device, ip, port, cSeq, fromTag, viaTag, callIdHeader);
        String realm = www.getRealm();
        String nonce = www.getNonce();
        String scheme = www.getScheme();
        String qop = www.getQop();

        String target = StringUtils.joinWith(":", serverConfig.getIp(), serverConfig.getPort());
        SipURI requestURI = MessageHelper.createSipURI(serverConfig.getId(), target);
        String cNonce = null;
        String nc = "00000001";
        if (qop != null) {
            if ("auth".equals(qop)) {
                // 客户端随机数，这是一个不透明的字符串值，由客户端提供，并且客户端和服务器都会使用，以避免用明文文本。
                // 这使得双方都可以查验对方的身份，并对消息的完整性提供一些保护
                cNonce = UUID.randomUUID().toString();
            } else if ("auth-int".equals(qop)) {
                // TODO
            }
        }
        String HA1 = DigestUtils.md5DigestAsHex((device.getGbDeviceId() + ":" + realm + ":" + serverConfig.getPassword()).getBytes());
        String HA2 = DigestUtils.md5DigestAsHex((Request.REGISTER + ":" + requestURI.toString()).getBytes());
        StringBuffer reStr = new StringBuffer(200);
        reStr.append(HA1);
        reStr.append(":");
        reStr.append(nonce);
        reStr.append(":");
        if (qop != null) {
            reStr.append(nc);
            reStr.append(":");
            reStr.append(cNonce);
            reStr.append(":");
            reStr.append(qop);
            reStr.append(":");
        }
        reStr.append(HA2);
        String response = DigestUtils.md5DigestAsHex(reStr.toString().getBytes());
        AuthorizationHeader authorizationHeader = getSipFactory().createHeaderFactory().createAuthorizationHeader(scheme);
        authorizationHeader.setUsername(device.getGbDeviceId());
        authorizationHeader.setRealm(realm);
        authorizationHeader.setNonce(nonce);
        authorizationHeader.setURI(requestURI);
        authorizationHeader.setResponse(response);
        authorizationHeader.setAlgorithm("MD5");
        if (qop != null) {
            authorizationHeader.setQop(qop);
            authorizationHeader.setCNonce(cNonce);
            authorizationHeader.setNonceCount(1);
        }
        request.addHeader(authorizationHeader);
        return request;
    }

    @SneakyThrows
    public static Request createMessageRequest(MockingDevice device, String ip, int port,long cSeq,String content, String fromTag, CallIdHeader callIdHeader) {
        Request request;
        String target = StringUtils.joinWith(":", serverConfig.getIp(), serverConfig.getPort());
        // sip uri
        SipURI requestURI = MessageHelper.createSipURI(serverConfig.getId(), target);

        // via
        List<ViaHeader> viaHeaders = getViaHeaders(serverConfig.getIp(), serverConfig.getPort(), sipConfig.getTransport(), null );

        String from = StringUtils.joinWith(":", ip, port);
        // from
        SipURI fromSipURI = MessageHelper.createSipURI(device.getGbDeviceId(), from);
        Address fromAddress = MessageHelper.createAddress(fromSipURI);
        FromHeader fromHeader = MessageHelper.createFromHeader(fromAddress, fromTag);
        // to
        SipURI toSipURI = MessageHelper.createSipURI(serverConfig.getId(), target);
        Address toAddress = MessageHelper.createAddress(toSipURI);
        ToHeader toHeader = MessageHelper.createToHeader(toAddress, null);

        // Forwards
        MaxForwardsHeader maxForwards = MessageHelper.createMaxForwardsHeader(70);
        // ceq
        CSeqHeader cSeqHeader = getSipFactory().createHeaderFactory().createCSeqHeader(cSeq, Request.MESSAGE);

        // 使用 GB28181 默认编码 否则中文将会乱码
        MessageFactoryImpl messageFactory = (MessageFactoryImpl) getSipFactory().createMessageFactory();
        messageFactory.setDefaultContentEncodingCharset(GB28181Constant.CHARSET);
        request = messageFactory.createRequest(requestURI, Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
                toHeader, viaHeaders, maxForwards);

        request.addHeader(SipUtil.createUserAgentHeader());

        ContentTypeHeader contentTypeHeader = getSipFactory().createHeaderFactory().createContentTypeHeader("Application", "MANSCDP+xml");
        request.setContent(content, contentTypeHeader);
        return request;
    }

    @SneakyThrows
    public static Request createMessageRequest(MockingDevice device, String ip, int port,long cSeq,String content, String viaTag, String fromTag, CallIdHeader callIdHeader) {
        Request request;
        String target = StringUtils.joinWith(":", serverConfig.getIp(), serverConfig.getPort());
        // sip uri
        SipURI requestURI = MessageHelper.createSipURI(serverConfig.getId(), target);

        // via
        List<ViaHeader> viaHeaders = getViaHeaders(serverConfig.getIp(), serverConfig.getPort(), sipConfig.getTransport(), viaTag );

        String from = StringUtils.joinWith(":", ip, port);
        // from
        SipURI fromSipURI = MessageHelper.createSipURI(device.getGbDeviceId(), from);
        Address fromAddress = MessageHelper.createAddress(fromSipURI);
        FromHeader fromHeader = MessageHelper.createFromHeader(fromAddress, fromTag);
        // to
        SipURI toSipURI = MessageHelper.createSipURI(serverConfig.getId(), target);
        Address toAddress = MessageHelper.createAddress(toSipURI);
        ToHeader toHeader = MessageHelper.createToHeader(toAddress, null);

        // Forwards
        MaxForwardsHeader maxForwards = MessageHelper.createMaxForwardsHeader(70);
        // ceq
        CSeqHeader cSeqHeader = getSipFactory().createHeaderFactory().createCSeqHeader(cSeq, Request.MESSAGE);

        // 使用 GB28181 默认编码 否则中文将会乱码
        MessageFactoryImpl messageFactory = (MessageFactoryImpl) getSipFactory().createMessageFactory();
        messageFactory.setDefaultContentEncodingCharset(GB28181Constant.CHARSET);
        request = messageFactory.createRequest(requestURI, Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
                toHeader, viaHeaders, maxForwards);

        request.addHeader(SipUtil.createUserAgentHeader());

        ContentTypeHeader contentTypeHeader = getSipFactory().createHeaderFactory().createContentTypeHeader("Application", "MANSCDP+xml");
        request.setContent(content, contentTypeHeader);
        return request;
    }

    @SneakyThrows
    public static Request createByeRequest(String ip, int port, long cSeq, String targetId, String fromTag, String toTag, String callId) {
        Request request;
        // 请求行
        String target = StringUtils.joinWith(":", ip, port);
        SipURI requestLine = MessageHelper.createSipURI(targetId, target);
        // via
        ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        ViaHeader viaHeader = getSipFactory().createHeaderFactory().createViaHeader(ip, port, sipConfig.getTransport(), SipUtil.generateViaTag());
        viaHeaders.add(viaHeader);
        // from
        SipURI fromSipURI = MessageHelper.createSipURI(sipConfig.getId(), sipConfig.getDomain());
        Address fromAddress = MessageHelper.createAddress(fromSipURI);
        FromHeader fromHeader = MessageHelper.createFromHeader(fromAddress, fromTag);
        // to
        SipURI toSipURI = MessageHelper.createSipURI(targetId, target);
        Address toAddress = MessageHelper.createAddress(toSipURI);
        ToHeader toHeader = MessageHelper.createToHeader(toAddress, toTag);

        // Forwards
        MaxForwardsHeader maxForwards = getSipFactory().createHeaderFactory().createMaxForwardsHeader(70);

        // ceq
        CSeqHeader cSeqHeader = getSipFactory().createHeaderFactory().createCSeqHeader(cSeq, Request.BYE);
        CallIdHeader callIdHeader = getSipFactory().createHeaderFactory().createCallIdHeader(callId);
        request = getSipFactory().createMessageFactory().createRequest(requestLine, Request.BYE, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

        request.addHeader(SipUtil.createUserAgentHeader());

        Address concatAddress = MessageHelper.createAddress(MessageHelper.createSipURI(sipConfig.getId(), ip + ":" + port));
        request.addHeader(getSipFactory().createHeaderFactory().createContactHeader(concatAddress));
        request.addHeader(SipUtil.createUserAgentHeader());

        return request;
    }

}
