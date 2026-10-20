# 功能规格：可靠告警到 Incident

**功能分支**：`feature/incident-platform`

**创建日期**：2026-09-22

**状态**：v1.0 基线（2026-09-23 确认）

**输入**：用户描述：“SPC-001 规格 A：定义一条可独立验收的可靠链路，从经过认证的告警 Webhook 开始，覆盖持久化异步处理、确定性告警聚合、Incident 生命周期、只追加 Timeline、Transactional Outbox、恢复、重放和可复现证据。”

## 范围

本功能首先向告警源提供可信确认，证明告警已经被持久化接收；随后，即使出现重复请求、Worker 重启、并发告警竞争或下游事件发布不可用，也能为值班人员提供确定的 Incident 和只追加记录。

范围内：

- 一份版本化的机器到机器告警 Webhook 契约；
- 来源认证、签名时效、载荷限制、校验和安全错误响应；
- 持久化幂等告警接收和状态查询；
- 异步告警处理、重试耗尽、授权人工重放和重启恢复；
- 版本化告警指纹、30 分钟滑动聚合规则，以及确定性的 Incident 创建或关联；
- Incident 主状态机和受保护的状态转换；
- 只追加 Timeline 事实和事务提交后的领域事件发布；
- 并发、故障恢复和可复现验收证据。

范围外：

- Runbook 检索、RAG 诊断、诊断持久化和评测；
- Incident Action、审批、执行票据、MCP 调用、执行回执、对账或模拟处置；
- 对真实服务器、Kubernetes、数据库、云或其他基础设施的变更；
- UI 工作、额外告警源适配器、多租户、通用工作流或 IAM 平台，以及推测性基础设施；
- 实现代码、数据库迁移和前端实现。

## 澄清

### 会话 2026-09-22

- Q：Spec A 是否应保留每次处理失败和重试调度的不可变审计历史，从而以新决策替代 DEC-010 的“初期不建立逐次 Attempt 历史表”？ → A：否。遵循 DEC-010，不新增 ADR；只追加 Alert 审计事实不记录逐次处理尝试失败或重试调度。
- Q：是否应保留机器告警源的状态查询，并新增 DEC-014，要求 HMAC 签名同时绑定 HTTP 方法和规范化目标路径？ → A：是。保留机器查询；所有来源 HMAC 请求的版本化签名输入必须绑定方法、目标路径（包含 `alertId`）和请求体。

### 会话 2026-09-23

- Q：Incident 首次进入 `RESOLVED` 时，Timeline 应增加两条独立事实，还是仍限制为一条事实？ → A：保留两条独立事实：一条 `INCIDENT_STATUS_CHANGED` 和一条 `INCIDENT_AGGREGATION_SEALED`；其他成功转换只增加状态变更事实。
- Q：重试耗尽而仍为 `FAILED` 的 Alert 是否必须已有 Incident 关联？ → A：不必须。所有已持久化 Alert（包括 `FAILED`）都参与 episode 划分；至少一条 Alert 成功处理后，该 episode 才形成唯一 Incident。只有 `PROCESSED` Alert 必须恰好关联一次；`FAILED` Alert 可保持无关联，授权重放成功后再按原 episode 关联。
- Q：同键并发接收取得相同或回拨的数据库时间、且 `alertId` 不按提交顺序递增时，如何保持 episode anchor 和生命周期边界稳定？ → A：在同一聚合键的持久化边界保证接收与首次解决操作的可信排序时间单调。后提交的接收必须晚于此前同键接收和封存边界；首次解决的封存时间不得早于此前同键接收。具体仲裁方式写入计划和 DEC-016，不依赖 Snowflake ID 顺序。

## 术语与排序规则

- **可信接收端时间**：由 Incident 持久化数据边界分配并在同一聚合键上保持单调的业务排序时间，而不是告警源时间、Worker 进程时钟或未经单调化的数据库墙钟。墙钟回拨或同一时刻并发接收不得使新业务事实在同键时间线中倒退。
- **`receivedAt`**：Alert 首次被持久化接收时分配的可信接收端时间；同键后提交接收的值必须严格晚于此前已提交接收及生命周期封存边界，重试和重放均不得修改它。
- **规范接收顺序**：同一聚合键的 Alert 按 `(receivedAt, alertId)` 排序；接收时间相同时，以 `alertId` 作为确定性决胜条件。
- **聚合窗口**：随 Incident 保存的实际生效时长；v1 默认为 30 分钟，并包含精确边界。
- **间隔分段**：按规范接收顺序形成的极大序列，其中每两个相邻 Alert 的接收时间差均不大于实际聚合窗口。
- **生命周期封存边界**：Incident 首次进入 `RESOLVED` 时记录的可信 `sealedAt` 时间；它不得早于此前已提交的同键 Alert 接收时间。该边界位于所有 `receivedAt` 小于或等于 `sealedAt` 的 Alert 之后；`CLOSED` 以及后续重新打开或再次解决，不得为该 Incident 创建或移除其他边界。
- **Incident episode（事件分段）**：按大于实际窗口的时间间隔或生命周期封存边界切分所有已持久化同键 Alert 的规范接收顺序后得到的极大分段，包含尚未处理或终态失败的 Alert。至少一条成员成功处理后，该 episode 才由恰好一个 Incident 表示；全部成员尚未成功处理时，可以暂无 Incident。
- **聚合开放 Incident**：其 episode 不存在生命周期封存，并且该 episode 中最新持久化 Alert 的 `receivedAt` 加实际窗口尚未到期的 Incident，不论每个成员是否已经处理完成。同一聚合键最多只能存在一个聚合开放 Incident。
- **已封存 Incident**：不能接收位于其时间间隔或生命周期边界之后才接收的 Alert；对于已经在其 episode 内持久化接收、但延迟处理的 Alert，仍可完成关联。
- **创建即封存 Incident**：其完整历史 episode 的窗口已经到期后才首次创建的 Incident；其生命周期状态从 `OPEN` 开始，但从不处于聚合开放状态。
- **未关闭 Incident**：生命周期状态不是 `CLOSED` 的任何 Incident，包括 `RESOLVED`。
- **episode 起点与 anchor**：`episodeStartAt` 等于该 episode 中 anchor（规范接收顺序首个 Alert）的 `receivedAt`，`episodeAnchorAlertId` 等于该 Alert 的 `alertId`。
- **较新 Incident**：对于同一聚合键，`(episodeStartAt, episodeAnchorAlertId)` 二元组较大的 Incident。
- **稳定关联事件身份**：同一 `incidentId` 与 `alertId` 的持久化关联始终使用同一业务事件身份；关联提交时选择 `ALERT_ATTACHED` 或 `LATE_ALERT_ATTACHED_AFTER_SEAL` 作为 Timeline 类型，不得改变该身份。
- **`requestId` 与 `correlationId`**：`requestId` 对每次请求尝试唯一，出现在每个响应中用于诊断；`correlationId` 只在首次持久化接收时分配，在 Alert 及其形成的 Incident 流程中保持稳定，并且只有在已知存在持久化 Alert 时才返回。

