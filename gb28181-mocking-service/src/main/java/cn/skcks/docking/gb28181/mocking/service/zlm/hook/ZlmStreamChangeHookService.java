package cn.skcks.docking.gb28181.mocking.service.zlm.hook;

import cn.skcks.docking.gb28181.mocking.config.sip.ZlmHookConfig;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Data
@Service
@RequiredArgsConstructor
public class ZlmStreamChangeHookService {
    private final ZlmHookConfig zlmHookConfig;
    public interface ZlmStreamChangeHookHandler{
        void handler();
    }

    @Getter(AccessLevel.PRIVATE)
    private ConcurrentMap<String,ConcurrentMap<String, ZlmStreamChangeHookHandler>> registHandler = new ConcurrentHashMap<>();
    @Getter(AccessLevel.PRIVATE)
    private ConcurrentMap<String,ConcurrentMap<String, ZlmStreamChangeHookHandler>> unregistHandler = new ConcurrentHashMap<>();

    public ConcurrentMap<String, ZlmStreamChangeHookHandler> getRegistHandler(String app){
        this.registHandler.putIfAbsent(app,new ConcurrentHashMap<>());
        return this.registHandler.get(app);
    }

    public ConcurrentMap<String, ZlmStreamChangeHookHandler> getUnregistHandler(String app){
        this.unregistHandler.putIfAbsent(app,new ConcurrentHashMap<>());
        return this.unregistHandler.get(app);
    }

    public void processEvent(String app,String streamId, Boolean regist){
        log.debug("流改变事件: app {}, streamId {}, regist {}", app,streamId, regist);

        if(regist){
            ConcurrentMap<String, ZlmStreamChangeHookHandler> registHandler = getRegistHandler(app);
            Optional.ofNullable(registHandler.remove(streamId)).ifPresent((handler)->{
                try {
                    Thread.sleep(zlmHookConfig.getDelay().toMillis());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                handler.handler();
            });
        } else {
            ConcurrentMap<String, ZlmStreamChangeHookHandler> unregistHandler = getUnregistHandler(app);
            Optional.ofNullable(unregistHandler.remove(streamId)).ifPresent((handler)->{
                try {
                    Thread.sleep(zlmHookConfig.getDelay().toMillis());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                handler.handler();
            });
        }
    }
}
