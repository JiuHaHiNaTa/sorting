# 文件服务器配置管理与连通性测试 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建文件服务器配置的增/改/启停管理 API 和 MinIO 连通性测试 API

**Architecture:** 标准 Spring Boot 三层架构（Controller → Service → MyBatis Mapper），Jasypt 加密 AK/SK 密文存储，MinIO SDK 验证连通性

**Tech Stack:** Spring Boot 4.1.0 / Java 21 / Maven / MyBatis 3.0.4 / H2 / Jasypt 3.0.5 / MinIO Java SDK 8.5.17 / JUnit 5 + Mockito

## Global Constraints

- 所有对话、代码注释、文档使用简体中文，仅关键字、库名保留英文
- AK/SK 使用 Jasypt 加密存储（EncryptTypeHandler）
- connectivityStatus 和 enabled 不可由新增/修改接口写入
- 连通性通过前禁止 enabled = true
- 所有字符串入参需做特殊字符安全过滤
- 异常返回含业务错误码（PARAM/CONFIG/CNN/SYS 前缀）和具体描述
- API 路径使用动作式风格（/config/add 等）
- 建表语句保存在 etc/schema.sql
- 测试使用 H2 内嵌数据库 + Mockito Mock MinIO Client，不连真实服务器
- 新增/修改请求 id 字段不传入（id 自动生成 UUID）
- 所有必填字段需要 @NotBlank / @NotNull 校验

---

### Task 1: 项目脚手架 — Maven 依赖 + 配置 + 建表语句

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/application.yaml`
- Create: `etc/schema.sql`

**Interfaces:**
- Consumes: 无（初始任务）
- Produces: 基础运行环境和 DDL

- [ ] **Step 1: 更新 pom.xml 添加 Spring Boot Web、MyBatis、H2、Jasypt、MinIO、Validation 依赖**

修改 `pom.xml`：

```xml
<dependencies>
    <!-- Spring Boot Web (REST API) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Bean Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- MyBatis -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>3.0.4</version>
    </dependency>

    <!-- H2 Database -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Jasypt -->
    <dependency>
        <groupId>com.github.ulisesbocchio</groupId>
        <artifactId>jasypt-spring-boot-starter</artifactId>
        <version>3.0.5</version>
    </dependency>

    <!-- MinIO Java SDK -->
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.17</version>
    </dependency>

    <!-- Spring Boot Starter (已有，用于基础功能) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

同时添加 MyBatis Spring Boot Starter Plugin：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

- [ ] **Step 2: 创建 application.yaml**

```yaml
server:
  port: 8080

spring:
  application:
    name: sorting
  datasource:
    url: jdbc:h2:file:./data/sorting;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.sorting.entity
  configuration:
    map-underscore-to-camel-case: true

jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD:default-dev-key}
    algorithm: PBEWithMD5AndDES
```

- [ ] **Step 3: 创建 etc/schema.sql**

```sql
CREATE TABLE IF NOT EXISTS file_server_config (
    id                   VARCHAR(36)   PRIMARY KEY,
    server_address       VARCHAR(255)  NOT NULL,
    server_port          VARCHAR(10)   NOT NULL,
    bucket_name          VARCHAR(255)  NOT NULL,
    access_key           VARCHAR(512)  NOT NULL,
    secret_key           VARCHAR(512)  NOT NULL,
    file_directory       VARCHAR(500)  NOT NULL,
    connectivity_status  BOOLEAN       DEFAULT FALSE NOT NULL,
    enabled              BOOLEAN       DEFAULT FALSE NOT NULL,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

因为使用 H2，将 `etc/schema.sql` 复制到 `src/main/resources/schema.sql`：

```bash
cp etc/schema.sql src/main/resources/schema.sql
```

- [ ] **Step 4: 验证基础编译通过**

```bash
mvn clean compile -DskipTests
```
预期：BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add pom.xml src/main/resources/application.yaml src/main/resources/schema.sql etc/schema.sql
git commit -m "chore: scaffold project with MyBatis, H2, Jasypt, MinIO deps"
```

---

### Task 2: 数据层 — Entity + MyBatis Mapper + EncryptTypeHandler

**Files:**
- Create: `src/main/java/com/example/sorting/entity/FileServerConfig.java`
- Create: `src/main/java/com/example/sorting/repository/ConfigMapper.java`
- Create: `src/main/java/com/example/sorting/handler/EncryptTypeHandler.java`
- Create: `src/main/resources/mapper/ConfigMapper.xml`
- Test: 随集成测试验证

**Interfaces:**
- Consumes: `schema.sql` 定义的表结构
- Produces: `ConfigMapper` — `int insert(FileServerConfig)`, `FileServerConfig findById(String)`, `int update(FileServerConfig)`, `int updateConnectivityStatus(String, Boolean)`, `int updateEnabled(String, Boolean)`
- Produces: `EncryptTypeHandler` — Jasypt 加密/解密 TypeHandler

- [ ] **Step 1: 创建 FileServerConfig POJO**

