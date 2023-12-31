package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.recordinfo.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@JacksonXmlRootElement(localName = "Response")
public class RecordInfoResponseDTO {
    /**
     * 命令类型:设备信息查询(必选)
     */
    @Builder.Default
    private String cmdType = "RecordInfo";

    /**
     * 命令序列号(必选)
     */
    @JacksonXmlProperty(localName = "SN")
    private String sn;

    /**
     * 目标设备的设备编码(必选)
     */
    @JacksonXmlProperty(localName = "DeviceID")
    private String deviceId;

    private String name;

    private Long sumNum;

    private RecordListDTO recordList;
}
