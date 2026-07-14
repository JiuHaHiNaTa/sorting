# Pipeline 核心框架实现计划

> **REQUIRED SUB-SKILL:** 使用 superpowers:subagent-driven-development 或 superpowers:executing-plans 按任务执行。步骤使用 `- [ ]` 语法追踪。

**目标:** 构建话单分拣系统的 Pipeline 核心框架，提供步骤化任务编排、重试、崩溃恢复和超时检测。

**架构:** 基于 PipelineExecutor 编排器，按顺序驱动注册的 StepHandler，每个步骤通过 StepContext 传递数据，StepResult 返回执行结果。异步线程池执行，自动归档已完成任务。

**技术栈:** Spring Boot 4.1.0, Java 21, MyBatis, H2, Mockito 5

## 全局约束

- 所有新文件使用 `package com.example.sorting.pipeline` 和 `com.example.sorting.config`
- 使用已存在的实体类: SortingTask、SortingTaskBackup、SortingStepLog
- 使用已存在的 Mapper: SortingTaskMapper、SortingStepLogMapper
- 所有时间使用 `java.time.LocalDateTime` + UTC
- 测试使用 JUnit 5 + Mockito，不启动 Spring 上下文
- @EnableScheduling 已在 SortingApplication 上启用
- pom.xml 已有 Lombok、MyBatis、H2、Spring Boot Starter Test 依赖

---

### Task 1: 创建实体类和 Mapper

**文件:**
- 创建: `src/main/java/com/example/sorting/entity/SortingTask.java`
- 创建: `src/main/java/com/example/sorting/entity/SortingTaskBackup.java`
- 创建: `src/main/java/com/example/sorting/entity/SortingStepLog.java`
- 创建: `src/main/java/com/example/sorting/repository/SortingTaskMapper.java`
- 创建: `src/main/java/com/example/sorting/repository/SortingStepLogMapper.java`

**接口:**
- 消费: 无
- 产出: `SortingTask`(id, fileServerId, status, currentStep, retryCount, errorMessage, startedAt, completedAt, timeoutAt, createdAt, updatedAt), `SortingTaskBackup`(extends SortingTask + archivedAt), `SortingStepLog`(id, taskId, stepName, status, startedAt, completedAt, detail), `SortingTaskMapper`(insert/updateStatus/updateStep/incrementRetry/updateErrorMessage/selectById/selectByStatus/selectRunningTimeoutTasks/selectAll/deleteById/insertBackup), `SortingStepLogMapper`(insert, selectByTaskId)

- [ ] **Step 1: 创建 SortingTask.java**

```java
package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分拣任务实体
 */
@Data
public class SortingTask {
    private String id;
    private String fileServerId;
    private String status;
    private String currentStep;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime timeoutAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 SortingTaskBackup.java**

```java
package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分拣任务备份实体（已完成任务归档）
 */
@Data
public class SortingTaskBackup {
    private String id;
    private String fileServerId;
    private String status;
    private String currentStep;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime timeoutAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建 SortingStepLog.java**

```java
package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分拣任务步骤日志实体
 */
@Data
public class SortingStepLog {
    private String id;
    private String taskId;
    private String stepName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String detail;
}
```

- [ ] **Step 4: 创建 SortingTaskMapper.java**

```java
package com.example.sorting.repository;

import com.example.sorting.entity.SortingTask;
import com.example.sorting.entity.SortingTaskBackup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分拣任务 Mapper
 */
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

- [ ] **Step 5: 创建 SortingStepLogMapper.java**

```java
package com.example.sorting.repository;

import com.example.sorting.entity.SortingStepLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分拣步骤日志 Mapper
 */
@Mapper
public interface SortingStepLogMapper {

    int insert(SortingStepLog log);

    List<SortingStepLog> selectByTaskId(@Param("taskId") String taskId);
}
```

- [ ] **Step 6: 创建 XML Mapper 文件 SortingTaskMapper.xml**

创建 `src/main/resources/mapper/SortingTaskMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.sorting.repository.SortingTaskMapper">

    <resultMap id="BaseResultMap" type="com.example.sorting.entity.SortingTask">
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

