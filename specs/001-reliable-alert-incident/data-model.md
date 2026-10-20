# 数据模型：可靠告警到 Incident

**日期**：2026-09-22\
**依据**：[spec.md](./spec.md)、DEC-009、DEC-010、DEC-015、DEC-016

本文描述逻辑模型、约束和事务边界，不是可直接执行的迁移。实现阶段必须同时更新 `resources/database/schema_pg.sql` 与对应追加式升级脚本，并以真实 PostgreSQL 集成测试证明约束。

## 1. 通用约定

- 表归 `incident` 模块独占，表名使用 `t_incident_*`。
- 业务 ID 沿用仓库字符串 Snowflake 风格，逻辑类型为 `VARCHAR(20)`；协议中的 ID 均作为不透明字符串处理。
- 可信时间统一为 `TIMESTAMPTZ`；`received_at` 与首次 `lifecycle_sealed_at` 遵循 DEC-016 的同键数据库单调业务排序时间，其余发生/调度时间由数据库时钟取得。来源 `occurredAt` 只保存证据，不参与可信排序。
- 枚举以受限字符串保存并建立 `CHECK` 约束，应用枚举与数据库集合必须一致。
- 可变聚合根包含 `version BIGINT NOT NULL`，每次受保护业务更新递增。
- `t_incident_alert_audit` 与 `t_incident_timeline` 不含业务更新/删除用 Mapper；生产数据库角色应撤销其普通 `UPDATE/DELETE` 权限。迁移和受控保留作业使用独立权限。
- 所有 JSONB 安全摘要均为白名单结构；秘密、签名、完整未脱敏请求体、堆栈和任意不受限用户文本不得写入审计、Timeline 或 Outbox。

## 2. `t_incident_alert`

保存不可变接收内容、可靠工作状态和唯一 Incident 归属。

### 字段

| 字段 | 类型/空值 | 规则 |
| --- | --- | --- |
| `id` | `VARCHAR(20)` PK | 稳定 `alertId` |
| `source_id` | `VARCHAR(128)` NOT NULL | 由认证元数据推导的规范来源 ID |
| `external_alert_id` | `VARCHAR(128)` NOT NULL | 来源内幂等 ID；持久化后不可变 |
| `protocol_version` | `SMALLINT` NOT NULL | v1 必须为 `1` |
| `payload_json` | `JSONB` NOT NULL | 首次接收时已校验的完整 v1 业务对象；保留原始标量值和字段缺失语义，但 JSONB 不保留无意义空白/键顺序；不含认证头和秘密，之后不可覆盖 |
| `payload_digest` | `CHAR(64)` NOT NULL | 版本化规范业务对象的 SHA-256 小写十六进制 |
| `event_type` | `VARCHAR(16)` NOT NULL | v1 仅 `FIRING` |
| `occurred_at` | `TIMESTAMPTZ` NOT NULL | 来源时间证据 |
| `rule_id` | `VARCHAR(128)` NOT NULL | 已通过契约校验的规范值 |
| `title` | `VARCHAR(256)` NOT NULL | 规范化标题 |
| `severity` | `VARCHAR(16)` NOT NULL | `CRITICAL/HIGH/MEDIUM/LOW` |
| `environment` | `VARCHAR(64)` NOT NULL | 指纹规范值 |
| `service` | `VARCHAR(128)` NOT NULL | 指纹规范值 |
| `resource_type` | `VARCHAR(64)` NOT NULL | 指纹规范值 |
| `resource_id` | `VARCHAR(256)` NOT NULL | 大小写敏感的不透明规范值 |
| `summary` | `VARCHAR(2048)` NULL | 缺失时为空；显式 null 在接入层已拒绝 |
| `labels_json` | `JSONB` NOT NULL | 排序后的扁平字符串映射；缺失和 `{}` 均保存为 `{}` |
| `fingerprint` | `CHAR(64)` NOT NULL | `alert-fingerprint-v1` 元组摘要 |
| `aggregation_key` | `VARCHAR(96)` NOT NULL | `alert-incident-v1:` + 指纹 |
| `received_at` | `TIMESTAMPTZ` NOT NULL | 首次持久化事务在同键锁内分配的单调可信业务排序时间；重试/重放不变 |
| `correlation_id` | `VARCHAR(20)` NOT NULL | 首次接收生成，所有重试/重放保持不变 |
| `processing_status` | `VARCHAR(16)` NOT NULL | `RECEIVED/PROCESSED/FAILED` |
| `processing_epoch` | `INTEGER` NOT NULL | 初始为 0；每次接受人工重放递增，用于稳定失败/恢复事实键 |
| `attempt_count` | `INTEGER` NOT NULL | 当前 epoch 已开始的处理尝试数 |
| `retry_count` | `INTEGER` NOT NULL | 当前 epoch 已调度/执行的自动重试数，默认最多 5 |
| `next_attempt_at` | `TIMESTAMPTZ` NULL | `RECEIVED` 时的下次可领取时间 |
| `last_failure_code` | `VARCHAR(64)` NULL | 安全机器码 |
| `last_failure_summary` | `VARCHAR(512)` NULL | 安全摘要，不含堆栈和载荷 |
| `failed_at` | `TIMESTAMPTZ` NULL | 当前终态失败时间，仅 `FAILED` 使用 |
| `incident_id` | `VARCHAR(20)` NULL FK | 一次性关联的 Incident；不得从一个非空值改到另一个值 |
| `attachment_event_id` | `VARCHAR(20)` NULL | 关联 Timeline 与 Outbox 复用的稳定事件 ID |
| `attached_at` | `TIMESTAMPTZ` NULL | 关联事务的可信提交时间 |
| `version` | `BIGINT` NOT NULL | 初始 0；状态/控制数据变化时递增 |
| `created_at` | `TIMESTAMPTZ` NOT NULL | 等于首次持久化时间 |
| `updated_at` | `TIMESTAMPTZ` NOT NULL | 最近受保护更新的数据库时间 |

