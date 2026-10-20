# 任务：OBS-001 统一关联标识与 Trace

**输入**：`specs/005-incident-correlation-trace/` 的规格、计划、研究、数据模型和契约。

**状态**：2026-10-05 用户已授权实施；本地安全模拟范围实施与验收完成，27/27；不代表生产或追踪后端验收。

**前置条件**：用户确认本组设计且明确允许本地实施；先speckit-analyze、重新核对dirty工作区和实时调用者。用户已以“实施”确认本组设计；不追加 Git/部署/远程操作授权。

**测试**：规格FR-012明确要求本地可复现验证。每项先最小有效检查，记录真实结果后才标记完成。测试classpath exporter不能作为生产插件扩展。

## 格式：`[ID] [P?] [Story] 描述`

路径均为仓库相对路径；落点调整与真实结果见下方实施证据。无本轮提交/部署任务。

## Phase 1：准备

- [X] T001 设计获批后在 `docs/DECISIONS.md` 追加统一身份/短时Trace/历史兼容决策，在 `docs/TODO.md` 记录实施授权及范围；确认既有修改保留基线。
- [X] T002 在 `framework/pom.xml`、`incident/pom.xml`、`mcp-server/pom.xml` 核对并显式声明所需现有OTel依赖，沿用 `pom.xml` BOM1.62.0；不引入新服务/框架/MCP对Incident运行依赖。

## Phase 2：公共基础（阻塞前置）

- [X] T003 在 `framework/src/test/java/com/nageoffer/ai/ragent/framework/observability/ObservabilityScopeTest.java`、`ObservabilityConfigurationTest.java` 及 `agent/src/test/java/com/nageoffer/ai/ragent/agent/trace/AgentTracingConfigurationTest.java` 添加白名单、预算、scope恢复、4组合开关/全局单owner及出口失败测试。
- [X] T004 在 `framework/src/main/java/com/nageoffer/ai/ragent/framework/observability/ObservabilityConfiguration.java` 和 `agent/src/main/java/com/nageoffer/ai/ragent/agent/trace/AgentTracingConfiguration.java` 协调条件SDK owner及现有Agent exporter/middleware；在 `bootstrap/src/main/resources/application.yaml` 增加默认关闭且无明文凭据的OBS配置，复跑T003。
- [X] T005 在 `framework/src/main/java/com/nageoffer/ai/ragent/framework/observability/ObservationScope.java` 实现固定白名单日志/span、request attribute及Context/MDC快照/恢复；无业务Mapper、无任意内容serializer或全局异步hook，复跑scope最小测试。

检查点：基础scope与SDK装配通过最小测试后，开始业务故事；配置失败与运行期出口失败分别验证。

## Phase 3：US1 - 请求与业务身份可检索（P1，首个增量）

**独立测试**：OBS关闭时Q01/Q02，实际权威身份不变；日志足够桥接Alert到Incident。

