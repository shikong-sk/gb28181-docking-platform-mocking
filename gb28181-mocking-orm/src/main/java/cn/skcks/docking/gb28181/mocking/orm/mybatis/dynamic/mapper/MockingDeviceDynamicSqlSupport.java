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

    /**
     * Database Column Remarks:
     *   自定义21位设备编码
     */
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.device_code")
    public static final SqlColumn<String> deviceCode = mockingDevice.deviceCode;

    /**
     * Database Column Remarks:
     *   设备id
     */
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_device_id")
    public static final SqlColumn<String> gbDeviceId = mockingDevice.gbDeviceId;

    /**
     * Database Column Remarks:
     *   通道id
     */
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_channel_id")
    public static final SqlColumn<String> gbChannelId = mockingDevice.gbChannelId;

    /**
     * Database Column Remarks:
     *   设备/通道名称
     */
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.name")
    public static final SqlColumn<String> name = mockingDevice.name;

    /**
     * Database Column Remarks:
     *   设备地址信息
     */
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.address")
    public static final SqlColumn<String> address = mockingDevice.address;

    /**
     * Database Column Remarks:
     *   是否启用
     */
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.enable")
    public static final SqlColumn<Boolean> enable = mockingDevice.enable;

    /**
     * Database Column Remarks:
     *   实时视频流地址
     */
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.live_stream")
    public static final SqlColumn<String> liveStream = mockingDevice.liveStream;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source Table: mocking_device")
    public static final class MockingDevice extends AliasableSqlTable<MockingDevice> {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> deviceCode = column("device_code", JDBCType.VARCHAR);

        public final SqlColumn<String> gbDeviceId = column("gb_device_id", JDBCType.VARCHAR);

        public final SqlColumn<String> gbChannelId = column("gb_channel_id", JDBCType.VARCHAR);

        public final SqlColumn<String> name = column("name", JDBCType.VARCHAR);

        public final SqlColumn<String> address = column("address", JDBCType.VARCHAR);

        public final SqlColumn<Boolean> enable = column("enable", JDBCType.BIT);

        public final SqlColumn<String> liveStream = column("live_stream", JDBCType.VARCHAR);

        public MockingDevice() {
            super("mocking_device", MockingDevice::new);
        }
    }
}