### 约束与索引

- `UNIQUE (source_id, external_alert_id)`：最终幂等仲裁。
- `UNIQUE (attachment_event_id)`，允许多个 null。
- `CHECK (processing_epoch >= 0 AND attempt_count >= 0 AND retry_count >= 0)`。
- 三个关联字段必须全空或全非空；`PROCESSED` 必须有 `incident_id`，`FAILED` 必须有失败码和 `failed_at`。
- `RECEIVED` 必须有 `next_attempt_at`；`PROCESSED/FAILED` 不得被普通 Worker 再领取。
- 工作索引：`(next_attempt_at, received_at, id) WHERE processing_status='RECEIVED'`。
- episode 索引：`(aggregation_key, received_at, id)`。
- 查询索引：`incident_id`、`correlation_id`。

### 允许状态变化

```text
新接收 -> RECEIVED
RECEIVED -> PROCESSED
RECEIVED -> FAILED
FAILED --授权重放且版本匹配--> RECEIVED（processing_epoch + 1）
```

相同提交只读取现状；冲突提交不改变任何字段。`PROCESSED` 不可重放，已关联 Alert 不得解除或迁移关联。

## 3. `t_incident_alert_audit`

只追加保存 Alert 级不可变事实，不保存逐次 Attempt 历史。

| 字段 | 类型/空值 | 规则 |
| --- | --- | --- |
| `id` | `VARCHAR(20)` PK | 审计事实 ID |
| `alert_id` | `VARCHAR(20)` NOT NULL FK | 目标 Alert |
| `fact_key` | `VARCHAR(160)` NOT NULL UNIQUE | 稳定业务事实键 |
| `fact_type` | `VARCHAR(40)` NOT NULL | `ALERT_RECEIVED/ALERT_TERMINAL_FAILED/ALERT_REPLAY_ACCEPTED/ALERT_REPLAY_REJECTED/ALERT_RECOVERED` |
| `occurred_at` | `TIMESTAMPTZ` NOT NULL | 数据库可信时间 |
| `actor_type` | `VARCHAR(24)` NOT NULL | `SOURCE/SYSTEM/USER` |
| `actor_id` | `VARCHAR(128)` NOT NULL | 稳定来源、服务或用户 ID |
| `request_id` | `VARCHAR(40)` NULL | 触发请求的诊断 ID |
| `correlation_id` | `VARCHAR(20)` NOT NULL | Alert 首次 correlation ID |
| `processing_epoch` | `INTEGER` NOT NULL | 对应处理 epoch |
| `reason_code` | `VARCHAR(64)` NULL | 安全机器原因 |
| `reason_summary` | `VARCHAR(512)` NULL | 人工原因的受限安全文本或系统摘要 |
| `failure_code` | `VARCHAR(64)` NULL | 终态失败/恢复所引用的安全失败码 |
| `attempt_count_snapshot` | `INTEGER` NULL | 终态失败时的尝试数 |
| `metadata_json` | `JSONB` NOT NULL | 白名单结构，默认 `{}` |
| `created_at` | `TIMESTAMPTZ` NOT NULL | 与 `occurred_at` 一致或更晚的写入时间 |

