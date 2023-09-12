package cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.mapper;

import jakarta.annotation.Generated;
import java.sql.JDBCType;
import org.mybatis.dynamic.sql.AliasableSqlTable;
import org.mybatis.dynamic.sql.SqlColumn;

public final class MockingDeviceDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    public static final MockingDevice mockingDevice = new MockingDevice();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.id")
    public static final SqlColumn<Long> id = mockingDevice.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.device_code")
    public static final SqlColumn<String> deviceCode = mockingDevice.deviceCode;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_device_id")
    public static final SqlColumn<String> gbDeviceId = mockingDevice.gbDeviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_channel_id")
    public static final SqlColumn<String> gbChannelId = mockingDevice.gbChannelId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.name")
    public static final SqlColumn<String> name = mockingDevice.name;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.address")
    public static final SqlColumn<String> address = mockingDevice.address;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.enable")
    public static final SqlColumn<Boolean> enable = mockingDevice.enable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    public static final class MockingDevice extends AliasableSqlTable<MockingDevice> {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> deviceCode = column("device_code", JDBCType.VARCHAR);

        public final SqlColumn<String> gbDeviceId = column("gb_device_id", JDBCType.VARCHAR);

        public final SqlColumn<String> gbChannelId = column("gb_channel_id", JDBCType.VARCHAR);

        public final SqlColumn<String> name = column("name", JDBCType.VARCHAR);

        public final SqlColumn<String> address = column("address", JDBCType.VARCHAR);

        public final SqlColumn<Boolean> enable = column("enable", JDBCType.BIT);

        public MockingDevice() {
            super("mocking_device", MockingDevice::new);
        }
    }
}