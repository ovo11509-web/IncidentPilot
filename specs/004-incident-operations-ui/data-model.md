# 数据模型与界面投影

**状态**：待确认；不创建表或迁移，不修改A/B/C状态机。

## 权威实体

- Incident：使用既有incidentId、lifecycleState、version、episodeStartAt、first/lastAlertReceivedAt、alertCount；列表不声明尚未持久化的service/环境索引。
- 关联Alert：必须按incident_id且PROCESSED查询；alertId/sourceId/ruleId/title/severity/service/environment/resourceType/resourceId/occurredAt/receivedAt按既有白名单清洗投影，不输出payload/labels/summary。
- Timeline：sequenceNo/eventId/eventType/actor/occurredAt/correlationId/relatedEntity/安全summary；沿用既有追加分页。
- Diagnosis：diagnosisId/requestState/outcome/snapshot/resultDigest/stale/evidenceApplicability/deadlineExceeded/nextStep；result内候选/缺失事实/限制/建议/引用使用当前contract，不重新计算摘要或来源资格。
- Action：现有actionId/state/version/目标/工具/bindingDigest/approval/diagnosisSource/execution/tickets安全状态/receipts。新增parameters安全预览和parametersAvailable；仅合法desiredState=READY|PAUSED，非法时parametersAvailable=false、无原JSON。
- Receipt：新增occurredAt、summaryAvailable与白名单summary(desiredState/applied/proofCode)，applied允许UNKNOWN对应null；按ActionReceiptService既有格式校验，异常则不输出原文。disposition保留，用于区分采纳与历史回执。

## 界面临时数据

路由ID和筛选、列表/告警/Timeline/诊断分页游标、当前加载/错误、在途读请求、未决命令sourceRequestId、按钮提交状态。临时数据不替代服务器记录，不存票据或完整证据到localStorage。刷新从权威GET恢复，不自动重放命令。

## 校验和状态

字符串ID原样传递，不转换Number。version和sequenceNo需非负安全整数；否则不能提交命令或继续相关游标。Incident列表状态仅OPEN/ACKNOWLEDGED/MITIGATING/RESOLVED/CLOSED（已核对现有Schema与状态机，不新增状态）。Action状态与nextStep使用现有协议，未知枚举显示原安全状态且禁用命令。

诊断可选择的前端必要条件：结果SUPPORTED、requestState=COMPLETED、非stale、evidenceApplicability=VALID、非deadlineExceeded、recommendation.convertible=true、事件非CLOSED；资格不构成授权，服务器还须检查绑定/事实/映射。Action批准还需PENDING_APPROVAL、合法安全预览和当前版本；不以浏览器时钟代替服务器有效期。

## 关系

Incident→已关联Alert/Timeline/Diagnosis/Action；Action→diagnosisSource→历史Diagnosis/recommendation；Action→Approval/Execution→Receipts。均为现有Incident领域关系，没有跨用户/RAG/MCP表JOIN。模拟回执SUCCESS只证明模拟资源处置，不能把来源报告或真实服务状态自动变成恢复事实。

回执proofCode仅显示NOT_STARTED、ROLLED_BACK或PRE_EFFECT_REJECTION等现有已知安全枚举；其他任意字符串不投影，不能以64字符长度校验代替秘密保护。相关非法/秘密值由T022验证。