稳定事实键建议：

- 接收：`alert:{alertId}:received`
- 某 epoch 终态失败：`alert:{alertId}:epoch:{n}:failed`
- 某 epoch 成功恢复：`alert:{alertId}:epoch:{n}:recovered`
- 重放接受/拒绝：`alert:{alertId}:replay:{requestId}:{decision}`

接收事实与 Alert 同事务；终态失败事实与 `FAILED` 更新同事务；重放决定与其状态判断/更新同事务；恢复事实只在曾失败后的 epoch 最终进入 `PROCESSED` 时写入。

## 4. `t_incident`

表示一个确定性 episode 及其主生命周期。

| 字段 | 类型/空值 | 规则 |
| --- | --- | --- |
| `id` | `VARCHAR(20)` PK | 稳定 `incidentId` |
| `aggregation_key` | `VARCHAR(96)` NOT NULL | v1 聚合键 |
| `fingerprint` | `CHAR(64)` NOT NULL | 对应 v1 指纹 |
| `aggregation_rule_version` | `VARCHAR(32)` NOT NULL | v1 为 `alert-incident-v1` |
| `aggregation_window_seconds` | `INTEGER` NOT NULL | 本 Incident 实际窗口，默认 1800 |
| `episode_start_at` | `TIMESTAMPTZ` NOT NULL | anchor Alert 的 `receivedAt` |
| `episode_anchor_alert_id` | `VARCHAR(20)` NOT NULL FK | 同时间以最小 `alertId` 决胜的 anchor；该 Alert 可尚未成功处理或处于 `FAILED` |
| `first_alert_received_at` | `TIMESTAMPTZ` NOT NULL | 所有关联 Alert 最小值 |
| `last_alert_received_at` | `TIMESTAMPTZ` NOT NULL | 所有关联 Alert 最大值 |
| `alert_count` | `INTEGER` NOT NULL | 唯一关联 Alert 数 |
| `lifecycle_state` | `VARCHAR(24)` NOT NULL | `OPEN/ACKNOWLEDGED/MITIGATING/RESOLVED/CLOSED` |
| `lifecycle_sealed_at` | `TIMESTAMPTZ` NULL | 首次进入 `RESOLVED` 时在同键锁内分配，不早于此前已提交同键 `received_at`；之后永不清空或移动 |
| `aggregation_slot_active` | `BOOLEAN` NOT NULL | 仅用于并发仲裁，不作为对外资格真值 |
| `born_sealed` | `BOOLEAN` NOT NULL | 历史 episode 创建时窗口已过则为 true |
| `created_event_id` | `VARCHAR(20)` NOT NULL UNIQUE | `INCIDENT_CREATED` Timeline 与 Outbox 复用的事件 ID |
| `timeline_seq` | `BIGINT` NOT NULL | 已分配的最后 Timeline 序号；新 Incident 的创建与 anchor 关联各占一个事实，因此首次事务后为 2 |
| `version` | `BIGINT` NOT NULL | 聚合摘要或生命周期变化均递增 |
| `created_at` | `TIMESTAMPTZ` NOT NULL | 创建可信时间 |
| `updated_at` | `TIMESTAMPTZ` NOT NULL | 最近业务更新可信时间 |

### 约束与索引