## 用户场景与测试（必填）

### 用户故事 1 - 可靠提交告警（优先级：P1）

作为经过授权的告警源，我可以提交受限且已签名的 v1 告警，并获得能够区分“已持久化接收”和“Incident 处理完成”的真实状态，从而保证重试不会创建第二条逻辑告警，也不会掩盖处理失败。

**优先级理由**：只有系统能够无丢失、无重复地认证、校验、持久化并识别告警，后续 Incident 行为才可信。

**独立测试**：在不运行聚合 Worker 的情况下，提交有效、重复、冲突、签名过期、超限、畸形以及暂时无法持久化的请求，验证响应矩阵和持久化 Alert 数量。

**验收场景**：

1. **Given** 已注册的来源和有效签名的 v1 请求，**When** Alert 首次被持久化接收，**Then** 响应为 `202`，包含稳定的 `alertId`、`correlationId` 和 `RECEIVED` 处理状态，并且不声称 Incident 已经存在。
2. **Given** 同一来源和外部告警 ID 已经标识一条内容相同的 `RECEIVED` Alert，**When** 来源重试该请求，**Then** 响应为 `202` 并返回相同的 `alertId`，且不得创建第二条 Alert。
3. **Given** 同一来源和外部告警 ID 已经标识一条内容相同的 `PROCESSED` 或 `FAILED` Alert，**When** 来源重试该请求，**Then** 响应为 `200`，公开真实处理状态并返回相同的持久化标识。
4. **Given** 同一来源和外部告警 ID 已经对应不同的已接收业务载荷，**When** 再次提交请求，**Then** 响应为 `409`，原 Alert 保持不变，且不得调度替代工作。
5. **Given** 认证、时效、大小、结构、媒体类型或契约校验失败，**When** 来源提交请求，**Then** 请求在持久化前按照规定的非成功响应被拒绝，并且不得泄露秘密或签名细节。
6. **Given** 提交前发生暂时性持久化故障，**When** 提交有效请求，**Then** 响应为 `503`，不得声称任何 `alertId` 已被接收，后续安全重试必须能够只创建一条 Alert。
7. **Given** 一条 Alert 已存在，**When** 其所属机器来源或授权人员按 `alertId` 查询，**Then** 调用方获得安全的当前状态视图；缺少认证、跨来源访问、人员权限不足和未知标识均按查询契约处理，不得泄露其他来源的 Alert。

---

### 用户故事 2 - 确定性形成 Incident（优先级：P2）

作为值班人员，我可以确信代表相同来源、规则、环境、服务和资源的 Alert 会被聚合到可预测的 Incident episode，而无关或时间上分离的 Alert 会保持分离。

**优先级理由**：确定性聚合将已接收 Alert 转化为有用的运维工作，同时避免并发、来源时钟偏差或重试顺序改变业务结果。

**独立测试**：使用受控的可信接收时间和标识准备已接收 Alert，固定各 Alert 的成功或终态失败结果，以不同物理调度运行一个或多个 Worker，验证 episode 分段、已实体化 Incident、成功 Alert 的关联、计数和稳定业务事件身份相同。关联提交与生命周期封存竞争时，Timeline 类型和相对顺序可以反映实际提交顺序，但同一关联不得产生第二个身份或重复事实。

**验收场景**：

1. **Given** 一条 Alert 属于尚无 Incident 表示的 episode，**When** 该 Alert 成功处理，**Then** 为该 episode 恰好创建一个生命周期状态为 `OPEN` 的 Incident，该 Alert 只关联一次，创建和关联事实以原子方式记录，而聚合资格独立计算。
2. **Given** 一条 Alert 属于已有 Incident 的 episode，**When** 该 Alert 成功处理，**Then** 即使物理处理发生延迟，也只将它关联到该 Incident 一次，并将 Incident 的首个和末个接收时间分别更新为所有已关联 Alert 的最小值和最大值。
3. **Given** 一条 Alert 与前一条同键 Alert 的接收时间相差超过 30 分钟，或者它在适用的生命周期封存边界之后才被接收，**When** 该 Alert 成功处理，**Then** 它属于新的 episode 并形成新的 `OPEN` Incident；如果该历史 episode 的窗口已经到期，则 Incident 创建即封存。
4. **Given** 两条 Alert 的任一指纹字段不同，**When** 处理它们，**Then** 不得将其自动聚合到同一 Incident。
5. **Given** Alert 的物理处理顺序错乱、在窗口时长后重试，或者携带偏差的来源发生时间，**When** 处理最终收敛，**Then** 聚合结果由规范接收顺序和相同的生命周期封存序列决定，来源时间戳仅作为证据保留。
6. **Given** 多个并发 Worker 处理同一新 episode 且各 Alert 均成功处理，**When** 它们竞争创建或关联，**Then** 最终业务结果只包含该 episode 的一个 Incident、每条 Alert 的一个关联、无重复 Timeline 或领域事件，并且该键不存在第二个聚合开放 Incident。
7. **Given** Alert B 在 Incident I 被解决前已经接收，但在解决后才处理，**When** B 属于 I 的封存前 episode，**Then** B 关联到 I 而不重新打开 I；封存后才接收的 Alert 不得关联到 I。

---

### 用户故事 3 - 控制 Incident 生命周期（优先级：P3）

作为经过授权的事件响应人员，我只能通过批准的状态转换确认、缓解、解决、重新打开和关闭 Incident；每次被接受或拒绝的并发尝试都必须保留最新状态和可审计的 Timeline。

**优先级理由**：完整状态机是告警处理与后续“审批到处置”功能之间的共同语言；静默状态覆盖会同时破坏审计和自动化。

