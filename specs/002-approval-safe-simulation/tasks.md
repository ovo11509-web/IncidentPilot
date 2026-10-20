# 任务：审批到安全模拟处置

**输入**：[spec.md](./spec.md) v1.1、已确认并同步修订的 [plan.md](./plan.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)、DEC-019/020。

**范围**：本文件追踪 Spec B 的实施；当前用户已授权本仓库代码、测试、本地数据库迁移和必要文档修改，未授权提交、推送、合并、部署、付费服务或真实基础设施操作。编号 `TASK-###` 稳定，不因普通问题重排或复用。所有路径相对于仓库根目录；新路径为计划目标，不代表文件已存在。

**前置门禁**：Spec A 的 50 项任务已在当前工作区标为完成；2026-09-28 重新运行 Incident、主状态、Timeline、Outbox 和真实 PostgreSQL 聚焦检查通过，命令与结果见 `docs/changelog.md`。实施时遵守 DEC-011/012/017～020 和 `docs/development/testing.md`。

**执行记录**：开始任务前核对当前代码和 Schema，记录关联编号、变更范围及真实测试结果；测试任务先建立能揭示缺失行为的断言。重要新长期决策先获确认再追加 ADR。未勾选表示未实施。

## 格式与路径

- `[P]` 仅表示同阶段中涉及不同文件且不依赖未完成任务时可并行；无 `[P]` 按依赖执行。
- `[US1]`～`[US4]` 对应规格的四个用户故事。任务末尾的 `FR/SC` 是主要追踪键，跨故事要求可在多个任务中复用。
- 本次规格明确要求安全、并发、持久化和跨进程验收，因此各故事都安排了必要的自动化测试。

## Phase 1：准备（Spec A 门禁与最小工程基线）

**目标**：证实 Spec A 基线可用，并建立 Spec B 的隔离测试与配置入口；不重复建设 Spec A。

- [X] TASK-001 对照 `specs/001-reliable-alert-incident/tasks.md`、`incident/pom.xml` 与 `resources/database/schema_pg.sql` 核验 Spec A 的 Incident、状态机、Timeline、Outbox 和迁移已实现且真实 PostgreSQL 检查通过；将命令与结果记入 `docs/changelog.md`（FR-001、FR-034、FR-040）。
- [X] TASK-002 在 `incident/pom.xml` 与 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionPostgresSmokeTest.java` 复用 Spec A Testcontainers 配置，证明 Spec B 隔离数据库及迁移可启动和清理（FR-037、SC-009）。
- [X] TASK-003 [P] 在 `mcp-server/src/test/java/com/nageoffer/ai/ragent/mcp/simulation/SimulationTestFixture.java` 建立仅指向隔离 `ragent_bit` 的演示目标夹具，固定初始状态与应用次数读取方法（FR-002、FR-019、SC-010）。
- [X] TASK-004 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/config/ActionProperties.java` 与 `bootstrap/src/main/resources/application.yaml` 定义审批、授权、票据、执行、回执宽限和 Worker 的非秘密默认值及配置校验；双向凭据仅由运行环境注入（FR-005、FR-007、FR-014、FR-016、FR-020）。

**检查点**：Spec A 已由实际运行证据证明可作为依赖；测试夹具不会接触真实处置目标。

## Phase 2：基础能力（所有故事的阻塞前置）

**目标**：建立共享持久化、安全绑定和事件边界，后续故事不得绕过。

