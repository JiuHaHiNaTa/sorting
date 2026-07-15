# 话单分拣系统 v0.0.3 — 设计文档

- 版本：0.0.3
- 日期：2026-07-15
- 技术栈：Spring Boot 4.1.0 + Java 21 + Maven + H2 + MyBatis + Jasypt + MinIO SDK + Kafka

> 基于 v0.0.2 分拣流水线，替换所有模拟实现为真实文件操作，新增下游数据推送、异常记录、文件清理等功能。

---

## 1. 架构总览

### 1.1 模块变化

```
v0.0.2（已有）                  v0.0.3（新增/改造）
=============                  =====================
controller/                    controller/
├── OperatorController          ├── OperatorController (不变)
├── ServiceAzController         ├── ServiceAzController (不变)
├── UsageUnitController         ├── UsageUnitController (不变)
├── SortingController           ├── SortingController (不变)
└── ConfigController            └── ConfigController (不变)

service/                       service/
├── OperatorService             ├── OperatorService (不变)
├── ServiceAzService            ├── ServiceAzService (不变)
├── UsageUnitService            ├── UsageUnitService (不变)
├── SortingService              ├── SortingService (增强)
├── PipelineExecutor (核心)     ├── PipelineExecutor (不变)
├── ConnectionService           ├── ConnectionService (不变)
├── MasterDataCache             ├── MasterDataCache (不变)
                                ├── FileService        ★ 新增 — MinIO 文件操作抽象
                                ├── CdrPushService     ★ 新增 — 推送接口抽象层
                                └── CdrPushKafkaImpl   ★ 新增 — Kafka 生产者实现

pipeline/                      pipeline/
├── StepHandler                 ├── StepHandler (不变)
├── ScanStepHandler (mock)      ├── ScanStepHandler ★ 改用 FileService 真实扫描
├── ValidateStepHandler         ├── ValidateStepHandler ★ 非法文件移入 /error
├── ExtractStepHandler (mock)   ├── ExtractStepHandler ★ 真实 ZIP 解压
├── ParseStepHandler (mock)     ├── ParseStepHandler ★ 真实 CSV 解析
├── PersistStepHandler          ├── PersistStepHandler (不变)
├── ArchiveStepHandler (mock)   ├── ArchiveStepHandler ★ 真实归档到 /backup
                                └── FileCleanupJob    ★ 新增 — 定时清理

dto/                           dto/
├── (现有 DTO)                  ├── (现有 DTO + 新增推送相关 DTO)

entity/                        entity/
├── (现有实体)                   ├── CdrErrorRecord   ★ 新增
                                └── CdrPushRecord    ★ 新增
```

### 1.2 完整处理流程

```
ScanStepHandler
  │  FileService.listFiles(directory)
  │  失败 → StepResult.failed (目录不存在)
  ↓
ValidateStepHandler
  │  正则校验文件名
  │  非法 → 移动文件到 /error/{yyyyMMdd}/
  │       → 写入 cdr_error_record
  │       → 不阻断剩余合法文件
  ↓
ExtractStepHandler
  │  FileService.downloadFile → 本地临时目录
  │  解压 ZIP → 校验子文件数 ≤ 100
  │  校验总大小 ≤ 100MB
  │  失败 → 移动源文件到 /error/{yyyyMMdd}/
  │       → 记录异常
  ↓
ParseStepHandler
  │  CSV 解析 (| 分隔符, 跳过表头)
  │  数据行数 ≤ 100
  │  MasterDataCache 验证字段
  │  非法行 → 丢弃 → 记录异常到 context
  │  全部失败 → 记录异常到 cdr_error_record
  ↓
PersistStepHandler
  │  CdrRecordMapper.batchInsert
  │  写入 cdr_push_record (PENDING)
  ↓
ArchiveStepHandler
  │  源 ZIP → /backup/{yyyyMMdd}/
  │  清理本地临时文件
  ↓
完成 → CdrPushService.schedulePush()
  │  每批 100 条待推送记录
  │  翻译 FK ID → code
  │  KafkaProducer.send(topic, CdrPushMessage)
  │  成功 → 标记 PUSHED
```

