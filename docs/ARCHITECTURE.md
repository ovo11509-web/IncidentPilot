# IncidentPilot 系统架构

> 最后核对：2026-10-06。本文明确区分“当前架构”和“目标架构”。标记为 `TBD` 的内容
> 尚未形成技术决策，不得据此直接实施。

## 当前整体架构

当前仓库由一个模块化 Java 主应用、一个独立 MCP 服务和一个 React 前端组成。

```text
浏览器
  │ HTTP/SSE，开发环境由 Vite 代理 /api
  ▼
主应用 bootstrap :9090，context path /api/ragent
  ├── incident ─────► framework / system（Spec A/B/C 的事件事实与审批）
  ├── agent ───────► rag ───────► framework
  │                  ├───────────► system
  │                  └───────────► infra-ai ──► 模型/Embedding/Rerank
  ├── PostgreSQL ragent / Redis / 对象存储
  └── MCP Client ────────────────► mcp-server :9099/mcp
                                      └───────► PostgreSQL ragent_bit

可选依赖：RocketMQ、Milvus、Elasticsearch、LightRAG、Langfuse、OSS/S3 服务。
```

主应用与 MCP 服务是两个独立 Spring Boot 进程。当前前端只通过主应用访问业务 API，
不会直接访问 MCP 服务。

## 模块职责与依赖

| 模块 | 当前职责 | 主要依赖/被依赖关系 |
| --- | --- | --- |
| `framework` | Web 约定、统一异常、数据库公共配置、Redis、MQ、幂等、分布式 ID、任务取消和追踪基础 | 被 `infra-ai`、`system`、`rag` 使用 |
| `infra-ai` | Chat、Embedding、Rerank 等 AI 供应商适配和模型调用基础设施 | 依赖 `framework`；被 `rag` 使用 |
| `system` | 认证、用户、用户上下文、示例问题和业务变更审计 | 依赖 `framework`；被 `rag` 使用 |
| `incident` | 告警、聚合、生命周期、Timeline/Outbox、审批/票据/回执/恢复，以及诊断请求/结果/Action 来源 | 运行时依赖 `framework`、`system`；被 `agent`、`bootstrap` 装配；MCP 仅测试依赖 |
| `rag` | 知识库、文档入库、检索、RAG 管线、意图树、Agent 配置数据、Trace 和评测 | 依赖 `framework`、`system`、`infra-ai`；被 `agent`、`bootstrap` 使用 |
| `agent` | AgentScope ReAct、会话、技能、记忆、人工确认、MCP 适配，以及本地 Runbook 诊断编排/证据校验 | 依赖 `rag`、`incident`；被 `bootstrap` 使用 |
| `bootstrap` | 装配并启动主应用，集中保存主运行配置 | 依赖 `rag`、`agent` 和 `incident` |
| `mcp-server` | 暴露 Streamable HTTP MCP 端点和演示工具；电商工具使用独立业务库 | 当前不依赖其他项目模块，由主应用通过 MCP 调用 |
| `frontend` | 登录、聊天、知识库、入库、Trace、Agent、用户及Incident/诊断/审批/回执管理页面 | 通过 HTTP/SSE 调用主应用；Incident授权再次由服务端判定 |

当前 Maven 依赖主链为：

```text
bootstrap -> agent -> rag -> framework
                    ├----> system -> framework
                    └----> infra-ai -> framework
             └----> incident -> framework + system

mcp-server（独立）
```

## 当前数据与基础设施边界

### 主数据库 `ragent`

由 `resources/database/schema_pg.sql` 和 `resources/database/upgrades/` 管理，当前包含：

- 用户、会话、消息、反馈和示例问题。
- 知识库、文档、切片、刷新任务和入库任务。
- 意图树、查询词映射、RAG Trace。
- Agent 配置、Prompt、技能、会话、状态、上下文压缩和长期记忆。
- 业务变更日志和 pgvector 向量数据。
- Spec A 的 Alert、Alert 审计、Incident、Timeline 与 Outbox 五张表；应用角色不能修改或删除两张只追加事实表。
- Spec B 的 Action、Approval、Execution、Ticket、Receipt；Spec C 的诊断 Request、Result、Action Link。结果/关联只追加，请求仅开放运行状态列更新；没有知识域 FK 或 JOIN。

### MCP 演示数据库 `ragent_bit`

由 `mcp-server` 启动期初始化，用于电商工具和安全模拟资源的 `simulation_target`/`simulation_application`。
Incident、审批与票据始终保留在主库；模拟应用以执行幂等键唯一。当前 Schema 明确不支持跨版本数据保留，
版本不匹配时要求重建数据库。因此在新的技术决策产生前，不得把需要长期保留的 Incident、
审批或审计数据放入该库。

### Incident 数据所有权

Incident 主数据存放在主 PostgreSQL `ragent`，复用 `resources/database/upgrades/` 的增量 SQL
机制。`incident` 模块独占其表、Mapper 和持久化事务；其他模块只能通过 `incident` 的应用服务
访问，不得直接查询或修改 Incident 表。

Incident 表之间可以建立外键和本地事务。对用户、Agent、RAG 等其他领域仅保存稳定标识和
必要的历史快照，不建立跨领域外键，不使用跨领域 SQL JOIN，也不让一个数据库事务同时承担
多个领域的状态一致性。跨领域协作使用已确认的服务接口或后续确定的可靠事件机制。

初期不额外创建 PostgreSQL 数据库、Schema 或多数据源。未来出现独立部署、扩缩容、故障隔离
或团队所有权需求时，再评估物理拆库；届时可以按 Incident 表集合迁移，而不需要重构领域模型。

### Redis 和 RocketMQ

- Redis/Redisson 已用于限流、并发协调和运行态控制。
- RocketMQ 已有框架抽象和现有业务消费者。Incident 告警链路只在 Incident 事务提交后通过
  Transactional Outbox 向 RocketMQ 发布跨领域事件；告警接入和 Incident 聚合不依赖 RocketMQ、
  Redis 或进程内状态保证正确性。完整决策见 `DEC-010`。

