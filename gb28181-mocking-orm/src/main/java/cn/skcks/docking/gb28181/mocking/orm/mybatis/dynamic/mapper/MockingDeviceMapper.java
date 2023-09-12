package cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.mapper;

import static cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.mapper.MockingDeviceDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import jakarta.annotation.Generated;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.select.CountDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.UpdateDSL;
import org.mybatis.dynamic.sql.update.UpdateDSLCompleter;
import org.mybatis.dynamic.sql.update.UpdateModel;
import org.mybatis.dynamic.sql.util.SqlProviderAdapter;
import org.mybatis.dynamic.sql.util.mybatis3.CommonCountMapper;
import org.mybatis.dynamic.sql.util.mybatis3.CommonDeleteMapper;
import org.mybatis.dynamic.sql.util.mybatis3.CommonInsertMapper;
import org.mybatis.dynamic.sql.util.mybatis3.CommonUpdateMapper;
import org.mybatis.dynamic.sql.util.mybatis3.MyBatis3Utils;

@Mapper
public interface MockingDeviceMapper extends CommonCountMapper, CommonDeleteMapper, CommonInsertMapper<MockingDevice>, CommonUpdateMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    BasicColumn[] selectList = BasicColumn.columnList(id, deviceCode, name, address, gbDeviceId, gbChannelId);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @Results(id="MockingDeviceResult", value = {
        @Result(column="id", property="id", jdbcType=JdbcType.BIGINT, id=true),
        @Result(column="device_code", property="deviceCode", jdbcType=JdbcType.VARCHAR),
        @Result(column="name", property="name", jdbcType=JdbcType.VARCHAR),
        @Result(column="address", property="address", jdbcType=JdbcType.VARCHAR),
        @Result(column="gb_device_id", property="gbDeviceId", jdbcType=JdbcType.VARBINARY),
        @Result(column="gb_channel_id", property="gbChannelId", jdbcType=JdbcType.VARBINARY)
    })
    List<MockingDevice> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ResultMap("MockingDeviceResult")
    Optional<MockingDevice> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, mockingDevice, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, mockingDevice, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default int insert(MockingDevice row) {
        return MyBatis3Utils.insert(this::insert, row, mockingDevice, c ->
            c.map(id).toProperty("id")
            .map(deviceCode).toProperty("deviceCode")
            .map(name).toProperty("name")
            .map(address).toProperty("address")
            .map(gbDeviceId).toProperty("gbDeviceId")
            .map(gbChannelId).toProperty("gbChannelId")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default int insertMultiple(Collection<MockingDevice> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, mockingDevice, c ->
            c.map(id).toProperty("id")
            .map(deviceCode).toProperty("deviceCode")
            .map(name).toProperty("name")
            .map(address).toProperty("address")
            .map(gbDeviceId).toProperty("gbDeviceId")
            .map(gbChannelId).toProperty("gbChannelId")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default int insertSelective(MockingDevice row) {
        return MyBatis3Utils.insert(this::insert, row, mockingDevice, c ->
            c.map(id).toPropertyWhenPresent("id", row::getId)
            .map(deviceCode).toPropertyWhenPresent("deviceCode", row::getDeviceCode)
            .map(name).toPropertyWhenPresent("name", row::getName)
            .map(address).toPropertyWhenPresent("address", row::getAddress)
            .map(gbDeviceId).toPropertyWhenPresent("gbDeviceId", row::getGbDeviceId)
            .map(gbChannelId).toPropertyWhenPresent("gbChannelId", row::getGbChannelId)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default Optional<MockingDevice> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, mockingDevice, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default List<MockingDevice> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, mockingDevice, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default List<MockingDevice> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, mockingDevice, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default Optional<MockingDevice> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, mockingDevice, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    static UpdateDSL<UpdateModel> updateAllColumns(MockingDevice row, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(row::getId)
                .set(deviceCode).equalTo(row::getDeviceCode)
                .set(name).equalTo(row::getName)
                .set(address).equalTo(row::getAddress)
                .set(gbDeviceId).equalTo(row::getGbDeviceId)
                .set(gbChannelId).equalTo(row::getGbChannelId);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(MockingDevice row, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(row::getId)
                .set(deviceCode).equalToWhenPresent(row::getDeviceCode)
                .set(name).equalToWhenPresent(row::getName)
                .set(address).equalToWhenPresent(row::getAddress)
                .set(gbDeviceId).equalToWhenPresent(row::getGbDeviceId)
                .set(gbChannelId).equalToWhenPresent(row::getGbChannelId);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default int updateByPrimaryKey(MockingDevice row) {
        return update(c ->
            c.set(deviceCode).equalTo(row::getDeviceCode)
            .set(name).equalTo(row::getName)
            .set(address).equalTo(row::getAddress)
            .set(gbDeviceId).equalTo(row::getGbDeviceId)
            .set(gbChannelId).equalTo(row::getGbChannelId)
            .where(id, isEqualTo(row::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    default int updateByPrimaryKeySelective(MockingDevice row) {
        return update(c ->
            c.set(deviceCode).equalToWhenPresent(row::getDeviceCode)
            .set(name).equalToWhenPresent(row::getName)
            .set(address).equalToWhenPresent(row::getAddress)
            .set(gbDeviceId).equalToWhenPresent(row::getGbDeviceId)
            .set(gbChannelId).equalToWhenPresent(row::getGbChannelId)
            .where(id, isEqualTo(row::getId))
        );
    }
}