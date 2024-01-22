package cn.skcks.docking.gb28181.mocking.api.zlm;

import cn.skcks.docking.gb28181.annotation.web.methods.PostJson;
import cn.skcks.docking.gb28181.mocking.api.zlm.dto.ZlmStreamChangeDTO;
import cn.skcks.docking.gb28181.mocking.api.zlm.dto.ZlmStreamNoneReaderDTO;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmStreamChangeHookService;
import cn.skcks.docking.gb28181.mocking.service.zlm.hook.ZlmStreamNoneReaderHookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @PostJson("/on_stream_changed")
    public void onStreamChanged(@RequestBody ZlmStreamChangeDTO dto){
        zlmStreamChangeHookService.processEvent(dto.getApp(),dto.getStream(), dto.getRegist());
    }

    @PostJson("/on_stream_none_reader")
    public void onStreamNoneReader(@RequestBody ZlmStreamNoneReaderDTO dto){
        zlmStreamNoneReaderHookService.processEvent(dto.getApp(),dto.getStream());
    }
}
