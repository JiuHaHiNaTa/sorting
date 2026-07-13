# 文件服务器配置管理与连通性测试微服务 — 设计文档

- 版本：0.0.1
- 日期：2026-07-13
- 技术栈：Spring Boot 4.1.0 + Java 21 + Maven + H2 + MyBatis + Jasypt + MinIO SDK

---

## 1. 项目概述

基于 Spring Boot 的文件服务器配置管理与连通性测试微服务。本版本（0.0.1）仅包含配置管理和连通性测试两个模块，话单分拣逻辑在后续版本添加。

## 2. 架构

### 2.1 分层结构

标准三层架构（按功能拆分为两个 Controller）：

```
controller
├── ConfigController          # 配置 CRUD（新增/修改/启用停用）
└── ConnectionController      # 连通性测试
service
├── ConfigService             # 配置业务逻辑
├── ConnectionService         # 连通性测试 + 状态更新
└── EncryptionService         # Jasypt 加密服务（TypeHandler 层）
repository
├── ConfigMapper              # MyBatis Mapper
entity
├── FileServerConfig          # 配置表实体 POJO
dto
├── ConfigAddReq              # 新增配置请求体
├── ConfigModifyReq           # 修改配置请求体
├── ConfigToggleReq           # 启用/停用请求体
├── ConnectionCheckReq        # 连通性测试请求体
└── ApiResponse<T>            # 统一响应体
exception
├── BusinessException         # 自定义业务异常
└── GlobalExceptionHandler    # @RestControllerAdvice 全局处理
config
└── JasyptConfig              # Jasypt 加密配置
```

### 2.2 数据库

- 类型：H2（开发 + 生产统一）
- 模式：文件模式（File Mode），数据持久化到磁盘
- DDL 建表语句：`etc/schema.sql`

### 2.3 API 路径风格

