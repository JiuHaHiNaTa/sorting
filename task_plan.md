# 话单分拣系统 v0.0.2 — 任务计划

**Goal:** 在 v0.0.1 基础上构建完整的话单分拣系统：基础数据管理、任务流水线编排、文件处理（扫描→校验→解压→解析→持久化→归档）

**Status:** ✅ **全部完成**

---

## 阶段

| Phase | Status | Description |
|-------|--------|-------------|
| 设计 | ✅ complete | 设计文档已写入 `docs/superpowers/specs/2026-07-13-cdr-sorting-design.md` |
| 实现 | ✅ complete | 所有 10 个实现任务已完成，104 个测试通过 |
| API 验证 | ✅ complete | 应用启动正常，7 个 API 端点全部验证通过 |

## 实现进度

| Task | Status | Files |
|------|--------|-------|
| 1. Schema + 实体层 | ✅ complete | etc/schema.sql, 7 个实体 |
| 2. Mapper 层 | ✅ complete | 6 个 Mapper + 6 个 XML |
| 3. 错误码 + DTO | ✅ complete | ErrorCode 扩展, ~10 个 DTO |
| 4. MasterDataCache | ✅ complete | MasterDataCache + 测试 |
| 5. 基础数据 Service | ✅ complete | Operator/ServiceAz/UsageUnitService + 测试 |
| 6. 基础数据 Controller | ✅ complete | 3 个 Controller + 测试 |
| 7. Pipeline 核心框架 | ✅ complete | StepHandler, PipelineExecutor 等 + 测试 |
| 8. StepHandlers | ✅ complete | 6 个步骤处理器 + 测试 |
| 9. Sorting API | ✅ complete | SortingService + SortingController + 测试 |
| 10. 最终验证 | ✅ complete | 全量构建 + 启动 + curl |

## 已知问题

- v0.0.1 遗留：Jasypt 默认密钥 `default-dev-key`
- v0.0.1 遗留：H2 Console 生产环境启用
