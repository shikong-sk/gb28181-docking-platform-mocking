package cn.skcks.docking.gb28181.mocking.config.sip;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gb28181.server", ignoreInvalidFields = true)
@Order(0)
@Data
public class ServerConfig {
    private String id;
    private String domain;
    private String ip;
    private int port;
    private String password;
}