- `UNIQUE (aggregation_key, episode_start_at, episode_anchor_alert_id)`：同一 episode 创建竞争收敛。
- `UNIQUE (aggregation_key) WHERE aggregation_slot_active=true`：每键最多一个并发仲裁位。
- `CHECK (aggregation_window_seconds > 0 AND alert_count > 0 AND timeline_seq >= 1)`。
- `lifecycle_sealed_at` 为空仅允许状态 `OPEN/ACKNOWLEDGED/MITIGATING`；进入 `RESOLVED/CLOSED` 后必须非空。重新打开为 `ACKNOWLEDGED` 时仍保持非空。
- `aggregation_slot_active=true` 要求 `born_sealed=false` 且 `lifecycle_sealed_at IS NULL`；反向不成立，因为窗口可能已过但尚待下一聚合事务关闭仲裁位。
- 查询索引：`(aggregation_key, episode_start_at, episode_anchor_alert_id)`、`lifecycle_state`、`updated_at`。

### 聚合资格

对外 `aggregationOpen` 不直接返回 `aggregation_slot_active`，而在数据库可信当前时间下计算：

```text
lifecycle_sealed_at IS NULL
AND born_sealed = false
AND dbNow <= episodeLatestReceivedAt + aggregation_window_seconds
```

`episodeLatestReceivedAt` 是按当前持久化同键 Alert 集合与生命周期边界重算的该 episode 最大 `received_at`，包括 `RECEIVED`、`FAILED` 和 `PROCESSED`；它不是 Incident 行中仅汇总已关联 Alert 的 `last_alert_received_at`。窗口边界包含在内。`aggregation_slot_active` 是写入仲裁位：处理新 Alert 时若发现其指向的 Incident 已不满足上述条件，必须在同一事务内将该位设为 false，再为新 episode 竞争新位。纯时间流逝不追加 Timeline。

### 生命周期转换

| 起始 | 目标 | 数据要求 |
| --- | --- | --- |
| `OPEN` | `ACKNOWLEDGED` | 授权响应人员 |
| `OPEN` | `RESOLVED` | 响应人员 + 解决原因；首次设置 `lifecycle_sealed_at`，关闭仲裁位 |
| `ACKNOWLEDGED` | `MITIGATING` | 响应人员；Spec B 后可允许授权处置服务 |
| `ACKNOWLEDGED` | `RESOLVED` | 响应人员 + 解决原因；首次封存 |
| `MITIGATING` | `ACKNOWLEDGED` | 响应人员 + 停止/失败原因 |
| `MITIGATING` | `RESOLVED` | 响应人员 + 解决原因；首次封存 |
| `RESOLVED` | `ACKNOWLEDGED` | 响应人员 + 重开原因 + 不存在较新未关闭 Incident；不清除封存、不恢复仲裁位 |
| `RESOLVED` | `CLOSED` | 响应人员 |

其余转换均拒绝且不写 Timeline。`CLOSED` 为终态。每个命令都比较预期状态和 `version`。

“较新”严格按 `(episode_start_at, episode_anchor_alert_id)` 升序比较；`RESOLVED` 属于未关闭，`CLOSED` 才是关闭。

## 5. `t_incident_timeline`

只追加保存 Incident 业务历史。

| 字段 | 类型/空值 | 规则 |
| --- | --- | --- |
| `id` | `VARCHAR(20)` PK | 对创建/关联事实可直接使用稳定事件 ID |
| `incident_id` | `VARCHAR(20)` NOT NULL FK | 所属 Incident |
| `sequence_no` | `BIGINT` NOT NULL | Incident 内从 1 递增 |
| `business_event_key` | `VARCHAR(180)` NOT NULL UNIQUE | 重试防重键 |
| `event_type` | `VARCHAR(48)` NOT NULL | 仅规格列出的五种类型 |
| `actor_type` | `VARCHAR(24)` NOT NULL | `SOURCE/SYSTEM/USER/SERVICE` |
| `actor_id` | `VARCHAR(128)` NOT NULL | 稳定操作者 ID |
| `occurred_at` | `TIMESTAMPTZ` NOT NULL | 可信业务发生时间 |
| `correlation_id` | `VARCHAR(20)` NOT NULL | 关联请求/Alert 的 correlation ID |
| `related_entity_type` | `VARCHAR(24)` NULL | `ALERT/INCIDENT` |
| `related_entity_id` | `VARCHAR(20)` NULL | 关联实体 ID |
| `summary_json` | `JSONB` NOT NULL | 版本化白名单安全摘要 |
| `created_at` | `TIMESTAMPTZ` NOT NULL | 写入时间 |