**独立测试**：创建具有固定版本的 Incident，尝试每个允许转换以及有代表性的禁止或过期转换，验证状态、版本、操作者、原因要求、冲突响应和 Timeline 顺序。

**验收场景**：

1. **Given** 一个 `OPEN` Incident，**When** 授权响应人员使用当前版本确认它，**Then** 它变为 `ACKNOWLEDGED`，并追加一条状态变更事实。
2. **Given** 一个 `ACKNOWLEDGED` Incident，**When** 缓解开始，**Then** 它变为 `MITIGATING`；当缓解停止但尚未解决时，只有提供原因才能返回 `ACKNOWLEDGED`。
3. **Given** 一个 `OPEN`、`ACKNOWLEDGED` 或 `MITIGATING` Incident，**When** 授权响应人员记录解决原因，**Then** 它变为 `RESOLVED`；由此形成的封存边界之后才接收的 Alert 不得关联，而封存前成员的延迟处理不得重新打开 Incident。
4. **Given** 一个 `RESOLVED` Incident，且同一聚合键不存在较新的未关闭 Incident，**When** 授权响应人员提供重新打开原因，**Then** 它变为 `ACKNOWLEDGED`，但不恢复聚合资格；否则拒绝重新打开且不修改任何 Incident。
5. **Given** 一个 `RESOLVED` Incident，**When** 授权响应人员使用当前版本完成归档，**Then** 它变为 `CLOSED`；`CLOSED` Incident 拒绝后续所有状态转换。
6. **Given** 两名响应人员并发操作同一版本，**When** 其中一个转换先成功，**Then** 另一个收到包含当前状态的冲突响应，且不得发生过期覆盖或重复 Timeline 事实。

---

### 用户故事 4 - 恢复并证明结果（优先级：P4）

作为操作人员或评审者，我可以恢复失败的告警处理、观察延迟的事件发布，并重复运行固定验收场景，在不依赖瞬时日志的情况下证明哪些内容已被接收、聚合、转换和发布。

**优先级理由**：重启和故障恢复使成功路径成为可靠系统，而可复现证据能够防止未经验证的可靠性声明。

**独立测试**：在隔离环境中运行已定义的故障目录，包括接收后崩溃、Worker 失败、重放竞争、事件发布失败和重启，然后将持久化结果及证据清单与预期结果比较。

**验收场景**：

1. **Given** `202` 响应已提交且进程在聚合前停止，**When** 重启后恢复处理，**Then** Alert 仍可被发现，并且无需来源重新提交即可得到一个 `PROCESSED` 结果。
2. **Given** 告警处理发生暂时性失败，**When** 执行自动重试，**Then** Alert 按实际重试计划执行——默认重试五次——并且只处理成功一次或以安全失败摘要进入 `FAILED`。
3. **Given** 一条 Alert 为 `FAILED`，**When** 授权操作人员提供重放原因和当前版本，**Then** 同一 Alert 在不改变身份或载荷的情况下重新调度，追加一条重放已接受审计事实，并且重复重放请求或重放与 Worker 的竞争仍只产生一个业务结果。
4. **Given** 一条 Alert 不是 `FAILED`，**When** 已认证且已授权的操作人员请求重放，**Then** 拒绝该请求，Alert 处理状态保持不变，并追加一条安全的重放拒绝审计事实。
5. **Given** Incident 工作提交后领域事件发布不可用，**When** 发布能力随后恢复，**Then** 待发布事件最终使用原始稳定事件 ID 发布，Incident 处理既不回滚也不重复。
6. **Given** 验收目录和干净的隔离状态，**When** 重复运行该目录，**Then** 每次运行都生成足以复现和比较结果的清单、逐场景观察、持久化记录数量、Timeline 顺序和事件 ID。
7. **Given** 预置的 Alert 与发布积压以及有代表性的秘密和不安全内容探针，**When** 检查运行状态、响应、Timeline 事实、Alert 审计事实和捕获日志，**Then** 报告计数与持久化状态一致，并且不暴露任何禁止内容。
8. **Given** 某 episode 的所有 Alert 均在重试耗尽后保持 `FAILED`，**When** 检查最终状态，**Then** 这些 Alert 保留可查询的失败和审计事实、继续参与 episode 划分，但在任何成员成功处理前可以没有 Incident 或关联。

### 边界情况

- 对超过配置字节限制的请求体计算了有效签名：在 JSON 解析或持久化前按大小拒绝。
- `Content-Length` 缺失、不正确或小于实际接收字节数：仍以实际字节数为准。
- JSON 语法有效，但超过最大深度、顶层为数组或标量、包含未知 v1 字段或违反字段边界：契约校验在持久化前拒绝它。
- 请求时间戳恰好位于任一配置时效边界：边界包含在允许范围内；超出边界的值被拒绝。
- 密钥轮换期间，同一来源有两个活动 key ID：配置重叠期内任一活动密钥均可认证；非活动或未知密钥认证失败，且请求体不能提供或修改来源身份。
- 使用不同的无意义 JSON 空白或对象键顺序重新发送同一已接收 JSON：仍属于幂等重复；任一已接收业务字段的规范形式变化均构成冲突。
- 可选 `labels` 字段缺失与 `{}`：两种形式等价；显式 `null`、存在但为空的 `summary` 以及重复 JSON 对象键均为无效 v1 请求。
- 来源 `occurredAt` 过旧或位于未来但格式有效：作为来源证据保留，不得改变可信聚合顺序。
- 隔离验收数据人为预置两条同键 Alert 具有相同可信接收时间：以稳定 `alertId` 顺序作为确定性决胜条件；正常接收路径必须避免产生这种同键并列。
- 新 Alert 恰好在 episode 中前一条 Alert 的 30 分钟后到达：加入该 episode；任何超过边界的正时间差都会启动新 episode。
- Alert 恰好在生命周期 `sealedAt` 时接收但稍后处理：属于封存前 episode；只有更晚的 `receivedAt` 才跨越封存边界。
- 积压导致旧 episode 在较新的聚合开放 episode 已存在后才处理：旧 Incident 创建即封存，不违反聚合开放唯一性。
- Incident 解决与 Alert 关联发生竞争：受保护的解决操作确定 `sealedAt`；重新判断后，在该时间或之前接收的 Alert 可以关联到已封存 Incident，更晚接收的 Alert 必须进入新 episode。
- 较旧的已解决 Incident 在同键存在较新的未关闭 Incident 时尝试重新打开：按照已定义 episode 顺序拒绝；重新打开永远不移除原聚合封存。
- 处理已提交，但 Worker 在观察成功前崩溃：重新处理收敛到现有关联、Timeline 事实和事件，不得重复创建。
- 事件发布成功，但标记已发布失败：稳定 `eventId` 可以再次投递，并且仍可识别为同一事件。
- 自动最终尝试仍在完成时，请求重放已失败 Alert：受保护的状态变更确保只有实际当前状态决定是否接受重放。

