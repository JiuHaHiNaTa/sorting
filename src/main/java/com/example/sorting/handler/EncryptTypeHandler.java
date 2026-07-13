package com.example.sorting.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jasypt.encryption.StringEncryptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EncryptTypeHandler extends BaseTypeHandler<String> {

    /**
     * 由 MyBatisConfig 在初始化时设置，供 MyBatis XML 无参构造实例使用。
     */
    private static StringEncryptor encryptor;

    /**
     * MyBatis 通过无参构造实例化 TypeHandler。
     */
    public EncryptTypeHandler() {
    }

    /**
     * 在 Spring 初始化 SqlSessionFactory 时设置加密器实例。
     * 由于 MyBatis 使用无参构造创建 TypeHandler，需要通过静态字段共享。
     */
    public static void setEncryptor(StringEncryptor encryptor) {
        EncryptTypeHandler.encryptor = encryptor;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, encryptor.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? encryptor.decrypt(value) : null;
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value != null ? encryptor.decrypt(value) : null;
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value != null ? encryptor.decrypt(value) : null;
    }
}
