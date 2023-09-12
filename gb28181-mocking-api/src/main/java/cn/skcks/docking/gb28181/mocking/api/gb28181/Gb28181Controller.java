package cn.skcks.docking.gb28181.mocking.api.gb28181;

import cn.skcks.docking.gb28181.annotation.web.methods.GetJson;
import cn.skcks.docking.gb28181.common.json.JsonResponse;
import cn.skcks.docking.gb28181.mocking.config.SwaggerConfig;
import cn.skcks.docking.gb28181.mocking.service.gb28181.register.RegisterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@Slf4j
@Tag(name = "设备信息")
@RestController
@RequestMapping("/gb28181")
@RequiredArgsConstructor
public class Gb28181Controller {
    private final RegisterService registerService;
    @Bean
    public GroupedOpenApi gb28181Api() {
        return SwaggerConfig.api("GB28181 Api", "/gb28181");
    }

    @GetJson("/register")
    public DeferredResult<JsonResponse<Boolean>> register(){
        return registerService.register();
    }
}
