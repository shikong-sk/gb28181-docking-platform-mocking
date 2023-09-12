package cn.skcks.docking.gb28181.mocking.api.device.convertor;

import cn.skcks.docking.gb28181.mocking.api.device.dto.AddDeviceDTO;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class DeviceDTOConvertor {
    public static final DeviceDTOConvertor INSTANCE = Mappers.getMapper(DeviceDTOConvertor.class);

    abstract public MockingDevice dto2dao(AddDeviceDTO dto);
}
