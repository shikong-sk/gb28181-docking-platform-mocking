package cn.skcks.docking.gb28181.mocking.orm;

import cn.skcks.docking.gb28181.mocking.orm.mybatis.operation.MockingOperateTableMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Order(0)
@RequiredArgsConstructor
@MapperScans({
        @MapperScan("cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic"),
        @MapperScan("cn.skcks.docking.gb28181.mocking.orm.mybatis.operation"),
})
public class MockingOrmInitService {
    private final MockingOperateTableMapper mapper;

    @PostConstruct
    public void init(){
        log.info("[orm] 自动建表");
        mapper.createDeviceTable();
    }
}