动作式路径，无版本号前缀：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/config/add` | 新增配置 |
| POST | `/config/modify` | 修改配置 |
| POST | `/config/toggle` | 启用/停用 |
| POST | `/connection/check` | 连通性测试 |

## 3. 数据模型

### 3.1 表结构

```sql
CREATE TABLE file_server_config (
    id                  VARCHAR(36)     PRIMARY KEY,
    server_address      VARCHAR(255)    NOT NULL,
    server_port         VARCHAR(10)     NOT NULL,
    bucket_name         VARCHAR(255)    NOT NULL,
    access_key          VARCHAR(512)    NOT NULL,
    secret_key          VARCHAR(512)    NOT NULL,
    file_directory      VARCHAR(500)    NOT NULL,
    connectivity_status BOOLEAN         DEFAULT FALSE NOT NULL,
    enabled             BOOLEAN         DEFAULT FALSE NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2 实体 POJO

```java
public class FileServerConfig {
    private String id;                  // UUID 自动生成
    private String serverAddress;       // 不允许为空
    private String serverPort;          // 不允许为空
    private String bucketName;          // 不允许为空
    private String accessKey;           // Jasypt 加密存储
    private String secretKey;           // Jasypt 加密存储
    private String fileDirectory;       // 不允许为空
    private Boolean connectivityStatus; // 默认 false
    private Boolean enabled;            // 默认 false
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 3.3 约束规则

- AK/SK 通过 Jasypt 自动加密解密（自定义 `BaseTypeHandler`）
- `connectivityStatus`、`enabled`、`id` 不可由新增/修改接口写入
- 连通性通过前禁止启用配置
- 所有字符串字段对特殊字符做安全过滤（防渗透攻击）

## 4. API 设计

### 4.1 统一响应格式

```json
// 成功
{ "code": "SUCCESS", "message": "操作成功", "data": { ... } }

// 失败
{ "code": "CONFIG_001", "message": "配置不存在" }
```

### 4.2 POST /config/add — 新增配置

请求体：
```json
{
  "serverAddress": "192.168.1.100",
  "serverPort": "9000",
  "bucketName": "billing-files",
  "accessKey": "your-access-key",
  "secretKey": "your-secret-key",
  "fileDirectory": "/opt/billing/cdr"
}
```

- 所有字段必填，`@NotBlank` 校验
- 新增后 `connectivityStatus = false`，`enabled = false`

### 4.3 POST /config/modify — 修改配置

请求体（新增字段 + `id`）：
```json
{
  "id": "uuid-xxx",
  "serverAddress": "192.168.1.101",
  "serverPort": "9001",
  "bucketName": "billing-files-v2",
  "accessKey": "new-access-key",
  "secretKey": "new-secret-key",
  "fileDirectory": "/opt/billing/cdr/2024"
}
```

- 忽略 `id`/`connectivityStatus`/`enabled` 入参
- 修改后不重置 `connectivityStatus`

### 4.4 POST /config/toggle — 启用/停用

请求体：
```json
{ "id": "uuid-xxx", "enabled": true }
```

- `connectivityStatus = false` 时，禁止 `enabled = true`，返回 `CONFIG_002`

### 4.5 POST /connection/check — 连通性测试

请求体：
```json
{ "id": "uuid-xxx" }
```

- 使用 MinIO SDK 验证 AK/SK 合法性 + 桶名有效性
- 通过 → `connectivityStatus = true`
- 失败 → 具体错误码 + 语义信息

## 5. 加密实现

**方案：Jasypt + MyBatis TypeHandler**

```
JasyptConfig:
  encryptor.password → ${JASYPT_ENCRYPTOR_PASSWORD}（环境变量）
  encryptor.algorithm → PBEWithMD5AndDES

EncryptTypeHandler extends BaseTypeHandler<String>:
  setParameter()  → Jasypt.encrypt(value)  写入密文
  getResult()     → Jasypt.decrypt(value)  自动解密
```

## 6. 连通性测试

使用 MinIO Java SDK，核心流程：
1. 根据 `id` 查询配置
2. 创建 `MinioClient`（address / port / AK / SK）
3. 调用 `bucketExists(bucketName)` — 同时验证 AK/SK 和桶名
4. 成功 → 更新 DB `connectivity_status = true`
5. 失败 → 分类返回错误码

## 7. 错误码体系

| 错误码 | 描述 | 触发场景 |
|--------|------|----------|
| `PARAM_001` | 参数校验失败 | @Valid 校验不通过 |
| `PARAM_002` | 请求体格式错误 | JSON 解析异常 |
| `PARAM_003` | 参数绑定异常 | 类型转换失败 |
| `CONFIG_001` | 配置不存在 | 按 id 查询为空 |
| `CONFIG_002` | 连通性未通过，禁止启用 | toggle 时 enabled=true 但未连通 |
| `CNN_001` | AK/SK 认证失败 | MinIO 认证异常 |
| `CNN_002` | 存储桶不存在 | bucketExists 返回 false |
| `CNN_003` | 服务器连接异常 | 超时/网络异常 |
| `SYS_001` | 未知服务端异常 | 兜底异常 |

## 8. 异常处理

| 异常类型 | HTTP 状态 | 错误码 |
|----------|-----------|--------|
| `MethodArgumentNotValidException` | 400 | `PARAM_001` |
| `HttpMessageNotReadableException` | 400 | `PARAM_002` |
| `BindException` | 400 | `PARAM_003` |
| `BusinessException` | 400 | 动态（CONFIG/CNN） |
| `Exception`（兜底） | 500 | `SYS_001` |

## 9. 测试方案

| 层级 | 框架 | 范围 |
|------|------|------|
| 单元测试 | JUnit 5 + Mockito | Service 层逻辑，Mock Mapper 和 MinIO |
| 集成测试 | @SpringBootTest + H2 | Controller 全链路 |
| Mock 策略 | @MockBean | MinIO Client 全 Mock，不连真实服务器 |

覆盖：
- 4 个 API 的正向 + 异常路径
- 所有必填字段边界校验
- AK/SK 密文写入验证
- 连通性状态约束验证

## 10. DDL 位置

建表语句：`etc/schema.sql`
