package cn.skcks.docking.gb28181.mocking.service.zlm.hook;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Data
@Service
public class ZlmStreamChangeHookService {
    public interface ZlmStreamChangeHookHandler{
        void handler();
    }

    public ConcurrentMap<String, ZlmStreamChangeHookHandler> handlerMap = new ConcurrentHashMap<>();

    public void processEvent(String streamId, Boolean regist){
        log.debug("stream {}, regist {}", streamId, regist);
        if(!regist){
            return;
        }

        Optional.ofNullable(handlerMap.remove(streamId)).ifPresent(ZlmStreamChangeHookHandler::handler);
    }
}
