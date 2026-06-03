package com.example.knowledgeagent.common.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
/**
 * 定义 JsonbStringTypeHandler 组件，承载对应模块的业务职责。
 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    /**
     * 处理 throws SQLException  方法对应的业务逻辑。
     */
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    /**
     * 处理 throws SQLException  方法对应的业务逻辑。
     */
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    /**
     * 处理 throws SQLException  方法对应的业务逻辑。
     */
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    /**
     * 处理 throws SQLException  方法对应的业务逻辑。
     */
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
