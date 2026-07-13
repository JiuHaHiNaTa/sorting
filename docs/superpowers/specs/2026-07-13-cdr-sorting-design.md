# 话单分拣系统 v0.0.2 — 设计文档

- 版本：0.0.2
- 日期：2026-07-13
- 技术栈：Spring Boot 4.1.0 + Java 21 + Maven + H2 + MyBatis + Jasypt + MinIO SDK

> 基于 v0.0.1 文件服务器配置管理，扩展话单分拣功能。

---

## 1. 架构总览

### 1.1 分层结构

```
controller
├── OperatorController     # 运营商 CRUD
├── ServiceAzController    # 可用区 CRUD
├── UsageUnitController    # 使用量单位 CRUD
├── SortingController      # 分拣任务控制（触发/重试/查询）
└── ConnectionController   # v0.0.1 保留

service
├── OperatorService        # 运营商业务逻辑
├── ServiceAzService       # 可用区业务逻辑
├── UsageUnitService       # 使用量单位业务逻辑
├── SortingService         # 分拣任务编排入口
├── PipelineExecutor       # 流水线执行器，步骤编排 + 超时 + 恢复
├── ConnectionService      # v0.0.1 保留
└── MasterDataCache        # 基础数据内存缓存

pipeline
├── StepHandler (接口)     # 步骤处理器接口
├── ScanStepHandler        # 步骤 1：扫描文件
├── ValidateStepHandler    # 步骤 2：校验文件
├── ExtractStepHandler     # 步骤 3：解压 ZIP
├── ParseStepHandler       # 步骤 4：解析 CSV
├── PersistStepHandler     # 步骤 5：持久化数据
└── ArchiveStepHandler     # 步骤 6：归档文件

repository
├── OperatorMapper
├── ServiceAzMapper
├── UsageUnitMapper
├── SortingTaskMapper
├── CdrRecordMapper
├── SortingStepLogMapper
└── ConfigMapper           # v0.0.1 保留

entity
├── Operator
├── ServiceAz
├── UsageUnit
├── SortingTask
├── SortingTaskBackup
├── SortingStepLog
├── CdrRecord
└── FileServerConfig       # v0.0.1 保留

dto
├── ... (各模块的 Req/Resp DTO)
└── ApiResponse<T>         # v0.0.1 保留，统一响应体

exception
├── BusinessException      # v0.0.1 保留
└── GlobalExceptionHandler # v0.0.1 保留
```

### 1.2 API 路径风格

延续 v0.0.1 的动作式路径风格，无版本号前缀。所有接口使用 `POST` 方法。

---

## 2. 数据库设计

所有表之间使用 **逻辑外键**（不创建物理外键约束），通过 ID 字段关联。

### 2.1 运营商量表 (`operator`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| code | VARCHAR(50) | UNIQUE NOT NULL | 运营商代码，必须在话单文件名中严格一致（如 `ColoCloud`） |
| name | VARCHAR(100) | NOT NULL | 显示名称（如"星辰云"） |
| created_at | TIMESTAMP | NOT NULL DEFAULT NOW | UTC |
| updated_at | TIMESTAMP | NOT NULL DEFAULT NOW | UTC |

### 2.2 可用区表 (`service_az`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| code | VARCHAR(50) | UNIQUE NOT NULL | 可用区代码（如 `az1`、`asia`） |
| name | VARCHAR(100) | NOT NULL | 显示名称 |
| created_at / updated_at | TIMESTAMP | NOT NULL | UTC |

### 2.3 使用量单位表 (`usage_unit`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| code | VARCHAR(20) | UNIQUE NOT NULL | 单位代码（如 `Count`、`MB`） |
| name | VARCHAR(50) | NOT NULL | 显示名称（如"次数"、"兆字节"） |
| created_at / updated_at | TIMESTAMP | NOT NULL | UTC |

