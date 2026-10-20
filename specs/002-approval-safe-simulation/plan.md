# 实施计划：审批到安全模拟处置

**分支**：`feature/incident-platform` | **日期**：2026-09-23 | **规格**：[spec.md](./spec.md)

**输入**：已确认的 Spec B v1.1、Spec A v1.0、DEC-008/009/011/012/017/018/019/020。
**计划状态**：已获用户确认（2026-09-23）；本阶段只设计，不实施代码、数据库迁移或前端。

## 摘要

在 Spec A 规划的 `incident` 模块内建立 Action、Approval、Execution、Ticket 与回执的权威事实；由 Incident Worker 调度，`agent` 只适配交互与出站 MCP，`mcp-server` 仅在专用受保护入口执行一个隔离模拟工具。票据在线消费与执行幂等键共同限制副作用启动；结构化回执、只读对账和人工证据处理 `UNKNOWN`。复用同一 Incident Timeline 与 Outbox，保留 Spec A 原有事实和事件的语义。

## 技术背景

**语言/版本**：Java 17\
**主要依赖**：现有 Spring Boot 4.1.0、Spring MVC、MyBatis-Plus、Sa-Token、MCP Java SDK、RocketMQ 适配器；安全摘要与随机数使用 JDK 标准库\
**存储**：主 PostgreSQL `ragent` 持有 Incident 安全事实；`ragent_bit` 只保存可重建的隔离模拟资源与应用记录\
**测试**：JUnit 5、Spring Test/MockMvc、Testcontainers PostgreSQL、固定响应的本地 MCP 双进程验收；不调用真实模型或付费服务\
**目标平台**：现有主应用与独立 MCP Server；本地 PowerShell/Docker、Linux 服务端\
**项目类型**：多模块 Maven 后端与独立 MCP 服务；本计划不设计前端实现\
**性能目标**：不声明未经实测的吞吐或时延；以单次启动、并发竞争和截止时间场景作为可复现正确性门禁\
**约束**：一个白名单模拟写工具；主库只追加 Timeline、持久化幂等与状态校验；服务凭据与票据明文不落库/日志/证据；跨主机 TLS；消费后不得盲目重试\
**规模/范围**：一条审批到模拟处置的纵向链路；MVP 停用未纳入 Incident 审批链的旧写工具注册，不迁移这些工具，不建设通用工作流、IAM 或真实基础设施适配器

## 宪章检查

| 原则/约束 | Phase 0 前检查 | Phase 1 后检查 |
| --- | --- | --- |
| 署名与贡献可追溯 | 通过：保持上游历史和许可 | 通过：设计只增加 IncidentPilot 专属边界，未来源码沿用许可头 |
| 持久化边界保证正确性 | 通过：规格要求唯一 Action、一次性票据与只追加 Timeline | 通过：数据模型规定唯一约束、条件转换、本地事务与稳定事实键 |
| 人工控制副作用 | 通过：仅安全模拟工具且先人工批准 | 通过：专用入口全路径强制服务认证与在线票据消费，旧入口只保留经核查的只读工具 |
| 按 Spec 控制范围 | 通过：Spec B 已确认，范围外明确 | 通过：计划只覆盖一个演示工具及必需接口，不扩展通用平台 |
| 有证据后再做结论 | 通过：规格有超时、竞态和故障验收 | 通过：Quickstart 定义真实 PostgreSQL、双进程与脱敏证据；未声称测试已通过 |
| 技术与秘密 | 通过：复用现有技术栈，秘密不入仓库 | 通过：双向独立服务凭据、开发回环例外、跨主机加密；不依赖 `_meta` 自报身份 |

**门禁结论**：无宪章例外。DEC-019/020 已获批准并归档；Phase 1 产物及本计划已获用户确认。实施仍须遵守任务与授权边界。

## 项目结构

### 当前功能的文档

```text
specs/002-approval-safe-simulation/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── openapi.yaml
│   ├── protected-mcp.md
│   └── action-events.md
└── checklists/
    └── requirements.md
```

`tasks.md` 只在计划确认后由 `$speckit-tasks` 生成，使用稳定 `TASK-###` 编号。

### 目标代码边界（仅规划）

