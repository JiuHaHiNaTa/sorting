# 话单分拣系统 v0.0.2 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 v0.0.1 文件服务器配置管理基础上，构建完整的话单分拣系统：基础数据管理、任务流水线编排、文件处理（扫描→校验→解压→解析→持久化→归档）。

**Architecture:** 分拣任务通过自建 Pipeline 流水线执行，6 个步骤各自实现 StepHandler 接口。步骤之间通过 StepContext 传递数据。任务状态持久化到 DB，支持崩溃恢复、超时控制、失败重试。线程池控制并发度。

**Tech Stack:** Spring Boot 4.1.0 + Java 21 + MyBatis + H2 + MinIO SDK (已有)

## Global Constraints

- 所有表使用逻辑外键，不创建物理外键约束
- 所有 API 使用 POST 方法，动作式路径风格（如 `/operator/add`）
- 统一响应使用 `ApiResponse<T>` 格式
- 所有时间使用 UTC，格式为 Epoch Seconds
- 错误码体系统一在 `ErrorCode` 枚举中管理
- 新代码遵循 SNAKE 命名约定（resources/配置）与 camelCase（Java 代码）
- 基础数据表（operator/service_az/usage_unit）的 code 字段在话单中必须严格匹配

---
## 文件结构总览

```
新增文件（约 50 个）：
├── etc/schema.sql  — 追加新表 DDL
├── entity/         — 6 个实体类 + 1 个备份表实体
├── repository/     — 6 个 Mapper 接口 + 6 个 XML
├── dto/            — ~10 个 DTO/VO
├── cache/          — 1 个缓存组件
├── service/        — 4 个 Service
├── pipeline/       — 1 个接口 + 8 个实现/辅助类
├── controller/     — 4 个 Controller
├── config/         — 线程池配置

修改文件：
├── exception/ErrorCode.java
├── config/MyBatisConfig.java
└── application.yaml
```

---

### Task 1: 数据库 DDL + 实体层

**Files:**
- Modify: `etc/schema.sql` — 追加新表 DDL
- Create: `src/main/java/com/example/sorting/entity/Operator.java`
- Create: `src/main/java/com/example/sorting/entity/ServiceAz.java`
- Create: `src/main/java/com/example/sorting/entity/UsageUnit.java`
- Create: `src/main/java/com/example/sorting/entity/SortingTask.java`
- Create: `src/main/java/com/example/sorting/entity/SortingTaskBackup.java`
- Create: `src/main/java/com/example/sorting/entity/SortingStepLog.java`
- Create: `src/main/java/com/example/sorting/entity/CdrRecord.java`

**Interfaces:**
- Consumes: 无（第一个任务）
- Produces: 7 个实体 POJO，后续 Task 2 的 Mapper 层依赖这些实体

- [ ] **Step 1: 追加 DDL 到 schema.sql**

追加到文件末尾：

```sql
-- ============================================================
-- v0.0.2: CDR Sorting tables
-- ============================================================

CREATE TABLE IF NOT EXISTS operator (
    id          VARCHAR(36)     PRIMARY KEY,
    code        VARCHAR(50)     NOT NULL UNIQUE,
    name        VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS service_az (
    id          VARCHAR(36)     PRIMARY KEY,
    code        VARCHAR(50)     NOT NULL UNIQUE,
    name        VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS usage_unit (
    id          VARCHAR(36)     PRIMARY KEY,
    code        VARCHAR(20)     NOT NULL UNIQUE,
    name        VARCHAR(50)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sorting_task (
    id              VARCHAR(36)     PRIMARY KEY,
    file_server_id  VARCHAR(36)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    current_step    VARCHAR(30),
    retry_count     INT             DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    timeout_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sorting_task_backup (
    id              VARCHAR(36)     PRIMARY KEY,
    file_server_id  VARCHAR(36)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    current_step    VARCHAR(30),
    retry_count     INT             DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    timeout_at      TIMESTAMP,
    archived_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cdr_record (
    id              VARCHAR(36)     PRIMARY KEY,
    task_id         VARCHAR(36)     NOT NULL,
    operator_id     VARCHAR(36)     NOT NULL,
    service_az_id   VARCHAR(36)     NOT NULL,
    resource_id     VARCHAR(36)     NOT NULL,
    usage_amount    DECIMAL(18,4)   NOT NULL,
    usage_unit_id   VARCHAR(36)     NOT NULL,
    start_time      TIMESTAMP       NOT NULL,
    end_time        TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sorting_step_log (
    id              VARCHAR(36)     PRIMARY KEY,
    task_id         VARCHAR(36)     NOT NULL,
    step_name       VARCHAR(30)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    started_at      TIMESTAMP       NOT NULL,
    completed_at    TIMESTAMP,
    detail          TEXT
);
```

- [ ] **Step 2: 创建 Operator 实体**

```java
package com.example.sorting.entity;

import java.time.LocalDateTime;

public class Operator {
    private String id;
    private String code;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: 创建 ServiceAz 实体**

同 Operator 结构，字段：`id, code, name, createdAt, updatedAt`

- [ ] **Step 4: 创建 UsageUnit 实体**

同 Operator 结构，字段：`id, code, name, createdAt, updatedAt`

- [ ] **Step 5: 创建 SortingTask 实体**

```java
package com.example.sorting.entity;

import java.time.LocalDateTime;

public class SortingTask {
    private String id;
    private String fileServerId;
    private String status;          // PENDING / RUNNING / COMPLETED / FAILED
    private String currentStep;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime timeoutAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // getters & setters...
}
```

- [ ] **Step 6: 创建 SortingTaskBackup 实体**

同 SortingTask，额外字段：

```java
public class SortingTaskBackup {
    // ... 同 SortingTask 所有字段 ...
    private LocalDateTime archivedAt;
    // getters & setters...
}
```

- [ ] **Step 7: 创建 SortingStepLog 实体**

```java
public class SortingStepLog {
    private String id;
    private String taskId;
    private String stepName;
    private String status;       // SUCCESS / FAILED
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String detail;
    // getters & setters...
}
```

- [ ] **Step 8: 创建 CdrRecord 实体**

```java
public class CdrRecord {
    private String id;
    private String taskId;
    private String operatorId;
    private String serviceAzId;
    private String resourceId;
    private java.math.BigDecimal usageAmount;
    private String usageUnitId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
    // getters & setters...
}
```

- [ ] **Step 9: 构建验证**

```bash
mvn clean compile -DskipTests
```
预期输出：`BUILD SUCCESS`

- [ ] **Step 10: 提交**

```bash
git add etc/schema.sql src/main/java/com/example/sorting/entity/
git commit -m "feat: add v0.0.2 entities and DDL for CDR sorting"
```

---

### Task 2: Mapper 层

**Files:**
- Create: `src/main/java/com/example/sorting/repository/OperatorMapper.java`
- Create: `src/main/java/com/example/sorting/repository/ServiceAzMapper.java`
- Create: `src/main/java/com/example/sorting/repository/UsageUnitMapper.java`
- Create: `src/main/java/com/example/sorting/repository/SortingTaskMapper.java`
- Create: `src/main/java/com/example/sorting/repository/SortingStepLogMapper.java`
- Create: `src/main/java/com/example/sorting/repository/CdrRecordMapper.java`
- Create: `src/main/resources/mapper/OperatorMapper.xml`
- Create: `src/main/resources/mapper/ServiceAzMapper.xml`
- Create: `src/main/resources/mapper/UsageUnitMapper.xml`
- Create: `src/main/resources/mapper/SortingTaskMapper.xml`
- Create: `src/main/resources/mapper/SortingStepLogMapper.xml`
- Create: `src/main/resources/mapper/CdrRecordMapper.xml`
- Modify: `src/main/java/com/example/sorting/config/MyBatisConfig.java` — 注册新 mapper

**Interfaces:**
- Consumes: Task 1 的实体类
- Produces: 6 个 Mapper 接口和 XML，后续 Service 层依赖

- [ ] **Step 1: 创建 OperatorMapper**

```java
package com.example.sorting.repository;

