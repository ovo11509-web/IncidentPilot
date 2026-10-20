# 数据模型：审批到安全模拟处置

**日期**：2026-09-23\
**依据**：[spec.md](./spec.md)、[plan.md](./plan.md)、DEC-009/011/012/019/020、Spec A v1.0。\
**状态**：设计已确认；本文不是可执行迁移，实施进度见 `tasks.md`。

## 1. 通用约定

- Incident 权威表属于主库 `ragent` 的 `incident` 模块。ID 沿用 Spec A 的不透明 `VARCHAR(20)` 风格；时间为 `TIMESTAMPTZ`，状态使用受限字符串与数据库 `CHECK`。新字段和约束仅由未来追加式迁移落地，本阶段不执行。
- `t_incident_action` 的源请求身份、执行幂等键均有数据库唯一约束。所有状态转换同时核对预期状态和版本；没有只靠进程锁或 Redis 的安全路径。
- 顺序为：必要时取 Spec A 同键事务锁，再锁 Incident 行，再锁 Action、Approval、Execution、Ticket；在同一 Incident 行保护下分配 Timeline 序号。不持锁等待网络。消费或回执路径不在持有 Action/Ticket 锁后反向索取 Incident 锁。
- 批准、票据消费、关闭、回执和清扫的资格判断采用主库可信时间；过期判断必须在取得所需行锁后核对，避免长时间等待使事务开始时刻的时间快照错误授予许可。锁后取时的要求遵循 DEC-019 对 DEC-012 的细化。
- 所有安全摘要和 Outbox 负载为版本化白名单结构；不保存凭据、票据明文、任意 MCP 原始文本、模型思维链或完整不可信请求。

## 2. `t_incident_action`

| 字段 | 类型/规则 |
| --- | --- |
| `id`、`incident_id` | `VARCHAR(20)` PK；Incident FK，`ON DELETE RESTRICT` |
| `source_type`、`source_request_id` | 受限来源枚举与不透明稳定请求 ID；`UNIQUE(incident_id, source_type, source_request_id)` |
| `proposed_by_type`、`proposed_by_id` | 已认证操作者/来源的稳定身份，不建立用户跨领域 FK |
| `original_action_id` | 可空、自领域 FK；再次执行时引用原 Action |
| `tool_name`、`tool_version` | P0 只允许 `simulation.set-state` 及版本 `1` |
| `target_type`、`target_id` | 服务端解析后的 `SIM_SERVICE` 和隔离资源 ID |
| `parameters_json` | 白名单规范业务参数；P0 为 `desiredState=READY/PAUSED` |
| `proposal_digest` | 不含新生成 `actionId`/执行键的规范提议内容摘要；同来源请求重试时比较，创建后不可变 |
| `binding_version`、`binding_digest` | `action-binding-v1` 与 64 位十六进制 SHA-256；创建后不可变 |
| `execution_key` | 不透明随机执行幂等键，`UNIQUE`；创建后不可变 |
| `state`、`version` | Action 状态和非负并发版本 |
| `approval_due_at`、`authorization_due_at` | 绝对截止时间；后者批准前为空 |
| `last_reason_code`、`created_at`、`updated_at` | 安全原因及可信时间 |

有效状态转换与 Spec B FR-009 完全一致。可变状态与时间必须符合以下约束：`PENDING_APPROVAL` 必有未来或已到期的审批截止；`APPROVED` 和后续状态必须有批准授权截止；`DISPATCHING` 及后续执行态必须关联唯一 Execution。终态不得回执行态。`UNKNOWN` 只可到 `SUCCEEDED/FAILED`。

## 3. `t_incident_action_approval`

| 字段 | 类型/规则 |
| --- | --- |
| `id`、`action_id` | 稳定 ID；`action_id UNIQUE` 且 FK Action |
| `status`、`version` | `PENDING/APPROVED/REJECTED/EXPIRED`；只允许从 `PENDING` 决定一次 |
| `binding_digest`、`approval_due_at` | 创建时复制 Action 的不可变绑定与截止 |
| `decider_id`、`role_snapshot`、`decided_at` | 决定后来自已认证会话与当前授权；待审批时为空 |
| `authorization_due_at`、`reason_code`、`reason_summary` | 批准时窗口或拒绝/过期安全原因；不保存任意原文 |