## 当前主要数据流

### Spec A 告警到 Incident

1. 来源向 `/incident/v1/alerts` 提交 HMAC 签名的 v1 Webhook；接入过滤器按固定顺序验证媒体类型、实际字节、签名、JSON 结构和字段契约。
2. 接收服务在同键数据库事务中分配可信 `receivedAt`，按来源及外部 ID 幂等插入 Alert 和只追加接收审计事实。共用状态查询路由在全局登录拦截放行后仍执行来源或人员本地鉴权。
3. Worker 从数据库重扫到期 Alert，在同键锁与状态重检下划分 episode，原子创建或关联 Incident、追加 Timeline 并写入两类 Outbox 事件。失败进入当前重试控制数据，五次默认重试耗尽后写终态审计；授权人员可按版本和原因重放。
4. 人员生命周期命令检查预期状态与版本；首次解决同时写状态变化和聚合封存两条 Timeline 事实。人员运行状态从数据库汇总积压与发布状态。
5. Relay 短事务领取已提交的 Outbox 行，事务外调用现有 MQ 生产者，仅 `SEND_OK` 后条件标记成功；失败保留同一 `eventId` 并退避重投。隔离 PostgreSQL 与模拟发布器已有验证；Broker/跨进程历史运行记录见 `docs/changelog.md` 的2026-09-28条目，Spec C 本轮不额外重跑 Broker 故障演练。

### 登录和普通 API

1. 前端调用 `/api/ragent/auth/login`。
2. 主应用通过 Sa-Token 建立登录态。
3. 除 `/auth/**` 和错误入口外，主应用 API 默认经过登录检查和用户上下文注入。
4. Controller 调用对应模块的 Service/Mapper，数据写入主 PostgreSQL。

### RAG 问答

1. 前端通过 `/api/ragent/rag/v3/chat` 建立 SSE 请求。
2. RAG 管线执行问题处理、作用域选择和已启用的检索通道。
3. 检索结果经过融合、Rerank 和上下文构建后提交给模型。
4. 回答、引用、会话记录和 Trace 按配置持久化或流式返回。

### Agent 与 MCP 工具调用

1. 前端通过 `/api/ragent/agent/v1/chat` 启动 Agent SSE 会话。
2. Agent 工具目录组合知识检索、记忆、技能和 MCP 工具。
3. MCP 工具通过主应用的 MCP Client 调用 `http://localhost:9099/mcp`。
4. 标记为写操作或配置为需要确认的工具触发确认卡。
5. 用户通过 `/api/ragent/agent/v1/chat/confirm` 批准或拒绝，Agent 再继续执行或结束调用。
6. Agent 消息、状态和工具 Trace 写入主数据库；部分电商工具数据写入 `ragent_bit`。

上游聊天确认交互与 Incident 审批链分开。Spec B 已实现权威审批、参数绑定、一次性票据、
全路径保护与模拟回执，并通过本地隔离验证；聊天确认不能替代该服务端链路。

### Spec C Runbook 诊断

`rag.runbook` 加载不可变 classpath manifest 和五类中文模拟资料，校验规范 UTF-8 全文/章节
SHA-256、1-based 行号、currentVersion、状态、有效期与精确 scope。检索是确定性的
`lexical-v1`：英文词/中文二元组覆盖率排序，稳定同分顺序，随后按已匹配症状扩展同文档审阅章节，
最多返回20片段；扩展仍属于本次实际检索集合，不允许生成器自行添加原文。

`incident` 在既有聚合键/Incident 锁内冻结精确关联的 PROCESSED Alert（最多100条），
保存请求和30秒截止。快照只含作用域、来源报告和稳定事实 ID；原始 payload、summary、labels
均不进入诊断。factsDigest 排除抓取时间、Timeline 水位和主 version，主 version 单独判断过时。

`agent.diagnosis` 在事务外做实际本地检索和 `extractive-v1` 抽取，结果明确为候选、只读检查和
缺失观测。指令性或矛盾来源、截断、跨范围或无匹配均降级。校验器重算允许的全部规则输出，
逐项绑定事实/引用/位置/hash/前提，拒绝任意工具或参数。没有模型、Embedding 或 MCP 调用。
结果按版本去重保存完整原文，Incident 原子追加结果与 DIAGNOSIS_RECORDED。

人员显式选择仅接受 ID/digest/version。Incident 在同一锁内重读当前事实和本地语料资格，
仅允许健康规则的 READY 模拟建议；还须配置 `incident-demo:demo:sim_service` 到注册模拟目标的
映射并与 B 白名单求交，默认无映射即失败关闭。同事务产生 B PENDING_APPROVAL、Approval、
诊断来源链接及 Timeline，不签票据、不批准、不执行。B 的 action-binding-v1 保持不变。

历史 GET/list 归 Incident，使用已持久化原文；GET 不触发生成或超时收敛。后续诊断 POST
惰性终结本 Incident 的超时请求；没有后台自动收敛保证。语料不可用不阻断 A/B 启动，
历史仍可读取，选择失败关闭。HTTP 边界将内部 Jackson 2 树转为普通 Map/List，以兼容 Boot 4。

接口：`/incident/v1/incidents/{id}/diagnoses`（POST/list）、`/incident/v1/diagnoses/{id}`（GET）
和 `/incident/v1/diagnoses/{id}/recommendations/{id}/actions`（POST）；均需登录且 admin。
决策见 DEC-023～025，执行证据见 `docs/changelog.md`；该语料与新增诊断属于 IncidentPilot。

### 文档入库

1. 管理端上传文件或创建入库任务。
2. 入库 Pipeline 解析、切分并生成向量等索引数据。
3. 元数据和默认 pgvector 数据写入主数据库，原始文件及多模态资产写入配置的对象存储。
4. 可选后端包括 Milvus、Elasticsearch 和 LightRAG。

## 当前关键接口

主应用统一前缀为 `/api/ragent`。下表列出接口族而非完整 API 清单：

