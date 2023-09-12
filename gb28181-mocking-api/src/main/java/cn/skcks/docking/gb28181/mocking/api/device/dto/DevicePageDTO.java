package cn.skcks.docking.gb28181.mocking.api.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DevicePageDTO {
    @Schema(description = "页数", example = "1")
    @NotNull(message = "page 不能为空")
    @Min(value = 1, message = "page 必须为正整数")
    int page;

    @Schema(description = "每页条数", example = "10")
    @NotNull(message = "size 不能为空")
    @Min(value = 1, message = "size 必须为正整数")
    int size;
}