## 需求（必填）

### 版本化告警契约

v1 Webhook 通过 `POST /incident/v1/alerts` 表示一条正在触发的告警通知。准确线协议 Schema 属于后续契约产物，但业务契约、参与者、状态语义和校验优先级在此固定。

**认证请求元数据**：

| 字段 | v1 规则 |
| --- | --- |
| `protocolVersion` | 必填；必须为 `1`；计入签名输入 |
| `sourceId` | 必填的已注册机器来源；从认证元数据取得，绝不信任请求体中的值 |
| `keyId` | 必填的来源活动密钥标识；支持受控轮换 |
| `requestTimestamp` | 必填的已签名时间戳；相对于接收端可信时间，默认在前后各五分钟的包含边界窗口内接受 |
| `signature` | 必填；对版本化、无歧义的签名输入执行 HMAC-SHA256，该输入覆盖协议版本、来源、时间戳、规范化 HTTP 方法、规范化目标路径和实际接收的请求体字节 |

所有使用来源 HMAC 认证的 v1 请求都必须使用相同的版本化签名规则。HTTP 方法规范化为大写；目标路径规范化为契约定义的路由路径，并保留影响授权的目标标识；状态查询中的 `alertId` 必须参与签名。准确字节编码、字段分隔和路径规范化由契约产物固化，不得使用有歧义的字符串拼接。无请求体请求使用确定的空字节序列；Webhook 必须签署实际接收的原始请求体字节，不得对重新序列化的 JSON 验签。

**请求体**：

| 字段 | v1 规则 |
| --- | --- |
| `externalAlertId` | 必填的来源内不透明标识，1～128 个字符；重试时保持稳定 |
| `eventType` | 必填；v1 中必须为 `FIRING` |
| `occurredAt` | 必填且带显式时区偏移的时间戳；作为来源证据保留 |
| `ruleId` | 必填的稳定机器规则标识，1～128 个字符 |
| `title` | 必填的人类可读标题，1～256 个字符 |
| `severity` | 必填：`CRITICAL`、`HIGH`、`MEDIUM` 或 `LOW` |
| `environment` | 必填的环境标识，1～64 个字符 |
| `service` | 必填的服务标识，1～128 个字符 |
| `resource.type` | 必填的资源类型标识，1～64 个字符 |
| `resource.id` | 必填的不透明资源标识，1～256 个字符 |
| `summary` | 可选的安全摘要，最多 2,048 个字符 |
| `labels` | 可选的扁平映射，最多 32 对字符串；键为 1～64 个字符，值最多 256 个字符且允许为空字符串 |

请求体必须是 UTF-8 JSON 对象，只能使用上述 v1 字段，实际字节数不得超过默认 64 KiB，最大嵌套深度不得超过 16，并且不得包含任意无界扩展对象。重复对象键和显式 `null` 均无效。限制可以配置，但不得静默削弱默认值。

字符串按以下顺序校验：UTF-8 解码后，先拒绝 Unicode 标量值数量已经超过字段限制的原始值；再将 Unicode 规范化为 NFC；去除受限字符串字段首尾的 Unicode 空白；最后要求每个必填字段、每个 label 键以及存在的 `summary` 保持非空且不超过限制。label 键和值都属于受限字符串字段并参与 NFC 与空白裁剪；label 值裁剪后允许为空，规范化后的重复 label 键必须拒绝。契约产物必须固定每类标识符允许的字符集。指纹大小写规则见 FR-016。缺失 `labels` 与空 labels 对象等价；其他字段的缺失与存在必须保持不同语义。

提交校验在多个失败同时存在时采用唯一确定的优先顺序：

1. 校验媒体类型是否受支持；
2. 读取请求体并同时强制执行实际字节限制；
3. 校验必需认证元数据、密钥活动状态、时间戳时效和签名；
4. 解析 JSON，同时强制执行 UTF-8、深度、重复键和顶层对象规则；
5. 校验协议版本、允许字段、规范化、边界和语义契约；
6. 判断持久化幂等性并执行持久化。

**成功响应**包含本次尝试的 `requestId`、`protocolVersion`、稳定的 `alertId`、首次接收时分配且保持稳定的 `correlationId`、`processingStatus` 和 `duplicate`。关联完成后还包含 `incidentId`。`FAILED` 响应还包含安全且机器可读的 `failureCode`。只有当前状态为 `FAILED` 时，`replayEligible` 才为 true；重放被接受后状态变为 `RECEIVED`，因此该值变为 false。成功响应绝不返回堆栈跟踪、原始秘密、签名材料或不安全载荷片段。

**错误响应**包含本次尝试的 `requestId`、机器可读的 `code`、安全的 `message` 和 `retryable`。除非响应明确描述一条已经识别的持久化 Alert，否则不得声称存在持久化 `correlationId`。

经过认证的来源提交触发 `409` 冲突时，响应 MUST 额外返回原有 `alertId`、`correlationId` 和当前 `processingStatus` 以便来源对账，但 MUST NOT 返回原载荷、载荷摘要或可用于推断其内容的差异详情。

| HTTP 状态 | 必需语义 |
| --- | --- |
| `200` | 对已有 `PROCESSED` 或 `FAILED` Alert 的相同重试 |
| `202` | 新的持久化接收，或对已有 `RECEIVED` Alert 的相同重试 |
| `400` | JSON 畸形或违反 JSON 结构限制 |
| `401` | 机器认证缺失、过期、使用非活动或未知密钥，或者认证无效；不公开具体原因 |
| `409` | 已有来源和外部告警 ID 对应不同的已接收业务载荷 |
| `413` | 实际请求字节超过配置限制 |
| `415` | 不支持的媒体类型 |
| `422` | 不支持的协议版本，或语法有效但违反 v1 字段契约的请求体 |
| `503` | 持久化接收前发生暂时性故障；可安全重试 |

