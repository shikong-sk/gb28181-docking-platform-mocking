package cn.skcks.docking.gb28181.mocking.service.zlm.hook;

import cn.skcks.docking.gb28181.mocking.config.sip.ZlmHookConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.executor.MockingExecutor;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.dto.ZlmPublishDTO;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

@Slf4j
@Data
@Service
@RequiredArgsConstructor
public class ZlmPublishHookService {
    private final ZlmHookConfig zlmHookConfig;

    @Qualifier(MockingExecutor.EXECUTOR_BEAN_NAME)
    private final Executor executor;

    public interface ZlmPublishHookHandler {
        void handler();
    }

    @Getter(AccessLevel.PRIVATE)
    private ConcurrentMap<String, ConcurrentMap<String, ZlmPublishHookHandler>> handler = new ConcurrentHashMap<>();

    public ConcurrentMap<String, ZlmPublishHookHandler> getHandler(String app) {
        this.handler.putIfAbsent(app, new ConcurrentHashMap<>());
        return this.handler.get(app);
    }


    public void processEvent(ZlmPublishDTO dto) {
        String app = dto.getApp();
        String streamId = dto.getStream();
        String ip = dto.getIp();
        log.debug("推流鉴权: app {}, streamId {}, ip {}", app, streamId, ip);

        ConcurrentMap<String, ZlmPublishHookHandler> handlers = getHandler(app);
        Optional.ofNullable(handlers.remove(streamId)).ifPresent((handler) -> {
            executor.execute(()->{
                handler.handler();
                try {
                    Thread.sleep(zlmHookConfig.getDelay().toMillis());
                } catch (InterruptedException e) {
                    log.error("{}", e.getMessage());
                }
            });
        });
    }
}
