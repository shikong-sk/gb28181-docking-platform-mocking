package cn.skcks.docking.gb28181.mocking.service.device;

import cn.skcks.docking.gb28181.common.json.JsonException;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.mapper.MockingDeviceDynamicSqlSupport;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.mapper.MockingDeviceMapper;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import com.github.pagehelper.ISelect;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Slf4j
@RequiredArgsConstructor
@Service
public class DeviceService {
    private final MockingDeviceMapper deviceMapper;

    public Optional<MockingDevice> getDeviceById(Long id){
        return deviceMapper.selectOne(s->
                s.where(MockingDeviceDynamicSqlSupport.id, isEqualTo(id)));
    }

    public Optional<MockingDevice> getDeviceByDeviceCode(String deviceCode){
        return deviceMapper.selectOne(s->
                s.where(MockingDeviceDynamicSqlSupport.deviceCode, isEqualTo(deviceCode)));
    }

    public Optional<MockingDevice> getDeviceByGbDeviceId(String gbDeviceId){
        return deviceMapper.selectOne(s->
                s.where(MockingDeviceDynamicSqlSupport.gbDeviceId,isEqualTo(gbDeviceId)));
    }

    public Optional<MockingDevice> getDeviceByGbDeviceIdAndChannel(String gbDeviceId,String channel){
        return deviceMapper.selectOne(s->
                s.where(MockingDeviceDynamicSqlSupport.gbDeviceId,isEqualTo(gbDeviceId))
                        .and(MockingDeviceDynamicSqlSupport.gbChannelId,isEqualTo(channel)));
    }

    /**
     * 添加设备
     * @param device 设备
     * @return 是否成功
     */
    @SneakyThrows
    public boolean addDevice(MockingDevice device) {
        if(device == null){
            return false;
        }

        String deviceCode = device.getDeviceCode();
        if(StringUtils.isBlank(deviceCode)){
            throw new JsonException("设备编码不能为空");
        }
        if(getDeviceByDeviceCode(deviceCode).isPresent()){
            throw new JsonException(MessageFormat.format("设备编码 {0} 已存在" ,deviceCode));
        }

        String gbDeviceId = device.getGbDeviceId();
        String channel = device.getGbChannelId();
        if(StringUtils.isBlank(gbDeviceId)){
            throw new JsonException("国标编码不能为空");
        }
        if(StringUtils.isBlank(channel)){
            throw new JsonException("国标通道不能为空");
        }
        if(getDeviceByGbDeviceIdAndChannel(gbDeviceId,channel).isPresent()){
            throw new JsonException(MessageFormat.format("国标编码 {0}, 通道 {1} 已存在" ,gbDeviceId, channel));
        }

        return deviceMapper.insert(device) > 0;
    }


    /**
     * 依据 id 或 deviceCode 或  gbDeviceId 删除设备信息
     * @param device 设备
     * @return 是否成功
     */
    public boolean deleteDevice(MockingDevice device){
        if(device == null){
            return false;
        }

        Long id = device.getId();
        String deviceCode = device.getDeviceCode();
        String gbDeviceId = device.getGbDeviceId();
        if(id != null){
            return deviceMapper.deleteByPrimaryKey(id) > 0;
        } else if(StringUtils.isNotBlank(deviceCode)){
            return deviceMapper.delete(d->d.where(MockingDeviceDynamicSqlSupport.deviceCode,isEqualTo(deviceCode))) > 0;
        } else if(StringUtils.isNotBlank(gbDeviceId)){
            return deviceMapper.delete(d->d.where(MockingDeviceDynamicSqlSupport.gbDeviceId,isEqualTo(gbDeviceId))) > 0;
        } else {
            return false;
        }
    }

    @SneakyThrows
    public boolean modifyDevice(MockingDevice device){
        if(device == null){
            return false;
        }
        Long id = device.getId();
        if(id == null){
            throw new JsonException("id 不能为空");
        }

        return deviceMapper.updateByPrimaryKey(device) > 0;
    }

    public List<MockingDevice> getAllDevice(){
       return deviceMapper.select(u -> u.orderBy(MockingDeviceDynamicSqlSupport.id.descending()));
    }

    /**
     * 分页查询设备
     * @param page 页数
     * @param size 数量
     * @return 分页设备
     */
    public PageInfo<MockingDevice> getDevicesWithPage(int page, int size){
        ISelect select = () -> deviceMapper.select(u -> u.orderBy(MockingDeviceDynamicSqlSupport.id.descending()));
        return getDevicesWithPage(page,size, select);
    }

    /**
     * 分页查询设备
     * @param page 页数
     * @param size 数量
     * @param select 查询语句
     * @return 分页设备
     */
    public PageInfo<MockingDevice> getDevicesWithPage(int page, int size, ISelect select){
        PageInfo<MockingDevice> pageInfo;
        try (Page<MockingDevice> startPage = PageHelper.startPage(page, size)) {
            pageInfo = startPage.doSelectPageInfo(select);
        }
        return pageInfo;
    }
}