**状态查询**使用 `GET /incident/v1/alerts/{alertId}`：

- 机器来源请求必须携带上述认证元数据，并将大写 `GET`、包含目标 `alertId` 的规范化路径和确定的空请求体字节序列纳入签名；签名不得跨方法、路径或目标 Alert 复用；
- 同一个已认证机器来源只能查询自己拥有的 Alert；所有权不匹配时按未找到报告，避免泄露其他来源的标识；
- 已认证人员能否查询目标 Alert 由现有授权能力给出的告警读取范围决定；范围内返回结果，范围外返回 `403`，本功能不新增按来源或 Incident 划分的通用权限模型；
- `200` 返回 `requestId`、`alertId`、`correlationId`、处理状态、重放资格、存在时的安全失败详情、重试元数据，以及已关联时的 `incidentId`；
- 缺少认证返回 `401`，标识格式错误返回 `400`，不存在或对来源隐藏的 Alert 返回 `404`，暂时性读取失败返回 `503`。

**人工重放**使用 `POST /incident/v1/alerts/{alertId}/replay`：

- 只有具有重放权限的已认证人员可以调用；机器来源不能重放；
- 请求必须声明预期的 `FAILED` 状态和当前 Alert 版本，并提供非空原因；
- 接受时返回 `202`，包含本次尝试的 `requestId`、相同的 `alertId` 和 `correlationId`、状态 `RECEIVED` 以及 `replayEligible=false`；
- 缺少认证返回 `401`，权限不足返回 `403`，Alert 不存在返回 `404`，原因无效返回 `422`，版本过期或任何非 `FAILED` 当前状态返回包含安全当前状态的 `409`，持久化接受重放前发生故障返回 `503`。

### 功能需求

#### 信任边界与接收

- **FR-001**：系统 MUST 按上述业务契约规定的路径和参与者，提供 v1 提交、状态查询和失败告警重放能力。
- **FR-002**：面向机器的提交和状态查询 MUST 独立于交互式用户登录认证已注册来源，从认证请求元数据推导 `sourceId`，并强制执行来源所有权；人员查询和重放 MUST 使用其契约规定的已认证用户和服务端权限判断。
- **FR-003**：所有来源 HMAC 请求的签名 MUST 使用 HMAC-SHA256 和版本化无歧义编码，覆盖协议版本、来源 ID、请求时间戳、大写 HTTP 方法、包含目标标识的规范化路径和实际接收的请求体字节；无请求体时 MUST 使用确定的空字节序列。任一已签名组成部分不匹配都 MUST 在返回业务数据或进入持久化事务前以 `401` 失败关闭。秘密材料 MUST 绝不进入告警业务数据、日志或响应。
- **FR-004**：相对于可信接收端时钟，默认时间戳容忍窗口 MUST 为包含边界的 ±5 分钟，并且可以配置。配置的轮换重叠期内，一个来源 MAY 拥有多个活动 key ID；非活动或未知密钥、窗口外时间戳以及无效认证 MUST 全部以 `401` 失败关闭，且不得回退到其他密钥。
- **FR-005**：系统 MUST 在开启告警持久化事务前，按照规定优先级强制执行媒体类型、实际字节限制、认证、JSON 结构和请求体契约，使每种复合失败都只有一个确定的响应类别。
- **FR-006**：请求体默认实际字节限制 MUST 为 64 KiB，默认最大 JSON 深度 MUST 为 16；系统 MUST 不得只依赖 `Content-Length`。
- **FR-007**：v1 请求体 MUST 遵守本规格和后续契约产物中的编码、重复键、null、规范化、可选字段、字符集和字段规则；未知 v1 字段和非字符串 label 值 MUST 被拒绝，不得作为无界载荷持久化。
- **FR-008**：认证和校验失败 MUST 遵循响应矩阵，并且 MUST NOT 创建 Alert、Incident、Timeline 事实、Alert 审计事实或领域事件。

#### 幂等与真实响应

- **FR-009**：持久化幂等身份 MUST 为经过认证的 `sourceId` 加 `externalAlertId`，并且在并发提交下 MUST 保持唯一。
- **FR-010**：载荷等价性 MUST 基于完整的已接收 v1 业务请求体，使用版本化规范形式计算。该规范形式拒绝重复键、对对象键排序、使用规范化后的已接收字符串值、将缺失 `labels` 与 `{}` 映射为同一空映射、使用独立缺失标记表示缺失 `summary`，并拒绝显式 null；无意义 JSON 空白和对象键顺序 MUST NOT 产生冲突。
- **FR-011**：相同重试 MUST 返回新的请求级 `requestId`，以及原始 `alertId`、首次接收 `correlationId`、实际 `processingStatus`、重放资格和存在时的 `incidentId`，且不得创建或重新调度工作。
- **FR-012**：已有幂等身份对应冲突载荷时 MUST 返回 `409`，包含原有 `alertId`、`correlationId` 和当前 `processingStatus`，并且 MUST NOT 返回或覆盖原始请求体、摘要、状态、失败证据或 Incident 关联。
- **FR-013**：`202` MUST 只表示告警已持久化且可恢复；MUST NOT 暗示 Incident 处理或下游发布已经完成。
- **FR-014**：持久化提交前的失败 MUST 返回 `503` 且不暴露已接收标识；后续有效重试 MUST 能够恰好创建一条 Alert。

#### 指纹与聚合