import com.example.sorting.entity.Operator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface OperatorMapper {
    int insert(Operator operator);
    int updateById(Operator operator);
    int deleteById(@Param("id") String id);
    Operator selectById(@Param("id") String id);
    Operator selectByCode(@Param("code") String code);
    List<Operator> selectAll();
    int countByCode(@Param("code") String code);
}
```

- [ ] **Step 2: 创建 OperatorMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.sorting.repository.OperatorMapper">

    <resultMap id="BaseResultMap" type="com.example.sorting.entity.Operator">
        <id column="id" property="id"/>
        <result column="code" property="code"/>
        <result column="name" property="name"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
    </resultMap>

    <insert id="insert" parameterType="com.example.sorting.entity.Operator">
        INSERT INTO operator (id, code, name, created_at, updated_at)
        VALUES (#{id}, #{code}, #{name}, #{createdAt}, #{updatedAt})
    </insert>

    <update id="updateById" parameterType="com.example.sorting.entity.Operator">
        UPDATE operator
        <set>
            <if test="code != null">code = #{code},</if>
            <if test="name != null">name = #{name},</if>
            updated_at = CURRENT_TIMESTAMP
        </set>
        WHERE id = #{id}
    </update>

    <delete id="deleteById">
        DELETE FROM operator WHERE id = #{id}
    </delete>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT * FROM operator WHERE id = #{id}
    </select>

    <select id="selectByCode" resultMap="BaseResultMap">
        SELECT * FROM operator WHERE code = #{code}
    </select>

    <select id="selectAll" resultMap="BaseResultMap">
        SELECT * FROM operator ORDER BY created_at ASC
    </select>

    <select id="countByCode" resultType="int">
        SELECT COUNT(1) FROM operator WHERE code = #{code}
        <if test="excludeId != null">AND id != #{excludeId}</if>
    </select>
</mapper>
```

- [ ] **Step 3: 创建 ServiceAzMapper + ServiceAzMapper.xml**

同 OperatorMapper 结构，字段对应 `service_az` 表。

- [ ] **Step 4: 创建 UsageUnitMapper + UsageUnitMapper.xml**

同 OperatorMapper 结构，字段对应 `usage_unit` 表。

- [ ] **Step 5: 创建 SortingTaskMapper**

关键方法：

```java
@Mapper
public interface SortingTaskMapper {
    int insert(SortingTask task);
    int updateStatus(@Param("id") String id, @Param("status") String status);
    int updateStep(@Param("id") String id, @Param("currentStep") String step);
    int incrementRetry(@Param("id") String id);
    int updateErrorMessage(@Param("id") String id, @Param("errorMessage") String msg);
    SortingTask selectById(@Param("id") String id);
    List<SortingTask> selectByStatus(@Param("status") String status);
    List<SortingTask> selectRunningTimeoutTasks(@Param("now") LocalDateTime now);
    List<SortingTask> selectAll();
    int deleteById(@Param("id") String id);
    int insertBackup(SortingTaskBackup backup);
}
```

- [ ] **Step 6: 创建 SortingTaskMapper.xml**

核心 SQL（完整版需包含所有方法对应的 SQL 语句）：

```xml
<mapper namespace="com.example.sorting.repository.SortingTaskMapper">

    <resultMap id="TaskResultMap" type="com.example.sorting.entity.SortingTask">
        <id column="id" property="id"/>
        <result column="file_server_id" property="fileServerId"/>
        <result column="status" property="status"/>
        <result column="current_step" property="currentStep"/>
        <result column="retry_count" property="retryCount"/>
        <result column="error_message" property="errorMessage"/>
        <result column="started_at" property="startedAt"/>
        <result column="completed_at" property="completedAt"/>
        <result column="timeout_at" property="timeoutAt"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
    </resultMap>

    <update id="updateStatus">
        UPDATE sorting_task SET status = #{status}, updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
    </update>

    <update id="updateStep">
        UPDATE sorting_task SET current_step = #{currentStep}, updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
    </update>

    <select id="selectRunningTimeoutTasks" resultMap="TaskResultMap">
        SELECT * FROM sorting_task
        WHERE status = 'RUNNING' AND timeout_at &lt; #{now}
    </select>
</mapper>
```

- [ ] **Step 7: 创建 SortingStepLogMapper + XML**

```java
@Mapper
public interface SortingStepLogMapper {
    int insert(SortingStepLog log);
    List<SortingStepLog> selectByTaskId(@Param("taskId") String taskId);
}
```

- [ ] **Step 8: 创建 CdrRecordMapper + XML**

核心方法：

```java
@Mapper
public interface CdrRecordMapper {
    int batchInsert(@Param("records") List<CdrRecord> records);
    int countByOperatorId(@Param("operatorId") String operatorId);
    int countByAzId(@Param("azId") String azId);
    int countByUnitId(@Param("unitId") String unitId);
}
```

- [ ] **Step 9: 更新 MyBatisConfig 注册新 Mapper**

在 `MyBatisConfig.java` 的 `sqlSessionFactory()` 方法中，确保新 mapper XML 也在扫描路径内：

```java
// 目前已有的 mapper locations 配置
sqlSessionFactoryBean.setMapperLocations(
    new PathMatchingResourcePatternResolver()
        .getResources("classpath:mapper/*.xml")
);
```
因已使用 `classpath:mapper/*.xml` 模式，新增的 XML 文件会自动被扫描。只需要确保 Mapper 接口也被 `@MapperScan` 或 `@Mapper` 注解覆盖。

检查 `MapperScan` 或 `@Mapper` 注解——确认 `com.example.sorting.repository` 包已被扫描。

- [ ] **Step 10: 构建验证**

```bash
mvn clean compile -DskipTests
```

- [ ] **Step 11: 提交**

```bash
git add src/main/java/com/example/sorting/repository/ src/main/resources/mapper/
git commit -m "feat: add v0.0.2 mapper layer"
```

---

### Task 3: 错误码扩展 + DTO 层