### 2.4 分拣任务表 (`sorting_task`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| file_server_id | VARCHAR(36) | NOT NULL | 逻辑 FK → `file_server_config.id` |
| status | VARCHAR(20) | NOT NULL | `PENDING / RUNNING / COMPLETED / FAILED` |
| current_step | VARCHAR(30) | | 当前执行步骤名（如 `extract`），用于崩溃恢复 |
| retry_count | INT | DEFAULT 0 | 当前任务重试次数 |
| error_message | TEXT | | 失败原因 |
| started_at | TIMESTAMP | | 任务开始时间（UTC） |
| completed_at | TIMESTAMP | | 任务完成时间（UTC） |
| timeout_at | TIMESTAMP | | 超时截止时间 = started_at + 5min（UTC） |
| created_at | TIMESTAMP | NOT NULL DEFAULT NOW | UTC |
| updated_at | TIMESTAMP | NOT NULL DEFAULT NOW | UTC |

### 2.5 分拣任务历史表 (`sorting_task_backup`)

结构与 `sorting_task` 完全一致，额外增加：

| 字段 | 类型 | 说明 |
|------|------|------|
| archived_at | TIMESTAMP | 归档时间（UTC） |

任务完成后从主表移入，清理主表记录。

### 2.6 话单记录表 (`cdr_record`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| task_id | VARCHAR(36) | NOT NULL | 逻辑 FK → `sorting_task.id` |
| operator_id | VARCHAR(36) | NOT NULL | 逻辑 FK → `operator.id`（从文件名 + CSV 解析后关联） |
| service_az_id | VARCHAR(36) | NOT NULL | 逻辑 FK → `service_az.id` |
| resource_id | VARCHAR(36) | NOT NULL | 服务资源 ID（UUID 格式） |
| usage_amount | DECIMAL(18,4) | NOT NULL | 使用量 |
| usage_unit_id | VARCHAR(36) | NOT NULL | 逻辑 FK → `usage_unit.id` |
| start_time | TIMESTAMP | NOT NULL | 开始时间戳（UTC） |
| end_time | TIMESTAMP | NOT NULL | 结束时间戳（UTC） |
| created_at | TIMESTAMP | NOT NULL DEFAULT NOW | UTC |

### 2.7 分拣任务步骤日志表 (`sorting_step_log`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| task_id | VARCHAR(36) | NOT NULL | 逻辑 FK → `sorting_task.id` |
| step_name | VARCHAR(30) | NOT NULL | 步骤名称（`scan` / `validate` / `extract` / `parse` / `persist` / `archive`） |
| status | VARCHAR(20) | NOT NULL | `SUCCESS / FAILED` |
| started_at | TIMESTAMP | NOT NULL | 步骤开始时间（UTC） |
| completed_at | TIMESTAMP | | 步骤完成时间（UTC） |
| detail | TEXT | | 执行详情（处理的文件数、异常信息等） |

---

## 3. REST API 设计

### 3.1 运营商 API

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/operator/add` | `{code, name}` | 新增运营商。`code` 唯一性校验 |
| POST | `/operator/modify` | `{id, code?, name?}` | 修改运营商（至少传一个字段） |
| POST | `/operator/list` | 空 | 返回所有运营商列表 |
| POST | `/operator/delete` | `{id}` | 删除运营商。若被 cdr_record 引用则拒绝 |

### 3.2 可用区 API

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/az/add` | `{code, name}` | 新增可用区 |
| POST | `/az/modify` | `{id, code?, name?}` | 修改可用区 |
| POST | `/az/list` | 空 | 返回所有可用区列表 |
| POST | `/az/delete` | `{id}` | 删除可用区。若被引用则拒绝 |

### 3.3 使用量单位 API

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/usage-unit/add` | `{code, name}` | 新增单位 |
| POST | `/usage-unit/modify` | `{id, code?, name?}` | 修改单位 |
| POST | `/usage-unit/list` | 空 | 返回所有单位列表 |
| POST | `/usage-unit/delete` | `{id}` | 删除单位。若被引用则拒绝 |

### 3.4 分拣任务 API

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/sorting/trigger` | 空 | 扫描所有 enabled=true 且连通的文件服务器，创建分拣任务 |
| POST | `/sorting/list` | `{status?}` | 按状态筛选查询分拣任务列表 |
| POST | `/sorting/retry` | `{id}` | 重试失败的分拣任务（重置 retry_count，恢复为 PENDING） |
| POST | `/sorting/detail` | `{id}` | 查询分拣任务详情，含步骤日志 |

### 3.5 统一响应格式