---

## 2. 数据库变更

所有表使用逻辑外键，表之间通过 ID 关联，不创建物理外键约束。

### 2.1 新增表：话单异常记录表 (`cdr_error_record`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| task_id | VARCHAR(36) | NOT NULL | 逻辑 FK → sorting_task.id |
| file_name | VARCHAR(255) | NOT NULL | 异常文件名 |
| error_type | VARCHAR(50) | NOT NULL | 异常类型：`FILE_NAME_INVALID` / `ZIP_CORRUPTED` / `PARSE_FAILED` / `UNDEFINED` |
| error_reason | TEXT | | 详细异常原因 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | UTC |

### 2.2 新增表：推送记录表 (`cdr_push_record`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| cdr_record_id | VARCHAR(36) | NOT NULL | 逻辑 FK → cdr_record.id |
| push_status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | `PENDING` / `PUSHED` / `FAILED` |
| pushed_at | TIMESTAMP | | 推送成功时间（UTC） |
| fail_reason | TEXT | | 推送失败原因 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | UTC |

### 2.3 已有表无结构变更

---

## 3. FileService 设计

### 3.1 接口定义

```java
@Service
public class FileService {

    /** 根据 FileServerConfig 创建 MinIO 客户端 */
    private MinioClient createClient(FileServerConfig config);

    /**
     * 检查目录是否存在。
     * 如果目录不存在，直接抛出 BusinessException - 不允许自动创建。
     */
    boolean directoryExists(FileServerConfig config, String directoryPath);

    /** 列出目录下的所有文件名 */
    List<String> listFiles(FileServerConfig config, String directoryPath);

    /** 从 MinIO 下载文件到本地临时目录，返回本地路径 */
    Path downloadFile(FileServerConfig config, String objectName);

    /** 在 MinIO bucket 内移动文件（跨目录 rename） */
    void moveFile(FileServerConfig config, String sourceObject, String targetObject);

    /** 从本地路径上传文件到 MinIO */
    void uploadFile(FileServerConfig config, String localPath, String objectName);

    /** 删除 MinIO 上的文件 */
    void deleteFile(FileServerConfig config, String objectName);
}
```

### 3.2 目录存在性校验

ScanStepHandler 在扫描前调用 `FileService.directoryExists()`：

- **存在** → 正常执行扫描
- **不存在** → 抛出异常，任务标记 FAILED，不创建目录

---

## 4. StepHandler 改造明细

### 4.1 ScanStepHandler

**改造前：** 硬编码返回 `["ColoCloud_az1_xxx.zip", ...]`

**改造后：**

```
1. 从 context 获取 FileServerConfig
2. FileService.checkDirectoryExists(config, config.fileDirectory)
3. 若目录不存在 → StepResult.failed("目录不存在: " + path)
4. FileService.listFiles(config, config.fileDirectory)
5. 只筛选 .zip 后缀文件
6. 放入 context.KEY_FILE_LIST
```

### 4.2 ValidateStepHandler

**改造前：** 仅正则校验，不操作实际文件

**改造后：**

```
1. 正则校验文件名（已有逻辑）
2. 非法文件：
   a. 生成目标路径: /error/{yyyyMMdd}/原文件名
   b. FileService.moveFile(config, 源路径, 错误路径)
   c. 写入 cdr_error_record
3. 合法文件继续传递到 context.KEY_VALID_FILES
```

### 4.3 ExtractStepHandler

**改造前：** `zipFile.replace(".zip", ".csv")` 模拟解压

**改造后：**

```
1. 对每个合法 ZIP 文件：
   a. FileService.downloadFile(config, zipFile) → 本地 Path
   b. 解压 ZIP 到临时目录
   c. 校验子文件数 ≤ 100，跳过非 .csv 文件
   d. 校验总大小 ≤ 100MB
   e. 失败 → 源文件移动到 /error/{yyyyMMdd}/ → 记录异常
   f. 成功 → CSV 文件路径加入 extractedFiles
2. 放入 context.KEY_EXTRACTED_FILES
```

### 4.4 ParseStepHandler

**改造前：** `generateMockRecords()` 生成模拟数据

