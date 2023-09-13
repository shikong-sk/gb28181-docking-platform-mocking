package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.deviceinfo;

import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.deviceinfo.dto.DeviceInfoRequestDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.deviceinfo.dto.DeviceInfoResponseDTO;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceInfoRequestProcessor {
    private final SipSender sender;

    private final DeviceService deviceService;

    public void process(SIPRequest request, byte[] content){
        DeviceInfoRequestDTO deviceInfoRequestDTO = XmlUtils.parse(content, DeviceInfoRequestDTO.class);
        String sn = deviceInfoRequestDTO.getSn();
        String deviceId = deviceInfoRequestDTO.getDeviceId();
        MockingDevice mockingDevice = deviceService.getDeviceByGbDeviceId(deviceId).orElse(null);
        if(mockingDevice == null){
            return;
        }
        DeviceInfoResponseDTO deviceInfoResponseDTO = DeviceInfoResponseDTO.builder()
                .sn(sn)
                .deviceId(deviceId)
                .deviceName(mockingDevice.getName())
                .channel(1)
                .manufacturer("GB28181-Docking-Platform")
                .build();
        FromHeader fromHeader = request.getFromHeader();
        sender.sendRequest((provider, ip, port)->{
            CallIdHeader callIdHeader = provider.getNewCallId();
            return SipRequestBuilder.createMessageRequest(mockingDevice,
                    ip, port, 1, XmlUtils.toXml(deviceInfoResponseDTO), fromHeader.getTag(), callIdHeader);
        });
    }
}