```json
// 成功
{ "code": "SUCCESS", "message": "操作成功", "data": { ... } }

// 失败
{ "code": "OP_001", "message": "运营商不存在" }
```

---

## 4. 错误码体系

在 v0.0.1 错误码基础上新增：

### 基础数据类

| 错误码 | HTTP | 描述 | 触发场景 |
|--------|------|------|----------|
| `OP_001` | 400 | 运营商不存在 | 按 code/id 查询为空 |
| `OP_002` | 400 | 运营商 code 已存在 | 新增时 code 重复 |
| `OP_003` | 400 | 运营商已被引用 | 删除时存在关联 cdr_record |
| `AZ_001` | 400 | 可用区不存在 | |
| `AZ_002` | 400 | 可用区 code 已存在 | |
| `AZ_003` | 400 | 可用区已被引用 | |
| `UNIT_001` | 400 | 单位不存在 | |
| `UNIT_002` | 400 | 单位 code 已存在 | |
| `UNIT_003` | 400 | 单位已被引用 | |

### 分拣类

| 错误码 | HTTP | 描述 | 触发场景 |
|--------|------|------|----------|
| `SORT_001` | 400 | 分拣任务不存在 | 按 id 查询为空 |
| `SORT_002` | 400 | 没有可执行的分拣任务 | 没有已启用且连通的服务器 |
| `SORT_003` | 400 | 分拣任务状态不允许该操作 | 试图重试非 FAILED 状态的任务 |
| `SORT_004` | 400 | 文件命名不符合规范 | 文件名不匹配正则 |
| `SORT_005` | 400 | ZIP 解压失败 | 文件损坏或格式异常 |
| `SORT_006` | 400 | CSV 解析失败 | 行格式不符合规范 |

---

## 5. Pipeline 流水线

### 5.1 StepHandler 接口

```java
public interface StepHandler {
    /** 步骤名称标识 */
    String getStepName();
    
    /** 执行步骤逻辑 */
    StepResult execute(StepContext context);
    
    /** 崩溃恢复时清理上一步可能的残留（可选） */
    default void rollback(StepContext context) { }
}
```

### 5.2 步骤执行顺序

| # | 步骤 | Handler | 说明 |
|---|------|---------|------|
| 1 | scan | ScanStepHandler | 扫描 MinIO 文件服务器目录，列出待处理的 ZIP 文件 |
| 2 | validate | ValidateStepHandler | 校验 ZIP 文件名是否匹配规范，不匹配的移入 error/ 目录 |
| 3 | extract | ExtractStepHandler | 解压 ZIP 到临时目录，验证子文件数量(≤100) 和总大小(≤100MB) |
| 4 | parse | ParseStepHandler | 解析每个 CSV 文件为 CdrRecord 列表，验证行数(≤100) |
| 5 | persist | PersistStepHandler | 批量写入 cdr_record 表 |
| 6 | archive | ArchiveStepHandler | 原始 ZIP 移到 backup/ 目录，清理临时目录 |

### 5.3 PipelineExecutor 核心逻辑

```java
@Component
public class PipelineExecutor {
    
    private final List<StepHandler> steps;  // 自动注入所有 StepHandler
    private final ThreadPoolTaskExecutor executor;  // 并发线程池
    
    /** 启动一个分拣任务 */
    public void startTask(SortingTask task) { ... }
    
    /** 崩溃恢复（@PostConstruct，服务重启时自动执行） */
    public void recoverInterruptedTasks() {
        // 1. 查询所有 status = RUNNING 的任务
        // 2. 从 current_step 恢复（跳过已完成步骤）
        // 3. 更新 timeout_at
    }
    
    /** 超时监控 */
    @Scheduled(fixedRate = 30_000)  // 30秒检测一次
    public void timeoutCheck() {
        // 查询 timeout_at < now 且 status = RUNNING 的任务
        // 标记为 TIMEOUT → FAILED
    }
}
```

### 5.4 流程控制

```
PENDING → RUNNING → [scan→validate→extract→parse→persist→archive] → COMPLETED
                                         ↕ (失败重试 ≤ 3 次)
                                        FAILED → (手动 retry API) → PENDING → ...
```

- 每个步骤执行前更新 `current_step`，执行后记录 `sorting_step_log`
- 失败后重试 ≤ 3 次，超过则标记 `FAILED`
- 每个任务超时限制 5 分钟，超时后标记 `FAILED(TIMEOUT)`
- 完成后移入 `sorting_task_backup` 表

