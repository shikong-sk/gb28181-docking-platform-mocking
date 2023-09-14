package cn.skcks.docking.gb28181.mocking.service.device;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.URLUtil;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProxyService {
    private final DeviceService deviceService;

    private final DeviceProxyConfig proxyConfig;

    public void proxyVideo2Rtp(MockingDevice device, Date startTime, Date endTime){
        String url = URLUtil.completeUrl(proxyConfig.getUrl(), "/video");
        HashMap<String, String> map = new HashMap<>(3);
        String deviceCode = device.getDeviceCode();
        map.put("device_id", deviceCode);
        map.put("begin_time", DateUtil.format(startTime, DatePattern.PURE_DATETIME_FORMAT));
        map.put("end_time", DateUtil.format(endTime, DatePattern.PURE_DATETIME_FORMAT));
        String query = URLUtil.buildQuery(map, StandardCharsets.UTF_8);
        url = StringUtils.joinWith("?",url,query);
        log.info("设备: {} 视频 url: {}", deviceCode, url);
    }
}