**改造后：**

```
1. 对每个 CSV 文件：
   a. 读取 CSV 文件（| 分隔符，跳过表头）
   b. 校验行数 ≤ 100
   c. 每行解析：
      - 运营商名称 → MasterDataCache → operator_id
      - 服务 az → MasterDataCache → service_az_id
      - 资源 id → UUID 格式校验
      - 时间戳 → UTC Epoch Seconds → LocalDateTime
      - 使用量 → BigDecimal
      - 使用量单位 → MasterDataCache → usage_unit_id
   d. 非法行丢弃，记录丢弃数
   e. 全行都丢弃的 CSV → 记录 cdr_error_record
2. 放入 context.KEY_CDR_RECORDS
```

### 4.5 PersistStepHandler

**改造前：** 已真实执行 `cdrRecordMapper.batchInsert`

**改造后：** 额外写入 `cdr_push_record`，每条 cdr_record 对应一条 PENDING 状态的推送记录

### 4.6 ArchiveStepHandler

**改造前：** `return StepResult.ok("归档完成")` 空壳

**改造后：**

```
1. 对所有原始 ZIP 文件（从 context 中获取扫描时的原始文件列表）：
   a. 目标路径: /backup/{yyyyMMdd}/原文件名
   b. FileService.moveFile(config, 源路径, 备份路径)
   c. 验证移动成功
2. 清理本地临时目录
```

---

## 5. 推送模块

### 5.1 接口抽象

```java
public interface CdrPushService {
    /** 推送待发送的话单数据 */
    void pushPendingRecords();

    /** 重试失败的推送 */
    void retryFailedRecords();

    /** 批量推送（供定时任务/手动触发调用） */
    PushResult batchPush(int batchSize);
}
```

### 5.2 Kafka 实现

```java
@Component
@ConditionalOnProperty(name = "cdr.push.type", havingValue = "kafka")
public class CdrPushKafkaImpl implements CdrPushService {
    // KafkaTemplate<String, CdrPushMessage> kafkaTemplate
    // Topic: cdr-record-push

    // 推送消息体:
    // {
    //     "cdrRecordId": "uuid",
    //     "operatorCode": "ColoCloud",
    //     "azCode": "az1",
    //     "resourceId": "uuid-xxx",
    //     "usageAmount": 2.5,
    //     "usageUnitCode": "MB",
    //     "startTime": "2026-07-12T08:00:00Z",
    //     "endTime": "2026-07-12T09:30:00Z"
    // }
}
```

### 5.3 推送流程

```
PushTrigger (双触发)
├── PipelineExecutor 分拣完成后调用 schedulePush()
│   → 立即触发一次推送
│
└── @Scheduled(fixedRate = 60_000) 定时检查
    → 每 1 分钟处理未推送的待发送数据

CdrPushService.batchPush(100)
→ 1. 查询 cdr_push_record WHERE push_status='PENDING' LIMIT 100
→ 2. 左连接 cdr_record / operator / service_az / usage_unit 获得 code
→ 3. 构造 CdrPushMessage
→ 4. KafkaTemplate.send(topic, message)
→ 5. 成功 → 更新 push_status='PUSHED', pushed_at=now
→ 6. 失败 → 更新 push_status='FAILED', fail_reason=error
```

### 5.4 配置

```yaml
cdr:
  push:
    type: kafka                    # kafka / none（none = 关闭推送，仅入数据库）
    batch-size: 100
    topic: cdr-record-push

spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

---

## 6. 定时清理任务

### 6.1 FileCleanupJob

```java
@Component
public class FileCleanupJob {

