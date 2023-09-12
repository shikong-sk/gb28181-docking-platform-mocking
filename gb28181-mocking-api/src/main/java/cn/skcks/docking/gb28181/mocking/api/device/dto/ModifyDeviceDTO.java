package cn.skcks.docking.gb28181.mocking.api.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModifyDeviceDTO {
    @NotBlank(message = "id 不能为空")
    @Schema(description = "主键id")
    private Long id;

    @NotBlank(message = "设备编码 不能为空")
    @Schema(description = "设备编码")
    private String deviceCode;
    @NotBlank(message = "国标编码 不能为空")
    @Schema(description = "国标编码")
    private String gbDeviceId;
    @NotBlank(message = "国标通道id 不能为空")
    @Schema(description = "国标通道id")
    private String gbChannelId;
    @NotBlank(message = "设备名称 不能为空")
    @Schema(description = "设备名称")
    private String name;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "是否启用")
    private Boolean enable = true;
}