```text
incident/                                  Action 聚合、审批、Worker、票据、回执、对账、Timeline/Outbox
agent/                                     确认卡薄适配与 RemediationDispatcher MCP 适配
mcp-server/                                旧入口只读注册门禁、受保护 MCP 入口、模拟工具、只读对账
bootstrap/                                 模块装配与非秘密配置
resources/database/schema_pg.sql           实施时扩展全量主库 Schema
resources/database/upgrades/               实施时追加主库迁移
mcp-server/src/main/resources/db/           实施时扩展可重建演示库 Schema
```

**结构决策**：遵循 DEC-008/011/012。`incident` 不依赖 `agent` 或 `mcp-server`；`agent` 依赖 Incident 的出站端口；`bootstrap` 只装配。Spec A 的告警事务与 Outbox Relay 继续工作，不让执行端可用性进入告警正确性边界。

## 设计方案

### 1. Action 与审批

- 以 Spec A 的 `incidentId` 和主状态为输入。提议只接受未 `CLOSED` Incident；人员 HTTP 来源类型由服务端会话确定，受控 Agent 来源只走内部应用服务。按 `incidentId + sourceType + sourceRequestId` 持久化防重；重试用不含新生成 ID/执行键的规范提议摘要比较。创建时冻结工具版本、目标、规范参数、审批绑定摘要、执行幂等键与审批截止时间。
- Approval 与 Action 一对一。人员批准/拒绝使用现有登录会话、当前权限和预期版本；卡片仅引用 `actionId`，不得以消息状态或 `_meta` 自报值授权。批准记录冻结审批人、权限快照、绑定摘要和绝对授权截止。
- P0 的人员查询、提议、审批和请求只读对账沿用 Spec A 的 `admin` 角色门禁；人工 `UNKNOWN` 结论另设独立的 `INCIDENT_ACTION_RESOLVE_UNKNOWN` 业务权限检查，当前仅映射到 `admin`，并要求原因与证据。两类检查在 Incident 服务端执行，不以请求字段决定身份，也不建设通用 IAM。
- Incident 关闭时，在同一受保护 Incident 决定内将尚未开始的 Action 转 `EXPIRED`、使未消费票据失效；关闭与消费用相同 Incident 行及 Action 条件检查仲裁。已 `EXECUTING`/`UNKNOWN` 的 Action 保留独立收敛路径，不改变 Incident 主状态。

### 2. 调度、票据与全路径保护

- Incident Worker 在短事务中领取仍有效的 `APPROVED`，创建唯一逻辑 Execution、签发 256 位随机票据、仅保存 SHA-256 摘要，提交 `DISPATCHING` 后调用出站端口；不在行锁或数据库事务内等待网络。
- 同一 Execution 最多一个 `ISSUED` 票据。进程内只有在原票据仍可核实为 `ISSUED` 且持有明文时有限重试；崩溃恢复先使旧票据 `EXPIRED`，再签发替代票据，保持 executionId 与执行幂等键不变。消费已胜出时不得替换。
- 专用受保护 MCP 入口只注册一个模拟写工具，入口先验证主应用服务身份；受保护工具不在旧 `/mcp` 或其他注册表暴露。每次调用在副作用前通过独立反向服务身份请求 Incident 在线消费票据。服务认证、票据、Action、审批、Incident 当前状态和完整参数绑定全部通过后才启动工具。旧确认卡、直接 MCP 请求和缺少票据的调用均不能旁路。
- 旧 `/mcp` 及其他普通发现/调用入口采用服务端明确的只读允许清单；实现时逐项核查现有工具行为，未纳入 Incident 审批链的旧写工具不注册，已知名称直调也不能执行。`readOnlyHint`、Agent 确认卡或工具自报不足以决定准入；未知或未核查的新注册默认拒绝。MVP 不把旧写工具迁移到新审批协议，启用它们须另行规格和授权。
- 两个方向分别使用可轮换高熵服务凭据。跨主机使用 TLS；本机开发回环例外。凭据从运行环境/秘密设施注入，敏感头与票据从日志、错误、Trace 和证据中排除。

### 3. 模拟目标、回执与超时