    <resultMap id="BackupResultMap" type="com.example.sorting.entity.SortingTaskBackup">
        <id column="id" property="id"/>
        <result column="file_server_id" property="fileServerId"/>
        <result column="status" property="status"/>
        <result column="current_step" property="currentStep"/>
        <result column="retry_count" property="retryCount"/>
        <result column="error_message" property="errorMessage"/>
        <result column="started_at" property="startedAt"/>
        <result column="completed_at" property="completedAt"/>
        <result column="timeout_at" property="timeoutAt"/>
        <result column="archived_at" property="archivedAt"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
    </resultMap>

    <insert id="insert" parameterType="com.example.sorting.entity.SortingTask">
        INSERT INTO sorting_task (id, file_server_id, status, current_step, retry_count,
                                  error_message, started_at, completed_at, timeout_at,
                                  created_at, updated_at)
        VALUES (#{id}, #{fileServerId}, #{status}, #{currentStep}, #{retryCount},
                #{errorMessage}, #{startedAt}, #{completedAt}, #{timeoutAt},
                #{createdAt}, #{updatedAt})
    </insert>

    <update id="updateStatus">
        UPDATE sorting_task SET status = #{status} WHERE id = #{id}
    </update>

    <update id="updateStep">
        UPDATE sorting_task SET current_step = #{currentStep} WHERE id = #{id}
    </update>

    <update id="incrementRetry">
        UPDATE sorting_task SET retry_count = COALESCE(retry_count, 0) + 1 WHERE id = #{id}
    </update>

    <update id="updateErrorMessage">
        UPDATE sorting_task SET error_message = #{errorMessage} WHERE id = #{id}
    </update>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT * FROM sorting_task WHERE id = #{id}
    </select>

    <select id="selectByStatus" resultMap="BaseResultMap">
        SELECT * FROM sorting_task WHERE status = #{status}
    </select>

    <select id="selectRunningTimeoutTasks" resultMap="BaseResultMap">
        SELECT * FROM sorting_task
        WHERE status = 'RUNNING' AND timeout_at &lt; #{now}
    </select>

    <select id="selectAll" resultMap="BaseResultMap">
        SELECT * FROM sorting_task
    </select>

    <delete id="deleteById">
        DELETE FROM sorting_task WHERE id = #{id}
    </delete>

    <insert id="insertBackup" parameterType="com.example.sorting.entity.SortingTaskBackup">
        INSERT INTO sorting_task_backup (id, file_server_id, status, current_step, retry_count,
                                         error_message, started_at, completed_at, timeout_at,
                                         archived_at, created_at, updated_at)
        VALUES (#{id}, #{fileServerId}, #{status}, #{currentStep}, #{retryCount},
                #{errorMessage}, #{startedAt}, #{completedAt}, #{timeoutAt},
                #{archivedAt}, #{createdAt}, #{updatedAt})
    </insert>
</mapper>
```

- [ ] **Step 7: 创建 SortingStepLogMapper.xml**

创建 `src/main/resources/mapper/SortingStepLogMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.sorting.repository.SortingStepLogMapper">

    <resultMap id="BaseResultMap" type="com.example.sorting.entity.SortingStepLog">
        <id column="id" property="id"/>
        <result column="task_id" property="taskId"/>
        <result column="step_name" property="stepName"/>
        <result column="status" property="status"/>
        <result column="started_at" property="startedAt"/>
        <result column="completed_at" property="completedAt"/>
        <result column="detail" property="detail"/>
    </resultMap>

    <insert id="insert" parameterType="com.example.sorting.entity.SortingStepLog">
        INSERT INTO sorting_step_log (id, task_id, step_name, status,
                                      started_at, completed_at, detail)
        VALUES (#{id}, #{taskId}, #{stepName}, #{status},
                #{startedAt}, #{completedAt}, #{detail})
    </insert>

    <select id="selectByTaskId" resultMap="BaseResultMap">
        SELECT * FROM sorting_step_log WHERE task_id = #{taskId}
    </select>
</mapper>
```

- [ ] **Step 8: 更新 schema.sql，添加排序任务相关表**

创建/更新 `src/main/resources/schema.sql`（追加到文件末尾）：

```sql
-- 分拣任务表
CREATE TABLE IF NOT EXISTS sorting_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    file_server_id VARCHAR(100),
    status VARCHAR(20),
    current_step VARCHAR(50),
    retry_count INT,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    timeout_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 分拣任务备份表（已完成任务归档）
CREATE TABLE IF NOT EXISTS sorting_task_backup (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    file_server_id VARCHAR(100),
    status VARCHAR(20),
    current_step VARCHAR(50),
    retry_count INT,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    timeout_at TIMESTAMP,
    archived_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 分拣步骤日志表
CREATE TABLE IF NOT EXISTS sorting_step_log (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    task_id VARCHAR(36),
    step_name VARCHAR(50),
    status VARCHAR(20),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    detail TEXT
);
```

- [ ] **Step 9: 验证编译**

```bash
mvn clean compile -DskipTests
```
预期: BUILD SUCCESS

- [ ] **Step 10: 提交**

```bash
git add -A
git commit -m "feat: add sorting task entities and mappers"
```

---

### Task 2: 创建 Pipeline 核心接口和类

**文件:**
- 创建: `src/main/java/com/example/sorting/pipeline/StepHandler.java`
- 创建: `src/main/java/com/example/sorting/pipeline/StepContext.java`
- 创建: `src/main/java/com/example/sorting/pipeline/StepResult.java`
- 创建: `src/main/java/com/example/sorting/config/PipelineConfig.java`

**接口:**
- 消费: SortingTask, SortingTaskBackup, SortingStepLog
- 产出: StepHandler(接口), StepContext(包装SortingTask), StepResult(value object), PipelineConfig(线程池Bean)

- [ ] **Step 1: 创建 StepHandler.java**

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

- [ ] **Step 2: 创建 StepContext.java**

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

- [ ] **Step 3: 创建 StepResult.java**

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

- [ ] **Step 4: 创建 PipelineConfig.java**

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

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -DskipTests
```
预期: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: add pipeline core interfaces and config"
```

---

### Task 3: 创建 PipelineExecutor 编排器

**文件:**
- 创建: `src/main/java/com/example/sorting/pipeline/PipelineExecutor.java`

**接口:**
- 消费: StepHandler, StepContext, StepResult, SortingTaskMapper, SortingStepLogMapper, ThreadPoolTaskExecutor
- 产出: PipelineExecutor(startTask, recoverInterruptedTasks, timeoutCheck)

- [ ] **Step 1: 创建 PipelineExecutor.java**

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
import java.util.UUID;

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
            task.setCurrentStep(handler.getStepName());
            taskMapper.updateStep(task.getId(), handler.getStepName());

            boolean stepSuccess = false;
            String errorMsg = null;

            for (int retry = 0; retry <= MAX_RETRY; retry++) {
                if (retry > 0) {
                    log.info("重试步骤 [{}] 任务 [{}], 第 {}/{} 次", handler.getStepName(), task.getId(), retry, MAX_RETRY);
                    handler.rollback(context);
                }

                SortingStepLog stepLog = new SortingStepLog();
                stepLog.setId(UUID.randomUUID().toString());
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
                        stepSuccess = true;
                        break;
                    } else {
                        errorMsg = result.getMessage();
                        stepLog.setStatus("FAILED");
                        stepLog.setCompletedAt(LocalDateTime.now());
                        stepLog.setDetail(errorMsg);
                        stepLogMapper.insert(stepLog);
                    }
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                    log.error("步骤 [{}] 任务 [{}] 失败: {}", handler.getStepName(), task.getId(), errorMsg);
                    stepLog.setStatus("FAILED");
                    stepLog.setCompletedAt(LocalDateTime.now());
                    stepLog.setDetail(errorMsg);
                    stepLogMapper.insert(stepLog);
                }
            }

            if (!stepSuccess) {
                task.setErrorMessage(errorMsg);
                taskMapper.updateErrorMessage(task.getId(), errorMsg);
                taskMapper.updateStatus(task.getId(), "FAILED");
                log.error("任务 [{}] 在步骤 [{}] 经重试后失败", task.getId(), handler.getStepName());
                return;
            }
        }

        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateStatus(task.getId(), "COMPLETED");

        archiveTask(task);
    }

    private void archiveTask(SortingTask task) {
        SortingTaskBackup backup = new SortingTaskBackup();
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
        log.info("任务 [{}] 已归档到备份表", task.getId());
    }

    @PostConstruct
    public void recoverInterruptedTasks() {
        List<SortingTask> runningTasks = taskMapper.selectByStatus("RUNNING");
        if (!runningTasks.isEmpty()) {
            log.info("恢复 {} 个中断任务", runningTasks.size());
            for (SortingTask task : runningTasks) {
                log.info("恢复中断任务 [{}] 在步骤 [{}]", task.getId(), task.getCurrentStep());
                task.setTimeoutAt(LocalDateTime.now().plusMinutes(TIMEOUT_MINUTES));
                startTask(task);
            }
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void timeoutCheck() {
        List<SortingTask> timeoutTasks = taskMapper.selectRunningTimeoutTasks(LocalDateTime.now());
        for (SortingTask task : timeoutTasks) {
            log.warn("任务 [{}] 超时，标记为 FAILED", task.getId());
            task.setStatus("FAILED");
            task.setErrorMessage("任务超时（" + TIMEOUT_MINUTES + " 分钟）");
            taskMapper.updateStatus(task.getId(), "FAILED");
            taskMapper.updateErrorMessage(task.getId(), task.getErrorMessage());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -DskipTests
```
预期: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat: add PipelineExecutor with retry, recovery and timeout"
```

---

### Task 4: 创建 PipelineExecutorTest 单元测试

**文件:**
- 创建: `src/test/java/com/example/sorting/pipeline/PipelineExecutorTest.java`

**接口:**
- 消费: PipelineExecutor, Mockito
- 产出: 3个测试用例的执行结果

- [ ] **Step 1: 创建 PipelineExecutorTest.java**

```java
package com.example.sorting.pipeline;

import com.example.sorting.entity.SortingStepLog;
import com.example.sorting.entity.SortingTask;
import com.example.sorting.repository.SortingStepLogMapper;
import com.example.sorting.repository.SortingTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineExecutorTest {

    @Mock private SortingTaskMapper taskMapper;
    @Mock private SortingStepLogMapper stepLogMapper;
    @Mock(name = "sortingTaskPool") private ThreadPoolTaskExecutor executor;
    @InjectMocks private PipelineExecutor pipelineExecutor;

    @Test
    void recoverInterruptedTasks_shouldDoNothingWhenNoRunningTasks() {
        when(taskMapper.selectByStatus("RUNNING")).thenReturn(Collections.emptyList());
        pipelineExecutor.recoverInterruptedTasks();
        verify(taskMapper).selectByStatus("RUNNING");
        verifyNoMoreInteractions(taskMapper);
    }

    @Test
    void recoverInterruptedTasks_shouldProcessRunningTasks() {
        SortingTask task = new SortingTask();
        task.setId("task-1");
        task.setStatus("RUNNING");
        task.setCurrentStep("extract");
        when(taskMapper.selectByStatus("RUNNING")).thenReturn(List.of(task));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executor).submit(any(Runnable.class));

        pipelineExecutor.recoverInterruptedTasks();

        verify(taskMapper).selectByStatus("RUNNING");
    }

    @Test
    void timeoutCheck_shouldMarkTimeoutTasks() {
        SortingTask timeoutTask = new SortingTask();
        timeoutTask.setId("timeout-1");
        when(taskMapper.selectRunningTimeoutTasks(any(LocalDateTime.class)))
            .thenReturn(List.of(timeoutTask));

        pipelineExecutor.timeoutCheck();

        verify(taskMapper).updateStatus("timeout-1", "FAILED");
        verify(taskMapper).updateErrorMessage(eq("timeout-1"), anyString());
    }
}
```

- [ ] **Step 2: 运行测试验证**

```bash
mvn test -Dtest="com.example.sorting.pipeline.PipelineExecutorTest"
```
预期: Tests run: 3, Failures: 0

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "test: add PipelineExecutorTest with recovery and timeout cases"
```

---

### Task 5: 最终验证和提交

- [ ] **Step 1: 完整构建验证**

```bash
mvn clean compile -DskipTests
```

- [ ] **Step 2: 运行全部测试**

```bash
mvn test
```
预期: 所有测试通过（包括已有的Config/Connection测试）

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat: add pipeline core framework with executor"
```