| 接口族 | 用途 |
| --- | --- |
| `/auth/login`、`/auth/logout` | 登录与退出 |
| `/user/**`、`/users/**` | 当前用户和用户管理 |
| `/rag/v3/chat`、`/rag/v3/stop` | RAG SSE 问答与停止 |
| `/agent/v1/chat`、`/agent/v1/chat/confirm`、`/agent/v1/stop` | Agent 问答、人工确认和停止 |
| `/agent/v1/conversations/**` | Agent 会话管理 |
| `/knowledge-base/**` | 知识库、文档和切片管理 |
| `/ingestion/**` | 入库 Pipeline 与任务 |
| `/intent-tree/**`、`/mappings/**` | 意图路由与词项映射 |
| `/rag/traces/**`、`/biz-change-logs/**` | RAG Trace 与业务审计 |
| `http://127.0.0.1:9099/mcp` | 独立 MCP Streamable HTTP 端点 |

## IncidentPilot 目标架构

### 已确认的重点能力

- **告警可靠性与故障回放实验室**：围绕重复、并发、乱序、延迟、消费失败和恢复重放建立
  可复现验证环境；可靠接入与消息一致性边界由 `DEC-010` 确定，Webhook 输入约束由 `DEC-013`
  补充，来源 HMAC 的方法和目标绑定由 `DEC-014` 补充；Spec A与Spec F首版六场景本地实验室已完成，更广目录/规模实验仍属后续。
- **Runbook RAG 评测门禁**：使用版本化测试集验证检索、引用和诊断质量，并为变更提供
  可重复的回归证据；本地lexical/抽取门槛与v2审阅已完成，CI定义已由Spec H提供；真实模型评测与托管CI运行仍未验证。
- **MCP 安全处置网关**：服务端强制审批，约束参数篡改、重复执行和票据重放，记录不确定
  执行结果并支持对账；执行协议由 `DEC-011` 确定，调度、超时收敛和全路径票据强制由
  `DEC-012` 补充；Spec B 已实现并完成本地隔离验收，真实跨主机部署未验证。
- 指标、日志、Trace 和统一关联标识作为以上三项能力的横切证据，不单独建设一套脱离主流程的
  可观测平台。

### 已确认的目标模块边界

Incident 领域归属于 9090 主应用中新建的 `incident` Maven 模块。该模块在 RAG、Agent、
RocketMQ 或 MCP 不可用时，仍必须支持告警接入、Incident 创建与查询、合法状态流转和时间线记录。

目标依赖方向为：

```text
incident  -> framework + system
rag       -> framework + system + infra-ai
agent     -> rag + incident
bootstrap -> rag + agent + incident

mcp-server（独立内部执行器，不拥有 Incident 主数据）
```

`incident` 模块负责告警、Incident、Action 审批记录和只追加时间线；不负责检索、模型调用、
Agent 推理、MCP 工具实现或前端页面。`agent` 通过模块内应用服务读取 Incident 快照并记录诊断或
处置流程，`mcp-server` 只执行经过授权的模拟动作。跨进程执行协议见 `DEC-011` 和 `DEC-012`。

Action 调度 Worker、过期清扫 Worker 和 `RemediationDispatcher` 出站端口归属于 `incident`。
`agent` 实现 MCP 调度适配器并保持 `agent -> incident` 的依赖方向；适配器可以复用现有 MCP SDK
和连接配置，但必须保留结构化执行语义，不得把传输异常折叠成普通工具错误。`bootstrap` 仅装配
端口实现和启动主应用，不放置业务 Worker。适配器或 `mcp-server` 不可用时，Incident 的告警、
查询、人工状态流转和 Timeline 仍必须可启动和使用；处置调度失败关闭并按授权窗口收敛。

保持模块化单体边界，未来只有出现独立扩缩容、部署隔离或团队所有权等真实需求时，才评估将
`incident` 拆为独立服务。

### 已确认的告警可靠处理链路

告警接入采用数据库优先的异步处理模型：

```text
告警请求
  -> PostgreSQL 本地事务写入 Alert
  -> 提交成功后返回 HTTP 202
  -> 数据库 Worker 使用 FOR UPDATE SKIP LOCKED 领取 Alert
  -> 本地事务创建或聚合 Incident、关联 Alert、追加 Timeline、写入 Outbox
  -> Outbox Relay 发布 RocketMQ
  -> 产生持久化副作用的下游消费者在自己的事务边界内使用 Inbox 防重
```

可靠性边界如下：

- HTTP 202 只表示 Alert 已持久化并可在应用重启后恢复处理，不表示 Incident 已经创建。
- Alert 记录自身作为持久工作项，初期不增加通用 Inbox 或 Job 表。
- 每条 Alert 使用一个短聚合事务；数据库行锁负责领取，部分唯一索引负责并发 Incident 创建仲裁。
- Alert、Incident、关联关系、Timeline 和 Outbox 依靠同一个 Incident 本地事务保持原子性。
- 聚合语义是“至少一次执行、业务结果有效一次”，不宣称端到端 exactly-once。
- Alert 自动重试耗尽后进入 `FAILED`，由受权用户填写原因后人工重放。
- Outbox 使用稳定 `eventId` 普通同步发布，发送失败持续退避重试；发送成功但状态提交失败时允许
  重复投递，由下游消费者以持久化 Inbox 或等价数据库约束防重。
- RocketMQ 故障只造成 Outbox 积压，不阻止告警接入或 Incident 创建。

初期核心表集合为 `t_incident_alert`、`t_incident_alert_audit`、`t_incident`、
`t_incident_timeline` 和 `t_incident_outbox`。Alert 行保存唯一 Incident 归属和稳定关联事件身份，
不另建关联表；Alert 审计仅记录接收、终态失败、人工重放决定和恢复，不记录逐次 Attempt。
核心记录不提供普通业务删除能力；需要筛选、排序或约束的数据使用普通列，
时间字段统一使用 `TIMESTAMPTZ`，原始载荷和事件快照使用版本化 JSONB。具体 Schema 必须在功能
Spec 和数据模型设计中确认并通过真实 PostgreSQL 集成测试验证。持久化集合边界见 `DEC-015`。