- **FR-015**：Alert 处理 MUST 从以下有序身份元组推导版本化指纹：`alert-fingerprint-v1`、经过认证的 `sourceId`、`environment`、`service`、`resource.type`、`resource.id` 和 `ruleId`。
- **FR-016**：指纹字段 MUST 先通过 v1 契约的原始长度和 Unicode 规则，再使用经过 NFC 规范化和 Unicode 空白裁剪后的值。`sourceId`、`environment`、`service`、`resource.type` 和 `ruleId` MUST 使用与区域设置无关的小写形式，不透明 `resource.id` MUST 保持大小写敏感；规范化后为空的值 MUST 被拒绝。
- **FR-017**：告警指纹 MUST 为该元组无歧义版本化编码的 SHA-256 小写十六进制摘要；v1 `aggregationKey` MUST 为 `alert-incident-v1:` 后接该指纹。
- **FR-018**：`externalAlertId`、发生时间、标题、摘要、严重度和 labels MUST NOT 影响 v1 指纹或聚合键。
- **FR-019**：自动聚合 MUST 根据持久化同键 Alert 的规范接收顺序和已记录生命周期封存序列计算 Incident episode。持久化接收 MUST 保证同键后提交 Alert 的 `receivedAt` 严格晚于此前已提交 Alert 及生命周期封存边界，数据库墙钟同值或回拨不得使已建 episode 的起点或 anchor 事后改变。只有相邻接收时间差大于实际窗口，或者 Alert 的 `receivedAt` 晚于适用生命周期 `sealedAt` 时，才开始新 episode；Worker 领取或完成顺序 MUST NOT 形成 episode 边界。
- **FR-020**：v1 窗口 MUST 默认为包含边界的 30 分钟，并且 MAY 按部署配置。每个 Incident MUST 保留实际生效的规则版本和窗口；同一聚合键 MUST NOT 同时存在第二个聚合开放 Incident；窗口结束后才首次成功处理的较旧历史 episode MUST 产生创建即封存 Incident。
- **FR-021**：来源发生时间 MUST 被保留，但 MUST NOT 决定聚合顺序、窗口边界、重试计划或生命周期时钟。
- **FR-022**：Incident 首次转换到 `RESOLVED` 时 MUST 记录不早于此前已提交同键 Alert 接收时间的可信生命周期 `sealedAt`，并使后续同键接收时间严格晚于该边界。属于该 episode 且在边界时刻或之前接收的 Alert，即使稍后处理，也 MUST 关联到同一 Incident；边界之后接收的 Alert MUST 创建或加入后续 episode。关闭或重新打开 MUST NOT 移动或移除封存，任何 Alert 均不得自动重新打开 Incident。
- **FR-023**：对于相同的持久化 Alert 集合、实际窗口、生命周期封存操作序列和各 Alert 的最终处理结果，不同的 Worker 物理调度、并发、重试和重启 MUST 收敛为：每个至少含一条 `PROCESSED` Alert 的 episode 恰好一个 Incident；每条 `PROCESSED` Alert 恰好一个持久化关联；终态 `FAILED` Alert 保留原 episode 身份但 MAY 暂无关联，只有授权重放后成功处理才关联。全部成员尚未成功处理的 episode MAY 暂无 Incident。
- **FR-024**：创建或关联 MUST 以原子方式更新 Alert 结果、Incident 摘要、关联、Timeline 事实和待发布领域事件，使任何部分业务结果都不可见。

#### Incident 生命周期与 Timeline

- **FR-025**：新建 Incident 的生命周期状态 MUST 从 `OPEN` 开始，并包含稳定 `incidentId`、episode 起点和 anchor、聚合键和规则版本、可信的首末 Alert 接收时间、Alert 数量、聚合资格以及当前并发版本。延迟关联 MUST 将首个接收时间重新计算为最小值、末个接收时间重新计算为最大值，并将数量计算为唯一关联 Alert 的数量，且不得改变生命周期状态。
- **FR-026**：完整的允许主状态转换 MUST 仅限下表；所有未列出的转换 MUST 被拒绝，且不得改变状态或 Timeline。

| 起始状态 | 目标状态 | 操作者与必需业务证据 |
| --- | --- | --- |
| `OPEN` | `ACKNOWLEDGED` | 授权响应人员身份 |
| `OPEN` | `RESOLVED` | 授权响应人员身份和解决原因 |
| `ACKNOWLEDGED` | `MITIGATING` | 授权响应人员身份，或后续授权处置服务身份 |
| `ACKNOWLEDGED` | `RESOLVED` | 授权响应人员身份和解决原因 |
| `MITIGATING` | `ACKNOWLEDGED` | 授权响应人员身份，以及缓解停止或失败的原因 |
| `MITIGATING` | `RESOLVED` | 授权响应人员身份和解决原因 |
| `RESOLVED` | `ACKNOWLEDGED` | 授权响应人员身份、重新打开原因，以及同一聚合键不存在较新未关闭 Incident 的证明 |
| `RESOLVED` | `CLOSED` | 授权响应人员身份 |
| `CLOSED` | 无 | 终态；新的状况必须创建新 Incident |

- **FR-027**：每个转换请求 MUST 声明预期当前状态和版本；过期或竞争请求 MUST 携带当前状态/版本失败，并且 MUST NOT 静默覆盖胜出者。
- **FR-028**：自动关联 Alert MUST NOT 改变 Incident 主状态；响应人员或后续明确授权的功能必须主动请求合法转换。
- **FR-029**：重新打开 `RESOLVED` Incident MUST 使用已定义的 `(episodeStartAt, episodeAnchorAlertId)` 顺序；同一聚合键存在较新未关闭 Incident 时必须拒绝。成功重新打开只改变生命周期状态，并且 MUST NOT 恢复聚合资格或移除原封存。
- **FR-030**：Timeline 事实 MUST 只能追加、按 Incident 有序，并且不可通过普通业务能力修改。
- **FR-031**：本功能的 Timeline 事件类型 MUST 仅限 `INCIDENT_CREATED`、`ALERT_ATTACHED`、`LATE_ALERT_ATTACHED_AFTER_SEAL`、`INCIDENT_AGGREGATION_SEALED` 和 `INCIDENT_STATUS_CHANGED`。关联事务提交时若 Incident 已存在生命周期 `sealedAt`，使用 `LATE_ALERT_ATTACHED_AFTER_SEAL`，否则使用 `ALERT_ATTACHED`；两种类型 MUST 复用该 Alert–Incident 关联的稳定事件身份。Incident 首次进入 `RESOLVED` 并形成生命周期封存时，MUST 在同一转换中恰好追加一次 `INCIDENT_AGGREGATION_SEALED`，其可信发生时间为 `sealedAt`。纯窗口到期只通过最新 Alert 的 `receivedAt` 加实际窗口计算聚合资格，MUST NOT 触发后台、查询时或后续请求驱动的封存 Timeline 事实；即使此后没有活动，也不要求补写。创建即封存 Incident 在 `INCIDENT_CREATED` 中记录该条件，不发出虚构的早期封存事件。每个适用业务事件 MUST 恰好追加一条 Timeline 事实，其中包含稳定事件身份、操作者类型和稳定操作者 ID、可信发生时间、correlation ID、关联实体 ID 以及安全结构化摘要；人工重放本身属于 Alert 审计事实，不属于 Incident Timeline 事件。
- **FR-032**：Timeline 事实 MUST NOT 包含秘密、完整未脱敏告警请求体、不安全用户输入、堆栈跟踪、模型思维链，或解释历史事实所必需但可变的引用。
- **FR-033**：重新处理同一业务事件 MUST 复用其稳定 Timeline 事件身份，并且 MUST NOT 追加重复事实。

