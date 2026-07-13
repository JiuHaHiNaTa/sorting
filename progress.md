# 进度日志

## 会话 1 — 2026-07-13

### 第一阶段：完整开发 + API 验证

#### 任务
完成文件服务器配置管理与连通性测试 v0.0.1 的完整开发。

#### 测试结果
- **测试总数:** 26
- **成功:** 26
- **失败:** 0
- **错误:** 0

#### API 验证（curl）
| 端点 | 预期 | 实际 |
|------|------|------|
| POST /config/add | 201/Success | OK ✅ |
| POST /config/modify (不存在) | 400/CONFIG_001 | OK ✅ |
| POST /config/toggle (未连通启用) | 400/CONFIG_002 | OK ✅ |
| POST /config/toggle (停用) | 200/Success | OK ✅ |
| POST /connection/check | CNN_001/CNN_003 | OK ✅ |
| POST /config/add (参数校验) | 400/PARAM_001 | OK ✅ |

#### Git 提交
- 分支: `feat/config-management-v0.0.1` → 当前为 `dev`
- 首次提交数: 11

#### 错误记录
| 错误 | 尝试 | 解决 |
|------|------|------|
| MyBatis 自动配置未激活 (SB 4.1.0) | 1 | MyBatisConfig 手动配置 |
| TestRestTemplate not found (SB 4.1.0) | 1 | 改用 RestTemplate + NoOpResponseErrorHandler |
| 4xx 响应 HTML 格式 | 1 | 增加 Accept: application/json 请求头 |
| EncryptTypeHandler 静态初始化失败 | 1 | 改为 MyBatisConfig 注入方式 |
| Jasypt 导入路径错误 | 1 | 修正为 `com.ulisesbocchio` |
| MinioException Mockito 模拟问题 | 1 | 使用 InsufficientDataException 子类 |

---

## 会话 2 — 2026-07-13（安全修复）

### 任务
修复 API 响应中 AK/SK 明文泄露问题。

#### 变更
1. 创建 `ConfigResponse` — AK/SK 前4位掩码输出的响应 DTO
2. 更新 `ConfigController` — 返回类型从 `FileServerConfig` 改为 `ConfigResponse`
3. 更新 `ConfigControllerTest` — 增加 AK/SK 掩码断言

#### 测试结果
- **测试总数:** 26（不变）
- **成功:** 26
- **失败:** 0

#### API 验证（curl）
```
"accessKey": "this-is-ak-long"  →  "accessKey": "this***"
"secretKey": "this-is-sk-long"  →  "secretKey": "this***"
```

#### 新增提交
| Hash | Message |
|------|---------|
| `80c67eb` | fix: mask AK/SK in API response to prevent credential leakage |

#### 待处理安全项
- Jasypt 默认密钥 `default-dev-key`
- H2 Console 生产环境启用
