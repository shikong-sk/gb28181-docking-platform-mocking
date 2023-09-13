package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog;

import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto.CatalogDeviceListDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto.CatalogItemDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto.CatalogRequestDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto.CatalogResponseDTO;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogCmdProcessor {
    private final DeviceService deviceService;

    public void process(SIPRequest request, byte[] content){
        CatalogRequestDTO catalogRequestDTO = XmlUtils.parse(content, CatalogRequestDTO.class);
        String sn = catalogRequestDTO.getSn();
        String deviceId = catalogRequestDTO.getDeviceId();

        List<MockingDevice> mockingDeviceList = deviceService.getDeviceByGbDeviceId(deviceId);
        List<CatalogItemDTO> catalogItemDTOList = mockingDeviceList.stream()
                .filter(MockingDevice::getEnable)
                .map(item -> CatalogItemDTO.builder()
                        .deviceId(item.getGbChannelId())
                        .name(item.getName())
                        .address(item.getAddress())
                        .manufacturer(item.getName())
                        .build()).toList();
        CatalogDeviceListDTO catalogDeviceListDTO = new CatalogDeviceListDTO(catalogItemDTOList.size(), catalogItemDTOList);
        CatalogResponseDTO catalogResponseDTO = CatalogResponseDTO.builder()
                .sn(sn)
                .deviceId(deviceId)
                .deviceList(catalogDeviceListDTO)
                .sumNum(Long.valueOf(catalogDeviceListDTO.getNum()))
                .build();


    }
}