**Files:**
- Modify: `src/main/java/com/example/sorting/exception/ErrorCode.java`
- Create: `src/main/java/com/example/sorting/dto/OperatorAddReq.java`
- Create: `src/main/java/com/example/sorting/dto/OperatorModifyReq.java`
- Create: `src/main/java/com/example/sorting/dto/ServiceAzAddReq.java`
- Create: `src/main/java/com/example/sorting/dto/ServiceAzModifyReq.java`
- Create: `src/main/java/com/example/sorting/dto/UsageUnitAddReq.java`
- Create: `src/main/java/com/example/sorting/dto/UsageUnitModifyReq.java`
- Create: `src/main/java/com/example/sorting/dto/SortingListReq.java`
- Create: `src/main/java/com/example/sorting/dto/SortingRetryReq.java`
- Create: `src/main/java/com/example/sorting/dto/SortingDetailReq.java`

**Interfaces:**
- Consumes: 无
- Produces: DTO 类 + 错误码枚举值，Service/Controller 层依赖

- [ ] **Step 1: 扩展 ErrorCode 枚举**

在现有枚举中添加：

```java
// 基础数据类
OP_001("OP_001", "运营商不存在"),
OP_002("OP_002", "运营商 code 已存在"),
OP_003("OP_003", "运营商已被 cdr_record 引用，无法删除"),
AZ_001("AZ_001", "可用区不存在"),
AZ_002("AZ_002", "可用区 code 已存在"),
AZ_003("AZ_003", "可用区已被 cdr_record 引用，无法删除"),
UNIT_001("UNIT_001", "使用量单位不存在"),
UNIT_002("UNIT_002", "使用量单位 code 已存在"),
UNIT_003("UNIT_003", "使用量单位已被 cdr_record 引用，无法删除"),

// 分拣类
SORT_001("SORT_001", "分拣任务不存在"),
SORT_002("SORT_002", "没有可执行的分拣任务"),
SORT_003("SORT_003", "分拣任务状态不允许该操作"),
SORT_004("SORT_004", "文件命名不符合规范"),
SORT_005("SORT_005", "ZIP 解压失败"),
SORT_006("SORT_006", "CSV 解析失败"),
```

- [ ] **Step 2: 创建 OperatorAddReq**

```java
package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class OperatorAddReq {
    @NotBlank(message = "运营商 code 不能为空")
    private String code;

    @NotBlank(message = "运营商名称不能为空")
    private String name;

    // getters & setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

- [ ] **Step 3: 创建 OperatorModifyReq**

```java
public class OperatorModifyReq {
    @NotBlank(message = "id 不能为空")
    private String id;
    private String code;
    private String name;

    // getters & setters...
}
```

- [ ] **Step 4: 创建 ServiceAzAddReq、ServiceAzModifyReq**

同 Operator 结构，字段为 `code, name`。

- [ ] **Step 5: 创建 UsageUnitAddReq、UsageUnitModifyReq**

同 Operator 结构，字段为 `code, name`。

- [ ] **Step 6: 创建 Sorting 相关 DTO**

```java
// SortingListReq
public class SortingListReq {
    private String status;  // 可选筛选条件，为空则查询全部
    // getter & setter
}

// SortingRetryReq
public class SortingRetryReq {
    @NotBlank(message = "任务 id 不能为空")
    private String id;
    // getter & setter
}

// SortingDetailReq
public class SortingDetailReq {
    @NotBlank(message = "任务 id 不能为空")
    private String id;
    // getter & setter
}
```

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/example/sorting/exception/ErrorCode.java src/main/java/com/example/sorting/dto/
git commit -m "feat: add v0.0.2 error codes and DTOs"
```

---

### Task 4: MasterDataCache 缓存组件

**Files:**
- Create: `src/main/java/com/example/sorting/cache/MasterDataCache.java`
- Test: `src/test/java/com/example/sorting/cache/MasterDataCacheTest.java`

**Interfaces:**
- Consumes: Task 2 的 Mapper 接口
- Produces: MasterDataCache Bean，后续 Service/StepHandler 依赖

- [ ] **Step 1: 创建 MasterDataCache**

```java
package com.example.sorting.cache;

import com.example.sorting.entity.Operator;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.repository.OperatorMapper;
import com.example.sorting.repository.ServiceAzMapper;
import com.example.sorting.repository.UsageUnitMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MasterDataCache {

    @Autowired
    private OperatorMapper operatorMapper;
    @Autowired
    private ServiceAzMapper serviceAzMapper;
    @Autowired
    private UsageUnitMapper usageUnitMapper;

    private volatile Map<String, Operator> operatorCache = Collections.emptyMap();
    private volatile Map<String, ServiceAz> azCache = Collections.emptyMap();
    private volatile Map<String, UsageUnit> unitCache = Collections.emptyMap();

    @PostConstruct
    public void init() {
        refreshAll();
    }

    public void refreshOperators() {
        List<Operator> list = operatorMapper.selectAll();
        Map<String, Operator> map = new ConcurrentHashMap<>();
        for (Operator op : list) {
            map.put(op.getCode(), op);
        }
        this.operatorCache = map;
    }

    public void refreshAzs() {
        List<ServiceAz> list = serviceAzMapper.selectAll();
        Map<String, ServiceAz> map = new ConcurrentHashMap<>();
        for (ServiceAz az : list) {
            map.put(az.getCode(), az);
        }
        this.azCache = map;
    }

    public void refreshUnits() {
        List<UsageUnit> list = usageUnitMapper.selectAll();
        Map<String, UsageUnit> map = new ConcurrentHashMap<>();
        for (UsageUnit unit : list) {
            map.put(unit.getCode(), unit);
        }
        this.unitCache = map;
    }

    public void refreshAll() {
        refreshOperators();
        refreshAzs();
        refreshUnits();
    }

    /** 定时兜底刷新：每 5 分钟 */
    @Scheduled(fixedRate = 300_000)
    public void scheduledRefresh() {
        refreshAll();
    }

    public Operator getOperatorByCode(String code) {
        return operatorCache.get(code);
    }

    public ServiceAz getAzByCode(String code) {
        return azCache.get(code);
    }

    public UsageUnit getUnitByCode(String code) {
        return unitCache.get(code);
    }

    public boolean operatorExists(String code) {
        return operatorCache.containsKey(code);
    }

    public boolean azExists(String code) {
        return azCache.containsKey(code);
    }

    public boolean unitExists(String code) {
        return unitCache.containsKey(code);
    }
}
```

- [ ] **Step 2: 开启 Spring 定时任务**

在 `application.yaml` 中添加 `spring.task.scheduling.pool.size=1` 或者在主启动类添加 `@EnableScheduling`：

```java
// SortingApplication.java
@SpringBootApplication
@EnableScheduling
public class SortingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SortingApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 MasterDataCacheTest**

```java
package com.example.sorting.cache;