```java
package com.example.sorting.entity;

import java.time.LocalDateTime;

public class FileServerConfig {
    private String id;
    private String serverAddress;
    private String serverPort;
    private String bucketName;
    private String accessKey;
    private String secretKey;
    private String fileDirectory;
    private Boolean connectivityStatus;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FileServerConfig() {}

    // — Getters & Setters —

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }

    public String getServerPort() { return serverPort; }
    public void setServerPort(String serverPort) { this.serverPort = serverPort; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getFileDirectory() { return fileDirectory; }
    public void setFileDirectory(String fileDirectory) { this.fileDirectory = fileDirectory; }

    public Boolean getConnectivityStatus() { return connectivityStatus; }
    public void setConnectivityStatus(Boolean connectivityStatus) { this.connectivityStatus = connectivityStatus; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: 创建 EncryptTypeHandler**

```java
package com.example.sorting.handler;

import com.github.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.core.env.StandardEnvironment;

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
```

- [ ] **Step 3: 创建 ConfigMapper 接口**

```java
package com.example.sorting.repository;

import com.example.sorting.entity.FileServerConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ConfigMapper {

    int insert(FileServerConfig config);

    Optional<FileServerConfig> findById(@Param("id") String id);

    int update(FileServerConfig config);

    int updateConnectivityStatus(@Param("id") String id, @Param("status") Boolean status);

    int updateEnabled(@Param("id") String id, @Param("enabled") Boolean enabled);

    List<FileServerConfig> findAll();
}
```

- [ ] **Step 4: 创建 ConfigMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.sorting.repository.ConfigMapper">

    <resultMap id="BaseResultMap" type="FileServerConfig">
        <id column="id" property="id"/>
        <result column="server_address" property="serverAddress"/>
        <result column="server_port" property="serverPort"/>
        <result column="bucket_name" property="bucketName"/>
        <result column="access_key" property="accessKey" typeHandler="com.example.sorting.handler.EncryptTypeHandler"/>
        <result column="secret_key" property="secretKey" typeHandler="com.example.sorting.handler.EncryptTypeHandler"/>
        <result column="file_directory" property="fileDirectory"/>
        <result column="connectivity_status" property="connectivityStatus"/>
        <result column="enabled" property="enabled"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
    </resultMap>

    <sql id="BaseColumns">
        id, server_address, server_port, bucket_name,
        access_key, secret_key, file_directory,
        connectivity_status, enabled, created_at, updated_at
    </sql>

    <insert id="insert">
        INSERT INTO file_server_config (id, server_address, server_port, bucket_name,
                                        access_key, secret_key, file_directory,
                                        connectivity_status, enabled, created_at, updated_at)
        VALUES (#{id}, #{serverAddress}, #{serverPort}, #{bucketName},
                #{accessKey, typeHandler=com.example.sorting.handler.EncryptTypeHandler},
                #{secretKey, typeHandler=com.example.sorting.handler.EncryptTypeHandler},
                #{fileDirectory}, #{connectivityStatus}, #{enabled},
                #{createdAt}, #{updatedAt})
    </insert>

    <select id="findById" resultMap="BaseResultMap">
        SELECT <include refid="BaseColumns"/>
        FROM file_server_config
        WHERE id = #{id}
    </select>

    <update id="update">
        UPDATE file_server_config
        SET server_address = #{serverAddress},
            server_port    = #{serverPort},
            bucket_name    = #{bucketName},
            access_key     = #{accessKey, typeHandler=com.example.sorting.handler.EncryptTypeHandler},
            secret_key     = #{secretKey, typeHandler=com.example.sorting.handler.EncryptTypeHandler},
            file_directory = #{fileDirectory},
            updated_at     = #{updatedAt}
        WHERE id = #{id}
    </update>

    <update id="updateConnectivityStatus">
        UPDATE file_server_config
        SET connectivity_status = #{status},
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
    </update>

    <update id="updateEnabled">
        UPDATE file_server_config
        SET enabled = #{enabled},
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
    </update>

    <select id="findAll" resultMap="BaseResultMap">
        SELECT <include refid="BaseColumns"/>
        FROM file_server_config
        ORDER BY created_at DESC
    </select>

</mapper>
```

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -DskipTests
```
预期：BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/sorting/entity/FileServerConfig.java \
       src/main/java/com/example/sorting/repository/ConfigMapper.java \
       src/main/java/com/example/sorting/handler/EncryptTypeHandler.java \
       src/main/resources/mapper/ConfigMapper.xml
git commit -m "feat: add Entity, ConfigMapper and EncryptTypeHandler"
```

---

### Task 3: 异常框架 + 统一响应 — ErrorCode + BusinessException + GlobalExceptionHandler + ApiResponse

**Files:**
- Create: `src/main/java/com/example/sorting/dto/ApiResponse.java`
- Create: `src/main/java/com/example/sorting/exception/ErrorCode.java`
- Create: `src/main/java/com/example/sorting/exception/BusinessException.java`
- Create: `src/main/java/com/example/sorting/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: 无
- Produces: `ApiResponse<T>`, `ErrorCode` 枚举, `BusinessException`, `GlobalExceptionHandler`

- [ ] **Step 1: 创建 ApiResponse**

```java
package com.example.sorting.dto;

