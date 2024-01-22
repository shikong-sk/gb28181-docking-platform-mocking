package cn.skcks.docking.gb28181.mocking.api.zlm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ZlmStreamNoneReaderDTO {
    @JsonProperty("mediaServerId")
    private String mediaServerId;
    @JsonProperty("app")
    private String app;
    @JsonProperty("schema")
    private String schema;
    @JsonProperty("stream")
    private String stream;
    @JsonProperty("vhost")
    private String vhost;
}
