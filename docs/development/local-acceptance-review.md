# 项目本地完成度核对（2026-10-06）

## 结论与范围

当前已批准的 Spec A～H 共266项任务、175条FR、57条SC均有实现或验收入口对应；没有发现阻止既定本地门槛收尾的漏项。SPC-001与M0在“本地隔离、受约束Runbook诊断、人工审批、安全模拟处置”范围完成。项目仍是面向生产设计的作品项目，生产就绪、真实模型质量和跨平台交付不在本结论中。

本次是代码、规格及已有运行制品的核对，不是一次新的业务验收运行，也没有新增功能、改动业务代码或重签人工审阅。任务复选框仅作索引；判定同时依据当前代码/Schema、实际XML、数据库投影、历史签署、浏览器服务器证据和制品完整性。

核对基线：`feature/incident-platform`，HEAD `67624931f20812922568d3641a095adb291263ba`，暂存为空，1807个已有非忽略文件用未转义Git路径建立摘要。核对制品位于 `bootstrap/target/project-closeout/0d0ee477-ca59-4e8a-a92b-17617a3eee2b/`；这是本地只读复核记录，不是新Spec或可发布原始制品。

## M0与SPC-001判定

| 门槛 | 当前实现与实际依据 | 判定 |
| --- | --- | --- |
| 告警可靠接入与幂等 | HMAC/响应矩阵、数据库唯一约束、AlertReceiveService、AlertWorker；新Backend中相关契约及真实PG检查零失败/跳过 | 本地满足 |
| Incident聚合与只追加事实 | EpisodePartitioner、可信同键时间、状态/版本校验、Timeline与Outbox原子提交；聚合、封存、回拨、状态转换及权限断言 | 本地满足 |
| 人工审批与安全执行 | Incident持有Action/Approval/Execution/Ticket；独立MCP在线消费GRANTED后才启动模拟写；B01～B12目录及新Backend | 本地满足 |
| 至多一次模拟应用及UNKNOWN | 逻辑Execution/幂等键、执行应用记录、可信回执与只读对账；恢复、双通道丢失及锁竞争证据，未知结果无自动写重试 | 指定场景满足 |
| 联合告警到处置 | IncidentDiagnosisGoldenPathTest：两告警、冻结诊断、拒绝0应用、批准1应用、重复/重启后事实保留；最新Backend实际执行 | 本地满足 |
| 诊断质量与人员交互 | C v2人工签署与契约补充；D历史隔离浏览器22项及数据库/可信回执，最新前端四阶段通过 | 满足各Spec限定门槛 |

100个重复提交任务使用20线程池，100条同键Alert处理使用16线程池。它们证明固定测试输入和这些并发调度下的唯一事实，不代表100个同时活跃连接、吞吐或生产容量。各场景的“零泄漏”“至多一次”也只针对声明的测试集合。

## 最新证据与本次实际检查

| 来源 | 实际内容 | 本次核对 |
| --- | --- | --- |
| H Backend `5576b210-ed88-4ef9-b841-e0a755d8f627`，2026-10-06 | 190份新XML、864项：843通过/21跳过、0失败/错误，门禁子进程0；Bootstrap 7+1+1+4项 | 534个制品哈希、1339个当前源码哈希、XML runId/计数、safe白名单均匹配 |
| H Frontend `42f23a1f-a861-4849-a674-1e0753b5836d`，2026-10-06 | npm安装、incident-view、lint、build各0，最终PASS/0 | 11个制品哈希及相同1339文件来源均匹配 |
| B目录 `4cf89770-cdca-4f7d-a675-2cc33798ac49`，2026-10-05 | 54项零失败/错误/跳过，18XML、15证明目录、B01～B12与A核心故障子集 | 113个制品哈希均匹配，XML重算54项 |
| C v2 `e1cf5f14-80f3-4227-ad71-a829cc6e072d` | 34个唯一消费输入、34通过、Hit@3=1.0、leakCount=0；fOggy于2026-10-04签署 | 原summary与签署文件可读取；签署保留，不代签 |
| D最终浏览器 `spec-d-2026-10-04T12-35-13-135553Z` | 22项最终通过、0跳过；应用计数0/0/1/1/1，前两轮补充证据见D quickstart | 26个最终制品哈希均匹配；本次没有启动浏览器 |
| F统一All `2fba02a7-4681-409b-9030-10212de0b51f`，2026-10-05 | 六场景7项通过、249实际Span、25个实际秘密扫描零命中、107制品 | 本次运行原入口只读Verify，VERIFIED/退出0；没有重跑故障 |

