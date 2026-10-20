# Incident 领域事件契约 v1

**Topic**：`incident-domain-events`\
**投递语义**：at-least-once\
**Schema 版本**：`1`

Spec A 只发布 Incident 创建和 Alert 关联两类事件。状态转换事件留在 Timeline，不在本 Spec 扩大消息面。

## 1. 通用 envelope

```json
{
  "eventId": "198765432100000001",
  "schemaVersion": 1,
  "eventType": "incident.created",
  "incidentId": "198765432100000000",
  "aggregateVersion": 1,
  "correlationId": "198765432099999999",
  "occurredAt": "2026-09-22T12:00:00.123456Z",
  "payload": {}
}
```

| 字段 | 规则 |
| --- | --- |
| `eventId` | 稳定、不透明字符串；同一业务事件的每次发布重试保持不变，并用作 Broker message key |
| `schemaVersion` | 正整数；当前固定为 1 |
| `eventType` | `incident.created` 或 `incident.alert-attached` |
| `incidentId` | 目标 Incident 稳定 ID |
| `aggregateVersion` | 产生该事件的事务提交后 Incident 版本；同一事务产生创建与首条关联事件时，两者共享该最终版本，不是事件内独立递增序号 |
| `correlationId` | 创建时使用 anchor Alert 的首次 correlation ID；关联时使用目标 Alert 的首次 correlation ID |
| `occurredAt` | 数据库可信 UTC 时间，RFC 3339，必须带 `Z` 或明确偏移 |
| `payload` | 与事件类型匹配的 v1 对象；拒绝未知顶层 envelope 字段 |

同一个 `eventId` 的 envelope 和 payload 在重投时必须逐字段保持相同。Broker 自身 message ID 不属于业务身份。

## 2. `incident.created`

业务键：`incident:{incidentId}:created`

```json
{
  "eventId": "198765432100000001",
  "schemaVersion": 1,
  "eventType": "incident.created",
  "incidentId": "198765432100000000",
  "aggregateVersion": 1,
  "correlationId": "198765432099999999",
  "occurredAt": "2026-09-22T12:00:00.123456Z",
  "payload": {
    "aggregationKey": "alert-incident-v1:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "aggregationRuleVersion": "alert-incident-v1",
    "aggregationWindowSeconds": 1800,
    "episodeStartAt": "2026-09-22T11:59:58.000000Z",
    "episodeAnchorAlertId": "198765432099999998",
    "firstAlertReceivedAt": "2026-09-22T11:59:58.000000Z",
    "lastAlertReceivedAt": "2026-09-22T11:59:58.000000Z",
    "alertCount": 1,
    "lifecycleState": "OPEN",
    "bornSealed": false
  }
}
```

要求：

- 与 `INCIDENT_CREATED` Timeline 事实共享同一稳定 `eventId`/业务事件身份。
- `bornSealed=true` 表示该历史 episode 在被 Worker 发现并创建时聚合窗口已经结束；生命周期状态仍从 `OPEN` 开始，且不会因此虚构 `INCIDENT_AGGREGATION_SEALED` 事件。
- 负载不包含标题、summary、labels、完整告警请求体、来源密钥或签名。

## 3. `incident.alert-attached`

业务键：`incident:{incidentId}:alert:{alertId}:attached`

```json
{
  "eventId": "198765432100000002",
  "schemaVersion": 1,
  "eventType": "incident.alert-attached",
  "incidentId": "198765432100000000",
  "aggregateVersion": 1,
  "correlationId": "198765432100000003",
  "occurredAt": "2026-09-22T12:00:05.123456Z",
  "payload": {
    "alertId": "198765432100000004",
    "sourceId": "monitoring-main",
    "receivedAt": "2026-09-22T12:00:04.000000Z",
    "attachmentKind": "STANDARD",
    "fingerprint": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }
}
```

| 字段 | 规则 |
| --- | --- |
| `alertId` | 唯一关联 Alert |
| `sourceId` | 已认证且规范化的来源 ID；不是凭据 |
| `receivedAt` | Alert 的数据库可信接收时间 |
| `attachmentKind` | `STANDARD` 或 `LATE_AFTER_LIFECYCLE_SEAL` |
| `fingerprint` | v1 告警指纹，用于下游安全关联；不包含原始业务字段 |

`attachmentKind` 由关联事务提交时 Incident 是否已经具有 `lifecycleSealedAt` 决定。无论它是 `STANDARD` 还是 `LATE_AFTER_LIFECYCLE_SEAL`：

- `eventType` 始终为 `incident.alert-attached`；
- 复用 Alert 行中持久化的同一个 `attachmentEventId`；
- 与 `ALERT_ATTACHED` 或 `LATE_ALERT_ATTACHED_AFTER_SEAL` Timeline 事实共享业务身份；
- 重试不得产生第二条 Outbox 行或第二个 `eventId`。

## 4. 生产与发布规则

- Outbox 行必须与对应 Incident、Alert 关联和 Timeline 在同一 PostgreSQL 事务提交。
- Relay 只能发布已经提交的 `PENDING` 行。
- 现有 MQ 适配器返回的 `SendResult` 只有 `SEND_OK` 才能把行标记为 `PUBLISHED`。
- 发布失败按退避重试，单次延迟上限 15 分钟；不能因为尝试次数丢弃事件。
- 发布成功但数据库标记失败时，允许再次发布同一 envelope；不得生成新 event ID 伪装成新业务事件。
- 已发布 Outbox 行默认保留至少七天后才可分批清理；Timeline 仍是长期业务审计来源。

## 5. 消费者义务

- 消费者在产生持久化副作用前必须以 `eventId` 做自己的持久化防重。
- 消费者必须容忍重复、延迟和同一 Incident 不同事件的跨分区到达；同一事务内的多个事件可共享 `aggregateVersion`，不能据此假设事件唯一顺序或 Broker exactly-once。
- 未知 `schemaVersion` 必须失败关闭或进入显式隔离流程，不能按 v1 猜测解析。
- 消费者不得把 envelope 当作完整 Incident 快照；需要当前状态时使用授权查询能力。

## 6. 契约测试

- 每种事件至少验证一个符合 Schema 的固定示例和未知字段/未知版本拒绝示例。
- 注入发布失败后验证 Outbox 保持 `PENDING`、`eventId` 不变且退避不超过 15 分钟。
- 注入发送成功/标记失败，验证第二次捕获消息与第一次具有相同 envelope 和业务 key。
- 并发处理同一 Alert 时只存在一条 `incident.alert-attached` Outbox 行。
- 扫描 payload，确认不含 title、summary、labels、完整 request body、签名、密钥或堆栈探针。