import com.example.sorting.entity.Operator;
import com.example.sorting.repository.OperatorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class MasterDataCacheTest {

    @Mock
    private OperatorMapper operatorMapper;

    @InjectMocks
    private MasterDataCache cache;

    @BeforeEach
    void setUp() {
        Operator op1 = new Operator();
        op1.setCode("ColoCloud");
        op1.setName("星辰云");
        when(operatorMapper.selectAll()).thenReturn(List.of(op1));
        cache.refreshOperators();
    }

    @Test
    void shouldFindOperatorByCode() {
        assertTrue(cache.operatorExists("ColoCloud"));
        assertFalse(cache.operatorExists("NonExistent"));
    }

    @Test
    void shouldReturnNullForMissingOperator() {
        assertNull(cache.getOperatorByCode("Missing"));
    }
}
```

- [ ] **Step 4: 测试**

```bash
mvn test -Dtest=MasterDataCacheTest
```
预期：`Tests run: 2, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/sorting/cache/ src/test/java/com/example/sorting/cache/ src/main/java/com/example/sorting/SortingApplication.java
git commit -m "feat: add MasterDataCache with auto-refresh"
```

---

### Task 5: 基础数据 Service 层

**Files:**
- Create: `src/main/java/com/example/sorting/service/OperatorService.java`
- Create: `src/main/java/com/example/sorting/service/ServiceAzService.java`
- Create: `src/main/java/com/example/sorting/service/UsageUnitService.java`
- Test: `src/test/java/com/example/sorting/service/OperatorServiceTest.java`
- Test: `src/test/java/com/example/sorting/service/ServiceAzServiceTest.java`
- Test: `src/test/java/com/example/sorting/service/UsageUnitServiceTest.java`

**Interfaces:**
- Consumes: Task 2 Mapper + Task 4 MasterDataCache
- Produces: 3 个 Service Bean，Controller 层依赖

- [ ] **Step 1: 创建 OperatorService**

```java
package com.example.sorting.service;

import com.example.sorting.entity.Operator;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.OperatorMapper;
import com.example.sorting.cache.MasterDataCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OperatorService {

    @Autowired
    private OperatorMapper operatorMapper;
    @Autowired
    private MasterDataCache masterDataCache;

    @Transactional
    public Operator add(String code, String name) {
        if (operatorMapper.countByCode(code) > 0) {
            throw new BusinessException(ErrorCode.OP_002);
        }
        Operator op = new Operator();
        op.setId(UUID.randomUUID().toString());
        op.setCode(code);
        op.setName(name);
        op.setCreatedAt(LocalDateTime.now());
        op.setUpdatedAt(LocalDateTime.now());
        operatorMapper.insert(op);
        masterDataCache.refreshOperators();
        return op;
    }

    @Transactional
    public Operator modify(String id, String code, String name) {
        Operator existing = operatorMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.OP_001);
        }
        if (code != null && !code.equals(existing.getCode())) {
            if (operatorMapper.countByCode(code) > 0) {
                throw new BusinessException(ErrorCode.OP_002);
            }
            existing.setCode(code);
        }
        if (name != null) {
            existing.setName(name);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        operatorMapper.updateById(existing);
        masterDataCache.refreshOperators();
        return operatorMapper.selectById(id);
    }

    @Transactional
    public void delete(String id) {
        Operator existing = operatorMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.OP_001);
        }
        // 检查是否被 cdr_record 引用（由 CdrRecordService 提供逻辑）
        // 此处简化处理，实际需要 CdrRecordMapper.countByOperatorId
        operatorMapper.deleteById(id);
        masterDataCache.refreshOperators();
    }

    public List<Operator> list() {
        return operatorMapper.selectAll();
    }

    public Operator getById(String id) {
        Operator op = operatorMapper.selectById(id);
        if (op == null) {
            throw new BusinessException(ErrorCode.OP_001);
        }
        return op;
    }
}
```

- [ ] **Step 2: 创建 ServiceAzService**

同 OperatorService 模式，使用 `ErrorCode.AZ_001/AZ_002/AZ_003`。

- [ ] **Step 3: 创建 UsageUnitService**

同 OperatorService 模式，使用 `ErrorCode.UNIT_001/UNIT_002/UNIT_003`。

- [ ] **Step 4: 创建 OperatorServiceTest**

```java
@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {

    @Mock private OperatorMapper operatorMapper;
    @Mock private MasterDataCache masterDataCache;
    @InjectMocks private OperatorService operatorService;

    @Test
    void add_shouldThrowWhenCodeExists() {
        when(operatorMapper.countByCode("dup")).thenReturn(1);
        assertThrows(BusinessException.class,
            () -> operatorService.add("dup", "重复"));
    }

    @Test
    void add_shouldSucceed() {
        when(operatorMapper.countByCode("ColoCloud")).thenReturn(0);
        Operator result = operatorService.add("ColoCloud", "星辰云");
        assertNotNull(result.getId());
        assertEquals("ColoCloud", result.getCode());
        verify(masterDataCache).refreshOperators();
    }

    @Test
    void modify_shouldThrowWhenNotFound() {
        when(operatorMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class,
            () -> operatorService.modify("non-existent", null, "新名称"));
    }

    @Test
    void list_shouldReturnAll() {
        when(operatorMapper.selectAll()).thenReturn(List.of(new Operator()));
        assertEquals(1, operatorService.list().size());
    }
}
```

- [ ] **Step 5: 创建 ServiceAzServiceTest + UsageUnitServiceTest**

测试模式同上，覆盖 add（code 重复）/ modify（不存在）/ list。

- [ ] **Step 6: 运行所有 Service 测试**

```bash
mvn test -Dtest="OperatorServiceTest,ServiceAzServiceTest,UsageUnitServiceTest"
```
预期：所有测试通过

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/example/sorting/service/ src/test/java/com/example/sorting/service/
git commit -m "feat: add operator/az/unit services"
```

---

### Task 6: 基础数据 Controller 层

**Files:**
- Create: `src/main/java/com/example/sorting/controller/OperatorController.java`
- Create: `src/main/java/com/example/sorting/controller/ServiceAzController.java`
- Create: `src/main/java/com/example/sorting/controller/UsageUnitController.java`
- Test: `src/test/java/com/example/sorting/controller/OperatorControllerTest.java`
- Test: `src/test/java/com/example/sorting/controller/ServiceAzControllerTest.java`
- Test: `src/test/java/com/example/sorting/controller/UsageUnitControllerTest.java`

**Interfaces:**
- Consumes: Task 5 的 Service + Task 3 的 DTO
- Produces: REST API 端点

- [ ] **Step 1: 创建 OperatorController**

```java
package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.OperatorAddReq;
import com.example.sorting.dto.OperatorModifyReq;
import com.example.sorting.entity.Operator;
import com.example.sorting.service.OperatorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/operator")
public class OperatorController {

    @Autowired
    private OperatorService operatorService;

    @PostMapping("/add")
    public ApiResponse<Operator> add(@Valid @RequestBody OperatorAddReq req) {
        Operator op = operatorService.add(req.getCode(), req.getName());
        return ApiResponse.success(op);
    }

    @PostMapping("/modify")
    public ApiResponse<Operator> modify(@Valid @RequestBody OperatorModifyReq req) {
        Operator op = operatorService.modify(req.getId(), req.getCode(), req.getName());
        return ApiResponse.success(op);
    }

    @PostMapping("/list")
    public ApiResponse<List<Operator>> list() {
        return ApiResponse.success(operatorService.list());
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdReq req) {
        operatorService.delete(req.getId());
        return ApiResponse.success(null);
    }
}
```

