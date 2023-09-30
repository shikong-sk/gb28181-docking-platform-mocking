package cn.skcks.docking.gb28181.mocking.service.zlm.hook;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Data
@Service
public class ZlmStreamChangeHookService {
    public interface ZlmStreamChangeHookHandler{
        void handler();
    }

    public ConcurrentMap<String, ZlmStreamChangeHookHandler> handlerMap = new ConcurrentHashMap<>();

    synchronized public void processEvent(String streamId, Boolean regist){
        if(!regist){
            return;
        }

        Optional.ofNullable(handlerMap.remove(streamId)).ifPresent((handler)->{
            handlerMap.remove(streamId);
            handler.handler();
        });
    }
}
