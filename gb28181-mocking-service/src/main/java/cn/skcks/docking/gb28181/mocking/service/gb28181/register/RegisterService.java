package cn.skcks.docking.gb28181.mocking.service.gb28181.register;


import cn.skcks.docking.gb28181.common.json.JsonResponse;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class RegisterService {
    private final DeviceService deviceService;

    private final SipSender sender;

    @SneakyThrows
    public DeferredResult<JsonResponse<Boolean>> register(){
        DeferredResult<JsonResponse<Boolean>> result = new DeferredResult<>();
        List<MockingDevice> allDevice = deviceService.getAllDevice();
        allDevice.parallelStream().forEach(device -> {
            sender.sendRequest((provider, ip, port) -> SipRequestBuilder.createRegisterRequest(device, ip, port, 1, SipUtil.generateFromTag(), null, provider.getNewCallId()));

        });
        result.setResult(JsonResponse.success(true));
        return result;
    }
}
