# 话单分拣系统 v0.0.3 — 任务计划

**Goal:** 替换所有模拟文件操作为真实 MinIO 实现；新增下游数据推送（Kafka）、异常文件记录、归档目录按日期细分、定时清理功能。

**Status:** ✅ **全部完成**

---

## 阶段

| Phase | Status | Description |
|-------|--------|-------------|
| 设计 | ✅ complete | 设计文档已写入 `docs/superpowers/specs/2026-07-15-cdr-sorting-v0.0.3-design.md` |
| 实现 | ✅ complete | 7 个实现任务已完成（Schema/Entity/Mapper → 基础设施 → FileService → StepHandler 改造 → 推送模块 → 清理任务 → 最终验证） |
| API 验证 | ✅ complete | 应用启动正常，116 个测试全部通过，6 个 API 端点全部验证通过 |

## 实现进度

| Task | Status | Files |
|------|--------|-------|
| 1. Schema + Entity + Mapper | ✅ complete | CdrErrorRecord/CdrPushRecord 实体+Mapper+XML |
| 2. Kafka 依赖 + 配置 + 错误码 | ✅ complete | pom.xml, application.yaml, ErrorCode, StepContext |
| 3. FileService (MinIO 操作) | ✅ complete | FileService + FileServiceTest |
| 4. Pipeline StepHandler 改造 | ✅ complete | Scan/Validate/Extract/Parse/Persist/Archive 6 个 Handler 真实实现 |
| 5. 推送模块 CdrPushService + Kafka | ✅ complete | CdrPushService, CdrPushKafkaImpl, CdrPushMessage, 单元+集成测试 |
| 6. FileCleanupJob | ✅ complete | FileCleanupJob + FileCleanupJobTest |
| 7. 最终验证 | ✅ complete | 全量构建(116 tests OK) + 应用启动 + curl API 验证 |

## 已知问题

- v0.0.1 遗留：Jasypt 默认密钥 `default-dev-key`
- v0.0.1 遗留：H2 Console 生产环境启用
- MinIO 在开发环境不可用，FileService 通过错误路径测试覆盖
- Kafka 在开发环境未配置，CdrPushKafkaImpl 启动时输出 "KafkaTemplate 未配置，跳过推送" 警告