告警 Webhook 使用独立机器认证，不依赖浏览器 Sa-Token 登录态。成功与失败必须返回真实 HTTP
状态码；同一来源和外部告警 ID 由数据库唯一约束兜底，相同 ID 但载荷摘要不同返回冲突。初期
接口为 `POST /incident/v1/alerts`、`GET /incident/v1/alerts/{alertId}` 和仅管理员可用的
`POST /incident/v1/alerts/{alertId}/replay`。机器认证至少包含来源 ID、请求时间戳和
HMAC-SHA256；所有来源 HMAC 请求的签名覆盖协议版本、来源、时间戳、大写 HTTP 方法、规范化
目标路径和实际请求体字节，状态查询的目标 `alertId` 必须参与签名，无请求体时使用确定的空字节
序列，默认容忍正负 5 分钟。原始请求体
默认上限为 64 KiB，JSON 默认最大嵌套深度为 16，均可配置并在持久化前校验。已处理或已失败的
相同载荷幂等重复返回 200 时，响应体必须包含稳定 `alertId` 和机器可读的 `processingStatus`。
完整约束见 `DEC-013`、`DEC-014`，准确字段与错误码由告警接入 Spec 固化；密钥不得进入源码、数据库、日志
或文档。

### 已确认的 MCP 审批与幂等执行链路

MCP 副作用执行采用 Incident 持有审批事实、数据库 Worker 调度、一次性票据在线消费和结构化
回执的模型。Agent 确认流程只作为交互适配器，`mcp-server` 只作为内部执行器。

```text
Agent 提出受保护写操作
  -> Incident 创建不可变 Action 和待审批记录
  -> Agent 确认卡引用 actionId
  -> 有权限的用户在 Incident 服务端批准或拒绝
  -> 同一事务更新 Action、Approval、Timeline、Outbox
  -> 数据库 Worker 领取仍在授权期内的 Action
  -> 创建逻辑 Execution、签发一次性票据、Action 进入 DISPATCHING
  -> 提交领取事务后通过 RemediationDispatcher 以服务身份调用 mcp-server
  -> mcp-server 回调 Incident 原子消费票据并校验工具、目标、参数摘要和幂等键
  -> 票据 CONSUMED、Action EXECUTING，记录 executionStartedAt 和 receiptDueAt 后才调用具体工具
  -> mcp-server 先回调保存结构化回执，再返回 MCP 响应
  -> Incident 幂等落库回执、更新 Action、追加 Timeline 和 Outbox
  -> 清扫 Worker 收敛过期记录，并将超过 receiptDueAt 且无终态回执的 EXECUTING 转为 UNKNOWN
```

主应用的内部 HTTP 路由位于 `/api/ragent/incident/internal/v1`。独立 MCP 进程的
`INCIDENT_MCP_CALLBACK_BASE_URL` 使用主应用根地址（含 `/api/ragent`），回调客户端只接受
空路径或这一已知前缀；票据消费和回执仍分别使用专用服务身份。

#### 唯一事实来源与模块边界

- `IncidentAction` 是一次拟执行副作用操作的聚合根。Action、Approval、Execution、Ticket、
  Timeline 和执行回执都由 `incident` 持久化并裁决。
- Agent 消息保存 `actionId` 并展示权威状态；Agent State 只保存暂停和恢复所需的运行快照。
  两者都不能授权 MCP，也不能判断副作用是否成功。
- Agent 会话恢复时按 `actionId` 查询权威状态；拒绝、过期或其他终态结束等待。批准后的工具执行
  由数据库 Worker 调度，Agent 不得在恢复时再次调用原写工具；P0 不要求增加状态推送机制。
- `mcp-server` 验证服务身份和票据、执行工具并回传结果，不拥有 Incident 主状态，不连接
  Incident 表。`ragent_bit` 只保存可重建的演示业务资源。
- Agent、用户和 MCP 关联使用稳定 ID 与必要快照，不对 Incident 表建立跨领域外键或 SQL JOIN。
  Agent 消息丢失或删除不能改变 Action、审批和执行审计事实。

#### Action、审批和票据状态

Action 合法转换为：

```text
PENDING_APPROVAL -> APPROVED | APPROVAL_REJECTED | EXPIRED
APPROVED         -> DISPATCHING | EXPIRED
DISPATCHING      -> EXECUTING | FAILED | EXECUTION_REJECTED | EXPIRED
EXECUTING        -> SUCCEEDED | FAILED | EXECUTION_REJECTED | UNKNOWN
UNKNOWN          -> SUCCEEDED | FAILED
```

审批只允许 `PENDING -> APPROVED | REJECTED | EXPIRED`，票据只允许
`ISSUED -> CONSUMED | EXPIRED`。终态不得重新进入执行；`UNKNOWN` 不能重置为 `EXECUTING`。
需要再次执行时创建关联原 Action 的新 Action，重新审批并生成新幂等键。

所有状态更新使用预期状态和版本的条件更新。数据库枚举检查、唯一约束和影响行数共同保证并发
请求不会静默覆盖。Timeline 只能追加，禁止普通业务更新或删除。

`DISPATCHING -> EXECUTION_REJECTED` 只用于已认证的合法调度上下文或 Incident 自身确认的工具、
目标、参数摘要、幂等键或策略绑定拒绝，并保存结构化原因码；未认证请求不能依赖调用方提供的
`actionId` 改变状态。`EXECUTING -> EXECUTION_REJECTED` 表示票据消费后、副作用发生前的工具侧
策略或业务拒绝。审批、授权或票据自然过期仍使用 `EXPIRED`。

#### 调度、过期清扫和执行截止时间

调度 Worker 与过期清扫 Worker 均归属 `incident`，使用 `FOR UPDATE SKIP LOCKED`、预期状态和
版本条件更新并发领取。调度 Worker 只在短事务内创建 Execution、签发票据并提交
`DISPATCHING`，提交后才调用出站端口，不在数据库事务或行锁内等待网络。Worker 崩溃后，只有
同一进程仍持有票据明文且数据库能够证明票据为 `ISSUED` 时，才允许在有效期内有限重试。