public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;

    public ApiResponse() {}

    public ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "操作成功", data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>("SUCCESS", "操作成功", null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
```

- [ ] **Step 2: 创建 ErrorCode 枚举**

```java
package com.example.sorting.exception;

public enum ErrorCode {

    // 参数校验
    PARAM_001("PARAM_001", "参数校验失败"),
    PARAM_002("PARAM_002", "请求体格式错误"),
    PARAM_003("PARAM_003", "参数绑定异常"),

    // 配置管理
    CONFIG_001("CONFIG_001", "配置不存在"),
    CONFIG_002("CONFIG_002", "连通性测试通过前禁止启用"),

    // 连通性测试
    CNN_001("CNN_001", "AK/SK 认证失败"),
    CNN_002("CNN_002", "存储桶不存在"),
    CNN_003("CNN_003", "服务器连接异常"),

    // 系统通用
    SYS_001("SYS_001", "未知服务端异常");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
```

- [ ] **Step 3: 创建 BusinessException**

```java
package com.example.sorting.exception;

public class BusinessException extends RuntimeException {

    private final String code;
    private final String message;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.message = detail;
    }

    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
```

- [ ] **Step 4: 创建 GlobalExceptionHandler**

```java
package com.example.sorting.exception;

import com.example.sorting.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return ApiResponse.error("PARAM_001", msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ApiResponse.error("PARAM_002", "请求体格式错误");
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBind(BindException ex) {
        return ApiResponse.error("PARAM_003", "参数绑定异常");
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnknown(Exception ex) {
        return ApiResponse.error("SYS_001", "未知服务端异常: " + ex.getMessage());
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -DskipTests
```
预期：BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/sorting/dto/ApiResponse.java \
       src/main/java/com/example/sorting/exception/
git commit -m "feat: add exception framework and ApiResponse"
```

---

### Task 4: DTO 请求体 — 含 Bean Validation 注解

**Files:**
- Create: `src/main/java/com/example/sorting/dto/ConfigAddReq.java`
- Create: `src/main/java/com/example/sorting/dto/ConfigModifyReq.java`
- Create: `src/main/java/com/example/sorting/dto/ConfigToggleReq.java`
- Create: `src/main/java/com/example/sorting/dto/ConnectionCheckReq.java`

**Interfaces:**
- Consumes: 无
- Produces: 供 Controller 层接收请求和校验的 DTO

- [ ] **Step 1: 创建 ConfigAddReq**

```java
package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfigAddReq {

    @NotBlank(message = "服务器地址不能为空")
    private String serverAddress;

    @NotBlank(message = "服务器端口不能为空")
    private String serverPort;

    @NotBlank(message = "存储桶名称不能为空")
    private String bucketName;

    @NotBlank(message = "访问密钥不能为空")
    private String accessKey;

    @NotBlank(message = "秘密密钥不能为空")
    private String secretKey;

    @NotBlank(message = "文件目录不能为空")
    private String fileDirectory;

    // Getters & Setters

    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }

    public String getServerPort() { return serverPort; }
    public void setServerPort(String serverPort) { this.serverPort = serverPort; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getFileDirectory() { return fileDirectory; }
    public void setFileDirectory(String fileDirectory) { this.fileDirectory = fileDirectory; }
}
```

- [ ] **Step 2: 创建 ConfigModifyReq**

```java
package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfigModifyReq {

    @NotBlank(message = "配置ID不能为空")
    private String id;

    @NotBlank(message = "服务器地址不能为空")
    private String serverAddress;

    @NotBlank(message = "服务器端口不能为空")
    private String serverPort;

    @NotBlank(message = "存储桶名称不能为空")
    private String bucketName;

    @NotBlank(message = "访问密钥不能为空")
    private String accessKey;

    @NotBlank(message = "秘密密钥不能为空")
    private String secretKey;

    @NotBlank(message = "文件目录不能为空")
    private String fileDirectory;

    // Getters & Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }
    public String getServerPort() { return serverPort; }
    public void setServerPort(String serverPort) { this.serverPort = serverPort; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getFileDirectory() { return fileDirectory; }
    public void setFileDirectory(String fileDirectory) { this.fileDirectory = fileDirectory; }
}
```

- [ ] **Step 3: 创建 ConfigToggleReq**

```java
package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ConfigToggleReq {

    @NotBlank(message = "配置ID不能为空")
    private String id;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
```

- [ ] **Step 4: 创建 ConnectionCheckReq**

```java
package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class ConnectionCheckReq {

    @NotBlank(message = "配置ID不能为空")
    private String id;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -DskipTests
```
预期：BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/sorting/dto/ConfigAddReq.java \
       src/main/java/com/example/sorting/dto/ConfigModifyReq.java \
       src/main/java/com/example/sorting/dto/ConfigToggleReq.java \
       src/main/java/com/example/sorting/dto/ConnectionCheckReq.java
git commit -m "feat: add request DTOs with validation annotations"
```

---

### Task 5: ConfigService — 配置管理业务逻辑

**Files:**
- Create: `src/main/java/com/example/sorting/service/ConfigService.java`
- Create: `src/main/java/com/example/sorting/service/ConfigServiceTest.java`

**Interfaces:**
- Consumes: `ConfigMapper` (insert / findById / update / updateConnectivityStatus / updateEnabled), `ConfigAddReq`, `ConfigModifyReq`, `ConfigToggleReq`
- Produces: `ConfigService.addConfig(ConfigAddReq) → FileServerConfig`, `ConfigService.modifyConfig(ConfigModifyReq) → FileServerConfig`, `ConfigService.toggleConfig(ConfigToggleReq) → FileServerConfig`, `ConfigService.getConfig(String) → FileServerConfig`

- [ ] **Step 1: 先写 ConfigServiceTest 单元测试**

```java
package com.example.sorting.service;

import com.example.sorting.dto.ConfigAddReq;
import com.example.sorting.dto.ConfigModifyReq;
import com.example.sorting.dto.ConfigToggleReq;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.ConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private ConfigMapper configMapper;

    @InjectMocks
    private ConfigService configService;

    @Captor
    private ArgumentCaptor<FileServerConfig> configCaptor;

    private ConfigAddReq addReq;
    private FileServerConfig existingConfig;

    @BeforeEach
    void setUp() {
        addReq = new ConfigAddReq();
        addReq.setServerAddress("192.168.1.100");
        addReq.setServerPort("9000");
        addReq.setBucketName("test-bucket");
        addReq.setAccessKey("test-ak");
        addReq.setSecretKey("test-sk");
        addReq.setFileDirectory("/data/cdr");

        existingConfig = new FileServerConfig();
        existingConfig.setId(UUID.randomUUID().toString());
        existingConfig.setServerAddress("192.168.1.100");
        existingConfig.setServerPort("9000");
        existingConfig.setBucketName("test-bucket");
        existingConfig.setAccessKey("test-ak");
        existingConfig.setSecretKey("test-sk");
        existingConfig.setFileDirectory("/data/cdr");
        existingConfig.setConnectivityStatus(false);
        existingConfig.setEnabled(false);
    }

    @Test
    void addConfig_shouldGenerateIdAndInsert() {
        // when
        FileServerConfig result = configService.addConfig(addReq);

        // then
        verify(configMapper).insert(configCaptor.capture());
        FileServerConfig captured = configCaptor.getValue();

        assertNotNull(captured.getId());
        assertTrue(captured.getId().matches("[a-f0-9-]{36}"));
        assertEquals("192.168.1.100", captured.getServerAddress());
        assertEquals("9000", captured.getServerPort());
        assertFalse(captured.getConnectivityStatus());
        assertFalse(captured.getEnabled());
        assertNotNull(captured.getCreatedAt());
        assertNotNull(captured.getUpdatedAt());

        assertEquals(captured.getId(), result.getId());
    }

    @Test
    void addConfig_shouldSanitizeInput() {
        // given
        addReq.setServerAddress("<script>alert(1)</script>");

        // when
        configService.addConfig(addReq);

        // then
        verify(configMapper).insert(configCaptor.capture());
        assertFalse(configCaptor.getValue().getServerAddress().contains("<script>"));
    }

    @Test
    void modifyConfig_shouldThrowWhenConfigNotFound() {
        // given
        ConfigModifyReq req = new ConfigModifyReq();
        req.setId("non-existent-id");
        req.setServerAddress("192.168.1.200");
        req.setServerPort("9000");
        req.setBucketName("b");
        req.setAccessKey("ak");
        req.setSecretKey("sk");
        req.setFileDirectory("/d");

        when(configMapper.findById("non-existent-id")).thenReturn(Optional.empty());

        // when & then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.modifyConfig(req));
        assertEquals(ErrorCode.CONFIG_001.getCode(), ex.getCode());
    }

    @Test
    void modifyConfig_shouldUpdateFields() {
        // given
        ConfigModifyReq req = new ConfigModifyReq();
        req.setId(existingConfig.getId());
        req.setServerAddress("192.168.1.200");
        req.setServerPort("9001");
        req.setBucketName("new-bucket");
        req.setAccessKey("new-ak");
        req.setSecretKey("new-sk");
        req.setFileDirectory("/data/new");

        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        // when
        configService.modifyConfig(req);

        // then
        verify(configMapper).update(configCaptor.capture());
        FileServerConfig updated = configCaptor.getValue();
        assertEquals("192.168.1.200", updated.getServerAddress());
        assertEquals("9001", updated.getServerPort());
        assertEquals("new-bucket", updated.getBucketName());
        assertEquals("new-ak", updated.getAccessKey());
        assertEquals("new-sk", updated.getSecretKey());
        assertEquals("/data/new", updated.getFileDirectory());
        // connectivityStatus 和 enabled 不应被修改
        assertFalse(updated.getConnectivityStatus());
        assertFalse(updated.getEnabled());
    }

    @Test
    void toggleConfig_shouldThrowWhenConfigNotFound() {
        // given
        ConfigToggleReq req = new ConfigToggleReq();
        req.setId("non-existent");
        req.setEnabled(true);

        when(configMapper.findById("non-existent")).thenReturn(Optional.empty());

        // when & then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.toggleConfig(req));
        assertEquals(ErrorCode.CONFIG_001.getCode(), ex.getCode());
    }

    @Test
    void toggleConfig_shouldThrowWhenEnableWithoutConnectivity() {
        // given
        ConfigToggleReq req = new ConfigToggleReq();
        req.setId(existingConfig.getId());
        req.setEnabled(true);

        existingConfig.setConnectivityStatus(false);
        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        // when & then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.toggleConfig(req));
        assertEquals(ErrorCode.CONFIG_002.getCode(), ex.getCode());
    }

    @Test
    void toggleConfig_shouldAllowEnableWhenConnectivityPassed() {
        // given
        ConfigToggleReq req = new ConfigToggleReq();
        req.setId(existingConfig.getId());
        req.setEnabled(true);

        existingConfig.setConnectivityStatus(true);
        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        // when
        configService.toggleConfig(req);

        // then
        verify(configMapper).updateEnabled(existingConfig.getId(), true);
    }

    @Test
    void toggleConfig_shouldAllowDisableWithoutConnectivity() {
        // given
        ConfigToggleReq req = new ConfigToggleReq();
        req.setId(existingConfig.getId());
        req.setEnabled(false);

        existingConfig.setConnectivityStatus(false);
        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        // when
        configService.toggleConfig(req);

        // then
        verify(configMapper).updateEnabled(existingConfig.getId(), false);
    }

    @Test
    void getConfig_shouldThrowWhenNotFound() {
        // given
        when(configMapper.findById("non-existent")).thenReturn(Optional.empty());

        // when & then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.getConfig("non-existent"));
        assertEquals(ErrorCode.CONFIG_001.getCode(), ex.getCode());
    }

    @Test
    void getConfig_shouldReturnConfig() {
        // given
        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        // when
        FileServerConfig result = configService.getConfig(existingConfig.getId());

        // then
        assertNotNull(result);
        assertEquals(existingConfig.getId(), result.getId());
    }
}
```

- [ ] **Step 2: 运行测试，预期失败（ConfigService 尚未创建）**

```bash
mvn test -Dtest=ConfigServiceTest -DskipTests=false 2>&1 | head -30
```
预期：编译失败，提示找不到 ConfigService

- [ ] **Step 3: 创建 ConfigService 实现**

```java
package com.example.sorting.service;

import com.example.sorting.dto.ConfigAddReq;
import com.example.sorting.dto.ConfigModifyReq;
import com.example.sorting.dto.ConfigToggleReq;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.ConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConfigService {

    private final ConfigMapper configMapper;

    public ConfigService(ConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Transactional
    public FileServerConfig addConfig(ConfigAddReq req) {
        FileServerConfig config = new FileServerConfig();
        config.setId(UUID.randomUUID().toString());
        config.setServerAddress(sanitize(req.getServerAddress()));
        config.setServerPort(sanitize(req.getServerPort()));
        config.setBucketName(sanitize(req.getBucketName()));
        config.setAccessKey(req.getAccessKey());
        config.setSecretKey(req.getSecretKey());
        config.setFileDirectory(sanitize(req.getFileDirectory()));
        config.setConnectivityStatus(false);
        config.setEnabled(false);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        configMapper.insert(config);
        return config;
    }

    @Transactional
    public FileServerConfig modifyConfig(ConfigModifyReq req) {
        FileServerConfig existing = configMapper.findById(req.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_001));

        existing.setServerAddress(sanitize(req.getServerAddress()));
        existing.setServerPort(sanitize(req.getServerPort()));
        existing.setBucketName(sanitize(req.getBucketName()));
        existing.setAccessKey(req.getAccessKey());
        existing.setSecretKey(req.getSecretKey());
        existing.setFileDirectory(sanitize(req.getFileDirectory()));
        existing.setUpdatedAt(LocalDateTime.now());
        // 不修改 connectivityStatus 和 enabled

        configMapper.update(existing);
        return existing;
    }

    @Transactional
    public FileServerConfig toggleConfig(ConfigToggleReq req) {
        FileServerConfig config = configMapper.findById(req.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_001));

        if (Boolean.TRUE.equals(req.getEnabled()) && !Boolean.TRUE.equals(config.getConnectivityStatus())) {
            throw new BusinessException(ErrorCode.CONFIG_002);
        }

        configMapper.updateEnabled(req.getId(), req.getEnabled());
        config.setEnabled(req.getEnabled());
        return config;
    }

    @Transactional(readOnly = true)
    public FileServerConfig getConfig(String id) {
        return configMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_001));
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return input.replaceAll("<script[^>]*>.*?</script>", "")
                     .replaceAll("on\\w+\\s*=\\s*\"[^\"]*\"", "")
                     .replaceAll("on\\w+\\s*=\\s*'[^']*'", "");
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=ConfigServiceTest
```
预期：所有 9 个测试通过

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/sorting/service/ConfigService.java \
       src/test/java/com/example/sorting/service/ConfigServiceTest.java
git commit -m "feat: add ConfigService with CRUD and toggle logic"
```

---

### Task 6: ConfigController — 配置管理 HTTP API

**Files:**
- Create: `src/main/java/com/example/sorting/controller/ConfigController.java`
- Create: `src/test/java/com/example/sorting/controller/ConfigControllerTest.java`

**Interfaces:**
- Consumes: `ConfigService` (addConfig / modifyConfig / toggleConfig / getConfig), DTOs
- Produces: HTTP 端点 `POST /config/add`, `POST /config/modify`, `POST /config/toggle`

- [ ] **Step 1: 先写 ConfigControllerTest 集成测试**

```java
package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void addConfig_shouldReturnSuccess() {
        Map<String, String> request = Map.of(
                "serverAddress", "192.168.1.100",
                "serverPort", "9000",
                "bucketName", "test-bucket",
                "accessKey", "test-ak",
                "secretKey", "test-sk",
                "fileDirectory", "/data/cdr"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/config/add", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("code"));

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
        assertEquals("192.168.1.100", data.get("serverAddress"));
        assertEquals(false, data.get("connectivityStatus"));
        assertEquals(false, data.get("enabled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addConfig_shouldReturnParamErrorWhenFieldMissing() {
        Map<String, String> request = Map.of(
                "serverAddress", "192.168.1.100"
                // 缺少其他必填字段
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/config/add", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addConfig_shouldReturnParamErrorWhenFieldEmpty() {
        Map<String, String> request = Map.of(
                "serverAddress", "",
                "serverPort", "9000",
                "bucketName", "test-bucket",
                "accessKey", "test-ak",
                "secretKey", "test-sk",
                "fileDirectory", "/data/cdr"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/config/add", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toggleConfig_shouldReturnConfig002WhenNotConnected() {
        // 先新增一条
        Map<String, String> addReq = Map.of(
                "serverAddress", "192.168.1.100",
                "serverPort", "9000",
                "bucketName", "test-bucket",
                "accessKey", "test-ak",
                "secretKey", "test-sk",
                "fileDirectory", "/data/cdr"
        );
        ResponseEntity<Map> addResp = restTemplate.postForEntity("/config/add", addReq, Map.class);
        String id = (String) ((Map<String, Object>) addResp.getBody().get("data")).get("id");

        // 尝试启用（连通性未通过）
        Map<String, Object> toggleReq = Map.of("id", id, "enabled", true);
        ResponseEntity<Map> toggleResp = restTemplate.postForEntity(
                "/config/toggle", toggleReq, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, toggleResp.getStatusCode());
        assertEquals("CONFIG_002", toggleResp.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modifyConfig_shouldThrowConfig001WhenNotFound() {
        Map<String, String> modifyReq = Map.of(
                "id", UUID.randomUUID().toString(),
                "serverAddress", "192.168.1.200",
                "serverPort", "9001",
                "bucketName", "b",
                "accessKey", "ak",
                "secretKey", "sk",
                "fileDirectory", "/d"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/config/modify", modifyReq, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONFIG_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toggleConfig_shouldReturnConfig001WhenNotFound() {
        Map<String, Object> toggleReq = Map.of(
                "id", UUID.randomUUID().toString(),
                "enabled", false
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/config/toggle", toggleReq, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONFIG_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toggleConfig_shouldAllowDisableWithoutConnectivity() {
        // 先新增一条（连通性默认 false）
        Map<String, String> addReq = Map.of(
                "serverAddress", "192.168.1.100",
                "serverPort", "9000",
                "bucketName", "test-bucket",
                "accessKey", "test-ak",
                "secretKey", "test-sk",
                "fileDirectory", "/data/cdr"
        );
        ResponseEntity<Map> addResp = restTemplate.postForEntity("/config/add", addReq, Map.class);
        String id = (String) ((Map<String, Object>) addResp.getBody().get("data")).get("id");

        // 停用（不需要连通性）
        Map<String, Object> toggleReq = Map.of("id", id, "enabled", false);
        ResponseEntity<Map> toggleResp = restTemplate.postForEntity(
                "/config/toggle", toggleReq, Map.class);

        assertEquals(HttpStatus.OK, toggleResp.getStatusCode());
        assertEquals("SUCCESS", toggleResp.getBody().get("code"));
    }
}
```

- [ ] **Step 2: 运行测试，预期失败（ConfigController 尚未创建）**

```bash
mvn test -Dtest=ConfigControllerTest 2>&1 | head -30
```
预期：编译失败，找不到 ConfigController

- [ ] **Step 3: 创建 ConfigController**

```java
package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.ConfigAddReq;
import com.example.sorting.dto.ConfigModifyReq;
import com.example.sorting.dto.ConfigToggleReq;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @PostMapping("/config/add")
    public ApiResponse<FileServerConfig> addConfig(@Valid @RequestBody ConfigAddReq req) {
        FileServerConfig config = configService.addConfig(req);
        return ApiResponse.success(config);
    }

    @PostMapping("/config/modify")
    public ApiResponse<FileServerConfig> modifyConfig(@Valid @RequestBody ConfigModifyReq req) {
        FileServerConfig config = configService.modifyConfig(req);
        return ApiResponse.success(config);
    }

    @PostMapping("/config/toggle")
    public ApiResponse<FileServerConfig> toggleConfig(@Valid @RequestBody ConfigToggleReq req) {
        FileServerConfig config = configService.toggleConfig(req);
        return ApiResponse.success(config);
    }
}
```

- [ ] **Step 4: 运行全部测试验证通过**

```bash
mvn test
```
预期：所有测试通过

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/sorting/controller/ConfigController.java \
       src/test/java/com/example/sorting/controller/ConfigControllerTest.java
git commit -m "feat: add ConfigController with /config/add, /modify, /toggle"
```

---

### Task 7: ConnectionService — MinIO 连通性测试

**Files:**
- Create: `src/main/java/com/example/sorting/service/ConnectionService.java`
- Create: `src/test/java/com/example/sorting/service/ConnectionServiceTest.java`

**Interfaces:**
- Consumes: `ConfigMapper`, `ConnectionCheckReq`, `MinioClient`
- Produces: `ConnectionService.checkConnection(ConnectionCheckReq) → ApiResponse<Map>`

- [ ] **Step 1: 先写 ConnectionServiceTest**

```java
package com.example.sorting.service;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.ConfigMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

    @Mock
    private ConfigMapper configMapper;

    @InjectMocks
    private ConnectionService connectionService;

    private FileServerConfig config;

    @BeforeEach
    void setUp() {
        config = new FileServerConfig();
        config.setId(UUID.randomUUID().toString());
        config.setServerAddress("192.168.1.100");
        config.setServerPort("9000");
        config.setBucketName("test-bucket");
        config.setAccessKey("test-ak");
        config.setSecretKey("test-sk");
        config.setFileDirectory("/data/cdr");
        config.setConnectivityStatus(false);
        config.setEnabled(false);
    }

    @Test
    void checkConnection_shouldThrowWhenConfigNotFound() {
        // given
        when(configMapper.findById("non-existent")).thenReturn(Optional.empty());

        // when & then
        assertThrows(Exception.class, () -> connectionService.checkConnection("non-existent"));
    }

    @Test
    void checkConnection_shouldReturnSuccessOnValidConnection() throws Exception {
        // given
        when(configMapper.findById(config.getId())).thenReturn(Optional.of(config));

        // 使用 spy 模拟 MinioClient
        ConnectionService spyService = spy(connectionService);
        MinioClient mockClient = mock(MinioClient.class);
        when(mockClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doReturn(mockClient).when(spyService).createMinioClient(any(FileServerConfig.class));

        // when
        ApiResponse<?> response = spyService.checkConnection(config.getId());

        // then
        assertEquals("SUCCESS", response.getCode());
        verify(configMapper).updateConnectivityStatus(config.getId(), true);
    }

    @Test
    void checkConnection_shouldReturnConn002WhenBucketNotFound() throws Exception {
        // given
        when(configMapper.findById(config.getId())).thenReturn(Optional.of(config));

        ConnectionService spyService = spy(connectionService);
        MinioClient mockClient = mock(MinioClient.class);
        when(mockClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        doReturn(mockClient).when(spyService).createMinioClient(any(FileServerConfig.class));

        // when
        ApiResponse<?> response = spyService.checkConnection(config.getId());

        // then
        assertEquals("CNN_002", response.getCode());
        verify(configMapper, never()).updateConnectivityStatus(any(), any());
    }

    @Test
    void checkConnection_shouldReturnConn001WhenAuthFails() throws Exception {
        // given
        when(configMapper.findById(config.getId())).thenReturn(Optional.of(config));

        ConnectionService spyService = spy(connectionService);
        MinioClient mockClient = mock(MinioClient.class);
        MinioException authException = new MinioException("Invalid access key");
        when(mockClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(authException);
        doReturn(mockClient).when(spyService).createMinioClient(any(FileServerConfig.class));

        // when
        ApiResponse<?> response = spyService.checkConnection(config.getId());

        // then
        assertEquals("CNN_001", response.getCode());
        verify(configMapper, never()).updateConnectivityStatus(any(), any());
    }

    @Test
    void checkConnection_shouldReturnConn003OnNetworkError() throws Exception {
        // given
        when(configMapper.findById(config.getId())).thenReturn(Optional.of(config));

        ConnectionService spyService = spy(connectionService);
        MinioClient mockClient = mock(MinioClient.class);
        when(mockClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        doReturn(mockClient).when(spyService).createMinioClient(any(FileServerConfig.class));

        // when
        ApiResponse<?> response = spyService.checkConnection(config.getId());

        // then
        assertEquals("CNN_003", response.getCode());
        verify(configMapper, never()).updateConnectivityStatus(any(), any());
    }
}
```

- [ ] **Step 2: 运行测试，预期失败（ConnectionService 尚未创建）**

```bash
mvn test -Dtest=ConnectionServiceTest 2>&1 | head -30
```
预期：编译失败，找不到 ConnectionService

- [ ] **Step 3: 创建 ConnectionService 实现**

```java
package com.example.sorting.service;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.ConfigMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionService {

    private final ConfigMapper configMapper;

    public ConnectionService(ConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Transactional
    public ApiResponse<?> checkConnection(String configId) {
        FileServerConfig config = configMapper.findById(configId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_001));

        try {
            MinioClient client = createMinioClient(config);
            boolean bucketExists = client.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(config.getBucketName())
                            .build());

            if (!bucketExists) {
                return ApiResponse.error(ErrorCode.CNN_002.getCode(),
                        ErrorCode.CNN_002.getMessage() + ": " + config.getBucketName());
            }

            configMapper.updateConnectivityStatus(configId, true);
            return ApiResponse.success();

        } catch (MinioException e) {
            return ApiResponse.error(ErrorCode.CNN_001.getCode(),
                    ErrorCode.CNN_001.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(ErrorCode.CNN_003.getCode(),
                    ErrorCode.CNN_003.getMessage() + ": " + e.getMessage());
        }
    }

    // 包级可见，方便测试 spy
    MinioClient createMinioClient(FileServerConfig config) {
        return MinioClient.builder()
                .endpoint(config.getServerAddress(), Integer.parseInt(config.getServerPort()), false)
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=ConnectionServiceTest
```
预期：所有 5 个测试通过

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/sorting/service/ConnectionService.java \
       src/test/java/com/example/sorting/service/ConnectionServiceTest.java
git commit -m "feat: add ConnectionService with MinIO connectivity check"
```

---

### Task 8: ConnectionController + 全量运行验证

**Files:**
- Create: `src/main/java/com/example/sorting/controller/ConnectionController.java`
- Create: `src/test/java/com/example/sorting/controller/ConnectionControllerTest.java`

**Interfaces:**
- Consumes: `ConnectionService`, `ConnectionCheckReq`
- Produces: HTTP 端点 `POST /connection/check`

- [ ] **Step 1: 先写 ConnectionControllerTest**

```java
package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.ConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ConfigMapper configMapper;

    private String configId;

    @BeforeEach
    void setUp() {
        // 准备一条测试配置
        FileServerConfig config = new FileServerConfig();
        configId = UUID.randomUUID().toString();
        config.setId(configId);
        config.setServerAddress("192.168.1.100");
        config.setServerPort("9000");
        config.setBucketName("test-bucket");
        config.setAccessKey("test-ak");
        config.setSecretKey("test-sk");
        config.setFileDirectory("/data/cdr");
        config.setConnectivityStatus(false);
        config.setEnabled(false);
        config.setCreatedAt(java.time.LocalDateTime.now());
        config.setUpdatedAt(java.time.LocalDateTime.now());
        configMapper.insert(config);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnConn003WhenMinioUnreachable() {
        Map<String, String> request = Map.of("id", configId);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/connection/check", request, Map.class);

        // 因为没有真实 MinIO 服务器，预期返回 CNN_003
        // 注意：这里验证的是接口正常返回（状态码 200，错误码在 body 中）
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String code = (String) response.getBody().get("code");
        assertTrue(code.equals("CNN_001") || code.equals("CNN_003"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnConfig001WhenNotFound() {
        Map<String, String> request = Map.of("id", UUID.randomUUID().toString());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/connection/check", request, Map.class);

        // BusinessException 返回 400
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONFIG_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnParamErrorWhenIdBlank() {
        Map<String, String> request = Map.of("id", "");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/connection/check", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }
}
```

- [ ] **Step 2: 运行测试，预期失败（ConnectionController 尚未创建）**

```bash
mvn test -Dtest=ConnectionControllerTest 2>&1 | head -30
```
预期：编译失败，找不到 ConnectionController

- [ ] **Step 3: 创建 ConnectionController**

```java
package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.ConnectionCheckReq;
import com.example.sorting.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/connection/check")
    public ApiResponse<?> checkConnection(@Valid @RequestBody ConnectionCheckReq req) {
        return connectionService.checkConnection(req.getId());
    }
}
```

- [ ] **Step 4: 运行所有测试验证全部通过**

```bash
mvn test
```
预期：所有测试通过

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/sorting/controller/ConnectionController.java \
       src/test/java/com/example/sorting/controller/ConnectionControllerTest.java
git commit -m "feat: add ConnectionController with /connection/check endpoint"
```

---

### Task 9: 最终验证 + 应用启动测试

- [ ] **Step 1: 运行全量构建和测试**

```bash
mvn clean test
```
预期：BUILD SUCCESS，全部测试通过

- [ ] **Step 2: 应用启动验证**

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djasypt.encryptor.password=test-key"
```
后台启动后，手动验证：
```bash
# 新增配置
curl -s -X POST http://localhost:8080/config/add \
  -H "Content-Type: application/json" \
  -d '{"serverAddress":"192.168.1.100","serverPort":"9000","bucketName":"billing","accessKey":"ak","secretKey":"sk","fileDirectory":"/data"}'

# 修改配置
curl -s -X POST http://localhost:8080/config/modify \
  -H "Content-Type: application/json" \
  -d '{"id":"<上一步返回的id>","serverAddress":"192.168.1.101","serverPort":"9001","bucketName":"billing-v2","accessKey":"ak2","secretKey":"sk2","fileDirectory":"/data/v2"}'

# 启用（应该返回 CONFIG_002）
curl -s -X POST http://localhost:8080/config/toggle \
  -H "Content-Type: application/json" \
  -d '{"id":"<id>","enabled":true}'

# 停用（应该成功）
curl -s -X POST http://localhost:8080/config/toggle \
  -H "Content-Type: application/json" \
  -d '{"id":"<id>","enabled":false}'

# 连通性测试（应返回 CNN_003，因为无真实 MinIO 服务器）
curl -s -X POST http://localhost:8080/connection/check \
  -H "Content-Type: application/json" \
  -d '{"id":"<id>"}'
```

- [ ] **Step 3: 全部验证通过后提交最终版本**

```bash
git add -A
git commit -m "chore: finalize v0.0.1 — config management and connectivity check"
```
