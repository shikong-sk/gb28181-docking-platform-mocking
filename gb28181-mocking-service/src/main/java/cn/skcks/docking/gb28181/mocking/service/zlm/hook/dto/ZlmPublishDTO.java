package cn.skcks.docking.gb28181.mocking.service.zlm.hook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZlmPublishDTO {
    @JsonProperty("mediaServerId")
    private String mediaServerId;
    @JsonProperty("app")
    private String app;
    @JsonProperty("id")
    private String id;
    @JsonProperty("ip")
    private String ip;
    @JsonProperty("params")
    private String params;
    @JsonProperty("port")
    private int port;
    @JsonProperty("schema")
    private String schema;
    @JsonProperty("stream")
    private String stream;
    @JsonProperty("vhost")
    private String vhost;
}
