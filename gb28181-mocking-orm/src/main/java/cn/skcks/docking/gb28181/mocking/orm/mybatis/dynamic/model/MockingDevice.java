package cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model;

import jakarta.annotation.Generated;

/**
 *
 * This class was generated by MyBatis Generator.
 * This class corresponds to the database table mocking_device
 */
public class MockingDevice {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.device_code")
    private String deviceCode;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_device_id")
    private String gbDeviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_channel_id")
    private String gbChannelId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.name")
    private String name;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.address")
    private String address;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.enable")
    private Boolean enable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.device_code")
    public String getDeviceCode() {
        return deviceCode;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.device_code")
    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode == null ? null : deviceCode.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_device_id")
    public String getGbDeviceId() {
        return gbDeviceId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_device_id")
    public void setGbDeviceId(String gbDeviceId) {
        this.gbDeviceId = gbDeviceId == null ? null : gbDeviceId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_channel_id")
    public String getGbChannelId() {
        return gbChannelId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.gb_channel_id")
    public void setGbChannelId(String gbChannelId) {
        this.gbChannelId = gbChannelId == null ? null : gbChannelId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.name")
    public String getName() {
        return name;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.name")
    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.address")
    public String getAddress() {
        return address;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.address")
    public void setAddress(String address) {
        this.address = address == null ? null : address.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.enable")
    public Boolean getEnable() {
        return enable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", comments="Source field: mocking_device.enable")
    public void setEnable(Boolean enable) {
        this.enable = enable;
    }
}