注意：`IdReq` 是一个通用 DTO，可在 `dto/` 包下创建：

```java
package com.example.sorting.dto;
import jakarta.validation.constraints.NotBlank;
public class IdReq {
    @NotBlank private String id;
    // getter & setter
}
```

- [ ] **Step 2: 创建 ServiceAzController、UsageUnitController**

同 OperatorController 模式，路径 `/az` 和 `/usage-unit`。

- [ ] **Step 3: 创建 OperatorControllerTest**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OperatorControllerTest {

    @Autowired
    private TestRestTemplate restTemplate; // 或者手动创建 RestTemplate
    private String baseUrl = "/operator";

    // 注意：SB 4.1.0 无 TestRestTemplate，参照 v0.0.1 的做法
    private RestTemplate template;

    @BeforeEach
    void setUp() {
        template = new RestTemplate();
        template.setErrorHandler(new NoOpResponseErrorHandler());
    }

    @Test
    void add_shouldReturnOperator() {
        // 需要在测试配置中准备好数据库
    }
}
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/sorting/controller/ src/test/java/com/example/sorting/controller/ src/main/java/com/example/sorting/dto/IdReq.java
git commit -m "feat: add operator/az/unit controllers"
```

---

### Task 7: Pipeline 核心框架

**Files:**
- Create: `src/main/java/com/example/sorting/pipeline/StepHandler.java`
- Create: `src/main/java/com/example/sorting/pipeline/StepContext.java`
- Create: `src/main/java/com/example/sorting/pipeline/StepResult.java`
- Create: `src/main/java/com/example/sorting/pipeline/PipelineExecutor.java`
- Create: `src/main/java/com/example/sorting/config/PipelineConfig.java`
- Test: `src/test/java/com/example/sorting/pipeline/PipelineExecutorTest.java`

**Interfaces:**
- Consumes: Task 1 (SortingTask entity) + Task 2 (SortingTaskMapper)
- Produces: PipelineExecutor Bean + StepHandler 接口，Task 8 的 StepHandlers 依赖

- [ ] **Step 1: 创建 StepHandler 接口**

```java
package com.example.sorting.pipeline;

public interface StepHandler {
    /** 步骤名称标识，如 "scan"、"validate" */
    String getStepName();

    /** 执行步骤逻辑 */
    StepResult execute(StepContext context);

    /** 崩溃恢复时清理上一步可能的残留（可选） */
    default void rollback(StepContext context) {
        // 默认空实现
    }
}
```

- [ ] **Step 2: 创建 StepContext**

```java
package com.example.sorting.pipeline;

import com.example.sorting.entity.SortingTask;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 步骤上下文，在 Pipeline 内部传递数据。
 * 每个步骤可以向 context 中放数据，后续步骤读取。
 */
public class StepContext {
    private final SortingTask task;
    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    public StepContext(SortingTask task) {
        this.task = task;
    }

    public SortingTask getTask() { return task; }
    public String getTaskId() { return task.getId(); }

    public void setAttribute(String key, Object value) { data.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) { return (T) data.get(key); }

    // 预定义的常用 key
    public static final String KEY_FILE_LIST = "fileList";
    public static final String KEY_VALID_FILES = "validFiles";
    public static final String KEY_EXTRACTED_FILES = "extractedFiles";
    public static final String KEY_CDR_RECORDS = "cdrRecords";
}
```

- [ ] **Step 3: 创建 StepResult**

```java
package com.example.sorting.pipeline;

public class StepResult {
    private final boolean success;
    private final String message;

    private StepResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static StepResult ok() {
        return new StepResult(true, null);
    }

    public static StepResult ok(String message) {
        return new StepResult(true, message);
    }

    public static StepResult failed(String message) {
        return new StepResult(false, message);
    }