Worker 崩溃后票据明文已经丢失，恢复 Worker 不得从摘要重建秘密。若 Action 仍在授权窗口内，
恢复事务要求 Action 仍为 `DISPATCHING` 且没有已消费 Ticket：原 Ticket 仍为 `ISSUED` 时先条件
更新为 `EXPIRED`，原 Ticket 已自然过期且当前没有 `ISSUED` Ticket 时直接继续，然后为同一逻辑
Execution 和幂等键签发替代 Ticket。数据库保证每个 Execution 同时最多一个 `ISSUED` Ticket。
如果原 Ticket 已先被消费，恢复条件失败，不得签发或派发新票据，Action 按 `EXECUTING` 路径等待
回执或超时。被替代 Ticket 保存结构化失效原因；携带旧 Ticket 的迟到请求失败关闭，但在当前替代
Ticket 仍为 `ISSUED` 时不得把 Action 改为终态。

过期清扫使用数据库 `CURRENT_TIMESTAMP` 作为单一状态时钟，负责收敛超过待审批有效期的 Action
与 Approval、超过授权窗口的 `APPROVED` Action，以及超过有效期的 `ISSUED` Ticket。仍为
`DISPATCHING` 且当前没有 `ISSUED` Ticket 的 Action，只有在授权窗口也已过期时才能转为
`EXPIRED`；授权仍有效时由恢复 Worker 补发替代 Ticket。每次转换都使用条件更新并追加 Timeline。

票据 TTL 只限制执行许可的开始时间。票据消费事务使用数据库时间写入 `executionStartedAt`，并
持久化绝对的 `receiptDueAt = executionStartedAt + maxExecutionDuration + receiptGracePeriod`。
P0 演示工具默认最大执行时长为 2 分钟、回执宽限期为 3 分钟，均可配置且每次 Execution 保存
实际值。超过 `receiptDueAt`、仍为 `EXECUTING` 且没有终态回执的 Action 由清扫 Worker 转为
`UNKNOWN` 并触发只读对账，不自动再次执行副作用。

每个 Incident 处置白名单工具在注册时必须声明确定的目标解析器、只读对账器、
`maxExecutionDuration`、`receiptGracePeriod` 和超时结果评估能力，作为版本化准入属性；每次
Execution 保存本次实际采用值。`mcp-server` 对具体工具的强制调用超时不得大于该工具声明的
`maxExecutionDuration`。

工具调用超时后，适配器先评估结果：能够明确证明副作用尚未开始或事务已经回滚时，提交
`FAILED` 和 `TIMEOUT_PROVEN_ROLLED_BACK`；无法证明时立即提交 `UNKNOWN` 和
`TIMEOUT_UNPROVEN`，禁止乐观判定失败。P0 单本地事务工具也必须取得可验证的回滚证据。若
`UNKNOWN` 回执提交失败，`mcp-server` 只对同一幂等回执做短退避有限重试，不再次执行工具；仍然
失败时由 `receiptDueAt` 清扫兜底。快速 `UNKNOWN` 立即触发对账，后续确定结果可以通过迟到回执
或只读对账收敛为 `SUCCEEDED` 或 `FAILED`，冲突确定终态不得覆盖。

#### 审批绑定和一次性票据

Action 创建后，Incident 使用版本化规范化规则对以下内容计算 SHA-256：

- 协议版本、Action ID 和 Incident ID；
- 工具名和工具版本；
- 由工具专属服务端解析器推导的目标资源类型与 ID；
- 规范化工具参数；
- Incident 生成的执行幂等键。

审批记录绑定上述摘要，并记录批准人、角色、批准时间和执行授权有效期。批准人来自当前登录态和
服务端权限校验，不接受前端或 `_meta` 指定。参数、目标或工具发生变化时必须创建新 Action。

默认待审批有效期为 15 分钟，批准后的执行授权窗口为 5 分钟，一次性票据有效期为 2 分钟且
不得超过授权窗口；具体数值可配置，每条记录保存实际绝对时间。票据使用 256 位随机秘密，传输
格式可以是 `ticketId.randomSecret`，数据库只保存秘密摘要，明文不得落库或进入日志。

`mcp-server` 在执行写工具前调用 Incident 内部票据消费接口。Incident 重新规范化收到的原始
参数并在一个事务中验证服务身份、Action、审批、票据、工具、目标、参数摘要和幂等键。只有
`ISSUED -> CONSUMED` 原子更新成功才返回执行许可；重放请求返回 `DUPLICATE`，不再次调用工具。

列入 Incident 处置白名单的工具必须在 `mcp-server` 的所有调用路径执行上述认证和消费流程。
旧 Agent 确认流程、直接 MCP 调用或其他客户端都不构成旁路；无票据、票据无效、票据重放或绑定
不一致必须在副作用发生前失败关闭。P0 只把一个专用安全演示工具加入该白名单。

#### 服务身份与传输信封

主应用到 `mcp-server`、`mcp-server` 到主应用分别使用独立高熵服务凭证。凭证通过环境或秘密
管理设施注入，支持 key ID 和轮换，不进入仓库或日志。开发环境可保持 loopback 通信；跨主机
通信必须使用 TLS。P0 不建设 OAuth Server 或通用 IAM，未来可以替换为 mTLS 而不改变 Action、
票据和回执契约。

MCP `_meta` 只承载 `actionId`、`executionId`、`requestId`、幂等键和票据等关联信息。只有服务
身份认证成功且票据在线校验通过后，这些信息才参与执行；`_meta` 本身不是身份或授权凭据。

#### 幂等键、回执和重复调用

执行幂等键由 Incident 在 Action 创建时生成，每个 Action 固定一个，并与 Action 审计记录同寿命；
P0 不自动清理。Agent Action 提议使用稳定 `sourceRequestId`，数据库通过
`incidentId + sourceType + sourceRequestId` 唯一约束返回已有 Action，避免响应丢失后重复创建。