约束：`UNIQUE (incident_id, sequence_no)`；`event_type` 仅允许 `INCIDENT_CREATED`、`ALERT_ATTACHED`、`LATE_ALERT_ATTACHED_AFTER_SEAL`、`INCIDENT_AGGREGATION_SEALED`、`INCIDENT_STATUS_CHANGED`。

稳定业务键：

- Incident 创建：`incident:{incidentId}:created`
- Alert 关联：`incident:{incidentId}:alert:{alertId}:attached`，无论 Timeline 类型是否为 late 均不变
- 生命周期首次封存：`incident:{incidentId}:aggregation-sealed`
- 状态转换：`incident:{incidentId}:transition:{requestId}`

## 6. `t_incident_outbox`

保存同事务产生、提交后投递的领域事件。

| 字段 | 类型/空值 | 规则 |
| --- | --- | --- |
| `event_id` | `VARCHAR(20)` PK | 稳定事件 ID；发布重试不变 |
| `business_event_key` | `VARCHAR(180)` NOT NULL UNIQUE | `incident.created` 或 Alert 关联业务键 |
| `incident_id` | `VARCHAR(20)` NOT NULL FK | 聚合根 |
| `aggregate_version` | `BIGINT` NOT NULL | 产生事件后的 Incident 版本 |
| `event_type` | `VARCHAR(64)` NOT NULL | `incident.created/incident.alert-attached` |
| `schema_version` | `SMALLINT` NOT NULL | 初始为 1 |
| `correlation_id` | `VARCHAR(20)` NOT NULL | 关联 correlation ID |
| `occurred_at` | `TIMESTAMPTZ` NOT NULL | 对应业务事实时间 |
| `payload_json` | `JSONB` NOT NULL | 符合事件契约的安全负载 |
| `publish_status` | `VARCHAR(16)` NOT NULL | `PENDING/PUBLISHED` |
| `attempt_count` | `INTEGER` NOT NULL | 发布尝试次数 |
| `next_attempt_at` | `TIMESTAMPTZ` NULL | PENDING 下次到期时间 |
| `claim_token` | `VARCHAR(64)` NULL | Relay 短期领取令牌 |
| `claim_until` | `TIMESTAMPTZ` NULL | 崩溃恢复租约；过期可重新领取 |
| `published_at` | `TIMESTAMPTZ` NULL | 首次确认 `SEND_OK` 后写入 |
| `broker_message_id` | `VARCHAR(128)` NULL | 安全 Broker 回执标识，仅诊断 |
| `last_error_code` | `VARCHAR(64)` NULL | 安全错误码 |
| `last_error_summary` | `VARCHAR(512)` NULL | 安全摘要 |
| `created_at` | `TIMESTAMPTZ` NOT NULL | 同领域事务写入时间 |
| `updated_at` | `TIMESTAMPTZ` NOT NULL | 最近发布控制更新 |

约束与索引：

- `PUBLISHED` 必须有 `published_at` 且不得再回到 `PENDING`。
- `PENDING` 必须有 `next_attempt_at`；claim token 与 claim until 必须同时为空或同时非空。
- 领取索引：`(next_attempt_at, created_at, event_id) WHERE publish_status='PENDING'`。
- 清理索引：`published_at WHERE publish_status='PUBLISHED'`；仅已发布且超过默认七天的行允许由受控作业分批删除。
- 发布退避上限 15 分钟，PENDING 永不因尝试次数自动丢弃。

## 7. 关系

```text
t_incident_alert 1 ── * t_incident_alert_audit
t_incident       1 ── * t_incident_alert
t_incident       1 ── * t_incident_timeline
t_incident       1 ── * t_incident_outbox
```

- Alert 接收时 `incident_id` 为空；处理成功后恰好一次变为非空。
- 每个 Incident 至少有一个已关联且成功处理的 Alert；anchor 必须属于其持久化 episode，但可为尚未处理或终态 `FAILED` 的未关联 Alert。
- Alert 审计不依赖 Incident，因此接收和终态失败可在尚未关联时记录。
- Timeline 与 Outbox 共享稳定业务事件身份，但保留各自的主键、保留期限和职责。

## 8. 原子事务

### T1：首次接收

