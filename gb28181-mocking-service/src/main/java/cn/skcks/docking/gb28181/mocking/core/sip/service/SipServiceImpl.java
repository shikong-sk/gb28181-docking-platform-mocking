package cn.skcks.docking.gb28181.mocking.core.sip.service;

import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.message.parser.GbStringMsgParserFactory;
import cn.skcks.docking.gb28181.core.sip.properties.DefaultProperties;
import cn.skcks.docking.gb28181.core.sip.service.SipService;
import cn.skcks.docking.gb28181.mocking.config.sip.SipConfig;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import javax.sip.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Data
@Order(10)
@Service
public class SipServiceImpl implements SipService {
    private final SipFactory sipFactory = SipFactory.getInstance();
    private final SipConfig sipConfig;
    private final SipListener sipListener;

    private final List<SipProviderImpl> pool = new ArrayList<>(2);
    private SipStackImpl sipStack;

    @Override
    public void run() {
        sipFactory.setPathName("gov.nist");
        sipConfig.getIp().parallelStream().forEach(ip -> {
            listen(ip, sipConfig.getPort());
        });
    }

    @Override
    public void stop() {
        if (sipStack == null) {
            return;
        }

        sipStack.closeAllSockets();
        pool.parallelStream().forEach(sipProvider -> {
            ListeningPoint listen = sipProvider.getListeningPoint();
            log.debug("移除监听 {}://{}:{}", listen.getTransport(), listen.getIPAddress(), listen.getPort());
            sipProvider.removeSipListener(sipListener);
            sipProvider.removeListeningPoints();

            try {
                sipStack.deleteListeningPoint(listen);
                sipStack.deleteSipProvider(sipProvider);
            } catch (Exception ignore) {
            }
        });
        pool.clear();
    }

    @Override
    public SipProvider getProvider(String transport, String ip) {
        return pool.parallelStream().filter(sipProvider -> {
            ListeningPoint listeningPoint = sipProvider.getListeningPoint();
            return listeningPoint != null && listeningPoint.getIPAddress().equals(ip) && listeningPoint.getTransport().equalsIgnoreCase(transport);
        }).findFirst().orElse(null);
    }

    public void listen(String ip, int port) {
        try {
            sipStack = (SipStackImpl) sipFactory.createSipStack(DefaultProperties.getProperties("GB28181_SIP"));
            sipStack.setMessageParserFactory(new GbStringMsgParserFactory());
            // sipStack.setMessageProcessorFactory();
            try {
                ListeningPoint tcpListen = sipStack.createListeningPoint(ip, port, ListeningPoint.TCP);
                SipProviderImpl tcpSipProvider = (SipProviderImpl) sipStack.createSipProvider(tcpListen);
                tcpSipProvider.setDialogErrorsAutomaticallyHandled();
                tcpSipProvider.addSipListener(sipListener);
                pool.add(tcpSipProvider);
                log.info("[sip] 监听 tcp://{}:{}", ip, port);
            } catch (TransportNotSupportedException
                     | ObjectInUseException
                     | InvalidArgumentException e) {
                log.error("[sip] tcp://{}:{} 监听失败, 请检查端口是否被占用, 错误信息 => {}", ip, port, e.getMessage());
            }

            try {
                ListeningPoint udpListen = sipStack.createListeningPoint(ip, port, ListeningPoint.UDP);
                SipProviderImpl udpSipProvider = (SipProviderImpl) sipStack.createSipProvider(udpListen);
                udpSipProvider.addSipListener(sipListener);
                pool.add(udpSipProvider);
                log.info("[sip] 监听 udp://{}:{}", ip, port);
            } catch (TransportNotSupportedException
                     | ObjectInUseException
                     | InvalidArgumentException e) {
                log.error("[sip] udp://{}:{} 监听失败, 请检查端口是否被占用, 错误信息 => {}", ip, port, e.getMessage());
            }
        } catch (Exception e) {
            log.error("[sip] {}:{} 监听失败, 请检查端口是否被占用, 错误信息 => {}", ip, port, e.getMessage());
        }

    }
}