- 模拟工具使用可重建的 `ragent_bit` 资源，与真实基础设施隔离。一次本地事务把目标变化和执行幂等应用记录一起提交；相同幂等键不会第二次改变目标。只读对账仅观察目标和应用记录，绝不调用写工具。
- 工具声明版本化的目标解析、最大执行时长、回执宽限期和超时评估能力。消费时以数据库可信时间固定 `executionStartedAt` 和 `receiptDueAt`。票据 TTL 不承担执行超时。
- 回执包含版本、Action/Execution/请求/工具/资源/幂等键、结果码、可信时间和安全摘要。`mcp-server` 先回调提交，随后响应携带同一回执；主应用通过第二路径幂等提交。重复回执复用权威结果，冲突结论留下核查信号且不覆盖终态。
- 超时只有取得“未开始或已回滚”的可验证证据才能 `FAILED`；否则立即 `UNKNOWN`，并只重试提交同一回执。`receiptDueAt` 清扫兜底。`UNKNOWN` 仅由绑定完整的迟到回执、只读对账或有专门权限与证据的人工结论收敛，不重放写工具。

### 4. Timeline、Outbox 与查询

- 沿用 Spec A 同一 Incident Timeline 的 `sequence_no`、`business_event_key`、安全摘要和只追加权限；新增处置事件类型与 `ACTION/EXECUTION` 关联实体，不改变已有五种类型和历史身份。只在有效业务转换时追加事实，重复回执不制造第二份成功事实。
- 审批决定与首次权威执行回执同 Action 状态、Timeline 和 Outbox 原子提交。新增事件使用独立类型和版本化负载，复用 Spec A 的稳定 envelope、Relay 和至少一次投递规则；不把所有 Timeline 事实默认发布。
- 人员查询按 Incident 权限展示 Action、Approval、Execution、Ticket 安全状态、回执与 `UNKNOWN` 证据；内部消费和回执接口使用独立服务身份。现有 Agent 会话恢复只查询权威 Action，绝不再次调用写工具。

### 5. 验证策略

- 最小单元检查覆盖规范绑定、状态转换、时钟边界与安全码。PostgreSQL 集成检查覆盖唯一约束、关闭/消费竞态、票据替换、Timeline/Outbox 原子性和回执冲突。
- 双进程本地 MCP 检查覆盖所有入口、旧写工具枚举与已知名称直调、未知注册默认拒绝、双向身份、篡改/重放、消费后崩溃、超时证明、双路径回执丢失、迟到回执与只读对账。使用隔离模拟库和固定凭据，记录脱敏证据；无真实处置或付费调用。

## Phase 0：研究结论

[research.md](./research.md) 记录现有代码边界、兼容扩展与安全协议取舍。技术未知项在设计中已给出可验证的选择；没有 `NEEDS CLARIFICATION`。跨 Spec 的长期决策已归档为 DEC-019/020。

## Phase 1：设计产物

- [data-model.md](./data-model.md)：Action/Approval/Execution/Ticket/回执与模拟目标逻辑模型、约束、状态和事务边界。
- [contracts/openapi.yaml](./contracts/openapi.yaml)：人员 Action/审批/查询/人工结论与内部票据消费、回执接口。
- [contracts/protected-mcp.md](./contracts/protected-mcp.md)：专用受保护 MCP 入口、绑定摘要、双向身份、回执与对账契约。
- [contracts/action-events.md](./contracts/action-events.md)：审批与权威回执领域事件的稳定身份、负载及复用 Spec A Relay 的规则。
- [quickstart.md](./quickstart.md)：实现后可复现的安全、竞态、故障和证据验收目录。

## 已批准的长期决策

1. **DEC-019：Incident 关闭与 Action 许可的原子裁决**。关闭使尚未开始的 Action/待消费票据失效；已开始执行继续接收回执并可从 `UNKNOWN` 收敛；期限判断在取得所需锁后读取主库可信当前时间，细化 DEC-012 的时钟表述。理由：这是 Spec A 生命周期与 Spec B 安全执行的长期共享边界，必须避免关闭/消费竞态或锁等待后的过期许可导致新副作用。
2. **DEC-020：共享 Timeline/Outbox 的兼容扩展与回执保留**。在同一 Incident 数据边界增加处置事实类型、关联实体和独立回执历史；审批与权威回执产生稳定待发布事件，原 Spec A 事件语义不变。理由：Spec A 数据模型当前对事件集合封闭，而迟到/冲突回执需要可审计的多条证据。

两项决策均已获用户确认并归档于 `docs/DECISIONS.md`。ADR 确认不代表本计划已确认；`tasks.md` 仍须等待计划单独确认。

## 复杂度跟踪

无宪章例外。独立回执历史是迟到回执、双路径去重与终态冲突保留所需的最小持久集合；不引入通用事件平台或跨库事务。