MCP 结构化回执必须携带协议版本、Action、Execution、幂等键、request、工具、资源引用、结果码、
发生时间和结果摘要。结果语义为：

| 结果 | 语义 |
| --- | --- |
| `SUCCESS` | 副作用已明确提交 |
| `FAILED` | 明确未提交、已回滚或确定失败 |
| `REJECTED` | 在产生副作用前被认证、票据、绑定、策略或业务校验拒绝 |
| `DUPLICATE` | 未再次执行，返回原执行引用或当前状态 |
| `UNKNOWN` | 无法证明副作用是否已经发生 |

不得根据自然语言或单一 `isError` 推断结果。`mcp-server` 完成调用后先通过内部接口向 Incident
幂等提交回执，再返回 MCP 响应；主应用收到响应后也幂等提交同一回执。相同终态回执返回原结果，
冲突终态不得覆盖，必须告警并进入人工核查。

#### 重试、UNKNOWN 和恢复

只有能够从 Incident 数据库证明票据仍为 `ISSUED` 时，消费前的连接建立失败或暂时网络错误才
允许在票据有效期内有限自动重试，默认最多三次短退避。以下情况禁止自动再次执行副作用：

- 票据已经消费；
- 网络超时或连接中断后无法确认执行结果；
- 主应用或 `mcp-server` 在消费后崩溃；
- 回执缺失、解析失败或关联 ID 不一致；
- 票据状态暂时无法查询。

这些情况进入 `UNKNOWN`。每个加入执行白名单的写工具必须提供确定的目标解析和只读对账能力。
经过服务认证且绑定完整的迟到结构化回执可以直接把 `UNKNOWN` 收敛为 `SUCCEEDED` 或 `FAILED`；
只读对账确认操作已应用时转为 `SUCCEEDED`，确认未应用或已回滚时转为 `FAILED`，无法判断时保持
`UNKNOWN`。人工结论需要专门权限、处理原因和证据，并追加 Timeline。相同终态幂等返回原结果，
任何冲突终态都不得覆盖，必须告警并进入人工核查。

#### 初期表与接口边界

ARC-004 的初期持久化集合为：

- `t_incident_action`
- `t_incident_action_approval`
- `t_incident_action_execution`
- `t_incident_execution_ticket`
- `t_incident_timeline`
- 已由告警链路确认的 `t_incident_outbox`

关键约束包括 Action 来源和执行幂等键唯一、每个 Action 最多一条审批和一个逻辑执行、票据摘要
唯一、每个 Execution 同时最多一个 `ISSUED` Ticket、状态和时间一致性检查、内部外键
`ON DELETE RESTRICT`、安全绑定字段不可变以及 Timeline 只追加。时间字段统一使用
`TIMESTAMPTZ`。

外部接口至少包含 Action 创建或查询、审批决定、状态查询、UNKNOWN 对账和人工结论；内部接口
至少包含票据消费与回执提交。准确路径、请求 Schema、错误码和权限名称由功能 Spec 固化，不能
削弱 `DEC-011`、`DEC-012` 的失败关闭语义。

P0 只接入一个具有持久模拟状态、确定回执和只读对账能力的安全演示工具，先证明完整的
Action—审批—票据—执行—回执—Timeline—UNKNOWN 恢复链路，再迁移其他写工具。

### 待确认部分

以下目标已经确定：告警幂等聚合、Runbook RAG 诊断、人工审批后的 MCP 处置、事件时间线与审计。
以下各项分别标明规格确认或待决策状态；需要长期决策归档的内容须在实施前取得确认并写入 `docs/DECISIONS.md`：

- Spec A 的 `FR-015`～`FR-025` 与 `DEC-017` 已规定告警指纹字段、聚合窗口、`aggregation_key` 和 episode 规则。
- Spec A 的 `FR-026`～`FR-031` 与 `DEC-018` 已规定 Incident 主状态机完整转换表和生命周期封存规则。
- 同键接收时间及首次解决封存的单调排序边界见 `DEC-016`。
- Runbook 版本、冻结快照、引用原文和离线数据由 Spec C 与 DEC-023～025 确定；生产资料与真实模型评测仍为后续。

## 必须保持的架构约束

- 告警、模型输出、用户输入和 MCP 参数均是不可信输入。
- 幂等不能只依赖进程内判断，必须由数据库约束或等价的持久化机制兜底。
- Incident 状态转换必须由服务端校验。
- 时间线只追加，不允许覆盖历史事件。
- 有副作用的 MCP 工具必须经过服务端人工审批并携带幂等键。
- 列入 Incident 处置白名单的工具必须在 `mcp-server` 所有调用路径强制服务身份认证和票据消费；
  旧 Agent 确认流程、直接 MCP 调用或其他客户端不得形成旁路。
- 在明确扩大范围前，只允许对演示或模拟资源执行处置。
- MCP 请求 `_meta` 是调用方提供的数据，不能单独作为身份认证依据。

## 未来拆库的禁止事项

以下操作会显著增加物理拆库成本，实施或评审时必须阻止；如确有必要，需先追加新的技术决策：

- 其他模块直接依赖 Incident Mapper、DO 或表结构。
- Incident 查询跨领域 JOIN 用户、Agent、RAG 或知识库表。
- Incident 表与其他领域表建立数据库外键。
- 单个本地事务同时修改 Incident 表和其他领域表。
- 使用数据库触发器、存储过程或视图跨领域读写。
- 依赖仅在主数据库中有效的共享序列、临时表或隐式 Schema 搜索路径。
- 把 Incident、审批、执行回执或时间线主数据写入可重建的 `ragent_bit`。
- 让 `mcp-server`、`rag`、`agent` 或前端绕过 `incident` 应用服务直接决定 Incident 主状态。
- 在报表或管理页面中形成无法替换的跨领域同步查询。
- 在没有迁移契约和校验方案时复制或双写 Incident 数据。

## Incident操作界面（Spec D，2026-10-04）

