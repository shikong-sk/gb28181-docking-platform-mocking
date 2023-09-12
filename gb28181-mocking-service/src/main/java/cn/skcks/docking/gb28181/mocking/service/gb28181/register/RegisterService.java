package cn.skcks.docking.gb28181.mocking.service.gb28181.register;


import cn.skcks.docking.gb28181.core.sip.listener.SipListener;
import cn.skcks.docking.gb28181.core.sip.service.SipService;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.config.sip.SipConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RegisterService {
    private final SipConfig sipConfig;
    private final SipListener sipListener;
    private final SipService sipService;
    private final DeviceService deviceService;

    private final SipSender sender;

    public boolean register(){
        deviceService.getAllDevice().parallelStream().forEach(device -> {
            sender.sendRequest((provider, ip) -> SipRequestBuilder.createRegisterRequest(device, ip, sipConfig.getPort(), 1, SipUtil.generateFromTag(), null, provider.getNewCallId()));
        });

        return true;
    }
}
