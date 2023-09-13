package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.deviceinfo.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;
@JacksonXmlRootElement(localName = "Query")
@Data
public class DeviceInfoRequestDTO {
    private String cmdType;

    @JacksonXmlProperty(localName = "SN")
    private String sn;

    @JacksonXmlProperty(localName = "DeviceID")
    private String deviceId;
}
