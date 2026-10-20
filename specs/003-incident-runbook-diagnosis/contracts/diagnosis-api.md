# 人员 API 契约

**状态**：已批准并进入实现。主应用统一前缀 `/api/ragent`，所有入口登录且 `admin`，使用当前 Result 信封并返回真实 HTTP 状态；source/actor 来自服务端，不接受请求自报。

## POST /incident/v1/incidents/{incidentId}/diagnoses

Agent 诊断 Controller 拥有编排，Incident 应用服务拥有持久事实。

请求仅 `sourceRequestId, expectedIncidentVersion`。sourceRequestId 1～128 `[A-Za-z0-9._:-]`、版本非负；拒绝额外字段、重复键、null/畸形，原始请求默认 ≤8 KiB。人员不能传 snapshot、Runbook IDs、模型名、工具参数或提示词。

先在 Incident 本地事务中惰性收敛该 Incident 已超时请求，再写可恢复请求，在事务外运行本地检索/抽取。正常完成首次返回 201，同请求同绑定返回 200；已有未完成请求返回 202 和 diagnosisId/deadline/status=RUNNING，不启动第二次生成。系统 deadline 30 秒，不因客户端断开撤销已提交请求。结果状态可为 SUPPORTED/INSUFFICIENT_EVIDENCE/INVALID_EVIDENCE/FAILED；这些是已完成业务结果，不能包装为成功诊断。

响应字段 `diagnosisId, incidentId, requestState, outcome?, snapshotDigest, resultDigest?, stale, nextStep=REVIEW|WAIT|REDIAGNOSE|NONE, requestId`。GET 详情才返回经脱敏的证据原文，不在 Timeline/日志输出。

## GET /incident/v1/incidents/{incidentId}/diagnoses

参数 `afterId?`、`limit=20`，最大100；稳定按 `(createdAt,id)` 游标分页。返回只含摘要/status/stale 的列表，不一次返回所有原文。

## GET /incident/v1/diagnoses/{diagnosisId}

返回权威快照、取回证据/文档快照、候选、建议、缺失和版本；重算 stale（当前主 version/factsDigest、截断和语料是否仍适用）。GET 只读，不触发生成、收敛或工具。已超时 RUNNING 显示 deadlineExceeded=true/nextStep=REDIAGNOSE；后续诊断 POST 才追加失败事实。无语料组件时仍返回历史原文证据，但适用性为 UNKNOWN，不能转换 Action。

## POST /incident/v1/diagnoses/{diagnosisId}/recommendations/{recommendationId}/actions

Incident Controller 拥有转换。请求仅 `sourceRequestId, expectedIncidentVersion, diagnosisDigest, recommendationDigest, originalActionId?`，不接受 toolName/targetId/parameters/decision。

服务端从原始建议推导规范绑定并核对语料、前提、事实摘要、版本、非 CLOSED 及模拟目标配置；请求摘要绑定 diagnosisId/recommendationId/digests/originalActionId 与人员来源。首次 201，幂等 200，返回 actionId/PENDING_APPROVAL/诊断来源。已存在相同 link 的重试先返回历史结果，不能再次提议或执行。不同 sourceRequestId 可创建新 Action，仍需单独审批。

审批继续用 Spec B `/incident/v1/actions/{actionId}/decision`；派发与票据继续 B 协议，本接口没有批准字段。

## 错误与可用性

| HTTP | 安全原因示例 | 结果 |
| --- | --- | --- |
| 400 | INVALID_INPUT、UNSUPPORTED_RECOMMENDATION | 拒绝，无资源副作用 |
| 401/403 | AUTHENTICATION_REQUIRED、ACCESS_DENIED | 身份门禁 |
| 404 | DIAGNOSIS_NOT_FOUND、RECOMMENDATION_NOT_FOUND | 不泄露其他事实 |
| 409 | REQUEST_CONFLICT、STALE_SNAPSHOT、INCIDENT_CLOSED、EVIDENCE_REVOKED | 新转换无 Action |
| 413 | INPUT_TOO_LARGE | 解析/持久化前拒绝 |
| 503 | DIAGNOSIS_UNAVAILABLE、PERSISTENCE_UNAVAILABLE | 不伪造成功、不外部回退 |

错误仅 requestId/安全 code/message，不含票据、原始载荷、堆栈或模型思维链。诊断组件不可用只影响新诊断，不应阻断 A/B；历史 GET 由 Incident Controller 读取，人员选择失败关闭。

## 内部应用服务（模块 DTO，不是匿名 HTTP）

- beginDiagnosis：检查服务端人员上下文、幂等请求和版本，返回冻结快照与进程内完成 token。
- completeDiagnosis：输入 request ID/token、快照摘要、实际证据与校验结果；Incident 保证唯一终态和 Timeline 原子性，拒绝非法形状及摘要。token 不出人员 API。
- get/list/select：由 Incident 所有；Agent 无 Mapper/DO 引用，也无 Action 批准权限。

完成服务必须验证可信装配的编排来源与 token，不把任意 JSON 当合规诊断；本轮不增加未经鉴权的诊断 internal HTTP 入口。
