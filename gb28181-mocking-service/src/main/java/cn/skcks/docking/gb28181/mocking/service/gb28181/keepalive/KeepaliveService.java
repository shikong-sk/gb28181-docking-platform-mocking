package cn.skcks.docking.gb28181.mocking.service.gb28181.keepalive;

import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.config.sip.SipConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.keepalive.KeepaliveNotifyDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeepaliveService {
    private final SipConfig sipConfig;
    private final SipSender sender;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private final ConcurrentHashMap<String, ScheduledFuture<?>> map = new ConcurrentHashMap<>();

    public void keepalive(MockingDevice mockingDevice){
        unKeepalive(mockingDevice);
        ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            KeepaliveNotifyDTO keepaliveNotifyDTO = KeepaliveNotifyDTO.builder()
                    .deviceId(mockingDevice.getGbDeviceId())
                    .sn(String.valueOf((int) ((Math.random() * 9 + 1) * 100000)))
                    .build();
            sender.sendRequest((provider, ip, port) -> {
                CallIdHeader callIdHeader = provider.getNewCallId();
                return SipRequestBuilder.createMessageRequest(mockingDevice,
                        ip,
                        port,
                        1,
                        XmlUtils.toXml(keepaliveNotifyDTO),
                        SipUtil.generateViaTag(),
                        SipUtil.generateFromTag(),
                        callIdHeader);
            });
        }, 0, sipConfig.getKeepAlive(), TimeUnit.SECONDS);
        map.put(mockingDevice.getGbDeviceId(), scheduledFuture);
    }

    public void unKeepalive(MockingDevice mockingDevice){
        ScheduledFuture<?> scheduledFuture = map.remove(mockingDevice.getGbDeviceId());
        if(scheduledFuture != null){
            scheduledFuture.cancel(true);
        }
    }
}