Backend/Frontend受控源码摘要同为 `D9FC783F358E7595172452A91281ED49F6A0074E50993737E788E25F7608D3F6`。运行时JDK21.0.12、编译release17、pwsh7.6.5、Docker Linux Engine29.4.3；本机Node20.19.5/npm10.8.2。Linux入口、CI的Node22及Java17运行没有实际验收。

本次实际命令：

```powershell
# 仅核对既有归档，不启动Maven、Docker或业务进程
pwsh -NoProfile -File specs/006-local-fault-replay/scripts/run-lab.ps1 -Verify bootstrap/target/fault-lab/2fba02a7-4681-409b-9030-10212de0b51f
# 以下一次性脚本仅存在于本次target核对目录
python bootstrap/target/project-closeout/0d0ee477-ca59-4e8a-a92b-17617a3eee2b/verify-evidence.py
python bootstrap/target/project-closeout/0d0ee477-ca59-4e8a-a92b-17617a3eee2b/verify-review.py
git diff --check
```

上述最终命令均退出0。Python逐项重算B/D/H哈希、H源码、XML来源/计数、任务唯一性及C已有结果；175/57是需求索引数量，不是代码覆盖率。最终保留与相对链接检查也仅使用本机文件。完整业务复现命令见各Spec quickstart；target在干净检出时可能不存在，不能将本次校验脚本当作产品入口。

本次临时索引核对脚本首轮出现AssertionError：H分段越过了下一节标题，把后续说明中的A／SC-010计入H。修正为在下一节标题处结束分段后，保存脚本并独立捕获子进程退出码0，核对232条要求、44个测试类和68个相对链接通过。首轮子进程退出码未独立捕获，不能用宿主后续命令的0作为通过证据；此次修正没有改动规格或业务实现。

## 要求、代码与检查对应

以下按共享实现边界列出全部FR与SC；每行中的范围均包含端点，重复SC表示同一验收需要多个边界。原Spec仍是要求权威，本表只是项目级索引。未运行的外部能力不因索引完整而变成通过。

### A：可靠告警到Incident（50项任务，48 FR／11 SC）

[规格](../../specs/001-reliable-alert-incident/spec.md)、[复现](../../specs/001-reliable-alert-incident/quickstart.md)。代码入口：[AlertReceiveService](../../incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/service/AlertReceiveService.java)、[IncidentAggregationService](../../incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentAggregationService.java)、[OutboxRelay](../../incident/src/main/java/com/nageoffer/ai/ragent/incident/outbox/worker/OutboxRelay.java)；Schema为`260923_incident_core.sql`。

| 要求 | 代码与检查 | SC及边界 |
| --- | --- | --- |
| FR-001～FR-008 | AlertIngressFilter/SourceHmacVerifier/AlertController；AlertIngressContractTest、HmacSigningContractTest、AlertQueryAuthContractTest | SC-004：认证/验证失败不落业务事实 |
| FR-009～FR-014 | AlertReceiveService/AlertV1Canonicalizer/AlertMapper及唯一约束；AlertIdempotencyTest | SC-001、SC-004：100任务及冲突/事务回滚 |
| FR-015～FR-025 | AlertFingerprint/EpisodePartitioner/AggregationClockMapper/IncidentAggregationService；FingerprintEpisodeTest、IncidentAggregationTest、AggregationClockTest、IncidentAtomicityTest | SC-002、SC-003、SC-005、SC-011：真实PG与不同处理次序 |
| FR-026～FR-029 | IncidentStateMachine/IncidentTransitionService；IncidentStateMachineTest、IncidentTransitionTest | SC-006、SC-011：合法边、过期版本、不可移动封存 |
| FR-030～FR-033 | TimelineWriter/TimelineMapper及只追加权限；IncidentSchemaTest、IncidentEvidenceCatalogTest | SC-006、SC-010：事实去重与指定内容扫描 |
| FR-034～FR-039 | AlertRetryPolicy/AlertWorker/AlertReplayService；AlertRecoveryTest、AlertReplayContractTest | SC-005、SC-008：五次重试、失败与原身份重放 |
| FR-040～FR-043 | IncidentEventWriter/OutboxRelay；OutboxRecoveryTest、IncidentEventContractTest | SC-007：固定/替身Producer，at-least-once |
| FR-044 | OperationsMapper/只读Controller；OperationsReconciliationTest、OperationsSecurityTest | SC-010：SQL状态对账与权限 |
| FR-045～FR-048 | IncidentAcceptanceSuiteTest及目录检查；H完整XML/来源/索引，B的A核心子集 | SC-009、SC-010：联合读取场景证据，不以单个smoke宣称全目录 |

