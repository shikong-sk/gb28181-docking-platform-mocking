package cn.skcks.docking.gb28181.mocking.starter;

import cn.skcks.docking.gb28181.common.json.JsonUtils;
import cn.skcks.docking.gb28181.core.sip.service.SipService;
import cn.skcks.docking.gb28181.mocking.config.sip.SipConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Order(0)
@Slf4j
@RequiredArgsConstructor
@Component
@DependsOn("mockingOrmInitService")
public class SipStarter implements SmartLifecycle {
    private final SipService sipService;
    private final SipConfig sipConfig;
    private boolean isRunning;
    @Override
    public void start() {
        if(checkConfig()){
            isRunning = true;
            log.debug("sip 服务 启动");
            sipService.run();
        }
    }

    @Override
    public void stop() {
        log.debug("sip 服务 关闭");
        sipService.stop();
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    public boolean checkConfig(){
        log.debug("sip 配置信息 => \n{}", JsonUtils.toJson(sipConfig));
        if(CollectionUtils.isEmpty(sipConfig.getIp())){
            log.error("sip ip 配置错误, 请检查配置是否正确");
            return false;
        }

        return true;
    }
}
