# 项目发现与决策记录

## Spring Boot 4.1.0 兼容性问题

### MyBatis 自动配置不兼容
- **问题:** Spring Boot 4.1.0 将 `DataSourceAutoConfiguration` 移到了 `spring-boot-jdbc` 模块，`mybatis-spring-boot-autoconfigure:3.0.4` 的 `@AutoConfigureAfter` 引用了旧包名，导致自动配置未激活
- **解决:** 改为手动配置 MyBatis（`MyBatisConfig.java`），使用 `SQLSessionFactoryBuilder` + 程序化配置
- **文件:** `src/main/java/com/example/sorting/config/MyBatisConfig.java`

### SqlSessionFactoryBean 不兼容
- **问题:** `mybatis-spring:3.0.4` 使用了已移除的 `PropertyMapper.alwaysApplyingWhenNonNull()`
- **解决:** 绕过自动 `SqlSessionFactoryBean`，直接使用 `SqlSessionFactoryBuilder`
- **影响:** 需要手动注册 mapper XML

### TestRestTemplate 已移除
- **问题:** Spring Boot 4.1.0 移除了 `TestRestTemplate`
- **解决:** 改用 `RestTemplate` + `NoOpResponseErrorHandler`
- **注意:** `RestTemplate.postForEntity()` 在 4xx 响应时，需要设置 `Accept: application/json` 头，否则 Spring Boot 默认返回 HTML 错误页面

### EncryptTypeHandler 初始化方式
- **问题:** 直接在 TypeHandler 中使用 `DefaultLazyEncryptor` 静态初始化与 MyBatis 生命周期不兼容
- **解决:** 改为静态 `StringEncryptor` 字段，由 `MyBatisConfig` 在初始化 `SqlSessionFactory` 时通过 `setEncryptor()` 注入
- **文件:** `src/main/java/com/example/sorting/handler/EncryptTypeHandler.java`

### Jasypt 导入路径
- **问题:** 计划中使用了 `com.github.ulisesbocchio.jasyptspringboot` 但实际 jar 包路径为 `com.ulisesbocchio.jasyptspringboot`（无 `.github` 前缀）
- **解决:** 已在实现时修正

## MinIO SDK 测试注意事项
- `MinioClient.bucketExists()` 的 `throws` 子句声明的是 `MinioException`，但 `MinioException` 本身不直接抛出
- Mockito 测试时需要使用 `MinioException` 子类（如 `InsufficientDataException`）来模拟异常
- 已通过 spy + mock 方式完成测试覆盖

## 项目结构变更
- 从 JPA 改为 MyBatis（根据用户需求）
- 从标准三层注解扫描改为纯手动 MyBatis 配置（因兼容性问题）

## 安全修复

### API 响应明 AK/SK 泄露
- **问题:** `ConfigController` 直接返回 `FileServerConfig` 实体，其中 `accessKey`/`secretKey` 以明文出现在 API 响应体中
- **解决:** 创建 `ConfigResponse` DTO，在 Controller 层对 AK/SK 做前4位掩码输出（`"this-is-ak-long"` → `"this***"`）
- **文件:** `src/main/java/com/example/sorting/dto/ConfigResponse.java`
- **不影响:** ConfigService 内部仍使用完整明文，数据库加密/解密逻辑未变

## 待处理安全项
- Jasypt 默认密钥 `default-dev-key` 存在于配置文件中
- H2 Web Console 在生产环境中启用