A的`incident-evidence`独立manifest只声明AGG/OUT/SAFE三个smoke场景；完整A判定还依赖同轮契约、并发、时钟、恢复及权限XML。本次最新A smoke位于`incident/target/incident-evidence/2026-10-06T07-03-08.835649200Z-50894358-c31b-491d-be98-2d66fb81636d/`。历史Broker生产者确认/持久化偏移记录见changelog，既不是H的新Broker验收，也不证明业务消费者或端到端exactly-once。

### B：审批与安全模拟（64项任务，41 FR／13 SC）

[规格](../../specs/002-approval-safe-simulation/spec.md)、[证据目录](../../specs/002-approval-safe-simulation/evidence-index.md)、[威胁模型](../security/incident-action-threat-model.md)。代码入口：[ActionTicketConsumptionService](../../incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionTicketConsumptionService.java)、[ProtectedSimulationTool](../../mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/simulation/ProtectedSimulationTool.java)。

| 要求 | 代码与检查 | SC及边界 |
| --- | --- | --- |
| FR-001～FR-004 | ActionProposalService/ActionBindingV1/唯一约束；ActionBindingV1Test、ActionProposalTest | SC-001、SC-002：冻结工具/目标/参数、意图幂等 |
| FR-005～FR-010 | ActionDecisionService/ActionExpiryWorker及状态校验；ActionStateMachineTest、ActionApprovalRaceTest、ActionExpiryWorkerTest | SC-003：实际绝对截止；部分SQL时间fixture |
| FR-011～FR-012 | IncidentActionConfirmationAdapter/IncidentActionStatusAdapter/ActionDispatchWorker | SC-008：Agent只查询事实，不以卡片或恢复重写 |
| FR-013～FR-018 | Execution/Ticket约束、在线消费、双向服务认证与ProtectedSimulationTool；ActionDispatchTicketTest、ProtectedSimulationE2ETest、BootstrapIsolatedStartupTest | SC-001、SC-002、SC-007：一次Execution、绑定/重放/TLS拒绝 |
| FR-019～FR-021 | SimulationToolExecutor/SimulationTimeoutEvaluator及固定receiptDueAt | SC-003、SC-010：只操作临时模拟目标 |
| FR-022～FR-025 | ActionReceiptService/双通道；ActionReceiptTest、实际receipt-channels场景 | SC-004～SC-006：有证明才FAILED、相反回执不覆盖 |
| FR-026～FR-028 | IncidentRemediationDispatcher/ActionDispatchRecoveryWorker/ActionExpiryWorker；ActionRecoveryRaceTest及真正Worker崩溃重启 | SC-007：同Execution安全替票，应用至多一次 |
| FR-029～FR-032 | 超时评估/ActionReceiptDeadlineWorker/ActionReconciliationService/ActionManualConclusionService；ActionUnknownConvergenceTest、ProtectedSimulationE2ETest | SC-004、SC-005：只读对账、证据不足继续UNKNOWN |
| FR-033～FR-036 | ActionQueryService/ActionTimelineWriter/新Action来源关联；ActionQueryContractTest、ActionSecretLeakTest | SC-008、SC-009：人员查询和安全投影 |
| FR-037 | B01～B12及场景manifest、指定日志扫描 | SC-009、SC-010：不同真实性层级分别标明 |
| FR-038 | ActionClosureInvalidator/受保护锁；ActionApprovalRaceTest、完整Worker场景 | SC-011：关闭与消费两种次序 |
| FR-039～FR-040 | ActionOutboxWriter/ActionTimelineWriter；ActionOutboxCompatibilityTest | SC-012：真实Relay/PG，Producer替身 |
| FR-041 | 安全工具准入及普通/专用入口；ProtectedSimulationRouteTest、ProtectedSimulationE2ETest | SC-013：旧写工具/未知注册失败关闭 |

B10部分身份/证明为fixture，B11的Producer为替身，部分截止通过SQL推进；这些是已声明的验收层级，不证明真实Broker、完整跨主机权限治理或所有攻击变体。