批准与 Action `APPROVED`、Timeline、Outbox 同事务。拒绝与 Action `APPROVAL_REJECTED` 同事务。Incident 成功关闭时，仍 `PENDING` 的审批转 `EXPIRED`；已 `APPROVED` 的记录不改写，Action 可独立转 `EXPIRED`。

## 4. `t_incident_action_execution`

| 字段 | 类型/规则 |
| --- | --- |
| `id`、`action_id`、`execution_key` | 稳定 ID；`action_id UNIQUE`、`execution_key UNIQUE` 且与 Action 相同 |
| `dispatch_request_id` | 首次领取时生成的稳定调用 ID；替换票据与消费前安全重试保持不变 |
| `tool_name`、`tool_version`、`target_type`、`target_id`、`binding_digest` | Action 不可变绑定的快照 |
| `max_execution_duration_ms`、`receipt_grace_period_ms` | 工具版本声明在本次 Execution 的实际正值 |
| `execution_started_at`、`receipt_due_at` | 票据首次合法消费时同事务写入；此前为空 |
| `current_receipt_id` | 可空，指向当前权威结果；历史回执不被覆盖 |
| `created_at` | 首次领取批准 Action 的可信时间 |

每个 Action 最多一个逻辑 Execution。替换票据不生成第二行，也不改变 `execution_key` 或 `dispatch_request_id`。消费与回执的 `requestId` 必须匹配该稳定调用 ID。`receipt_due_at = execution_started_at + max_execution_duration_ms + receipt_grace_period_ms`；票据有效期只约束启动。`UNKNOWN` 时仍保留 Execution 及历史回执。

## 5. `t_incident_execution_ticket`

| 字段 | 类型/规则 |
| --- | --- |
| `id`、`execution_id` | 稳定 ID；Execution FK |
| `secret_digest` | 256 位随机明文的 SHA-256 摘要；`UNIQUE`，明文不持久化 |
| `status` | `ISSUED/CONSUMED/EXPIRED`；只允许 `ISSUED` 出发 |
| `issued_at`、`expires_at`、`consumed_at` | 可信时间；`expires_at <= authorization_due_at` |
| `invalidation_reason`、`replaced_by_ticket_id` | 过期、替换或 Incident 关闭的受限原因；后者可空 |

`UNIQUE(execution_id) WHERE status='ISSUED'` 保证同时最多一张可消费票据。消费事务需验证 Action、Approval、Execution、Incident 未关闭、服务身份与完整绑定，再原子地改票据 `CONSUMED`、Action `EXECUTING`，写入 `execution_started_at/receipt_due_at` 和 Timeline。崩溃补票先在相同保护范围内过期旧票据，再签发替代票据；旧票据迟到不能改变持有有效替代票据的 Action。

## 6. `t_incident_action_receipt`

迟到回执与冲突证据需要保留多条只追加记录，不用覆盖 Execution 中的唯一结果字段。

| 字段 | 类型/规则 |
| --- | --- |
| `id`、`execution_id`、`action_id` | 回执稳定 ID；自领域 FK |
| `request_id`、`receipt_version` | 原工具请求身份与版本 |
| `receipt_digest` | 完整白名单回执规范编码的摘要；同 `id` 不同摘要为冲突 |
| `result_status`、`result_code` | `SUCCESS/FAILED/REJECTED/UNKNOWN` 与安全机器码；`DUPLICATE` 仅为重放响应，不新增执行回执 |
| `tool_name`、`tool_version`、`target_type`、`target_id`、`execution_key` | 必须与 Execution 不可变绑定一致 |
| `occurred_at`、`accepted_at`、`summary_json` | 可信发生时间、主库接收时间与白名单摘要 |
| `disposition` | `APPLIED/CONSISTENT/CONFLICT`；冲突只保留证据，不覆盖 Action |

`UNIQUE(execution_id, id)`，相同 ID 与相同摘要走幂等读取；相同 ID 与不同摘要不得覆盖原行，须以 `execution:{executionId}:receipt-conflict:{receiptId}:{digest}` 稳定键追加安全冲突 Timeline 事实并发出核查信号。新 `UNKNOWN` 可先 `APPLIED` 并使 Action `UNKNOWN`；后续确定回执可再次 `APPLIED` 并收敛。已确定终态的同向新回执为 `CONSISTENT`；不同 ID 的相反回执保存为 `CONFLICT`，不覆盖 Action，并发出核查信号。无认证或绑定不完整的回执在入库前拒绝，不凭自报标识改变 Action。

## 7. Spec A 共享事实的兼容扩展

### Timeline

