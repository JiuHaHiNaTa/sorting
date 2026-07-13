package com.example.sorting.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.core.env.StandardEnvironment;
import com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EncryptTypeHandler extends BaseTypeHandler<String> {

    private static final StringEncryptor ENCRYPTOR =
            new DefaultLazyEncryptor(new StandardEnvironment());

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, ENCRYPTOR.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? ENCRYPTOR.decrypt(value) : null;
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value != null ? ENCRYPTOR.decrypt(value) : null;
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value != null ? ENCRYPTOR.decrypt(value) : null;
    }
}
