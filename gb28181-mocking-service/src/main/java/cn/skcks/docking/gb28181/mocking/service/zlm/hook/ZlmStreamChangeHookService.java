package cn.skcks.docking.gb28181.mocking.service.zlm.hook;

import cn.skcks.docking.gb28181.mocking.config.sip.ZlmHookConfig;
import lombok.Data;
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

    public ConcurrentMap<String, ZlmStreamChangeHookHandler> handlerMap = new ConcurrentHashMap<>();

    public void processEvent(String streamId, Boolean regist){
        log.debug("stream {}, regist {}", streamId, regist);
        if(!regist){
            return;
        }

        Optional.ofNullable(handlerMap.remove(streamId)).ifPresent((handler)->{
            try {
                Thread.sleep(zlmHookConfig.getDelay().toMillis());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            handler.handler();
        });
    }
}
