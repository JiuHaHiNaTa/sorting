# Pipeline 核心框架设计文档

## 概述

构建话单分拣系统的 Pipeline 核心框架，提供步骤化任务编排能力。每个分拣任务由多个有序步骤组成，支持重试、崩溃恢复、超时检测和任务归档。

## 架构组件

### 1. StepHandler 接口

步骤处理器接口，所有具体步骤必须实现。

| 方法 | 说明 |
|---|---|
| `getStepName()` | 步骤唯一标识，如 "scan"、"validate" |
| `execute(StepContext)` | 执行步骤核心逻辑，返回 StepResult |
| `rollback(StepContext)` | （可选）崩溃恢复时清理上一步残留 |

### 2. StepContext 上下文

在 Pipeline 内部传递的上下文对象，包装 `SortingTask` 实体，提供线程安全的键值对存储。

- 包装 `SortingTask` 实体
- `ConcurrentHashMap` 存储步骤间共享数据
- 预定义常量 key：`fileList`、`validFiles`、`extractedFiles`、`cdrRecords`

### 3. StepResult 结果类

不可变的步骤执行结果，提供静态工厂方法。

- `StepResult.ok()` — 成功（无消息）
- `StepResult.ok(message)` — 成功（带消息）
- `StepResult.failed(message)` — 失败（带错误消息）

### 4. PipelineConfig 线程池配置

Spring `@Configuration`，注册 `sortingTaskPool` Bean。

| 参数 | 值 |
|---|---|
| corePoolSize | 4 |
| maxPoolSize | 8 |
| queueCapacity | 50 |
| threadNamePrefix | "sorting-" |
| awaitTerminationSeconds | 30 |

### 5. PipelineExecutor 编排器

核心编排器，`@Component` 注入所有 `StepHandler` 实现。

#### 核心流程

```
startTask(task) → executor.submit()
  → 标记 task.status = RUNNING
  → for each StepHandler (按注入顺序):
    → 更新 task.current_step
    → 重试循环 (0..MAX_RETRY):
      → retry > 0: handler.rollback(context)
      → 创建 SortingStepLog (status=RUNNING)
      → handler.execute(context)
      → 成功: 标记 SUCCESS, break 重试
      → 失败: 标记 FAILED, 继续重试
      → 异常: 标记 FAILED, 继续重试
    → 步骤全部失败: 标记 task FAILED, 短路返回
  → 全部成功: 标记 COMPLETED
  → archiveTask: 复制到 backup 表 + 删除原记录
```

#### 崩溃恢复

`@PostConstruct recoverInterruptedTasks()`:
- 启动时查询所有 `status = RUNNING` 的任务
- 重新提交执行（重置超时时间）

#### 超时监控

`@Scheduled(fixedRate = 30_000)`:
- 查询超时任务（`selectRunningTimeoutTasks`）
- 标记为 FAILED，记录超时错误信息

### 重试策略

- 每步最多执行 1 次初始 + 3 次重试 = 4 次尝试
- 重试前调用 `rollback()` 清理残留
- 步骤失败则整个任务失败

### 数据流

```
外部系统 → startTask(task)
  → PipelineExecutor.executeTask (异步线程池)
    → StepHandler[0].execute → StepHandler[1].execute → ...
      → SortingStepLog 记录每一步生命周期
    → 完成 → 归档 SortingTaskBackup + 删除原记录
    → 失败 → 更新 task 状态为 FAILED
```

## 文件清单

| 文件 | 路径 |
|---|---|
| StepHandler.java | `src/main/java/com/example/sorting/pipeline/StepHandler.java` |
| StepContext.java | `src/main/java/com/example/sorting/pipeline/StepContext.java` |
| StepResult.java | `src/main/java/com/example/sorting/pipeline/StepResult.java` |
| PipelineConfig.java | `src/main/java/com/example/sorting/config/PipelineConfig.java` |
| PipelineExecutor.java | `src/main/java/com/example/sorting/pipeline/PipelineExecutor.java` |
| PipelineExecutorTest.java | `src/test/java/com/example/sorting/pipeline/PipelineExecutorTest.java` |

## 测试策略

- 使用 Mockito `@ExtendWith(MockitoExtension.class)` 纯单元测试
- Mock Mapper 和线程池，验证 PipelineExecutor 三个关键场景：
  1. 崩溃恢复：无运行中任务时不做操作
  2. 崩溃恢复：有运行中任务时重新提交
  3. 超时监控：标记超时任务为 FAILED

## 依赖关系

- StepHandler ← PipelineExecutor 注入
- StepContext → SortingTask（包装）
- PipelineExecutor → SortingTaskMapper, SortingStepLogMapper（数据库操作）
- PipelineExecutor → ThreadPoolTaskExecutor（异步执行）
- PipelineConfig → ThreadPoolTaskExecutor（Bean 注册）