- [X] TASK-005 在 `resources/database/schema_pg.sql` 与新增 `resources/database/upgrades/v2.0.0/260923_incident_safe_action.sql` 中按 `specs/002-approval-safe-simulation/data-model.md` 添加 Action、Approval、Execution、Ticket、Receipt 约束与索引，兼容扩展同一 Timeline/Outbox 的受限类型和只追加权限；保留 Spec A 原五种事实及两种事件（FR-003、FR-009、FR-010、FR-034、FR-040）。
- [X] TASK-006 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionSchemaTest.java` 以真实 PostgreSQL 验证全量 Schema 与升级路径、来源/执行/票据唯一约束、外键、状态 CHECK、只追加回执/Timeline 和旧事件兼容性（FR-003、FR-010、FR-040）。
- [X] TASK-007 [P] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/dao/ActionMapper.java`、`ApprovalMapper.java`、`ExecutionMapper.java`、`TicketMapper.java` 与 `ReceiptMapper.java` 封装受限插入、锁定、条件转换及只读查询；不得暴露覆盖或删除回执的方法（FR-009、FR-010、FR-023）。
- [X] TASK-008 [P] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/contract/ActionBindingV1.java` 实现提议摘要与版本化长度前缀执行绑定，规范解析工具、目标、参数和幂等键；比较相同来源重试时不把新生成 ID/键混入提议摘要（FR-003、FR-004、FR-015）。
- [X] TASK-009 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionTransactionGuard.java` 复用 Spec A 同键事务锁与 Incident 行锁，统一锁后主库可信时间、预期状态/版本和 Timeline 序号分配；禁止持锁调用网络（FR-009、FR-010、FR-038、DEC-019）。
- [X] TASK-010 [P] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/security/ActionServiceAuthentication.java` 与 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/ProtectedServiceAuthentication.java` 实现两个方向独立、可轮换的服务身份校验和敏感头排除；跨主机只允许 TLS（FR-016、FR-017、FR-033）。
- [X] TASK-011 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/timeline/ActionTimelineWriter.java` 与 `ActionOutboxWriter.java` 复用 Spec A 同一只追加 Timeline、稳定事实键、Outbox envelope/Relay，并按 `contracts/action-events.md` 限定新增事件及安全负载（FR-034、FR-039、FR-040）。

**检查点**：基础约束与锁顺序有真实 PostgreSQL 证据；没有对外开放写工具。

## Phase 3：用户故事 1——创建并审批模拟处置（P1，审批 MVP）

**目标**：在已有 Incident 上形成不可变 Action 和人工审批事实，不启动模拟工具。

**独立测试**：预置未关闭 Incident，关闭调度 Worker；验证提议去重、人员权限、批准/拒绝/到期、确认卡失效及关闭竞争，目标变化为零。

### 测试（先建立失败断言）

- [X] TASK-012 [P] [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/contract/ActionApprovalContractTest.java` 按 `contracts/openapi.yaml` 覆盖提议、详情和决定的权限、真实 HTTP 分类、来源身份去重及冲突（FR-001～FR-008、FR-033）。
- [X] TASK-013 [P] [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionApprovalRaceTest.java` 验证并发批准/拒绝/过期、锁等待越过截止、Incident 关闭先胜出与审批先胜出的持久事实（FR-007～FR-010、FR-038、SC-003、SC-011）。

### 实现

- [X] TASK-014 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionProposalService.java` 校验当前 Incident 与已认证来源、唯一请求身份、白名单模拟目标及不可变绑定，原子创建 Action、PENDING Approval 和提议 Timeline；同摘要重试返回原 Action（FR-001～FR-005、FR-034、FR-036）。
- [X] TASK-015 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionDecisionService.java` 从服务端会话执行当前权限、绑定与锁后时限检查，原子批准/拒绝、追加 Timeline 和审批事件 Outbox；迟到或重复决定不再授权（FR-006～FR-010、FR-039）。
- [X] TASK-016 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/web/ActionController.java` 实现人员提议、详情与审批端点，来源类型和审批人只能从认证上下文获取；安全响应不回传秘密（FR-003、FR-006、FR-033）。
- [X] TASK-017 [US1] 在 `agent/src/main/java/com/nageoffer/ai/ragent/agent/confirm/IncidentActionConfirmationAdapter.java` 将确认卡限制为引用 `actionId` 和展示 Incident 权威状态；旧消息、自然语言或卡片重放不能调用写工具（FR-011、SC-002）。
- [X] TASK-018 [US1] 在 Spec A 的 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentLifecycleService.java` 的 `RESOLVED -> CLOSED` 事务中接入 Action 许可失效：未启动 Action、PENDING Approval 与 ISSUED Ticket 同事务失效并追加各自 Timeline，已消费执行保留收敛路径（FR-038、DEC-019）。
- [X] TASK-019 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/worker/ActionExpiryWorker.java` 以可信时钟清扫待审批与已批准过期状态；`DISPATCHING` 无有效票据但授权未过期时交恢复，不误判为时间过期（FR-005、FR-007、FR-028）。
- [X] TASK-020 [US1] 运行 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/contract/ActionApprovalContractTest.java` 与 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionApprovalRaceTest.java` 中的审批 MVP 检查，并将真实命令、状态与零目标变化证据记录于 `docs/changelog.md`（SC-002、SC-003、SC-011）。

**检查点**：US1 可独立验证人工授权事实，尚无执行副作用。

## Phase 4：用户故事 2——一次性授权执行安全演示工具（P2）

**目标**：有效批准 Action 只通过受保护入口、在线票据消费和执行幂等记录改变一次隔离目标。

**独立测试**：预置已批准 Action 与模拟目标，驱动 Worker、MCP Server 和两条回执路径，验证正常/篡改/重放/旁路与确定结果。

### 测试（先建立失败断言）

- [X] TASK-021 [P] [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionDispatchTicketTest.java` 验证唯一 Execution、随机票据摘要、单张 ISSUED、授权截止、重复消费及 Incident 关闭/消费竞态（FR-012～FR-015、FR-018、FR-021、FR-038）。
- [X] TASK-022 [P] [US2] 在 `mcp-server/src/test/java/com/nageoffer/ai/ragent/mcp/simulation/ProtectedSimulationRouteTest.java` 枚举全部 MCP 注册/发现入口，验证旧写工具逐个已知名称直调均不可执行、未知新注册默认拒绝、经核查的只读工具仍可调用；对专用入口另试无身份、无票、绑定篡改与重放，断言全部越权副作用为零（FR-016～FR-019、FR-041、SC-002、SC-010、SC-013）。
- [X] TASK-023 [P] [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionReceiptTest.java` 验证版本化回执、双路径相同回执幂等、迟到已认证回执、同 ID 异内容及确定终态冲突不覆盖（FR-022～FR-025、FR-039、SC-006）。

### 实现

- [X] TASK-024 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/worker/ActionDispatchWorker.java` 事务领取 APPROVED、建立唯一 Execution、签发 256 位票据并提交 DISPATCHING 后才调用出站端口；固定请求 ID 与执行幂等键（FR-012～FR-014、FR-020）。
- [X] TASK-025 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionTicketConsumptionService.java` 对双向服务身份及完整绑定做锁后在线校验，一次性消费、固定 executionStartedAt/receiptDueAt 并原子写 Timeline；重复或失效票据不得启动（FR-015～FR-018、FR-021、FR-038）。
- [X] TASK-026 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/web/ActionInternalController.java` 按 `contracts/openapi.yaml` 暴露仅服务可访问的票据消费和回执提交端点，拒绝未认证自报 Action 身份及不安全响应（FR-016、FR-017、FR-022、FR-033）。
- [X] TASK-027 [US2] 在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/config/McpServerConfig.java` 与 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/config/ProtectedSimulationMcpConfig.java` 审核旧工具并对普通 MCP 入口实行服务端只读允许清单，未纳入 Incident 审批链的写工具和未知新注册均不进入可执行注册表；仅将 `simulation.set-state` v1 注册到专用 `/mcp/incident`，已知名称在错误入口直调失败（FR-002、FR-017、FR-041、SC-002、SC-013）。
- [X] TASK-028 [US2] 在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/SimulationToolExecutor.java` 和 `mcp-server/src/main/resources/db/bit-schema.sql` 实现仅 `SIM_SERVICE`、`READY/PAUSED` 的隔离目标，本地事务同时写目标与执行键唯一的前后状态应用记录；重复键不二次改变（FR-019、FR-020、SC-001、SC-010）。
- [X] TASK-029 [US2] 在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/ProtectedSimulationTool.java` 将身份与在线消费守卫置于所有执行路径的副作用之前，产生版本化结构化回执并先回调 Incident、后返回同一回执（FR-015～FR-018、FR-022～FR-024）。
- [X] TASK-030 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionReceiptService.java` 验证服务身份和全部不可变绑定，处理确定结果、执行前拒绝、重复/冲突回执，原子保留回执历史、Action、Timeline 与适用 Outbox（FR-022～FR-025、FR-034、FR-039）。
- [X] TASK-031 [US2] 在 `agent/src/main/java/com/nageoffer/ai/ragent/agent/tool/IncidentRemediationDispatcher.java` 实现仅供 Incident Worker 调用的专用 MCP 出站适配：独立服务凭据、稳定请求 ID、消费前可证实重试与响应回执第二路径提交；消费后不盲目重调写工具（FR-012、FR-016、FR-023、FR-026）。
- [X] TASK-032 [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ProtectedSimulationE2ETest.java` 以主应用和 MCP Server 双进程检查正常授权、旧写工具停用与旧入口旁路、一次副作用、结构化回执及双向身份（FR-017～FR-023、FR-041、SC-001、SC-002、SC-010、SC-013）。

**检查点**：US2 在预置批准 Action 下完成隔离模拟处置；任何未授权路径目标变化次数为零。

## Phase 5：用户故事 3——故障恢复与 UNKNOWN 证据收敛（P3）

**目标**：恢复 Worker、超时和回执故障时不重放副作用，以可验证证据收敛不确定结果。

**独立测试**：预置 DISPATCHING 或 EXECUTING，注入崩溃、票据替换竞态、回执丢失、超时和对账证据。

### 测试（先建立失败断言）

- [X] TASK-033 [P] [US3] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionRecoveryRaceTest.java` 验证明文丢失、替代票据、旧票迟到、已消费拒补、截止后恢复和同一幂等键（FR-026～FR-028、SC-007）。
- [X] TASK-034 [P] [US3] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionUnknownConvergenceTest.java` 覆盖可证实回滚/未开始、证据不足超时、receiptDueAt、迟到回执、只读对账和人工证据收敛（FR-029～FR-032、SC-004、SC-005）。

### 实现

- [X] TASK-035 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/worker/ActionDispatchRecoveryWorker.java` 对失去明文的 DISPATCHING 先核对 Incident/授权/已消费事实，再原子过期旧票并签发同一 Execution 的替代票据；已消费或不可证实状态不补发（FR-026、FR-027、FR-038）。
- [X] TASK-036 [US3] 在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/SimulationTimeoutEvaluator.java` 强制声明的最大执行时长；仅凭执行已停止且未开始或已回滚的可验证证明产生 FAILED，否则立即提交同一 UNKNOWN 回执并有限重试回执提交（FR-020、FR-029、FR-030）。
- [X] TASK-037 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/worker/ActionReceiptDeadlineWorker.java` 锁后核对绝对 receiptDueAt 和确定回执，将到期 EXECUTING 转 UNKNOWN、追加 Timeline 并发起只读对账；不得再次派发工具（FR-021、FR-030）。
- [X] TASK-038 [US3] 在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/SimulationReconciliationReader.java` 只读目标与按执行键的前后状态应用记录；记录不存在须结合执行已停止/回滚证明，演示库证据丢失保持不确定（FR-019、FR-031、FR-032）。
- [X] TASK-039 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionReconciliationService.java` 校验只读证据身份，已应用收敛 SUCCEEDED、可证实未应用收敛 FAILED、否则保持 UNKNOWN；终态冲突只留核查信号（FR-031、FR-032、FR-034）。
- [X] TASK-040 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionManualConclusionService.java` 与 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/web/ActionController.java` 的人工结论入口强制 `INCIDENT_ACTION_RESOLVE_UNKNOWN`、原因、可核查证据与预期版本；只允许 UNKNOWN 到确定状态且不覆盖冲突终态（FR-031、FR-033、FR-034）。
- [X] TASK-041 [US3] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionFailureInjectionE2ETest.java` 跨进程注入消费后崩溃、双路径回执失败、关闭后回执、超时与对账故障，核对副作用最多一次及 UNKNOWN 证据保留（FR-023、FR-029～FR-032、FR-038、SC-004、SC-005、SC-011）。

断点续跑核对：故障注入断言集中在 `ProtectedSimulationE2ETest`，未另建任务建议名的测试类。2026-10-01 Docker Engine 恢复后，该测试跨独立 MCP JVM 完成消费后崩溃、双路径回执失败、关闭后回执、只读对账故障，以及持有隔离模拟库目标行锁触发 100 ms 执行超时；Surefire 记录 1 测试、0 失败、0 错误。超时后应用次数仍为 2，目标保持 PAUSED；子进程日志敏感值扫描通过。具体命令与未穷举范围见 `docs/changelog.md`。

**检查点**：US3 不以网络超时或证据缺失推定 FAILED，未知状态有可审计收敛路径。

## Phase 6：用户故事 4——查询、会话恢复与可复现验收（P4）

**目标**：人员、Agent 与验收者可凭稳定标识理解权威状态、事件和下一步。

**独立测试**：预置全部 Action 状态并重启 Agent，执行只读查询、Outbox 故障和脱敏证据目录。

### 测试（先建立失败断言）

- [X] TASK-042 [P] [US4] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/contract/ActionQueryContractTest.java` 覆盖按 actionId/incidentId 查询的权限、跨 Incident 隐藏、票据安全状态、五种终止/未知原因与下一步（FR-033、FR-035、SC-008）。
- [X] TASK-043 [P] [US4] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionOutboxCompatibilityTest.java` 验证审批/回执 Outbox 提交、发布失败和重复投递稳定身份，Spec A 两类旧事件负载及 Timeline 键不变（FR-034、FR-039、FR-040、SC-012）。

### 实现

- [X] TASK-044 [US4] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionQueryService.java` 与 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/web/ActionController.java` 的查询入口按人员权限返回 Action、Approval、Execution、票据安全状态、回执历史、Timeline 及明确下一步；不泄露秘密或任意参数（FR-033、FR-035）。
- [X] TASK-045 [US4] 在 `agent/src/main/java/com/nageoffer/ai/ragent/agent/confirm/IncidentActionStatusAdapter.java` 实现会话恢复只查权威 Action；APPROVED 仍交 Worker，UNKNOWN 不解释为可重试失败（FR-011、FR-012、FR-035、SC-008）。
- [X] TASK-046 [US4] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionAuditEvidenceTest.java` 核对 Timeline Incident 内顺序与稳定键、回执/Outbox 去重、脱敏日志和证据，覆盖 `specs/002-approval-safe-simulation/quickstart.md` 的 B01～B12（含 B04 旧写工具停用）（FR-034、FR-037、FR-039～FR-041、SC-009、SC-012、SC-013）。
- [X] TASK-047 [US4] 在 `docs/changelog.md` 记录 `quickstart.md` B01～B12 的真实命令、环境、输入、预期/实际状态与脱敏证据位置；未运行场景明确标为未验证或受阻，不声称设计已通过验收（FR-037、SC-009）。

**检查点**：US4 可独立查询预置事实；完整链路验收须在 US1～US3 完成后执行。

## Phase 7：完善与横切门禁

- [X] TASK-048 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/security/ActionSecretLeakTest.java` 扫描响应、日志与验收证据中的凭据、票据明文、不可信完整参数及敏感头；跨主机无 TLS 配置须失败关闭（FR-016、FR-033、SC-009）。
- [X] TASK-049 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionStateMachineTest.java` 枚举 Action/Approval/Ticket 合法与非法转换及预期版本、锁后截止竞争，证明无终态重入或 UNKNOWN 回执行态（FR-009、FR-010）。
- [X] TASK-050 在 `specs/002-approval-safe-simulation/quickstart.md` 与 `docs/changelog.md` 对照实际运行完善精确测试入口和失败证据；仅在全部阻断项解除后记录真实通过范围，不扩大到真实基础设施（FR-037、SC-009、SC-010）。

## 依赖关系与执行顺序

- Phase 1 的 `TASK-001` 是 Spec A 实施门禁；未通过不得把 Phase 2～7 标为已完成。`TASK-002`～`TASK-004` 依赖实际模块或夹具就绪。
- Phase 2 阻塞全部用户故事；Schema/迁移 `TASK-005` 先于数据库测试与 Mapper，`TASK-009` 先于所有事务服务。MVP 对外启用 MCP 前必须完成 `TASK-022` 与 `TASK-027` 的旧写工具停用验收。
- US1 依赖 Phase 2，交付审批 MVP；US2 可用预置 APPROVED Action 独立测试，但真实链路依赖 US1 的批准事实和关闭挂钩。
- US3 可用预置 DISPATCHING/EXECUTING 独立测试；真实恢复依赖 US2 的 Execution、票据、模拟应用记录和回执协议。
- US4 可用预置事实独立测试；完整 B01～B12 验收依赖 US1～US3。Phase 7 最终门禁在全部计划故事完成后运行。
- 每个故事中先建立测试断言，再实现领域服务、端点和跨进程集成；本表的执行检查点不得被设计文档替代。

## 并行示例

- **US1**：`TASK-012` 与 `TASK-013` 在不同测试文件建立契约和竞态断言；实现阶段依赖共享事务守卫，按 `TASK-014`～`TASK-019` 顺序整合。
- **US2**：`TASK-021`、`TASK-022`、`TASK-023` 分别在 Incident、MCP 和回执测试文件建立失败断言；服务与工具完成后执行 `TASK-032`。
- **US3**：`TASK-033` 与 `TASK-034` 分别验证补票竞争和 UNKNOWN 收敛，随后由 `TASK-041` 跨进程整合。
- **US4**：`TASK-042` 与 `TASK-043` 分别验证查询与 Outbox 兼容；实现后执行 `TASK-046` 的证据核查。

## 实施策略

1. 先完成并验证 Spec A，再完成 Phase 1、Phase 2；不得为绕过缺失基线另建平行 Incident 表或时间线。
2. US1 是审批 MVP：只形成授权事实，模拟目标变化必须为零。
3. US2 增加一个受保护模拟工具，形成正常“审批到模拟处置”纵向链路；US3 再补齐故障证明与 UNKNOWN 收敛。
4. US4 与 Phase 7 提供人员可见状态、双路径和 Outbox 验收证据；所有可靠性结论以实际运行记录为准。

## Phase 8：验收收敛（B06，2026-10-04）

用户授权继续执行只读盘点发现的 B06 缺口；不修改既有执行协议，不增加生产故障开关或 Schema。本阶段仅补 B06，其他盘点缺口保持开放。

- [X] T051 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionRecoveryRaceTest.java` 以真实 PostgreSQL 锁和并发线程验证消费/恢复两种胜出顺序，断言最多一张 ISSUED、一个 Execution、稳定 executionKey/requestId、旧票拒绝与消费后拒补（SC-007、FR-027；partial）。2026-10-04 定向类 4 项通过，0 失败/错误/跳过；日志 `incident/target/b06-race.log`。
- [X] T052 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java` 复用隔离完整应用、独立 MCP 和测试专用回调代理，签票未消费时实际杀死主应用 Worker JVM，重启后由调度恢复替票，真实旧票迟到拒绝且模拟应用一次（FR-027、FR-028、SC-007、quickstart:B06；partial）。2026-10-04 真实跨 JVM 场景通过，运行目录 `bootstrap/target/b06-evaluation/3fbfcb09-8099-4b0a-9bfc-f1f381bb9fc0/`；同轮旧正常 Worker 回归的过时参数断言失败，另行修正重跑，不将整轮记为通过。
- [X] T053 运行新增最小检查及适用共享边界回归，保存脱敏 B06 运行清单、状态/计数/Timeline/日志证据，更新 `quickstart.md`、`docs/changelog.md` 和项目状态；未验证的其他场景不关闭（FR-037、SC-009；partial）。2026-10-04 共享边界 15 项与 B06 场景通过；正常 Worker 旧断言按已批准 Spec D 参数投影修正后单独重跑 1 项通过、Maven 退出 0。共 17 个不同测试均有通过记录，0 最终失败/错误/跳过；两次初始失败保留，不把失败的联合 Maven 记为成功。制品清单及摘要见同一运行目录的 `manifest.json`，项目级 LAB-000 / ACT-003 保持开放。

## Phase 9：验收收敛（B09 双通道故障）

沿用已批准协议，补只读盘点 G02；回执/截止并发 G03 和其他缺口仍开放。功能指针保持 Spec D，不把目录编号当作 Git 分支。

- [X] T054 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java` 复用隔离完整 Worker/MCP 和测试代理，分别阻断回执回调、丢失真实工具响应、同时丢失两通道；断言真实 Dispatcher 不重调已消费工具，单通道仍收敛、双通道到期 UNKNOWN，原迟到回执只生效一次且冲突不覆盖（FR-023、FR-030、FR-031、SC-004/005/006；partial）。2026-10-04 三个子场景通过；UNKNOWN 快照场景额外令只读对账暂时不可用，正常可用时自动对账可迅速收敛，不改变生产行为。运行目录 `bootstrap/target/b09-evaluation/9963cf7f-016e-4cf1-90da-1375a452a740/`。
- [X] T055 运行新增最小检查及适用回执/Dispatcher 回归，保存三个子场景的输入、通道故障计数、状态、真实应用/回执/Timeline/Outbox 和日志扫描证据，更新 Spec B quickstart 及项目状态；不关闭未验证的 B09 并发、B11 和威胁模型（FR-037、SC-009；partial）。2026-10-04 联合回归 12 项通过（Incident 7、Agent 3、Bootstrap 2，含 B09 与 B06），0 失败/错误/跳过，Maven 退出 0；日志 `bootstrap/target/b09-final-regression.log`，4m56s。三个先前失败及局部制品保留，清单/摘要见运行目录；LAB-000 / ACT-003 保持进行中。

## Phase 10：验收收敛（并发、发布恢复与安全证据）

按用户“继续完成”推进只读盘点 G03～G06；描述既有已批准边界，不添加新权限、协议、生产资源或数据库表。

- [X] T056 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionReceiptTest.java` 用真实 PostgreSQL 锁协调回执/截止清扫两种胜出次序，核对确定结果、UNKNOWN 审计、只读对账请求、唯一回执/Timeline/Outbox 及重复无新事实（FR-010/030/031、SC-005/006；partial）。2026-10-04 最小 5 项、联合该类 5 项均通过；真实 Lock 等待，fixture 回执/截止与对账替身边界见 evidence-index。
- [X] T057 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionOutboxCompatibilityTest.java` 对审批/回执已提交事件经真实 OutboxRelay 注入发送失败和数据库发布标记失败，证明稳定 eventId/envelope、重投与原业务事实保持，保留模拟 Producer 边界（FR-039、SC-012、B11；partial）。2026-10-04 最小 2 项、联合该类 2 项均通过，两事件各尝试 3 次并 PUBLISHED；没有真实 Broker 或 MCP 应用。
- [X] T058 在 `docs/security/incident-action-threat-model.md` 固化当前实现的资产、信任边界、攻击/控制/回归映射、信任假设和剩余风险；不把作者核对当第三方安全认证或新增设计批准（FR-016/033/037、宪章三/五；partial）。已核对 11 类威胁、当前代码入口及本地回归，外部安全评审 TBD。
- [X] T059 在 `specs/002-approval-safe-simulation/evidence-index.md` 索引 B01～B12 的实际测试入口、证据路径、运行层级和未覆盖范围；为本轮并发/B11 保存独立安全运行清单与事实快照，历史证据不冒充本轮重跑（SC-009；partial）。归档 run-742defc2-0468-4e47-b99d-34cb1fe77d8a，14 份本轮 XML、三场景、日志及哈希；历史全场景同格式 manifest 尚未补齐，不据此关闭 LAB-000。
- [X] T060 先运行新增最小检查，再运行适用共享安全回归；核对文档相对链接、秘密及原文件保留，更新 quickstart/changelog/项目状态，未验证或更广目标继续开放（FR-037、SC-009；partial）。2026-10-04 最小 5/2 项和联合 38 项通过，0 失败/错误/跳过，退出 0；文档链接与 git diff --check 通过，1735 个原文件无丢失/意外变更，HEAD/分支/功能指针保持、暂存为空、隔离 JVM/容器退出。扫描范围和历史制品缺口见 evidence-index/manifest；SEC-001 当前模拟范围完成，LAB-000/ACT-003 等更广目标保持开放。

## Phase 11：验收收敛（逐场景证据闭环）

2026-10-05 用户授权继续验收收尾；不扩展生产能力、Broker 需求或权限设计。实施前核对三处 partial 缺口：完整 Bootstrap 记录、独立 MCP 故障记录与人工结论记录尚未同格式归档；均映射 FR-037/SC-009，未发现 CRITICAL 一致性问题。前置清单 20/20，只读路径解析保持 Spec D 功能指针。

- [X] T061 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java` 复用实际隔离测试，为普通/拒绝/截止/TLS/崩溃/双通道场景保存脱敏持久化投影、模拟应用计数和逐实际秘密扫描结果，保持原断言及生产协议（FR-037、SC-009；partial）。2026-10-05 普通完整链路最小 1 项通过，随后七个入口全部通过，各存独立日志/快照；扫描本次会话、凭据与所有已捕获票据，包括消费先于关闭的票据，命中 0。
- [X] T062 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ProtectedSimulationE2ETest.java` 为实际旁路、绑定/重放、消费后崩溃、两种超时和只读对账保存分阶段事实，扫描所有本次票据及双向凭据；不把精简主服务或 SQL 截止 fixture 当完整 Worker 自然截止（FR-037、SC-009；partial）。最小与联合该类均通过，八个阶段快照、六张实际票据及双向凭据扫描为 0；固定认证替身和 SQL 截止明确标注。
- [X] T063 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionUnknownConvergenceTest.java` 归档证据不足/回滚/已应用/人工结论的真实数据库事实和重放/冲突验证入口，明确证据与认证替身，无实际 MCP 应用（FR-033/037、SC-009；partial）。最小与联合 4 项均通过，复用 ActionDbFixture 白名单 SQL 投影，四个独立成功目录；未宣称人员真实 HTTP 人工结论或实际应用。
- [X] T064 先运行新增最小检查，再复跑适用隔离边界，按 B01～B12 保存统一场景 manifest、XML/原制品索引/哈希；核对 Spec A 已有故障目录、已批准验收门槛和保密/保留检查后更新 evidence-index、quickstart 和项目状态，只关闭满足本地范围门槛的 LAB-000/ACT-003（FR-037/039、SC-009/012；partial）。最小 5/1 项及联合 54 项通过，0 失败/错误/跳过、退出 0；首次 Docker 不可用的 5 个环境错误原样归档。runId 4cf89770-cdca-4f7d-a675-2cc33798ac49 含 12 个 B 场景清单、A 核心故障清单、18 XML、15 证明目录及哈希。脚本拒绝失败日志/旧 XML 的两项守卫检查通过；LAB-000/ACT-003 当前本地范围收尾，真实 Broker/跨主机/生产验证保持范围外。