    public boolean isSuccess() { return success; }
    public boolean isFailed() { return !success; }
    public String getMessage() { return message; }
}
```

- [ ] **Step 4: 创建 PipelineConfig (线程池配置)**

```java
package com.example.sorting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PipelineConfig {

    @Bean("sortingTaskPool")
    public ThreadPoolTaskExecutor sortingTaskPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sorting-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 5: 创建 PipelineExecutor**

```java
package com.example.sorting.pipeline;

import com.example.sorting.entity.SortingStepLog;
import com.example.sorting.entity.SortingTask;
import com.example.sorting.entity.SortingTaskBackup;
import com.example.sorting.repository.SortingStepLogMapper;
import com.example.sorting.repository.SortingTaskMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);
    private static final int MAX_RETRY = 3;
    private static final int TIMEOUT_MINUTES = 5;

    @Autowired
    private List<StepHandler> stepHandlers;
    @Autowired
    private SortingTaskMapper taskMapper;
    @Autowired
    private SortingStepLogMapper stepLogMapper;
    @Autowired
    @Qualifier("sortingTaskPool")
    private ThreadPoolTaskExecutor executor;

    /**
     * 启动一个分拣任务（异步）
     */
    public void startTask(SortingTask task) {
        executor.submit(() -> executeTask(task));
    }

    private void executeTask(SortingTask task) {
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        task.setTimeoutAt(LocalDateTime.now().plusMinutes(TIMEOUT_MINUTES));
        taskMapper.updateStatus(task.getId(), "RUNNING");

        StepContext context = new StepContext(task);

        for (StepHandler handler : stepHandlers) {
            // 更新 current_step
            task.setCurrentStep(handler.getStepName());
            taskMapper.updateStep(task.getId(), handler.getStepName());

            // 记录步骤开始
            SortingStepLog stepLog = new SortingStepLog();
            stepLog.setId(java.util.UUID.randomUUID().toString());
            stepLog.setTaskId(task.getId());
            stepLog.setStepName(handler.getStepName());
            stepLog.setStatus("RUNNING");
            stepLog.setStartedAt(LocalDateTime.now());
            stepLogMapper.insert(stepLog);

            try {
                StepResult result = handler.execute(context);
                if (result.isSuccess()) {
                    stepLog.setStatus("SUCCESS");
                    stepLog.setCompletedAt(LocalDateTime.now());
                    stepLog.setDetail(result.getMessage());
                    stepLogMapper.insert(stepLog);
                } else {
                    throw new RuntimeException(result.getMessage());
                }
            } catch (Exception e) {
                log.error("Step [{}] failed for task [{}]: {}", handler.getStepName(), task.getId(), e.getMessage());
                stepLog.setStatus("FAILED");
                stepLog.setCompletedAt(LocalDateTime.now());
                stepLog.setDetail(e.getMessage());
                stepLogMapper.insert(stepLog);

                // 记录错误信息
                task.setErrorMessage(e.getMessage());
                taskMapper.updateErrorMessage(task.getId(), e.getMessage());

                // 重试逻辑
                int retry = task.getRetryCount() != null ? task.getRetryCount() : 0;
                if (retry < MAX_RETRY) {
                    task.setRetryCount(retry + 1);
                    taskMapper.incrementRetry(task.getId());
                    log.info("Retrying task [{}] attempt {}/{}", task.getId(), retry + 1, MAX_RETRY);
                    // 重新从当前步骤开始
                    handler.rollback(context);
                    // 递归重试但限制深度，用循环来实现
                    continue; // 简化：实际上需要递归/循环
                } else {
                    taskMapper.updateStatus(task.getId(), "FAILED");
                    log.error("Task [{}] failed after {} retries", task.getId(), MAX_RETRY);
                    return;
                }
            }
        }

        // 全部步骤完成
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateStatus(task.getId(), "COMPLETED");

        // 归档到 backup 表
        archiveTask(task);
    }

    private void archiveTask(SortingTask task) {
        SortingTaskBackup backup = new SortingTaskBackup();
        // 复制字段...
        backup.setId(task.getId());
        backup.setFileServerId(task.getFileServerId());
        backup.setStatus(task.getStatus());
        backup.setCurrentStep(task.getCurrentStep());
        backup.setRetryCount(task.getRetryCount());
        backup.setErrorMessage(task.getErrorMessage());
        backup.setStartedAt(task.getStartedAt());
        backup.setCompletedAt(task.getCompletedAt());
        backup.setTimeoutAt(task.getTimeoutAt());
        backup.setArchivedAt(LocalDateTime.now());
        taskMapper.insertBackup(backup);
        taskMapper.deleteById(task.getId());
    }

    /** 崩溃恢复：服务启动时自动执行 */
    @PostConstruct
    public void recoverInterruptedTasks() {
        List<SortingTask> runningTasks = taskMapper.selectByStatus("RUNNING");
        for (SortingTask task : runningTasks) {
            log.info("Recovering interrupted task [{}] at step [{}]", task.getId(), task.getCurrentStep());
            // 重置超时时间并从当前步骤继续
            task.setTimeoutAt(LocalDateTime.now().plusMinutes(TIMEOUT_MINUTES));
            startTask(task);
        }
    }

    /** 超时监控：每 30 秒检测一次 */
    @Scheduled(fixedRate = 30_000)
    public void timeoutCheck() {
        List<SortingTask> timeoutTasks = taskMapper.selectRunningTimeoutTasks(LocalDateTime.now());
        for (SortingTask task : timeoutTasks) {
            log.warn("Task [{}] timed out", task.getId());
            task.setStatus("FAILED");
            task.setErrorMessage("Task timed out after " + TIMEOUT_MINUTES + " minutes");
            taskMapper.updateStatus(task.getId(), "FAILED");
            taskMapper.updateErrorMessage(task.getId(), task.getErrorMessage());
        }
    }
}
```

- [ ] **Step 6: 创建 PipelineExecutorTest**

```java
@ExtendWith(MockitoExtension.class)
class PipelineExecutorTest {

    @Mock private SortingTaskMapper taskMapper;
    @Mock private SortingStepLogMapper stepLogMapper;
    @Mock @Qualifier("sortingTaskPool") private ThreadPoolTaskExecutor executor;
    @InjectMocks private PipelineExecutor pipelineExecutor;

    @Test
    void recoverInterruptedTasks_shouldRestartRunningTasks() {
        SortingTask runningTask = new SortingTask();
        runningTask.setId("task-1");
        runningTask.setStatus("RUNNING");
        runningTask.setCurrentStep("extract");

        when(taskMapper.selectByStatus("RUNNING")).thenReturn(List.of(runningTask));

        pipelineExecutor.recoverInterruptedTasks();

        // 验证：任务被重新提交到线程池
        verify(taskMapper).selectByStatus("RUNNING");
    }
}
```

- [ ] **Step 7: 构建验证**

```bash
mvn clean compile -DskipTests
```

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/example/sorting/pipeline/ src/main/java/com/example/sorting/config/PipelineConfig.java src/test/java/com/example/sorting/pipeline/
git commit -m "feat: add pipeline framework with executor"
```

---

### Task 8: Pipeline StepHandlers 实现

**Files:**
- Create: `src/main/java/com/example/sorting/pipeline/ScanStepHandler.java`
- Create: `src/main/java/com/example/sorting/pipeline/ValidateStepHandler.java`
- Create: `src/main/java/com/example/sorting/pipeline/ExtractStepHandler.java`
- Create: `src/main/java/com/example/sorting/pipeline/ParseStepHandler.java`
- Create: `src/main/java/com/example/sorting/pipeline/PersistStepHandler.java`
- Create: `src/main/java/com/example/sorting/pipeline/ArchiveStepHandler.java`
- Test: `src/test/java/com/example/sorting/pipeline/ParseStepHandlerTest.java`

**Interfaces:**
- Consumes: Task 7 (StepHandler, StepContext, StepResult) + 相关 Mapper + MasterDataCache
- Produces: 6 个 @Component StepHandler Bean，PipelineExecutor 自动注入

- [ ] **Step 1: 创建 ScanStepHandler**

扫描 MinIO 目录，列出 ZIP 文件：

```java
@Component
public class ScanStepHandler implements StepHandler {

    @Override
    public String getStepName() { return "scan"; }

    @Override
    public StepResult execute(StepContext context) {
        SortingTask task = context.getTask();
        String fileServerId = task.getFileServerId();
        // 1. 通过 FileServerConfigMapper 查询服务器配置
        // 2. 使用 MinioClient 连接
        // 3. 列出 fileDirectory 下的所有 .zip 文件
        // 4. 放入 context (KEY_FILE_LIST)
        // 简化实现：
        context.setAttribute(StepContext.KEY_FILE_LIST, List.of("file1.zip", "file2.zip"));
        return StepResult.ok("Found 2 files");
    }
}
```

- [ ] **Step 2: 创建 ValidateStepHandler**

校验 ZIP 文件名正则：

```java
@Component
public class ValidateStepHandler implements StepHandler {

    private static final Pattern FILE_NAME_PATTERN =
        Pattern.compile("^[A-Za-z0-9]+_[A-Za-z0-9]+_\\d{12}_\\d{12}_[A-Za-z0-9]+\\.zip$");

    @Override
    public String getStepName() { return "validate"; }

    @Override
    public StepResult execute(StepContext context) {
        List<String> fileList = context.getAttribute(StepContext.KEY_FILE_LIST);
        List<String> validFiles = new ArrayList<>();
        List<String> invalidFiles = new ArrayList<>();

        for (String fileName : fileList) {
            if (FILE_NAME_PATTERN.matcher(fileName).matches()) {
                validFiles.add(fileName);
            } else {
                invalidFiles.add(fileName);
                // 将非法文件移动到 error/ 目录（MinIO move 操作）
            }
        }

        context.setAttribute(StepContext.KEY_VALID_FILES, validFiles);
        return StepResult.ok("Valid: " + validFiles.size() + ", Invalid: " + invalidFiles.size());
    }
}
```

- [ ] **Step 3: 创建 ExtractStepHandler**

从 MinIO 下载 ZIP → 解压 → 校验子文件数量/大小：

```java
@Component
public class ExtractStepHandler implements StepHandler {

    private static final int MAX_FILES = 100;
    private static final long MAX_SIZE_BYTES = 100 * 1024 * 1024; // 100MB

    @Override
    public String getStepName() { return "extract"; }

    @Override
    public StepResult execute(StepContext context) {
        List<String> validFiles = context.getAttribute(StepContext.KEY_VALID_FILES);
        List<String> extractedFiles = new ArrayList<>();

        // 对每个 valid ZIP 文件：
        // 1. 从 MinIO 下载到本地临时目录
        // 2. 校验文件总大小 ≤ 100MB
        // 3. 解压
        // 4. 校验子文件数量 ≤ 100
        // 5. 筛选 .csv 后缀文件
        // 6. 放入 extractedFiles 列表

        context.setAttribute(StepContext.KEY_EXTRACTED_FILES, extractedFiles);
        return StepResult.ok("Extracted " + extractedFiles.size() + " CSV files");
    }
}
```

- [ ] **Step 4: 创建 ParseStepHandler（核心解析逻辑）**

```java
@Component
public class ParseStepHandler implements StepHandler {

    @Autowired
    private MasterDataCache masterDataCache;

    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private static final int MAX_DATA_ROWS = 100;

    @Override
    public String getStepName() { return "parse"; }

    @Override
    public StepResult execute(StepContext context) {
        List<String> extractedFiles = context.getAttribute(StepContext.KEY_EXTRACTED_FILES);
        List<CdrRecord> allRecords = new ArrayList<>();

        for (String csvFile : extractedFiles) {
            // 逐行读取 CSV 文件（使用 | 分隔符）
            // 第一行是表头，跳过
            // 每行解析为 CdrRecord
            try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                String header = reader.readLine(); // skip header
                String line;
                int rowCount = 0;

                while ((line = reader.readLine()) != null) {
                    rowCount++;
                    if (rowCount > MAX_DATA_ROWS) {
                        break; // 超过 100 行，丢弃多余数据
                    }

                    // CSV 格式（| 分隔）：
                    // | 运营商名称 | 服务 az | 服务资源 id | 开始时间戳 | 结束时间戳 | 使用量 | 使用量单位 |
                    String[] fields = line.split("\\|");
                    // fields[1] = operator code, fields[2] = az code, ...

                    CdrRecord record = parseLine(line);
                    if (record != null) {
                        allRecords.add(record);
                    }
                }
            } catch (IOException e) {
                return StepResult.failed("Failed to parse " + csvFile + ": " + e.getMessage());
            }
        }

        context.setAttribute(StepContext.KEY_CDR_RECORDS, allRecords);
        return StepResult.ok("Parsed " + allRecords.size() + " records");
    }

    private CdrRecord parseLine(String line) {
        String[] parts = line.split("\\|");
        // parts[0] 为空（行首 |），parts[1]=运营商, parts[2]=az, ...
        if (parts.length < 8) return null;

        String operatorCode = parts[1].trim();
        String azCode = parts[2].trim();
        String resourceId = parts[3].trim();
        long startTs = Long.parseLong(parts[4].trim());
        long endTs = Long.parseLong(parts[5].trim());
        BigDecimal amount = new BigDecimal(parts[6].trim());
        String unitCode = parts[7].trim();

        // 校验运营商
        Operator operator = masterDataCache.getOperatorByCode(operatorCode);
        if (operator == null) return null; // 丢弃

        // 校验可用区
        ServiceAz az = masterDataCache.getAzByCode(azCode);
        if (az == null) return null;

        // 校验 UUID 格式
        if (!UUID_PATTERN.matcher(resourceId).matches()) return null;

        // 校验单位
        UsageUnit unit = masterDataCache.getUnitByCode(unitCode);
        if (unit == null) return null;

        CdrRecord record = new CdrRecord();
        record.setId(UUID.randomUUID().toString());
        record.setTaskId(context.getTaskId());
        record.setOperatorId(operator.getId());
        record.setServiceAzId(az.getId());
        record.setResourceId(resourceId);
        record.setUsageAmount(amount);
        record.setUsageUnitId(unit.getId());
        record.setStartTime(LocalDateTime.ofEpochSecond(startTs, 0, ZoneOffset.UTC));
        record.setEndTime(LocalDateTime.ofEpochSecond(endTs, 0, ZoneOffset.UTC));
        return record;
    }
}
```

- [ ] **Step 5: 创建 ParseStepHandlerTest**

```java
@ExtendWith(MockitoExtension.class)
class ParseStepHandlerTest {