既有RequireAdmin及AdminLayout承载 `/admin/incidents` 和 `/admin/incidents/:incidentId`。领域请求通过incidentService→incidentApi，沿用storage会话和VITE_API_BASE_URL（默认/api/ragent），按DEC-027逐接口指定成功码；Vite仓库默认127.0.0.1:5173代理9090。服务端再次要求登录及admin，不靠路由保证权限。

新增GET `/incident/v1/incidents`：白名单字段、可选生命周期筛选、(episode_start_at,id)倒序二元游标、limit≤100与limit+1。新增 `/{incidentId}/alerts`：只读取精确incident_id且PROCESSED的告警，id升序游标、limit≤100，复用现有秘密清洗，不输出payload/labels/summary。查询不重新聚合、不跨episode、不逐行构造snapshot。无新表或迁移。

Action查询增加parametersAvailable及parameters.desiredState，严格读取已持久化parametersJson并由action-binding-v1校验；非法数据只显示预览不可用。回执增加occurredAt、summaryAvailable、白名单desiredState/applied/proofCode，原disposition和历史状态保留；异常JSON不回传。

详情显示来源、关联告警、只追加Timeline、诊断历史/冻结快照/原文与hash、显式建议选择、冻结Action预览、独立人工审批、执行及回执。ID保持字符串，版本/序列写入前检查安全整数。相同页面意图复用sourceRequestId和冻结绑定；拒绝后明确新意图携带originalActionId。服务端仍重新校验版本、事实和证据资格；历史可读不等于当前可选择。

读请求串行排队，活跃诊断/执行每5秒读取；后台暂停、终态/错误停止、路由切换取消旧请求。写请求无自动重试，409刷新后重新审阅，不自动重批。UNKNOWN仅提示未知与手动核证，无写重试；成功不会自动解决Incident。诊断仍不确认根因、不审批、不调用写工具。

真实隔离运行及限制见Spec D quickstart和docs/changelog.md；这是本地安全模拟验证，不代表真实基础设施或生产验收。

## Incident主链关联与 Trace（Spec E，2026-10-05）

`framework.observability` 提供固定21字段的安全JSON日志、OTel scope及不可变Context/MDC快照；`incident.observability` 只从现有领域记录填充可信身份。HTTP过滤器先分配attempt UUID，接入过滤器、Controller和Advice复用request attribute，响应body/header/log一致。业务sourceRequestId、dispatchRequestId和各Alert correlationId保持，聚合后以incidentId检索；没有新表、迁移、历史回写或Trace业务双写。

主应用由framework配置持有单一OpenTelemetrySdk；OBS默认关闭，开启后沿用已有1.62.0依赖，可不配置出口，也可配置OTLP HTTP BatchSpanProcessor。Agent原Trace开启时通过命名customizer加入同一owner，保留其serializer、middleware、资源与内容开关；仅Agent开启时继续使用原owner。独立MCP自持同版本SDK和小型固定字段scope，不依赖framework/incident运行模块。主应用配置是 `observability.enabled` / `observability.endpoint`，环境变量是 `OBSERVABILITY_ENABLED` / `OBSERVABILITY_ENDPOINT`。

范围内每个HTTP、实际诊断阶段、Worker单条记录、Relay单次投递都有短时Span。人员与公开告警请求不采用外来父Trace；受保护 `/mcp/incident` 和内部回调仅原认证通过后提取单个有效W3C traceparent，不传播baggage/tracestate。初始化只记录HTTP处理，未经消费GRANTED不得建立可信执行关联。工具 `_meta` 仍是原七字段，不把Trace写进授权、参数、摘要或回执正文。

MCP transport contextExtractor捕获当前Context，工具回调显式恢复；timeout executor在真实task提交处捕获Context/MDC并在工作线程finally恢复。execute Span按实际任务完成结束，取消不等于停止。Servlet异步SERVER scope在线程返回时detach，并在实际异步完成/错误/超时关闭。

审批等待、持久队列和重启不维持活跃Span，不持久化SpanContext或承诺跨重启Link；稳定Action/Execution/dispatch身份与权威数据库事实连接恢复证据。采集失败不决定审批、重试或UNKNOWN结论。新日志/Span禁止正文、凭据、票据、参数、内容摘要和自由文本异常，值预算128安全ASCII字符、属性预算24。生产SDK owner/出口和上游Agent兼容由装配回归覆盖；双JVM验收使用测试classpath文件exporter采集实际SpanData，后端存储、生产与性能尚未验证，详见Spec E实施报告。


## LAB-001本地实验入口与验收（2026-10-05）

Spec F新增测试侧固定入口 `specs/006-local-fault-replay/scripts/run-lab.ps1`，串行选择既有六类JUnit检查，使用临时资源、自有JVM及LAB-only Surefire报告profile。runId/sourceDigest绑定本轮XML与proof-origin，数据库计数为业务事实，Trace用于调用解释；归档位于bootstrap/target/fault-lab，Verify不启动服务，只读核对文件集合、摘要、精确测试与关键事实。仅扩展测试证据输出与本地脚本，没有生产Schema/审批/UNKNOWN恢复协议变更；范围与实际验收见Spec F实施报告。

## DEP-001工程交付边界（2026-10-06）

Spec H工程入口scripts/check-delivery.ps1位于业务架构之外，复用原BootstrapIsolatedStartupTest/Testcontainers及主/MCP双JVM；既有lab.reportRoot只改变本轮Surefire目录，delivery.gates只在测试helper选择受控delivery proof路径，业务模块、Schema和生产配置不变。Smoke精确黄金路径，Backend全仓并核对必需目标/手工跳过，Frontend固定四阶段，All顺序聚合。临时原环境恢复；raw/reports/proof仅本地，CI只安全摘要。相对命令、初始化/资源所有权/部署TBD见 [交付说明](development/delivery.md)，来源及实际验收见 [Spec H](../specs/008-local-delivery-gates/quickstart.md)。CI定义不代表已发布或托管运行；常驻/生产部署与外部服务尚未验收。

