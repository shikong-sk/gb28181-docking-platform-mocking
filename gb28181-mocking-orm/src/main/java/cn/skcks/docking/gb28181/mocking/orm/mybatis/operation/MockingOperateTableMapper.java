package cn.skcks.docking.gb28181.mocking.orm.mybatis.operation;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MockingOperateTableMapper {
    // int createNewTable(@Param("tableName")String tableName);
    void createDeviceTable();
    void dropProcedureUpdateDeviceTable();
    void createProcedureUpdateDeviceTable();
    void callProcedureUpdateDeviceTable();
}