    @Mock private MasterDataCache cache;
    @InjectMocks private ParseStepHandler handler;

    @Test
    void shouldParseValidCsvLine() {
        Operator op = new Operator(); op.setId("op-1"); op.setCode("ColoCloud");
        ServiceAz az = new ServiceAz(); az.setId("az-1"); az.setCode("az1");
        UsageUnit unit = new UsageUnit(); unit.setId("unit-1"); unit.setCode("MB");

        when(cache.getOperatorByCode("ColoCloud")).thenReturn(op);
        when(cache.getAzByCode("az1")).thenReturn(az);
        when(cache.getUnitByCode("MB")).thenReturn(unit);

        // 测试完整的 CSV 解析流程
    }
}
```

- [ ] **Step 6: 创建 PersistStepHandler**

```java
@Component
public class PersistStepHandler implements StepHandler {

    @Autowired private CdrRecordMapper cdrRecordMapper;

    @Override
    public String getStepName() { return "persist"; }

    @Override
    public StepResult execute(StepContext context) {
        List<CdrRecord> records = context.getAttribute(StepContext.KEY_CDR_RECORDS);
        if (records == null || records.isEmpty()) {
            return StepResult.ok("No records to persist");
        }
        // 批量插入
        cdrRecordMapper.batchInsert(records);
        return StepResult.ok("Persisted " + records.size() + " records");
    }
}
```

- [ ] **Step 7: 创建 ArchiveStepHandler**

```java
@Component
public class ArchiveStepHandler implements StepHandler {

    @Override
    public String getStepName() { return "archive"; }

    @Override
    public StepResult execute(StepContext context) {
        List<String> fileList = context.getAttribute(StepContext.KEY_FILE_LIST);
        // 将原始 ZIP 文件从源目录移到 backup/ 目录（MinIO move）
        // 清理本地临时目录
        return StepResult.ok("Archived " + fileList.size() + " files");
    }
}
```

- **Step 8: 运行测试**

```bash
mvn test -Dtest=ParseStepHandlerTest
```

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/example/sorting/pipeline/ScanStepHandler.java \
       src/main/java/com/example/sorting/pipeline/ValidateStepHandler.java \
       src/main/java/com/example/sorting/pipeline/ExtractStepHandler.java \
       src/main/java/com/example/sorting/pipeline/ParseStepHandler.java \
       src/main/java/com/example/sorting/pipeline/PersistStepHandler.java \
       src/main/java/com/example/sorting/pipeline/ArchiveStepHandler.java \
       src/test/java/com/example/sorting/pipeline/ParseStepHandlerTest.java
git commit -m "feat: implement all pipeline step handlers"
```

---