2026-10-06项目级核对确认上述A～H已批准的本地边界实现并具备对应证据，SPC-001/M0收尾；具体要求映射、真实性层级和残余范围见 [本地完成度核对](development/local-acceptance-review.md)。本次只修正现状说明，没有新增架构、修改历史DEC或扩大生产权限。

## B 风格值班工作台（Spec I，2026-10-10）

管理员默认入口为 `/admin/workbench`；普通用户仍进入 `/chat`。原 `/admin/dashboard`、知识配置等地址保留。公共后台使用 `incidentpilot-shell`，首页与事件内容使用 `incidentpilot-page` 范围主题；旧配置内容不继承Incident深色覆盖。品牌和自有SVG标识保留上游归属链接。

GET `/incident/v1/workbench` 在Controller登录/admin校验后进入 `IncidentWorkbenchQueryService`，用现有事务管理器的readOnly REPEATABLE_READ事务访问 `IncidentWorkbenchMapper`。数据库首次读取时刻建立快照；四状态计数、24个相对60分钟的告警接收桶、关注事件/待审批/最近活动三个最多8条列表共用该事务。活动Incident与Action状态独立统计，接收桶包括所有持久化处理状态，列表使用固定SQL排序和安全白名单，无新表、锁写、Worker或Schema迁移。

前端沿用Incident专用请求客户端，新增响应校验、手动刷新/取消、显式时间窗口与加载/错误/空态。待审批深链携带真实actionId，详情数据就绪后聚焦对应提案；流程摘要仅关联人员选定的动作及其诊断来源。失效、审批、幂等及UNKNOWN处理仍由既有服务端规则约束，执行成功不改变Incident生命周期。真实隔离功能证据与截图工具限制见 [Spec I实施记录](../specs/009-incidentpilot-workbench/implementation-report.md)。

## Spec J：只读事件复盘与版本化 Runbook（2026-10-11）

前端 `incidentReview.ts` 消费现有 A/B/C 人员安全 GET，顺序采集告警、时间线、诊断详情和动作；校验身份/游标/序号，限制1000告警、200诊断、5000事实、200动作，随后复核事件版本、动作指纹、诊断指纹及新增时间线。首尾核对不能保证数据库原子快照，报告与导出明确声明时间范围；已知变化/截断/失败阻止导出。Markdown 只选择展示字段并转义文本，不导出 raw payload/任意 JSON/票据/凭据，不调用模型生成根因。现有 ActionQuery 无分页且逐动作读 Timeline 的成本保持，不以客户端限制冒充服务端有界查询。

`rag/.../runbook/RunbookCatalogController` 的 `/incident/v1/runbooks` 在读取 Corpus 前验证 admin，以 Map 返回验证过的打包元数据、正文与章节；本类处理401/403及不可用503。目录不可用不是空目录。前端 `runbookView.ts` 核对来源、范围、章节正文和精确版本/摘要。Report/Diagnosis 的保存引用原文独立展示；目录缺少旧版本不得替代历史证据。B公共外壳新增处置手册入口与复盘面包屑；只提高 Incident 页输入控件的选择器优先级，修复旧 `.admin-layout .ui-input` 白底覆盖，其他后台范围不变。无新持久化实体、模块依赖与副作用。

## Spec K：查询一致性与操作体验（2026-10-11）

`ActionQueryService` 完成登录/admin检查后，以显式 `TransactionTemplate` 的readOnly REPEATABLE_READ事务建立人员查询快照；详情和列表中的Action、Approval、Execution、Ticket、Receipt及相关Timeline共用观察。列表一次读取当前Incident的轻量动作Timeline投影，再按Action/Execution身份分组；当前Receipt从同次回执集合选择。Approval、Execution及Ticket等仍存在逐动作读取，未声称全面消除N+1。动作查询仍无服务端分页，前端复盘上限不等于SQL上限。

`DiagnosisQueryService` 的列表页只进入一次既有Incident事务守卫，统一版本与数据库时钟；Results按当前页ID批查，需要比较当前版本时才一次捕获factsDigest。每项继续单独核对证据资格、发布失效和截止时间；历史目录不构造随后删除的完整snapshot/result投影。详情仍保留原冻结证据。

事件列表新增服务端组合筛选，SQL在游标与limit之前匹配；返回独立的首条告警摘要，而非把匹配成员伪称为首条。复盘补目标/安全目标状态、超时下一步与采集阶段计数。请求异常只暴露受控分类、原因码和合法UUID定位；写结果未知时无自动重试。登录及认证/角色守卫保持静态，页面在守卫内动态加载；路由加载失败显示固定提示，浏览器标题统一IncidentPilot。实际检查及本地运行装配另见 [Spec K实施记录](../specs/011-incident-quality-delivery/implementation-report.md)。

本机持久化入口 `scripts/local-demo.ps1` 以ignored实例目录保存随机凭据、所有权/配置/制品摘要与进程身份。四依赖Compose使用自有标签、独占持久卷和回环端口；Java主应用/MCP运行真实executable JAR，Node标准库提供production dist及同源HTTP/SSE代理。生命周期排他锁阻止并行初始化/启动；重复Init核对Schema和配置，不重置业务数据；Stop验证归属并保留卷。

2026-10-17新实例显式保存存储`/logs`到第五个命名卷`storage-logs`，与`storage-data:/data`分离并沿用同instance标签/Stop保留规则；旧私有配置和卷不自动迁移。真实三轮验证限Compose依赖生命周期，见 [修复记录](../specs/013-release-candidate/storage-volume-fix-report.md)。

`local-demo` profile提供明确拒绝Broker操作的装配和HTTP范围过滤，限定认证、Incident及只读当前用户。普通MCP/模型/解析器入口关闭，protected simulation仍执行原服务认证、审批票据和幂等协议。范围过滤核对规范化路径，Node限制回环Host和静态真实路径；这套装配不替代上游完整功能部署。初始化、运行与重启实际证据见 [本机演示说明](development/local-demo.md) 及Spec K实施记录。