#### 处理、恢复与领域事件

- **FR-034**：Alert 处理状态 MUST 仅为 `RECEIVED`、`PROCESSED` 或 `FAILED`；已接收 Alert MUST 作为持久化工作项保留，直到到达后两个状态之一。`PROCESSED` Alert MUST 恰好关联一个 Incident；`FAILED` Alert MAY 暂无 Incident 关联，但仍保留已接收载荷和 episode 划分资格。Alert 接收、终态失败、人工重放接受或拒绝，以及恢复为已处理，MUST 产生不可变 Alert 审计事实，而不是覆盖先前操作历史。逐次处理尝试失败和重试调度属于 Alert 的当前重试控制数据与运行观测（见 FR-035、FR-044），MUST NOT 要求逐次只追加历史。
- **FR-035**：可恢复处理失败 MUST 默认调度五次自动重试，名义延迟依次为 10 秒、30 秒、2 分钟、10 分钟和 30 分钟。重试次数和延迟 MAY 配置；实际计划和有界抖动 MUST 被记录或可从证据推导。
- **FR-036**：重试耗尽 MUST 将 Alert 转为 `FAILED`，记录安全且机器可读的失败码、最后一条安全失败摘要、尝试次数和失败时间；MUST NOT 丢弃或修改已接收载荷；终态失败 MUST 作为只追加 Alert 审计事实记录安全失败码、尝试次数和失败时间，逐次尝试细节保留在日志与运行状态中，不进入只追加审计历史。
- **FR-037**：只有授权人员可以按照重放契约重放 `FAILED` Alert，重放请求 MUST 包含原因和预期版本。被接受的重放 MUST 保留相同 `alertId`、载荷、指纹、幂等身份、`receivedAt` 和 `correlationId`，并且 MUST 追加包含操作人员、原因、请求时间和先前失败引用的审计事实。
- **FR-038**：任何非 `FAILED` Alert 或过期版本的重放 MUST 以 `409` 拒绝；并发重放、Worker 和重试活动 MUST 通过受保护的当前状态检查解决。对已知 Alert 的已认证重放拒绝 MUST 追加包含安全原因的审计事实，且不得改变处理状态。
- **FR-039**：Worker 或进程重启 MUST 自动重新发现到期的 `RECEIVED` 工作和待发布领域事件，不要求来源重新提交或操作人员修复。
- **FR-040**：Incident 创建和 Alert 关联 MUST 分别创建稳定的 `incident.created` 与 `incident.alert-attached` 领域事件；`ALERT_ATTACHED` 和 `LATE_ALERT_ATTACHED_AFTER_SEAL` 都对应 `incident.alert-attached`，并复用同一关联的稳定事件身份。每个事件携带 `eventId`、`schemaVersion`、`incidentId`、Incident 聚合版本和 `correlationId`。
- **FR-041**：待发布领域事件 MUST 与其 Incident 业务事实原子提交，只能在提交后发布，并重试直到确认；下游不可用 MUST NOT 阻塞告警接收或 Incident 处理。
- **FR-042**：发布重试 MUST 保持原始稳定 `eventId`；允许重复投递，但系统 MUST NOT 声称端到端 exactly-once 投递。
- **FR-043**：发布重试延迟 MUST 以 15 分钟为上限，事件 MUST NOT 被自动丢弃；由于 Timeline 仍是业务审计来源，成功发布记录 MAY 在默认保留七天后分批清理。
- **FR-044**：系统 MUST 公开只读运行状态，包括已接收 Alert 积压数量和时长、失败 Alert 数量、重试数量、待发布数量及最老时长，以及 Worker 和发布器最近一次成功活动。数值 MUST 可与持久化状态对账，并通过安全的 Alert、Incident、事件和 correlation ID 关联，且不得暴露秘密或不安全原始输入。

#### 可复现验收证据

- **FR-045**：验收目录 MUST 至少覆盖：有效接收；复合失败优先级和每个响应类别；活动、非活动及轮换重叠密钥；签名跨方法、跨路径和跨 `alertId` 复用以及请求体篡改的拒绝；相同和冲突重复；100 路并发重复提交；相同指纹并发聚合；同键接收时间相同或墙钟回拨、接收与解决竞争时的单调顺序；时间间隔、生命周期封存、延迟处理、创建即封存和聚合开放唯一性行为；来源时间乱序；处理回滚；接收后崩溃恢复；重试耗尽；重放授权和竞争；发布失败和重复发布；每个允许状态转换；有代表性的禁止和过期版本转换；Timeline 与 Alert 审计不可变性；运行状态对账；以及响应、Timeline、审计和捕获日志的内容安全检查。
- **FR-046**：每次目录运行 MUST 从隔离的已知状态开始或明确标识该状态，并生成运行清单，包含场景版本、实际限制和聚合设置、确定性输入标识、开始/结束时间，以及所使用的命令或入口。
- **FR-047**：每个场景的证据 MUST 包含预期与实际响应、最终 Alert 和 Incident 状态、关联数、Timeline 数和 Alert 审计数、稳定事件 ID、重试或重放观察、适用时的运行状态比较、禁止内容扫描结果，以及任何跳过或因环境受阻的检查。重试观察来自运行状态、运行清单与捕获日志，不要求逐次 Alert 审计计数。
- **FR-048**：可靠性、并发和性能声明 MUST 引用已完成的证据运行；本规格中的目标数量是验收标准，不是对当前行为的声明。

### 关键实体