### C：Runbook诊断（36项任务，19 FR／7 SC）

[规格](../../specs/003-incident-runbook-diagnosis/spec.md)、[v2签署](../../specs/003-incident-runbook-diagnosis/review/v2/human-review.md)、[FR-017补充](../../specs/003-incident-runbook-diagnosis/review/v2/fr-017-coverage.md)。代码入口：[RunbookRetriever](../../rag/src/main/java/com/nageoffer/ai/ragent/rag/runbook/RunbookRetriever.java)、[DiagnosisEvidenceValidator](../../agent/src/main/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvidenceValidator.java)、[DiagnosisActionSelectionService](../../incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/service/DiagnosisActionSelectionService.java)。

| 要求 | 代码与检查 | SC及边界 |
| --- | --- | --- |
| FR-001～FR-003 | RunbookCorpus/RunbookRetriever/资格适配；RunbookCorpusTest、RunbookRetrievalTest | SC-001、SC-002：五类模拟Markdown、版本/范围过滤 |
| FR-004～FR-005 | IncidentSnapshotService；IncidentSnapshotIntegrationTest | SC-003、SC-006：关联Alert一致快照和缺失事实 |
| FR-006～FR-010 | IncidentDiagnosisOrchestrator/ExtractiveDiagnosisGenerator/DiagnosisEvidenceValidator/DiagnosisRecordService；Validator与OfflineEvaluation检查 | SC-003、SC-004、SC-007：固定抽取规则，不是自由生成蕴含证明 |
| FR-011～FR-016 | 人员选择、Action Link唯一约束、只追加权限及安全接口；DiagnosisActionSelectionIntegrationTest、DiagnosisActionSecurityTest、IncidentDiagnosisGoldenPathTest | SC-005、SC-006：建议不等于批准，独立审批后一次应用 |
| FR-017～FR-018 | cases-v1/v2、人工签署、评测分母/来源及补充契约 | SC-002、SC-004、SC-007：Hit@3与拒绝门槛；非盲评 |
| FR-019 | 真实PG约束/并发/权限、黄金路径及重启 | SC-005、SC-006：指定本地链路 |

34例之外的撤回/冲突/伪造由已批准的“数据集＋安全契约”承载；消费首条queryFact，不宣称多事实融合或未知分布质量。历史v1未签署记录不被本次改写。

### D：人员操作界面（31项任务，20 FR／7 SC）

[规格与浏览器记录](../../specs/004-incident-operations-ui/quickstart.md)。代码入口：[IncidentDetailPage](../../frontend/src/pages/admin/incidents/IncidentDetailPage.tsx)、[ActionPanel](../../frontend/src/pages/admin/incidents/ActionPanel.tsx)、[incidentApi](../../frontend/src/services/incidentApi.ts)。

| 要求 | 代码与检查 | SC及边界 |
| --- | --- | --- |
| FR-001～FR-004 | RequireAdmin/IncidentListPage/IncidentQueryService/分页Timeline；查询契约与浏览器1～4/20～22 | SC-001、SC-005：服务端再次鉴权、刷新读取持久事实 |
| FR-005～FR-008 | DiagnosisPanel/历史原文与可选择性；诊断契约、浏览器5～7/16 | SC-002、SC-004：SUPPORTED不宣称根因确认 |
| FR-009～FR-012 | DiagnosisActionSelectionService/冻结Action预览/独立审批；浏览器8～12/14～17 | SC-003、SC-004：冲突不重批、未知提交手动原意图恢复 |
| FR-013～FR-015 | ActionPanel/服务端安全查询投影；浏览器11～13/18/19 | SC-003～SC-005：可信回执；UNKNOWN展示为SQL fixture |
| FR-016～FR-018 | incidentView/incidentApi/取消与串行轮询；确定性逻辑检查、浏览器刷新/冲突 | SC-005、SC-006：A code="0"，B/C code="SUCCESS"按接口识别 |
| FR-019～FR-020 | 后端34项、前端四检查、隔离浏览器22项与保留审计 | SC-006、SC-007：本次未新跑浏览器；没有新权限体系 |

Runbook HTML浏览器样例、全部网络时序、桌面断点和跨浏览器没有穷举。UNKNOWN执行恢复由Java检查支撑，不能拿UI展示种子冒充真实执行故障。

### E：关联与短时Trace（27项任务，14 FR／5 SC）

