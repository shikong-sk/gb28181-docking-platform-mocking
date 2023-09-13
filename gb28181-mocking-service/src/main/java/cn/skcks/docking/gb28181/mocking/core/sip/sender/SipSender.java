package cn.skcks.docking.gb28181.mocking.core.sip.sender;

import cn.skcks.docking.gb28181.core.sip.service.SipService;
import cn.skcks.docking.gb28181.mocking.config.sip.SipConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.ListeningPoint;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.message.Request;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Component
public class SipSender {
    private final SipService sipService;
    private final SipConfig sipConfig;

    public SipProvider getProvider(String transport, String ip) {
        return sipService.getProvider(transport, ip);
    }

    public List<SipProvider> getProviders() {
        return sipConfig.getIp().stream().map(item -> getProvider(sipConfig.getTransport(), item))
                .filter(Objects::nonNull)
                .toList();
    }

    public void sendRequest(SendRequest request) {
        getProviders().parallelStream().forEach(sipProvider -> {
            log.info("{}", sipProvider);
            ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
            if (listeningPoints == null || listeningPoints.length == 0) {
                log.error("发送请求失败, 未找到有效的监听地址");
                return;
            }
            ListeningPoint listeningPoint = listeningPoints[0];
            String ip = listeningPoint.getIPAddress();
            int port = listeningPoint.getPort();
            try {
                sipProvider.sendRequest(request.build(sipProvider, ip, port));
            } catch (SipException e) {
                log.error("向{} {}:{} 发送请求失败, 异常: {}", ip, listeningPoint.getPort(), listeningPoint.getTransport(), e.getMessage());
            }
        });
    }

    public interface SendRequest {
        Request build(SipProvider provider, String ip, int port);
    }
}
