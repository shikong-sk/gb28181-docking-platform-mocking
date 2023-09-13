package cn.skcks.docking.gb28181.mocking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@EnableFeignClients(basePackages = "cn.skcks.docking.gb28181.media")
@SpringBootApplication
@ComponentScan(basePackages = {
        "cn.skcks.docking.gb28181.annotation",
        "cn.skcks.docking.gb28181.common",
        "cn.skcks.docking.gb28181.mocking",
        "cn.skcks.docking.gb28181.core.sip.utils",
        "cn.skcks.docking.gb28181.core.sip.message.sender",
})
public class Gb28181MockingStarter {
    public static void main(String[] args) {
        SpringApplication.run(Gb28181MockingStarter.class, args);
    }
}
