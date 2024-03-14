package cn.skcks.docking.gb28181.mocking.service.zlm.hook;

import cn.skcks.docking.gb28181.mocking.config.sip.ZlmHookConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.executor.MockingExecutor;
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
public class ZlmStreamNoneReaderHookService {
    private final ZlmHookConfig zlmHookConfig;

    @Qualifier(MockingExecutor.EXECUTOR_BEAN_NAME)
    private final Executor executor;

    public interface ZlmStreamNoneReaderHookHandler {
        void handler();
    }

    @Getter(AccessLevel.PRIVATE)
    private ConcurrentMap<String, ConcurrentMap<String, ZlmStreamNoneReaderHookHandler>> handler = new ConcurrentHashMap<>();

    public ConcurrentMap<String, ZlmStreamNoneReaderHookHandler> getHandler(String app) {
        this.handler.putIfAbsent(app, new ConcurrentHashMap<>());
        return this.handler.get(app);
    }


    public void processEvent(String app, String streamId) {
        log.debug("流无人观看事件: app {}, streamId {}", app, streamId);

        ConcurrentMap<String, ZlmStreamNoneReaderHookHandler> handlers = getHandler(app);
        Optional.ofNullable(handlers.remove(streamId)).ifPresent((handler) -> {
            executor.execute(()->{
                try {
                    Thread.sleep(zlmHookConfig.getDelay().toMillis());
                } catch (InterruptedException ignored) {

                }
                handler.handler();
            });
        });
    }
}