[实施报告](../../specs/005-incident-correlation-trace/implementation-report.md)。代码入口：[ObservationScope](../../framework/src/main/java/com/nageoffer/ai/ragent/framework/observability/ObservationScope.java)、[IncidentObservation](../../incident/src/main/java/com/nageoffer/ai/ragent/incident/observability/IncidentObservation.java)、[IncidentObservabilityIsolatedTest](../../bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentObservabilityIsolatedTest.java)。

| 要求 | 代码与检查 | SC及边界 |
| --- | --- | --- |
| FR-001～FR-004 | HTTP attempt/业务sourceRequestId/Execution/Alert correlationId分层；IncidentRequestIdentityTest、IncidentCorrelationTraceTest | SC-001、SC-002：聚合后incidentId汇总，原身份保持 |
| FR-005～FR-007 | scope、诊断真实阶段、认证传播；OBS正常/诊断安全两个双JVM场景 | SC-003：实际SpanData父子关系 |
| FR-008～FR-009 | 显式线程恢复/取消/新Trace恢复；线程与Worker恢复检查 | SC-004：取消不证明停止或回滚 |
| FR-010～FR-011 | 字段白名单/出口失效保护；指定凭据扫描、导出失败与业务回归 | SC-005、SC-004：不把Trace用于安全结论 |
| FR-012～FR-014 | 四OBS场景、SDK装配与上游Agent兼容、默认开关 | SC-001～SC-005：H本轮四项通过；OTLP后端未验收 |

### F：本地故障实验室（18项任务，12 FR／5 SC）

[实施与命令](../../specs/006-local-fault-replay/implementation-report.md)。代码入口：[run-lab.ps1](../../specs/006-local-fault-replay/scripts/run-lab.ps1)、[scenarios.json](../../specs/006-local-fault-replay/scripts/scenarios.json)。

| 要求 | 代码与检查 | SC及边界 |
| --- | --- | --- |
| FR-001～FR-005 | 固定六目录、选择/All/Verify、新runId、受控路径/锁及失败聚合 | SC-001、SC-002：六场景7项真实运行 |
| FR-006～FR-009 | 事实/Trace/来源报告与测试侧proof-origin | SC-003、SC-005：不以Span数量判断副作用 |
| FR-010～FR-012 | Test-LabArchive/契约负例、默认profile兼容 | SC-004、SC-005：两PowerShell版本各30契约；本次原归档Verify通过 |

### G：旧RAG测试治理（20项任务，9 FR／4 SC）

[分类和证据](../../specs/007-rag-test-isolation/implementation-report.md)。代码入口：[QueryTermMappingServiceTest](../../rag/src/test/java/com/nageoffer/ai/ragent/rag/core/rewrite/QueryTermMappingServiceTest.java)、[SiliconFlowEmbeddingClientTest](../../infra-ai/src/test/java/com/nageoffer/ai/ragent/infra/embedding/SiliconFlowEmbeddingClientTest.java)、[MilvusVectorStoreAdminTest](../../rag/src/test/java/com/nageoffer/ai/ragent/rag/core/vector/MilvusVectorStoreAdminTest.java)。

| 要求 | 代码与检查 | SC及边界 |
| --- | --- | --- |
| FR-001～FR-002 | 11类盘点、复用已有覆盖及保留test-only演示 | SC-001：原探针/上游归属保留 |
| FR-003～FR-004 | 映射/会话/意图树/分类实际服务、Embedding本地HTTP、Milvus SDK替身；六组40项 | SC-002：不是真实SQL并发/供应商质量/Milvus服务验证 |
| FR-005～FR-006 | You.com/MinerU许可门及无许可负例 | SC-003：key/文件不是授权 |
| FR-007～FR-009 | 独立模块/全仓XML及来源，外部条件关闭/恢复 | SC-004：跳过逐类列明，H新入口再次确认 |

### H：本地交付门禁（20项任务，12 FR／5 SC）

[实施与完整跳过名单](../../specs/008-local-delivery-gates/quickstart.md)、[部署准备](delivery.md)。代码入口：[check-delivery.ps1](../../scripts/check-delivery.ps1)、[契约脚本](../../scripts/test-check-delivery.ps1)、[CI定义](../../.github/workflows/incidentpilot-ci.yml)。

