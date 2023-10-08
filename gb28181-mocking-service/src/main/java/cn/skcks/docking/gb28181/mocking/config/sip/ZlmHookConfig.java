package cn.skcks.docking.gb28181.mocking.config.sip;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "media.local")
public class ZlmHookConfig {
    private String hook;
    private Duration delay = Duration.ofMillis(100);
    private Duration fixed = Duration.ofSeconds(30);
    private Boolean proxy = true;
}
