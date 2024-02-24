package cn.skcks.docking.gb28181.mocking.config.sip;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "media.rtmp")
public class ZlmRtmpConfig {
    int port = 1935;
}