| 要求 | 代码与检查 | SC及边界 |
| --- | --- | --- |
| FR-001～FR-004 | 固定参数、工具/Docker/安全选项、临时环境finally恢复、复用四容器双JVM | SC-001、SC-003：CheckOnly不启动业务，环境受阻不降级 |
| FR-005～FR-007 | Read-DeliveryReports/来源结束核对/本地制品索引 | SC-002、SC-003、SC-004：12必需类与13手工类21跳过 |
| FR-008、FR-010 | 最小权限workflow及重新构造safe白名单 | SC-004、SC-005：只有安全摘要可发布；CI未运行 |
| FR-009 | npm ci/incident-view/lint/build与All失败聚合 | SC-002：不是浏览器验收 |
| FR-011 | 原夹具依赖/Schema初始化与部署准备说明 | SC-005：常驻栈/生产/迁移矩阵仍TBD |
| FR-012 | 87项契约、新Smoke及完整Backend/Frontend、失败修复记录 | SC-001～SC-005：历史结果不顶替新入口验收 |

## 文档冲突与修正

- PROJECT_CONTEXT当前部分仍把LAB/威胁模型列为未实现、B01～B12列为未收尾；按代码、B/F/H制品和已批准范围更新当前摘要，日期改为2026-10-06。历史分日记录保留。
- TODO的SPC-001仍“进行中”，EVAL-001/ACT-001/ACT-002/OBS-001仍留在后续表；依据本核对迁入完成表，不顺带关闭EXT或生产范围。
- ARCHITECTURE将实验室扩展列为未来、前端职责未体现Incident；同步已实现边界，不新增架构决策。
- NOTES的无日期待补充项仍称完整Java/前端基线待验证；区分已完成的隔离基线和未验证的生产启动。
- A quickstart只列SC-001～SC-010，现行spec/tasks还包含SC-011；指南补可信时间验收，不修改规格门槛。

## 真正剩余的工作

| 优先顺序 | 状态与完成要求 |
| --- | --- |
| 作者走读与演示 | 你独立讲清主链、一次Worker崩溃恢复及UNKNOWN；这是下一步学习/展示工作，本次未代替你完成 |
| 可交付版本整理 | 当前是未提交工作区；需要审查源码、配置/许可证及公开安全证据，保留原有修改归属。提交/推送仍须明确授权 |
| Linux／Node22／Java17运行 | 没有运行证据；先准备独立临时环境并验证同一入口。Java21执行不能替代Java17兼容性 |
| 托管CI | 定义/官方SHA/静态检查已完成，远程运行未发生；触发需要新授权 |
| 明确需求后再扩展 | 常驻部署、跨主机TLS/权限/轮换/备份回滚、真实模型/Runbook、Broker消费者、Milvus/OTLP与性能另需范围和实际证据 |

已知非阻塞遗留差异：QueryTermMappingDO称priority小值优先，当前Comparator逆序；实际语义仍TBD，G没有擅自修改。镜像浮动标签、本地证据保存期限/安全销毁、迁移版本矩阵及公开配置审阅也未解决。它们保留在NOTES/交付说明，不以关闭SPC-001抹去。

## 作者核对与三个问题

正常路径为AlertReceiveService持久化 → AlertWorker聚合 → IncidentDiagnosisOrchestrator检索/抽取/校验 → 人员选择PENDING_APPROVAL → 独立人工批准 → Worker派发 → MCP在线消费票据 → 模拟库一次应用 → 双通道可信回执。拒绝不产生应用；审批等待和进程重启通过稳定业务身份连接，Trace不是事实来源。

真实失败取自已保留的H记录：DisplayName误判报告；随后三项恢复检查因缺失scenario/sourceDigest/路径前提出错。修复后先三个真实恢复检查，再完整新入口通过。本次没有制造故障或把旧失败改成通过；更完整的失败与取舍见H实施报告。

1. 为什么H全仓绿色、任务266/266完成，仍不能证明Linux、真实模型和生产部署就绪？
2. Outbox重复投递与模拟执行至多一次分别由哪些稳定身份和数据库约束保证？
3. Worker崩溃或双回执丢失后，哪些事实能证明一次应用，哪些情况只能保持UNKNOWN？

上游Ragent提供RAG/Agent/管理界面与基础工具；A～H的Incident主链、模拟审批/恢复、受约束Runbook、界面扩展、观测及工程门禁是新增贡献。保留Git历史与Apache-2.0，未提交、推送、合并、部署、触发远程CI、调用付费或真实基础设施/开发数据库，没有全局容器清理。