    /**
     * 每天凌晨 2:00 执行
     * 清理 /backup/ 和 /error/ 目录中创建时间超过 7 天的文件
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldFiles() {
        // 1. 列出 /backup/ 下所有子目录
        // 2. 对每个子目录，检查目录名日期 < 7天前
        // 3. 删整个子目录
        // 4. 同样处理 /error/
    }

    /**
     * 清理 cdr_error_record 中 30 天前的记录
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanupOldErrorRecords();
}
```

**清理判断方式：**
- 目录路径格式：`/backup/20260708/` → 解析路径中的 yyyyMMdd
- 对比当前日期，超出 7 天则删除该目录及内容
- 无法解析日期的目录跳过，仅记录日志

---

## 7. CSV 推送消息格式

Kafka 消息体（JSON）：

```json
{
  "messageId": "uuid",
  "cdrRecordId": "uuid",
  "pushedAt": "2026-07-15T10:30:00Z",
  "payload": {
    "operatorCode": "ColoCloud",
    "azCode": "az1",
    "resourceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "usageAmount": 2.5000,
    "usageUnitCode": "MB",
    "startTime": "2026-07-12T08:00:00Z",
    "endTime": "2026-07-12T09:30:00Z"
  }
}
```

---

## 8. 错误码扩展

| 错误码 | HTTP | 描述 | 触发场景 |
|--------|------|------|----------|
| `SORT_005` | 400 | ZIP 解压失败 | 文件损坏或格式异常 |
| `SORT_006` | 400 | CSV 解析失败 | 行格式不符合规范 |
| `FILE_001` | 400 | MinIO 目录不存在 | scan 时目标目录不存在 |
| `FILE_002` | 400 | MinIO 文件操作失败 | 下载/移动/删除异常 |
| `PUSH_001` | 400 | 推送失败 | Kafka 发送失败 |

---

## 9. 测试方案

### 9.1 单元测试

| 测试类 | 范围 | Mock 对象 |
|--------|------|-----------|
| FileServiceTest | MinIO 操作逻辑 | MinioClient（spy） |
| StepHandlerTest（改造） | 各 Handler 替换后的逻辑 | FileService, MasterDataCache |
| CdrPushServiceTest | 推送逻辑 | KafkaTemplate |
| FileCleanupJobTest | 清理逻辑 | FileService |

### 9.2 集成测试

| 测试类 | 范围 | 说明 |
|--------|------|------|
| PipelineExecutorIntegrationTest（改造） | 全流程端到端 | Embedded Kafka + H2 |
| CdrPushServiceIntegrationTest | 推送全链路 | Embedded Kafka |
| SortingControllerTest（扩展） | 新增 API 端点 | 扩展已有测试类 |

### 9.3 嵌入式 Kafka 测试

```java
@SpringBootTest
@EmbeddedKafka(
    topics = {"cdr-record-push"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CdrPushServiceIntegrationTest {
    // 自动启动、停止嵌入式 Kafka
    // 验证消息发送和消费
}
```

---

## 10. 中断恢复与幂等性

### 10.1 可中断场景

| 场景 | 影响 | 恢复方式 |
|------|------|---------|
| Scan 中 MinIO 连接断开 | 任务 FAILED | 重试 API 重启任务 |
| Extract 解压到一半 | 临时文件残留 | rollback 清理临时目录 |
| Persist 部分写入 | 部分 cdr_record 已入库 | PipelineExecutor 已提交的事务在异常时回滚 |
| Archive 移动失败 | 源文件仍在原目录 | 重试时重新归档 |
| 推送 Kafka 中途失败 | 部分推送记录标记 PUSHED | 定时任务重试 FAILED 记录 |

### 10.2 防重复推送

推送记录唯一键 `cdr_record_id`，推送前查状态：
- `PUSHED` → 跳过
- `FAILED` → 重试
- `PENDING` → 推送

---

## 11. 配置参数

```yaml
cdr:
  push:
    type: kafka                    # kafka / none
    batch-size: 100
    topic: cdr-record-push
    retry-interval-ms: 60000       # 推送失败重试间隔

  cleanup:
    file-retention-days: 7         # 备份/异常文件保留天数
    error-record-retention-days: 30 # 异常记录保留天数
    cron: "0 0 2 * * ?"           # 清理定时表达式

  archive:
    directory-pattern: "yyyyMMdd"  # 归档子目录日期格式
```

---

## 12. 安全

延续 v0.0.1/v0.0.2 的安全策略：
- MinIO AK/SK 通过 Jasypt 加密存储
- API 返回掩码处理 AK/SK
- Kafka 连接未加密（开发环境），生产环境需配置 SSL
