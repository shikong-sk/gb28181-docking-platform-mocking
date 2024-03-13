package cn.skcks.docking.gb28181.mocking.service.zlm;

import cn.skcks.docking.gb28181.media.dto.config.HookConfig;
import cn.skcks.docking.gb28181.media.dto.config.ServerConfig;
import cn.skcks.docking.gb28181.media.dto.response.ZlmResponse;
import cn.skcks.docking.gb28181.media.proxy.ZlmMediaService;
import cn.skcks.docking.gb28181.mocking.config.sip.ZlmHookConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Order(0)
@Slf4j
@RequiredArgsConstructor
@Component
public class ZlmInitService {
    private final ZlmMediaService zlmMediaService;
    private final ZlmHookConfig zlmHookConfig;
    @PostConstruct
    public void init(){
        ZlmResponse<List<ServerConfig>> serverConfig = zlmMediaService.getServerConfig();
        List<ServerConfig> data = serverConfig.getData();
        ServerConfig config = data.get(0);
        HookConfig hook = config.getHook();
        hook.setOnStreamChanged(zlmHookConfig.getHook() + "/on_stream_changed");
        hook.setOnStreamNoneReader(zlmHookConfig.getHook() + "/on_stream_none_reader");
        hook.setOnPublish(zlmHookConfig.getHook() + "/on_publish");
        config.getRtmp().setHandshakeSecond(15);
        config.getRtmp().setKeepAliveSecond(10);
        zlmMediaService.setServerConfig(config);
    }
}
