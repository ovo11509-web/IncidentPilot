# Incident Action 领域事件契约 v1

**状态**：Spec B 计划草案。沿用 Spec A 的 `incident-domain-events` Topic、Outbox Relay、至少一次投递和通用 envelope；仅新增下列事件类型。

## 1. 兼容规则

- 原 `incident.created` 和 `incident.alert-attached` 的业务键、payload、schemaVersion 及重投身份保持不变；原五种 Timeline 类型也保持不变。
- 新事件与产生它的 Action 状态、Approval/Receipt、Timeline 在同一主库事务插入 Outbox。`eventId` 与对应 Timeline 事实 ID 相同；`business_event_key` 相同，重投时 envelope 与 payload 逐字段不变。
- envelope 仍含 `eventId`、`schemaVersion=1`、`eventType`、`incidentId`、`aggregateVersion`、`correlationId`、`occurredAt`、`payload`。`aggregateVersion` 是提交后的 Incident 主聚合版本；Action 自身更新只递增 Action 版本，不伪造 Incident 生命周期转换或额外递增其版本。消费者不能用 `aggregateVersion` 推断 Action 事件唯一顺序，应使用业务键和自己的持久化防重。
- 事件负载只含已认证稳定标识、状态、截止和安全码；不含票据、服务凭据、完整工具参数、任意原始文本或模拟资源敏感详情。未知事件/版本必须显式隔离或失败关闭，不能按 Spec A 旧类型猜测解析。

## 2. `incident.action-approval-decided`

**稳定业务键**：`action:{actionId}:approval:{approvalId}`。首次批准或拒绝各产生一次；清扫过期和 Incident 关闭导致的过期不是“人员审批决定”，只记录 Timeline，不冒充本事件。

```json
{
  "eventId": "198765432100000110",
  "schemaVersion": 1,
  "eventType": "incident.action-approval-decided",
  "incidentId": "198765432100000000",
  "aggregateVersion": 3,
  "correlationId": "198765432100000009",
  "occurredAt": "2026-09-23T12:00:00Z",
  "payload": {
    "actionId": "198765432100000001",
    "actionVersion": 1,
    "approvalId": "198765432100000010",
    "decision": "APPROVED",
    "bindingVersion": "action-binding-v1",
    "bindingDigest": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "authorizationDueAt": "2026-09-23T12:05:00Z",
    "actorId": "2001"
  }
}
```

`decision` 为 `APPROVED/REJECTED`；拒绝时 `authorizationDueAt` 缺失，增加安全 `reasonCode`。批准人只能来自服务端当前会话。重复审批请求不得生成第二个事件。

## 3. `incident.action-receipt-accepted`

**稳定业务键**：`execution:{executionId}:receipt:{receiptId}`。每条真正改变 Action 结果的权威回执各产生一次；`UNKNOWN` 后迟到的确定回执使用新的 receiptId 和新事件。相同回执重复、仅同向确认及冲突终态均不再发布“已接受结果”事件，冲突留下单独审计和核查信号。

```json
{
  "eventId": "198765432100000111",
  "schemaVersion": 1,
  "eventType": "incident.action-receipt-accepted",
  "incidentId": "198765432100000000",
  "aggregateVersion": 3,
  "correlationId": "198765432100000009",
  "occurredAt": "2026-09-23T12:00:04Z",
  "payload": {
    "actionId": "198765432100000001",
    "actionVersion": 4,
    "executionId": "198765432100000050",
    "receiptId": "198765432100000101",
    "resultStatus": "SUCCESS",
    "resultCode": "SIMULATION_APPLIED",
    "toolName": "simulation.set-state",
    "toolVersion": "1",
    "targetType": "SIM_SERVICE",
    "targetId": "demo-service-1"
  }
}
```

`resultStatus` 为 `SUCCESS/FAILED/REJECTED/UNKNOWN`；`DUPLICATE` 不是权威执行结果。安全目标 ID 若在部署中含敏感信息，发布前应改为稳定不透明引用并在 contracts 中版本化，不直接泄露。

## 4. 投递与验证

沿用 Spec A 的 `PENDING/PUBLISHED`、短期领取租约、`SEND_OK` 判定、持续退避及已发布记录保留规则。发送成功但标记失败可重投同一 `eventId`，消费者按该 ID 持久化去重。MQ 故障只积压 Outbox，不撤销审批或回执，也不阻断告警接入、查询和人工生命周期操作。

契约检查至少覆盖：两种新事件固定示例；原两种事件逐字段不变；重复审批/双路径回执只一条对应 Outbox；`UNKNOWN` 后迟到确定回执有新的稳定身份；发布失败及重复发布仍保持相同 envelope；秘密和不安全输入扫描为零。
