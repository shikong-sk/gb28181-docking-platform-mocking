package cn.skcks.docking.gb28181.mocking.api.device;

import cn.skcks.docking.gb28181.annotation.web.JsonMapping;
import cn.skcks.docking.gb28181.annotation.web.methods.GetJson;
import cn.skcks.docking.gb28181.annotation.web.methods.PostJson;
import cn.skcks.docking.gb28181.common.json.JsonResponse;
import cn.skcks.docking.gb28181.common.page.PageWrapper;
import cn.skcks.docking.gb28181.mocking.api.device.convertor.DeviceDTOConvertor;
import cn.skcks.docking.gb28181.mocking.api.device.dto.AddDeviceDTO;
import cn.skcks.docking.gb28181.mocking.api.device.dto.DevicePageDTO;
import cn.skcks.docking.gb28181.mocking.config.SwaggerConfig;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "设备信息")
@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;


    @Bean
    public GroupedOpenApi deviceApi() {
        return SwaggerConfig.api("DeviceApi", "/device");
    }

    @Operation(summary = "分页查询设备列表")
    @GetJson("/page")
    public JsonResponse<PageWrapper<MockingDevice>> getDevicesWithPagePage(@ParameterObject @Validated DevicePageDTO dto) {
        return JsonResponse.success(PageWrapper.of(deviceService.getDevicesWithPage(dto.getPage(), dto.getSize())));
    }

    @Operation(summary = "添加设备")
    @PostJson("/add")
    public JsonResponse<Boolean> addDevice(@RequestBody AddDeviceDTO dto) {
        return JsonResponse.success(deviceService.addDevice(DeviceDTOConvertor.INSTANCE.dto2dao(dto)));
    }

    @Operation(summary = "根据设备编码(21位) 查询指定设备信息")
    @GetJson("/info/deviceCode")
    public JsonResponse<MockingDevice> infoByDeviceCode(@RequestParam String deviceCode) {
        MockingDevice MockingDevice = deviceService.getDeviceByDeviceCode(deviceCode).orElse(null);
        return JsonResponse.success(MockingDevice);
    }

    @Operation(summary = "根据国标id(20位) 查询指定设备信息")
    @GetJson("/info/gbDeviceId")
    public JsonResponse<List<MockingDevice>> infoByGbDeviceId(@RequestParam String gbDeviceId) {
        List<MockingDevice> MockingDevice = deviceService.getDeviceByGbDeviceId(gbDeviceId);
        return JsonResponse.success(MockingDevice);
    }

    @Operation(summary = "根据设备编码(21位) 删除指定设备")
    @JsonMapping(value = "/delete/deviceCode", method = {RequestMethod.GET,RequestMethod.DELETE})
    public JsonResponse<Boolean> deleteByDeviceCode(@RequestParam String deviceCode){
        MockingDevice MockingDevice = new MockingDevice();
        MockingDevice.setDeviceCode(deviceCode);
        return JsonResponse.success(deviceService.deleteDevice(MockingDevice));
    }

    @Operation(summary = "根据国标id(20位) 删除指定设备")
    @JsonMapping(value = "/delete/gbDeviceId",method = {RequestMethod.GET,RequestMethod.DELETE})
    public JsonResponse<Boolean> deleteByGbDeviceId(@RequestParam String gbDeviceId){
        MockingDevice MockingDevice = new MockingDevice();
        MockingDevice.setGbDeviceId(gbDeviceId);
        return JsonResponse.success(deviceService.deleteDevice(MockingDevice));
    }

    @Operation(summary = "根据主键 id 删除指定设备")
    @JsonMapping(value = "/delete/id",method = {RequestMethod.GET,RequestMethod.DELETE})
    public JsonResponse<Boolean> deleteByGbDeviceId(@RequestParam Long id){
        MockingDevice MockingDevice = new MockingDevice();
        MockingDevice.setId(id);
        return JsonResponse.success(deviceService.deleteDevice(MockingDevice));
    }
}
