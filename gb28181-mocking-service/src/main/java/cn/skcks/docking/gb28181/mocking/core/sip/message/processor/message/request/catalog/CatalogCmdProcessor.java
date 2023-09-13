package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog;

import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto.CatalogDeviceListDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto.CatalogItemDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto.CatalogRequestDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto.CatalogResponseDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogCmdProcessor {
    private final SipSender sender;
    private final DeviceService deviceService;

    public void process(SIPRequest request, byte[] content){
        CatalogRequestDTO catalogRequestDTO = XmlUtils.parse(content, CatalogRequestDTO.class);
        String sn = catalogRequestDTO.getSn();
        String deviceId = catalogRequestDTO.getDeviceId();

        MockingDevice mockingDevice = deviceService.getDeviceByGbDeviceId(deviceId).orElse(null);
        if(mockingDevice == null){
            return;
        }
        CatalogItemDTO catalogItemDTO = CatalogItemDTO.builder()
                .deviceId(mockingDevice.getGbChannelId())
                .name(mockingDevice.getName())
                .address(mockingDevice.getAddress())
                .manufacturer(mockingDevice.getName())
                .build();
        List<CatalogItemDTO> catalogItemDTOList = Collections.singletonList(catalogItemDTO);
        CatalogDeviceListDTO catalogDeviceListDTO = new CatalogDeviceListDTO(catalogItemDTOList.size(), catalogItemDTOList);
        CatalogResponseDTO catalogResponseDTO = CatalogResponseDTO.builder()
                .sn(sn)
                .deviceId(deviceId)
                .deviceList(catalogDeviceListDTO)
                .sumNum(Long.valueOf(catalogDeviceListDTO.getNum()))
                .build();

        FromHeader fromHeader = request.getFromHeader();
        sender.sendRequest((provider, ip, port)->{
            CallIdHeader callIdHeader = provider.getNewCallId();
            return SipRequestBuilder.createMessageRequest(mockingDevice,
                    ip, port, 1, XmlUtils.toXml(catalogResponseDTO), fromHeader.getTag(), callIdHeader);
        });
    }
}