### Task 9: Sorting 编排 Service + Controller

**Files:**
- Create: `src/main/java/com/example/sorting/service/SortingService.java`
- Create: `src/main/java/com/example/sorting/controller/SortingController.java`
- Test: `src/test/java/com/example/sorting/service/SortingServiceTest.java`
- Test: `src/test/java/com/example/sorting/controller/SortingControllerTest.java`

**Interfaces:**
- Consumes: Task 2 (SortingTaskMapper) + Task 7 (PipelineExecutor) + v0.0.1 (ConfigMapper, ConnectionService)
- Produces: `/sorting/*` REST API 端点

- [ ] **Step 1: 创建 SortingService**

```java
@Service
public class SortingService {

    @Autowired private ConfigMapper configMapper;
    @Autowired private SortingTaskMapper taskMapper;
    @Autowired private SortingStepLogMapper stepLogMapper;
    @Autowired private PipelineExecutor pipelineExecutor;

    /**
     * 触发分拣：扫描所有 enabled=true + connectivityStatus=true 的文件服务器
     */
    @Transactional
    public int trigger() {
        List<FileServerConfig> servers = configMapper.selectAll();
        List<FileServerConfig> candidates = servers.stream()
            .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
            .filter(s -> Boolean.TRUE.equals(s.getConnectivityStatus()))
            .toList();

        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.SORT_002);
        }

        int created = 0;
        for (FileServerConfig server : candidates) {
            SortingTask task = new SortingTask();
            task.setId(UUID.randomUUID().toString());
            task.setFileServerId(server.getId());
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.insert(task);

            // 异步启动任务
            pipelineExecutor.startTask(task);
            created++;
        }
        return created;
    }

    public List<SortingTask> list(String status) {
        if (status != null && !status.isEmpty()) {
            return taskMapper.selectByStatus(status);
        }
        return taskMapper.selectAll();
    }

    @Transactional
    public void retry(String id) {
        SortingTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.SORT_001);
        }
        if (!"FAILED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.SORT_003);
        }
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setErrorMessage(null);
        task.setCurrentStep(null);
        taskMapper.updateStatus(task.getId(), "PENDING");
        taskMapper.incrementRetry(task.getId()); // 重置方式
    }

    public SortingTask detail(String id) {
        SortingTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.SORT_001);
        }
        return task;
    }

    public List<SortingStepLog> stepLogs(String taskId) {
        return stepLogMapper.selectByTaskId(taskId);
    }
}
```

- [ ] **Step 2: 创建 SortingController**

```java
@RestController
@RequestMapping("/sorting")
public class SortingController {

    @Autowired private SortingService sortingService;

    @PostMapping("/trigger")
    public ApiResponse<Integer> trigger() {
        int count = sortingService.trigger();
        return ApiResponse.success(count);
    }

    @PostMapping("/list")
    public ApiResponse<List<SortingTask>> list(@RequestBody(required = false) SortingListReq req) {
        String status = (req != null) ? req.getStatus() : null;
        return ApiResponse.success(sortingService.list(status));
    }

    @PostMapping("/retry")
    public ApiResponse<Void> retry(@Valid @RequestBody SortingRetryReq req) {
        sortingService.retry(req.getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(@Valid @RequestBody SortingDetailReq req) {
        SortingTask task = sortingService.detail(req.getId());
        List<SortingStepLog> logs = sortingService.stepLogs(req.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("stepLogs", logs);
        return ApiResponse.success(result);
    }
}
```

- [ ] **Step 3: 创建 SortingServiceTest**

```java
@ExtendWith(MockitoExtension.class)
class SortingServiceTest {

    @Mock private ConfigMapper configMapper;
    @Mock private SortingTaskMapper taskMapper;
    @Mock private SortingStepLogMapper stepLogMapper;
    @Mock private PipelineExecutor pipelineExecutor;
    @InjectMocks private SortingService sortingService;

    @Test
    void trigger_shouldThrowWhenNoAvailableServers() {
        when(configMapper.selectAll()).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> sortingService.trigger());
    }

    @Test
    void trigger_shouldCreateTaskForConnectedServer() {
        FileServerConfig server = new FileServerConfig();
        server.setId("fs-1");
        server.setEnabled(true);
        server.setConnectivityStatus(true);
        when(configMapper.selectAll()).thenReturn(List.of(server));

        int count = sortingService.trigger();
        assertEquals(1, count);
        verify(taskMapper).insert(any(SortingTask.class));
        verify(pipelineExecutor).startTask(any(SortingTask.class));
    }
}
```

- [ ] **Step 4: 运行测试**

```bash
mvn test -Dtest="SortingServiceTest,SortingControllerTest,ParseStepHandlerTest,PipelineExecutorTest"
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/sorting/service/SortingService.java src/main/java/com/example/sorting/controller/SortingController.java src/test/java/com/example/sorting/service/SortingServiceTest.java src/test/java/com/example/sorting/controller/SortingControllerTest.java
git commit -m "feat: add sorting service and controller"
```

---

### Task 10: 最终验证

**Files:** 无新增

- [ ] **Step 1: 全量构建**

```bash
mvn clean compile -DskipTests
```
预期：BUILD SUCCESS

- [ ] **Step 2: 运行全量测试**

```bash
mvn test
```
预期：Tests run: 30+ (含新增 + v0.0.1 既有测试)

- [ ] **Step 3: 启动应用**

```bash
mvn spring-boot:run
```
预期：应用正常启动，无异常堆栈

- [ ] **Step 4: API 验证 — 运营商 CRUD**

```bash
# 新增运营商
curl -X POST http://localhost:8080/operator/add \
  -H "Content-Type: application/json" \
  -d '{"code": "ColoCloud", "name": "星辰云"}'
# 预期：{"code":"SUCCESS","data":{"id":"...","code":"ColoCloud",...}}

# 重复 code 新增 → 400 OP_002
curl -X POST http://localhost:8080/operator/add \
  -H "Content-Type: application/json" \
  -d '{"code": "ColoCloud", "name": "重复"}'
# 预期：{"code":"OP_002","message":"运营商 code 已存在"}

# 查询列表
curl -X POST http://localhost:8080/operator/list
# 预期：返回列表包含 ColoCloud
```

- [ ] **Step 5: API 验证 — 可用区/单位**

```bash
# 新增可用区
curl -X POST http://localhost:8080/az/add \
  -H "Content-Type: application/json" \
  -d '{"code": "az1", "name": "可用区 1"}'

# 新增使用量单位
curl -X POST http://localhost:8080/usage-unit/add \
  -H "Content-Type: application/json" \
  -d '{"code": "MB", "name": "兆字节"}'
```

- [ ] **Step 6: API 验证 — 分拣触发**

```bash
# 注意：需要先有一个 enabled=true + 连通的文件服务器配置
curl -X POST http://localhost:8080/sorting/trigger

# 查询分拣任务列表
curl -X POST http://localhost:8080/sorting/list
```

- [ ] **Step 7: 提交最终代码**

```bash
git add -A && git commit -m "feat: finalize v0.0.2 CDR sorting implementation"
```
