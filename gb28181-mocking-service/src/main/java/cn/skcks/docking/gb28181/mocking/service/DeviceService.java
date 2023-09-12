package cn.skcks.docking.gb28181.mocking.service;

import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.mapper.MockingDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class DeviceService {
    private final MockingDeviceMapper deviceMapper;

    
}