- [X] T006 [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/observability/IncidentRequestIdentityTest.java` 添加成功/早拒绝/登录/非法输入/冲突/ERROR分派的body-header-log一致性及外来ID忽略测试。
- [X] T007 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/observability/IncidentRequestObservationFilter.java` 覆盖人员/内部/agent诊断路由，在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/web/AlertIngressFilter.java` 复用已分配attempt，保留HMAC/输入检查顺序，复跑T006。
- [X] T008 [US1] 修改 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/web/AlertController.java`、`IncidentErrorAdvice.java`、`incident/src/main/java/com/nageoffer/ai/ragent/incident/core/web/IncidentController.java`、`IncidentOperationsController.java`、`incident/src/main/java/com/nageoffer/ai/ragent/incident/action/web/ActionController.java`、`ActionInternalController.java` 为共享attempt；保留0/SUCCESS、正文意图和dispatch requestId，复跑入口/Action契约测试。
- [X] T009 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/web/DiagnosisResponses.java`、`DiagnosisErrorAdvice.java`、`agent/src/main/java/com/nageoffer/ai/ragent/agent/diagnosis/IncidentDiagnosisController.java` 接入共享HTTP requestId与安全错误上下文；不修改snapshot/hash，复跑诊断接口与T006。
- [X] T010 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/observability/IncidentObservation.java` 及 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/service/AlertReceiveService.java`、`incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentAggregationService.java` 添加实际身份桥接和白名单事件；相关Action/诊断日志只从原服务已知事实填字段，在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/observability/IncidentCorrelationTraceTest.java` 覆盖两Alert/重复/非anchor/无归属，无回写历史。

## Phase 4：US2 - 实际诊断与模拟调用Trace（P1）

**独立测试**：Q03/Q04/Q05；真实阶段与父子/业务结果核对，普通聊天回归保持。

- [X] T011 [P] [US2] 在 `agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/IncidentDiagnosisTraceTest.java` 用真实本地Runbook与collecting exporter验证retrieve/generate/validate和REUSED，无模型或内容采集。
- [X] T012 [US2] 在 `agent/src/main/java/com/nageoffer/ai/ragent/agent/diagnosis/IncidentDiagnosisOrchestrator.java`、`ExtractiveDiagnosisGenerator.java`、`DiagnosisEvidenceValidator.java` 添加实际阶段scope，并在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/service/DiagnosisRecordService.java`、`DiagnosisActionSelectionService.java` 记录begin/complete/选择的真实身份，复跑T011及诊断安全回归。
- [X] T013 [P] [US2] 在 `mcp-server/src/test/java/com/nageoffer/ai/ragent/mcp/simulation/ProtectedSimulationTraceTest.java` 和 `agent/src/test/java/com/nageoffer/ai/ragent/agent/tool/IncidentRemediationDispatcherTest.java` 添加meta七字段不变、无效trace降级、认证前无可信业务字段、Context注入/timeout线程无串线测试。
- [X] T014 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/worker/ActionDispatchWorker.java`、`ActionDispatchRecoveryWorker.java`、`ActionExpiryWorker.java`、`ActionReceiptDeadlineWorker.java` 及 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionProposalService.java`、`ActionDecisionService.java`、`ActionReconciliationService.java`、`ActionManualConclusionService.java` 包装每条实际操作；保留锁/截止/身份/重试与清扫结果，最小状态/恢复测试通过。
- [X] T015 [US2] 在 `agent/src/main/java/com/nageoffer/ai/ragent/agent/tool/IncidentRemediationDispatcher.java` 建立dispatch CLIENT scope、捕获不可变父Context并在transport customizeRequest注入traceparent；不修改meta/参数/票据，复跑Dispatcher测试及Agent现有Trace场景。
- [X] T016 [US2] 在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/config/McpObservabilityConfiguration.java`、`ProtectedSimulationMcpConfig.java` 和 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/ProtectedSimulationTool.java` 添加独立SDK/受保护HTTP SERVER scope与GRANTED后可信属性；禁止初始化/认证失败伪造execute，复跑T013/Route安全测试。
- [X] T017 [US2] 在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/SimulationTimeoutEvaluator.java` 的实际task提交处显式传Context和白名单字段，execute span按实际线程完成关闭并finally恢复；不以取消证明停止，复跑并发/超时/复用测试。
- [X] T018 [US2] 在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/IncidentCallbackClient.java`、`agent/src/main/java/com/nageoffer/ai/ragent/agent/tool/IncidentReconciliationClient.java` 和 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionTicketConsumptionService.java`、`ActionReceiptService.java` 添加真实CLIENT/consume/accept scope和安全结果；消费/回执正文及digest保持，复跑回执、绑定、UNKNOWN回归。
- [X] T019 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/outbox/worker/OutboxRelay.java`、`incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/worker/AlertWorker.java` 及 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentTransitionService.java`、`incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/service/AlertReplayService.java` 包装单条操作与Relay尝试；payload/eventId/历史事实不变，复跑Alert/Outbox恢复最小测试。

## Phase 5：US3 - 恢复与故障证据（P1）

**独立测试**：Q06～Q10；原业务身份/结果保持，不因采集失败重执行。

- [X] T020 [US3] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/observability/IncidentCorrelationTraceTest.java` 补历史Timeline/诊断hash/Outbox payload原样、多值correlation及无持久父Context时新root断言，复跑真实PostgreSQL测试。
- [X] T021 [US3] 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentObservabilityIsolatedTest.java` 与 `mcp-server/src/test/java/com/nageoffer/ai/ragent/mcp/simulation/TestSpanFileExporter.java` 添加测试限定的实际SpanData exporter/双JVM夹具，复用 `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java` 隔离启动模式；动态端口、临时库、无Collector部署。
- [X] T022 [US3] 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentObservabilityIsolatedTest.java` 实际运行两Alert→诊断→拒绝0应用→新批准1应用，采集双JVM父子/线程span及可信业务事实；必须核对SERVER执行与CLIENT真实parent而非只看header。
- [X] T023 [US3] 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java` 和 `IncidentObservabilityIsolatedTest.java` 复用真实B06崩溃/重启、B09通道丢失/UNKNOWN/迟到回执，增加稳定身份/新Trace与秘密扫描；在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionOutboxCompatibilityTest.java` 证明Relay原event重投与新attempt。
- [X] T024 [US3] 在 `framework/src/test/java/com/nageoffer/ai/ragent/framework/observability/ObservabilityConfigurationTest.java`、`mcp-server/src/test/java/com/nageoffer/ai/ragent/mcp/simulation/ProtectedSimulationTraceTest.java` 和 `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentObservabilityIsolatedTest.java` 完成关闭/组合开关、出口503/阻塞/饱和、无效传播、并发/复用与本次全部实际凭据扫描；结果和应用计数保持，输出固定安全字段。
- [X] T025 [US3] 按 `specs/005-incident-correlation-trace/quickstart.md` 执行共享Alert/Action/回执/Relay/MCP/AgentTrace回归，再按公共依赖影响运行默认Java门禁；实际命令/测试数/跳过/环境故障单独记录，不启用旧外部探针或开发库。

## Phase 6：完善与收尾

- [X] T026 在 `specs/005-incident-correlation-trace/quickstart.md`、`docs/changelog.md`、`docs/ARCHITECTURE.md`、`docs/PROJECT_CONTEXT.md`、`docs/TODO.md` 记录实际实现/本轮manifest/runId/测试计数及限制，更新任务完成状态；归档 `bootstrap/target/obs-evaluation/` 的安全制品及hash，不覆盖历史。
- [X] T027 检查 `specs/005-incident-correlation-trace/` 的FR/SC/任务映射、所有变更与文件保留基线、暂存/HEAD/branch，报告真实运行/跳过范围及作者审阅；未获额外授权不提交/推送/合并/部署。

## 依赖关系与执行顺序

T001→T002→T003→T004→T005阻塞全部故事。US1=T006～T010；US2=T011～T019；US3=T020～T025；最后T026/T027。每个实现任务之前先编写对应最小检查，实际通过才推进。

US1可在Trace关闭状态独立验收；US2用受信夹具运行诊断/MCP边界；US3集成前两者的本地恢复。编号是执行顺序，故事内共享文件不并发编辑。

## 并行示例

仅未来授权实施时的机会，本轮不派实施代理：US1入口契约与数据库身份测试可由不同人员独立编写后串行集成；US2的T011诊断测试与T013独立MCP测试文件不重叠，可并行；US3的故障场景都共享双JVM夹具，按顺序运行避免覆盖证据。标记[P]只指无文件冲突的测试工作，测试完成不等于对应实现完成。

## FR/SC覆盖

| 要求 | 任务 | 证明 |
| --- | --- | --- |
| FR-001 | T005 T007 T008 T009 T010 | Q01 Q02 |
| FR-002 | T006 T007 T008 T009 | Q01 |
| FR-003 | T010 T020 T022 | Q02 Q04 |
| FR-004 | T010 T012 T014 T018 T019 T022 | Q02 Q03 Q04 Q08 |
| FR-005 | T004 T005 T012 T014 T019 T023 | Q03 Q04 Q06 Q08 |
| FR-006 | T011 T012 | Q03 |
| FR-007 | T013 T015 T016 T018 T022 | Q04 |
| FR-008 | T005 T013 T017 T024 | Q05 Q09 |
| FR-009 | T014 T019 T023 | Q06 Q07 Q08 |
| FR-010 | T003 T005 T013 T024 | Q10 |
| FR-011 | T003 T004 T020 T024 T025 | Q07 Q08 Q09 |
| FR-012 | T021 T022 T023 T024 T025 T026 | Q01～Q10 |
| FR-013 | T002 T003 T004 T015 T025 | Q09及原Agent回归 |
| FR-014 | T001 T020 T026 T027 | diff/文件保留/授权核对 |
| SC-001 | T006 T007 T008 T009 | Q01 |
| SC-002 | T010 T020 T022 | Q02 Q04 |
| SC-003 | T011 T012 T022 T023 | Q03 Q04 Q06 |
| SC-004 | T017 T023 T024 T025 | Q05 Q06 Q07 Q08 Q09 |
| SC-005 | T005 T024 T026 | Q10 |

## 实施策略

先完成请求身份/业务日志增量，不以US1通过关闭OBS。再完成诊断与连续MCP短时Trace，最后验证真实恢复和可观测故障。没有前端、Schema、真实模型/Broker/生产部署任务。
## 实施证据（持续追加）

2026-10-05：工作区哈希基线 1752 文件；analyze 覆盖19/19，无阻塞项。foundation 6/6通过；identity-sdk 首次鉴权包名修正、第二次 Agent 测试绑定装配修正，均保留失败日志。business-trace 命令覆盖32项，0失败/错误/跳过，包含非anchor真实PostgreSQL历史兼容、诊断实际阶段及Agent四组合。新增出口饱和/恢复/双JVM检查仍需后续运行，不因此提前关闭相关任务。

落点调整：IncidentCorrelationTraceTest 放在 incident/integration 测试包复用包内隔离数据库夹具；DiagnosisResponses 通过既有 RequestContextHolder 获取统一attempt，无需改Controller签名。ErrorAdvice/OperationsController 已依赖 AlertController 共享函数，不重复修改。

2026-10-05：thread-fix 的实际 MCP 线程检查4/4通过；full-chain-2 中 framework7、MCP4、Incident26、Agent14 共51项通过，Bootstrap4项在测试classpath启动失败，不能算双JVM通过。dual-jvm 重跑4项，3通过/1失败/0错误/0跳过：黄金主链68个Span、诊断安全56个Span、回执丢失88个Span完成实际断言；恢复场景因夹具将认证内部回调误按公开入口检查而失败，已修正该断言。此前真实业务断言没有改宽；恢复结果与默认全仓门禁等待后续记录。

已修正的实际缺陷：Context/MDC继承的可信字段最初只进日志未进入子Span属性，ProtectedSimulationTraceTest发现后在构造scope时补属性安装，4项线程/无效传播检查复跑通过。Bootstrap测试exporter漏入MCP子进程classpath导致ClassNotFound，修正测试启动classpath后主链采集通过。失败日志与目录均保留，未用历史验收替代。

2026-10-05：首次默认Java门禁254项、0失败/4错误/1跳过，在Incident停止，RAG/Agent/Bootstrap未运行；原因是既有人员网络夹具漏装Spec D查询Bean。新增活跃scope ContextKey门修复直接诊断调用的MDC身份残留。regression-fix实际最小回归framework9、MCP4、Incident6，共19项，0失败/错误/跳过，退出0；包含真实SpanExporter失败下的PostgreSQL聚合/幂等兼容。第二轮默认门禁截至Bootstrap之前各模块均通过：framework38、infra-ai28、MCP68（1跳过）、Incident122、RAG302（19跳过）、Agent269（1跳过）；既有Bootstrap七项双JVM及诊断黄金/安全两项均通过。OBS最终四场景仍在执行，不提前关闭T023～T027。

最终默认Java门禁 `.\mvnw.cmd '-Dspotless.apply.skip=true' test`（RAG_LIVE_TESTS=false）退出0，840项、0失败/错误、21跳过，结束于2026-10-05T12:49:20+08:00。新OBS四个双JVM场景全部通过，真实SpanData父子、线程、重启稳定身份、业务应用计数及实际凭据扫描均已断言。MCP1项真实You.com、RAG19项旧外部探针、Agent1项可选Dashboard JDBC检查跳过，未将其算成通过。最新制品与hash见quickstart、implementation-report及本轮manifest。T026/T027已完成文档、19/19 FR/SC映射、作者审阅、文件保留与Git边界检查；清单标记保持只读，extensions.yml不存在，前后hook均不适用。
