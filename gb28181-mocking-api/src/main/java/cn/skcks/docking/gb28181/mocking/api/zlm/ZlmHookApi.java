package cn.skcks.docking.gb28181.mocking.api.zlm;

import cn.skcks.docking.gb28181.annotation.web.methods.PostJson;
import cn.skcks.docking.gb28181.media.dto.response.ZlmResponse;
import cn.skcks.docking.gb28181.media.dto.status.ResponseStatus;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.dto.ZlmPublishDTO;
import cn.skcks.docking.gb28181.mocking.api.zlm.dto.ZlmStreamChangeDTO;
import cn.skcks.docking.gb28181.mocking.api.zlm.dto.ZlmStreamNoneReaderDTO;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmPublishHookService;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmStreamChangeHookService;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmStreamNoneReaderHookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@Tag(name = "zlm Hook")
@RestController
@RequestMapping("/zlm/hook")
@RequiredArgsConstructor
public class ZlmHookApi {
    private final ZlmStreamChangeHookService zlmStreamChangeHookService;
    private final ZlmStreamNoneReaderHookService zlmStreamNoneReaderHookService;
    private final ZlmPublishHookService zlmPublishHookService;

    @PostJson("/on_stream_changed")
    public void onStreamChanged(@RequestBody ZlmStreamChangeDTO dto){
        log.debug("on_stream_changed {}", dto);
        if(StringUtils.equalsIgnoreCase(dto.getSchema(), "rtmp")){
            zlmStreamChangeHookService.processEvent(dto.getApp(),dto.getStream(), dto.getRegist());
        }
    }

    @PostJson("/on_stream_none_reader")
    public void onStreamNoneReader(@RequestBody ZlmStreamNoneReaderDTO dto){
        if(StringUtils.equalsIgnoreCase(dto.getSchema(), "rtmp")){
            zlmStreamNoneReaderHookService.processEvent(dto.getApp(),dto.getStream());
        }
    }

    @SneakyThrows
    @PostJson("/on_publish")
    public ZlmResponse<Void> onPublish(@RequestBody ZlmPublishDTO dto){
        zlmPublishHookService.processEvent(dto);
        return new ZlmResponse<>(ResponseStatus.Success, null, "");
    }
}