沿用 `t_incident_timeline` 的 Incident FK、`sequence_no`、`business_event_key UNIQUE`、安全 `summary_json` 和只追加权限。计划新增关联类型 `ACTION/EXECUTION`，并明确以下处置事实类型；Spec A 原有五种类型保持不变：

```text
ACTION_PROPOSED
ACTION_APPROVAL_DECIDED
ACTION_DISPATCHED
ACTION_TICKET_ISSUED
ACTION_TICKET_EXPIRED
ACTION_TICKET_CONSUMED
ACTION_STATUS_CHANGED
ACTION_RECEIPT_ACCEPTED
ACTION_RECONCILIATION_RECORDED
ACTION_MANUAL_CONCLUSION
ACTION_RECEIPT_CONFLICT
```

同一业务动作可用一个含前后状态的事实表达，不因同时改变多个聚合内部行而重复制造“成功”事实；首次 Incident `RESOLVED` 仍遵循 Spec A 的两条既有事实规则。稳定键使用独立 `action:{actionId}:...` / `execution:{executionId}:...` 命名空间，例如 `action:{actionId}:proposed`、`action:{actionId}:approval:{approvalId}`、`execution:{executionId}:ticket:{ticketId}:consumed`、`execution:{executionId}:receipt:{receiptId}`，与 Spec A `incident:{incidentId}:...` 键不碰撞。关闭时每个失效 Action 有自己的稳定事实键，并保留 Spec A 原 `INCIDENT_STATUS_CHANGED`。

### Outbox

沿用 Spec A `t_incident_outbox` 的字段、稳定 `event_id/business_event_key`、`PENDING/PUBLISHED`、领取/退避/保留与至少一次投递。新增类型 `incident.action-approval-decided` 和 `incident.action-receipt-accepted`；同一事实的 Timeline/Outbox 共享稳定事件身份。仅首次生效的审批决定或权威回执生成 Outbox；普通查询、重复回执、对账未改变结果及票据控制事实不自动发布。新负载见 [action-events.md](./contracts/action-events.md)，原 `incident.created` 与 `incident.alert-attached` 的 v1 envelope 和 payload 不改。

## 8. 隔离模拟库（非 Incident 主数据）

`ragent_bit` 仅保存可重建演示资源：

- `simulation_target`：`target_id` PK、`state READY/PAUSED`、版本、更新时间。
- `simulation_application`：`execution_key` PK、`target_id`、`desired_state`、`before_state`、`after_state`、`applied_at`、结果摘要。

写工具在同一个本地事务中条件更新目标并插入应用记录；重复 `execution_key` 返回既有结果，不第二次改变目标。对账只读两表：与本次执行键绑定、包含前后状态的已提交应用记录可证明本次已应用，即使当前目标随后被另一 Action 改变；记录缺失只有在执行实例已停止且事务中止/回滚可证明时才证明未应用，否则 `UNKNOWN`。演示库重建会丢失资源证据；不得据此把旧 `UNKNOWN` 自动判成 `FAILED`，须保留主库审计并走人工证据处理。

## 9. 关键本地事务与并发断言

| 事务 | 同时生效的事实 | 必需断言 |
| --- | --- | --- |
| A1 提议 | Action、PENDING Approval、Timeline | 来源唯一键；Incident 未关闭 |
| A2 审批 | Action、Approval、Timeline、Outbox | 当前人员权限、绑定摘要、期限、预期版本 |
| A3 关闭 | Spec A Incident 转换、每个未开始 Action/Approval/Ticket 失效及各自 Timeline | 与票据消费互斥；已 EXECUTING 不改 |
| A4 领取/补票 | Execution、Ticket、Action、Timeline | 授权有效、最多一个 ISSUED、无已消费票据 |
| A5 票据消费 | Ticket、Action、Execution 截止时间、Timeline | 服务身份、未 CLOSED、全绑定与状态版本 |
| A6 回执 | Receipt、Action、Execution 当前结果、Timeline、适用时 Outbox | 双路径幂等、冲突不覆盖 |
| A7 清扫/对账/人工结论 | 条件状态变化、证据引用、Timeline、适用时 Outbox | 只读对账、明确证明、权限与预期版本 |

所有注入事务失败必须整体回滚。网络调用只在 A4 提交之后；模拟库事务与主库事务不跨库原子化，由 `UNKNOWN` 和对账处理剩余窗口。具体 SQL、索引名、迁移顺序和 PostgreSQL 真实并发检查留到实施任务。