- **告警源（Alert Source）**：已注册机器身份，包含活动、轮换重叠期或非活动的密钥标识。秘密值属于运行凭据，不属于 Incident 业务数据。
- **Alert**：不可变的已接收 v1 通知，以及经过认证的来源、幂等身份、语义载荷摘要、`receivedAt`、来源发生时间、指纹、处理状态、并发版本、当前重试/重放控制数据、`correlationId` 和可选 Incident 结果。
- **Alert 审计事实**：只追加记录，覆盖接收、终态失败、人工重放决定（接受或拒绝）和恢复；包含稳定 Alert 身份、事件类型、可信时间、适用时的操作者、安全原因和相关终态失败引用。
- **Incident**：一个聚合键下恰好一个响应 episode，包含生命周期状态、实际聚合规则/窗口、episode 起点和 anchor、首末可信 Alert 时间、Alert 数量、并发版本、聚合资格和可选的不可变生命周期封存。
- **Alert–Incident 关联**：证明哪个 Incident 处理了一条 Alert 的持久化一次性关系。
- **Timeline 事实**：关联到 Incident 和稳定业务事件身份的不可变、有序、安全历史事实。
- **领域事件**：具有稳定事件身份和发布状态的版本化事务后消息记录；投递可以重复，但业务事件保持不变。

## 成功标准（必填）

### 可度量结果

- **SC-001**：在使用同一来源、同一外部告警 ID 和相同载荷的 100 请求并发测试中，所有成功响应返回同一 `alertId`，并且只存在一条持久化 Alert。
- **SC-002**：在包含 100 条 Alert 的并发测试中，各 Alert 使用不同外部告警 ID、同一聚合键，接收间隔均在 30 分钟内且无生命周期封存，最终恰好得到一个 Incident episode、100 个关联和 100 条唯一关联事实。
- **SC-003**：对于同一固定持久化 Alert 集合、实际窗口、生命周期封存操作序列和各 Alert 的最终处理结果，改变 Worker 物理执行顺序时，每次验收运行都产生相同的 episode 分段、已实体化 Incident 集合、`PROCESSED` Alert 关联集合、Incident 首末接收值、聚合开放结果和稳定业务事件身份。全部成员均为 `FAILED` 的 episode 可以没有 Incident，仍可经相同身份查询和重放；关联提交与生命周期封存竞争时，`ALERT_ATTACHED` 与 `LATE_ALERT_ATTACHED_AFTER_SEAL` 的选择及其 Timeline 相对顺序可以反映实际提交顺序，但关联身份、领域事件类型和最终业务结果必须相同且不得重复。
- **SC-004**：每个提交响应类别以及每个查询或重放契约结果都返回规定的 HTTP 状态和安全响应结构，并且 100% 的提交认证或校验失败均创建零条 Alert、Incident、Timeline、Alert 审计或领域事件记录。
- **SC-005**：`202` 提交后，每次注入的处理前崩溃或重启都能在无需来源重新提交且不留下部分 Incident 事实的情况下，使 Alert 恰好恢复到一个 `PROCESSED` 或明确的 `FAILED` 结果。
- **SC-006**：每个允许的 Incident 转换都在其声明前置条件下成功，每个未列出或过期的转换均被拒绝。每次成功转换恰好增加一条不可变的 `INCIDENT_STATUS_CHANGED` Timeline 事实；首次进入 `RESOLVED` 时，在同一事务中另增加且仅增加一条不可变的 `INCIDENT_AGGREGATION_SEALED` Timeline 事实，后续转换不重复增加封存事实。
- **SC-007**：领域事件发布器可在整个 Incident 处理期间保持不可用，随后仍能使用原始事件 ID 发布所有待发布事件；任何已接收 Alert 或 Incident 结果均不回滚、不重复。
- **SC-008**：对 `FAILED` Alert 的授权重放保留原始 Alert 身份、载荷、接收时间和 correlation ID，追加一条重放已接受审计事实，并且即使重放请求并发，也最多产生一个处理结果；竞争失败请求收到 `409`，并且只追加其已定义的拒绝事实。
- **SC-009**：从干净状态重新运行完整验收目录时，生成自包含的清单和证据集，将每个场景标识为通过、失败或明确受阻，并且不作出无证据的可靠性声明。
- **SC-010**：对于预置运行状态，所有公开的积压、时长、失败、重试和发布数值均与持久化记录一致；扫描响应、Timeline 事实、Alert 审计事实和捕获日志时，发现的配置秘密、签名、完整原始请求体、堆栈跟踪或不安全探针值数量为零。
- **SC-011**：在同键并发接收取得相同墙钟时间、接收期间墙钟回拨，以及接收与首次解决竞争的三类隔离场景中，按持久化提交顺序核对 100% 的新 `receivedAt` 严格晚于此前同键 Alert 和已形成的 `sealedAt`，`sealedAt` 不早于此前已提交的同键 `receivedAt`，且 episode anchor、关联和封存前后归属不发生事后逆转。

## 假设

- v1 只接收正在触发的通知；恢复通知语义和 Incident 自动解决需要后续契约版本或单独批准的范围。
- 告警源能够提供稳定的 `ruleId`、环境、服务和资源标识，并能在重试时保持 `externalAlertId` 不变。
- 接收环境具有可信同步时钟；时钟监控和组织级时间基础设施不属于本功能范围。
- 默认聚合窗口为 30 分钟。部署可以配置其他值，但修改配置不会重写历史 Incident，因为每个 Incident 都保留实际生效的规则版本和窗口。
- 在已封存聚合 episode 之后才接收的 Alert 会创建或加入较新的 Incident，而不是重新打开旧 Incident；封存前接收的 Alert 在处理时仍可关联到已封存 Incident。跨来源或跨规则关联推迟到真实证据证明需要更宽规则时再处理。
- 现有用户认证和授权能力将用于识别响应人员、告警读取人员与重放操作人员，并决定人员可查询的 Alert 范围；本功能只消费该授权结果，不创建按来源或 Incident 划分的通用 IAM 系统或 UI。
- 领域事件消费者在产生持久化副作用时负责自己的持久化防重；消费者实现不属于 Spec A。
- 后续“审批到安全模拟处置”Spec 将复用 `incidentId`、Incident 状态名和转换规则、作为自身稳定标识的 `actionId`、Timeline 事实模型、`correlationId` 和稳定领域事件约定，而不改变本功能的告警语义。

## 依赖

- 已批准的 DEC-008 和 DEC-009 定义 Incident 所有权与持久化数据边界。
- 已批准的 DEC-010、DEC-013、DEC-014、DEC-015 和 DEC-016 定义可靠接收、异步处理、重试、发布、认证、签名目标绑定、输入限制、幂等响应、Alert 审计与同键单调可信排序时间边界。
- 项目宪章要求持久化幂等、受保护状态转换、只追加 Timeline 事实、限制不可信输入，并要求可靠性声明必须先有可复现证据。
