# 话单分拣系统 v0.0.2 — 任务计划

**Goal:** 在 v0.0.1 基础上构建完整的话单分拣系统：基础数据管理、任务流水线编排、文件处理（扫描→校验→解压→解析→持久化→归档）

**Status:** 🔄 **进行中**

---

## 阶段

| Phase | Status | Description |
|-------|--------|-------------|
| 设计 | ✅ complete | 设计文档已写入 `docs/superpowers/specs/2026-07-13-cdr-sorting-design.md` |
| 实现 | 🔄 in_progress | 10 个实现任务 |

## 实现进度

| Task | Status | Files |
|------|--------|-------|
| 1. Schema + 实体层 | ⬜ pending | etc/schema.sql, 7 个实体 |
| 2. Mapper 层 | ⬜ pending | 6 个 Mapper + 6 个 XML |
| 3. 错误码 + DTO | ⬜ pending | ErrorCode 扩展, ~10 个 DTO |
| 4. MasterDataCache | ⬜ pending | MasterDataCache + 测试 |
| 5. 基础数据 Service | ⬜ pending | Operator/ServiceAz/UsageUnitService + 测试 |
| 6. 基础数据 Controller | ⬜ pending | 3 个 Controller + 测试 |
| 7. Pipeline 核心框架 | ⬜ pending | StepHandler, PipelineExecutor 等 + 测试 |
| 8. StepHandlers | ⬜ pending | 6 个步骤处理器 + 测试 |
| 9. Sorting API | ⬜ pending | SortingService + SortingController + 测试 |
| 10. 最终验证 | ⬜ pending | 全量构建 + 启动 + curl |

## 已知问题

- v0.0.1 遗留：Jasypt 默认密钥 `default-dev-key`
- v0.0.1 遗留：H2 Console 生产环境启用