### 5.5 命名规范校验正则

```
^[A-Za-z0-9]+_[A-Za-z0-9]+_\d{12}_\d{12}_[A-Za-z0-9]+\.zip$
```

示例：`ColoCloud_az1_202607120800_202607120930_asia.zip`

### 5.6 CSV 文件格式

子话单 CSV 文件使用 `|`（竖线）作为分隔符，每行数据对应一条使用量记录：

```
| 运营商名称 | 服务 az | 服务资源 id | 开始时间戳 | 结束时间戳 | 使用量 | 使用量单位 |
|  ColoCloud |   az1   |  uuid-xxx   | 1720771200 | 1720773000 |  2.5   |    MB     |
```

- 第一行为列名行（表头），解析时跳过
- 时间戳为 UTC Epoch Seconds
- 数据行中的运营商名称、可用区、使用量单位必须与 master_data 表中存在的 code 一致，否则丢弃该行

### 5.7 ZIP/CSV 校验规则

| 规则 | 限制 | 违规处理 |
|------|------|---------|
| 子文件数量 | ≤ 100 个 | 丢弃超限文件 |
| 上传总大小 | ≤ 100MB | 标记 SORT_005 |
| 子文件后缀 | 必须 `.csv` | 跳过非 CSV |
| CSV 数据行数 | ≤ 100 行（不含表头） | 丢弃整行数据，标记异常 |
| 运营商名称 | 必须在 operator 表中存在 | 丢弃整行 |
| 服务可用区 | 必须在 service_az 表中存在 | 丢弃整行 |
| 使用量单位 | 必须在 usage_unit 表中存在 | 丢弃整行 |
| 资源 ID | 必须为 UUID 格式 | 丢弃整行 |

### 5.7 并发控制

- 默认线程池：core=4, max=8（同时处理 4~8 个分拣任务）
- 每个文件服务器串行处理（一个任务只对应一个服务器）
- 多个服务器之间并发执行

---

## 6. 基础数据缓存

### 6.1 缓存内容

| 缓存 | Key | Value | 更新策略 |
|------|-----|-------|---------|
| operatorCache | code → Operator | 完整实体 | 写操作触发刷新 + 定时 5min 全量刷新 |
| azCache | code → ServiceAz | 完整实体 | 同上 |
| unitCache | code → UsageUnit | 完整实体 | 同上 |

### 6.2 自动刷新机制

**双触发策略：**

1. **写操作即时刷新** — 各 Service 的 add/modify/delete 方法调用后触发 `MasterDataCache.refreshOperators()/refreshAzs()/refreshUnits()`
2. **定时兜底刷新** — `@Scheduled(fixedRate = 300_000)` 每 5 分钟全量刷新所有缓存

**粒度控制**：
- 写操作 → 只刷新被修改的单个缓存区
- 定时任务 → 全量刷新三个缓存区

---

## 7. 异常处理

沿用 v0.0.1 的统一异常处理框架：

| 异常类型 | HTTP 状态 | 错误码 |
|----------|-----------|--------|
| `MethodArgumentNotValidException` | 400 | `PARAM_001` |
| `BusinessException` | 400 | 动态（OP/AZ/UNIT/SORT 系列） |
| `Exception`（兜底） | 500 | `SYS_001` |

---

## 8. 测试方案

### 分层测试

| 层级 | 框架 | 范围 |
|------|------|------|
| 单元测试 | JUnit 5 + Mockito | Service 层逻辑，Mock Mapper |
| 集成测试 | @SpringBootTest + H2 | Controller 全链路 |
| Pipeline 测试 | @SpringBootTest + Mock StepHandler | PipelineExecutor 编排逻辑 |

### 测试覆盖

- 基础数据 CRUD 正向 + 异常路径（code 重复、被引用删除等）
- 缓存自动刷新逻辑
- 文件名规范校验（合法/非法文件名）
- ZIP 解压逻辑（正常/损坏/超限）
- CSV 解析逻辑（正常/非法行/数据超限）
- PipelineExecutor 编排（正常流、中断恢复、超时、重试耗尽）
- 崩溃恢复场景