1. 在事务外完成认证、字节/JSON/字段校验和规范摘要。
2. 在事务外确定规范指纹及聚合键；事务内按该键取得 PostgreSQL advisory transaction lock，持有至提交。
3. 在锁内查询同键已提交 Alert 最大 `received_at` 与 Incident 最大 `lifecycle_sealed_at`。没有前序事实时取数据库当前墙钟；否则取墙钟与前序最大值后一个微秒中的较大者，检查 `TIMESTAMPTZ` 范围，作为新 `received_at` 并插入 Alert。
4. 同事务插入 `ALERT_RECEIVED` 审计事实。
5. 唯一冲突时回滚该插入路径，读取既有行后按 digest 返回重复或冲突；既有 Alert 的 `received_at` 不变。

提交保证：Alert 与接收审计同时存在或同时不存在。

### T2：Incident 创建或 Alert 关联

1. 按到期顺序只读发现 RECEIVED 候选，先取得候选的同键事务锁；再用 `FOR UPDATE SKIP LOCKED` 领取该 Alert，重新校验当前状态与到期资格。
2. 在同键锁内查询全部已接收集合（含终态 `FAILED`）和适用封存边界，确定 episode；仅当本次 Alert 成功处理时才创建或关联 Incident。
3. 锁定/创建 Incident，并通过 episode 唯一键与活动位条件唯一索引解决竞争。
4. 条件更新 Alert 的关联和 `PROCESSED` 状态。
5. 更新 Incident 首末时间、计数、版本与 Timeline 序号。
6. 插入创建/关联 Timeline 和对应 Outbox；若为失败后恢复，同事务插入 `ALERT_RECOVERED` 审计。

提交保证：关联、Incident 摘要、Timeline、Outbox 和 Alert 状态全部可见或全部不可见。

### T3：处理失败

- 聚合事务失败后，以新短事务比较 Alert 当前状态与版本。
- 仍可重试时只更新当前重试控制数据，不写逐次审计。
- 首次尝试加最多五次自动重试均耗尽时，原子更新 `FAILED` 并插入一个当前 epoch 的终态失败审计事实。

### T4：人工重放

- 锁定已知 Alert，校验人员权限、`FAILED`、预期版本和非空原因。
- 接受：递增 epoch/version，重置当前失败与重试控制，改为 `RECEIVED`，插入接受事实。
- 拒绝：状态不变，插入以本次 `requestId` 防重的拒绝事实；返回当前安全状态和版本。

### T5：Incident 状态转换

- 取得同键事务锁后锁定 Incident 或执行状态+版本条件更新，校验完整转换表。
- 首次进入 `RESOLVED` 时，以数据库当前墙钟与已提交同键 Alert 最大 `received_at` 的较大者写不可变 `lifecycle_sealed_at`、关闭活动位，并追加 `INCIDENT_AGGREGATION_SEALED`；提交后下一条同键接收必须取得更晚的 `received_at`。
- 追加 `INCIDENT_STATUS_CHANGED`，更新序号/版本；没有 Outbox 事件，因为 Spec A 只规定创建和关联事件。
- 任一校验或写入失败则整体回滚。

### T6：Outbox 发布

1. 领取事务用 `SKIP LOCKED` 设置 claim 后提交。
2. 事务外发布，只有 `SEND_OK` 算成功。
3. 完成事务用 `event_id + claim_token + PENDING` 条件标记发布；失败则记录安全摘要并安排不超过 15 分钟的下次尝试。
4. claim 过期后可重新领取；重复发布保留同一 `eventId`。

## 9. 必须由 PostgreSQL 集成测试证明的性质

- 并发幂等提交只产生一条 Alert 和一条接收事实。
- episode 唯一键和条件唯一索引在多 Worker 下只产生一个目标 Incident/活动位。
- 同键接收事务的时钟同值/回拨、接收与首次解决交错时，`received_at` 严格递增、`lifecycle_sealed_at` 不早于前序接收，anchor 和封存归属不事后逆转；不同键不错误共享业务排序时间。
- Alert 关联不能迁移或重复；事件业务键不能重复。
- T2/T5 任一注入失败不会留下部分 Alert、Incident、Timeline 或 Outbox。
- Timeline/审计普通角色不能更新或删除。
- `SKIP LOCKED` 允许并行领取不同工作，崩溃/回滚后工作可再次发现。
- Outbox claim 过期可恢复，发送成功但标记失败会以相同 `eventId` 重投。
