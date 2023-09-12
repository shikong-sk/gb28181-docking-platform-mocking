package cn.skcks.docking.gb28181.mocking.config.sip;



import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sip.ListeningPoint;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "gb28181.sip", ignoreInvalidFields = true)
@Order(0)
@Data
public class SipConfig {
	private List<String> ip;

	private List<String> showIp;

	private Integer port;

	private String domain;

	private String id;

	private String password;

	private int expire = 3600;

	Integer ptzSpeed = 50;

	Integer registerTimeInterval = 120;

	private String transport = ListeningPoint.UDP;

	private boolean alarm;

	public List<String> getShowIp() {
		if (this.showIp == null) {
			return this.ip;
		}
		return showIp;
	}
}
