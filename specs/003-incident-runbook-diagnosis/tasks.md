# 任务：Incident Runbook RAG 诊断与可复核引用

**输入**：`specs/003-incident-runbook-diagnosis/` 的 spec/plan/research/data-model/contracts/quickstart。

**状态**：2026-10-03 用户“确认执行”批准实施；正式 analyze 已复核全部 19 项 FR 和 7 项 SC 的任务覆盖及领域/审批边界，未发现宪章冲突。2026-10-04 用户确认审阅完成，v2 34例及HR-001/002已有fOggy签署，剩余安全契约覆盖已核对；36/36项完成，范围限本地模拟语料与受约束诊断证据链；不提交、推送、部署、付费或真实处置。

**测试**：规格明确要求。先编写能暴露缺失行为的测试，再实现；遵循 testing 指南，禁自动格式化。路径均对应已实现文件，实际命令与自动结果见 `docs/changelog.md`。

## Phase 1：准备

- [x] T001 在 `specs/003-incident-runbook-diagnosis/spec.md`、`plan.md`、`checklists/requirements.md` 完成用户确认与 `$speckit-analyze`，复核 `research.md` 的代码依据、冲突和未批准边界。
- [x] T002 在 `docs/DECISIONS.md` 追加获批的版本/证据保留、诊断 Action 来源与离线评测决策，在 `docs/TODO.md` 记录正式实施范围；不得把草案提案写成既有事实。

## Phase 2：基础能力（阻塞性前置任务）

目标：可恢复请求、只追加结果、稳定快照和服务 DTO；不依赖检索/生成器可用性。

- [x] T003 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/diagnosis/DiagnosisPersistenceIntegrationTest.java` 编写真实 PostgreSQL 请求幂等、冻结输入列保护、结果唯一/只追加、升级链和 Timeline 回滚测试。
- [x] T004 在 `resources/database/upgrades/v2.0.0/261003_incident_diagnosis.sql` 和 `resources/database/schema_pg.sql` 实现三张表、状态/摘要/唯一/FK/角色列权限和兼容 Timeline 枚举；保持 A/B 原语义。
- [x] T005 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/dao/DiagnosisRequestMapper.java`、`DiagnosisResultMapper.java`、`DiagnosisActionLinkMapper.java` 实现专属 Mapper，只开放冻结字段插入/查询及运行状态条件更新。
- [x] T006 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/contract/DiagnosisContracts.java` 定义应用 DTO、v1 canonical 摘要/限额，禁止对外暴露 Mapper/DO 和 token 明文；模型规则与检索 DTO 留在所属模块。
- [x] T007 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/service/DiagnosisRecordService.java` 实现 begin/complete、令牌/截止/幂等、POST 惰性失败收敛、stale 发布与唯一 Timeline，锁内不检索/生成/MCP。
- [x] T008 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/service/DiagnosisTimelineWriter.java` 实现稳定业务键、现有序号分配和安全摘要，运行 T003 验证事务与非 owner 只追加保护。

## Phase 3：用户故事 1 - 可复核 Runbook（P1，检索 MVP）

**目标**：真实本地召回、范围过滤、历史原文定位，无模型/处置。

**独立测试**：固定查询与语料，核对真实排序、全文/片段 hash、位置、无跨范围/失效泄漏。

- [x] T009 [P] [US1] 在 `rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookCorpusTest.java` 编写不可变版本、manifest/摘要、行号、非法路径、重复 ID、过期/撤回测试。
- [x] T010 [P] [US1] 在 `rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookRetrievalTest.java` 编写真实 lexical-v1 过滤/排序、中文英文措辞、同分/无匹配/干扰项测试，不 mock 检索或读 gold IDs。
- [x] T011 [US1] 在 `rag/src/main/resources/runbooks/manifest-v1.json` 与 `RB-OVERLOAD/1.0.0.md`、`RB-JAVA-OOM/1.0.0.md`、`RB-POOL/1.0.0.md`、`RB-HEALTH/1.0.0.md`、`RB-BACKLOG/1.0.0.md` 建立五类中文模拟语料、审阅前提、章节与唯一模拟恢复规则。
- [x] T012 [US1] 在 `rag/src/main/java/com/nageoffer/ai/ragent/rag/runbook/RunbookCorpus.java` 实现 classpath 不可变加载、规范文本、manifest 摘要、作用域/有效期/currentVersion；拒绝任意文件/URL。
- [x] T013 [US1] 在 `rag/src/main/java/com/nageoffer/ai/ragent/rag/runbook/RunbookRetriever.java` 实现明确 lexical-v1 查询构造、过滤、覆盖率排序和稳定 Top-K，无外部模型/通道回退。
- [x] T014 [US1] 在 `rag/src/main/java/com/nageoffer/ai/ragent/rag/runbook/RunbookEvidence.java` 实现专用返回 DTO、去重全文快照、片段位置/摘要与限额；运行 T009/T010，验证源更新/缺失后历史引用仍可复算。

## Phase 4：用户故事 2 - 证据诊断（P1）

**目标**：权威快照→检索→逐项校验→不可变结果，历史读取独立于生成器。

**独立测试**：fixture 检索/生成器可独立验证编排；真实语料+抽取规则另行检查正例输出。非法/不足/矛盾时无 convertible 建议。

- [x] T015 [P] [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/diagnosis/IncidentSnapshotIntegrationTest.java` 编写已关联 Alert 精确范围、一致读、并发新告警/主状态、白名单/截断及自身 Timeline 不影响 factsDigest 测试。
- [x] T016 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/service/IncidentSnapshotService.java` 和 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/dao/AlertMapper.java` 实现锁内精确 incidentId 投影、稳定事实 ID、独立水位与摘要，接入 T007；运行 T015。
- [x] T017 [P] [US2] 在 `agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvidenceValidatorTest.java` 编写未知事实/引用、hash/原文篡改、未满足前提、矛盾、截断、提示注入及任意写工具输出拒绝测试。
- [x] T018 [US2] 在 `agent/src/main/java/com/nageoffer/ai/ragent/agent/diagnosis/ExtractiveDiagnosisGenerator.java` 实现审阅规则抽取候选/只读检查与唯一健康模拟规则，记录规则/生成器版本，不能注册或调用 MCP 工具。
- [x] T019 [US2] 在 `agent/src/main/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvidenceValidator.java` 实现逐项事实/引用/位置/摘要/范围/规则/前提校验和安全降级，运行 T017；固定模型输出只在测试注入。
- [x] T020 [P] [US2] 在 `agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/IncidentDiagnosisOrchestratorTest.java` 编写事务边界、截止、中断/重复完成、禁外部调用、过时保留和生成器不可用不阻断 A/B 测试。
- [x] T021 [US2] 在 `agent/src/main/java/com/nageoffer/ai/ragent/agent/diagnosis/IncidentDiagnosisOrchestrator.java`、`IncidentDiagnosisController.java` 实现显式人员 POST、事务外真实本地检索/抽取和安全完成；Agent 不直接引用 Incident Mapper/DO，运行 T020。
- [x] T022 [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/diagnosis/DiagnosisApiContractTest.java` 先编写 GET/list 鉴权、限额/游标、原文回放、GET 只读和 POST 截止收敛测试，再在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/web/DiagnosisQueryController.java`、`service/DiagnosisQueryService.java` 实现对应接口。

## Phase 5：用户故事 3 - 建议转待审批 Action（P2）

**目标**：持久来源、幂等选择、服务端重新校验和沿用 B 审批链。

**独立测试**：预置诊断可以独立测试，不依赖实际生成器；明确新选择与历史重试不同。

- [x] T023 [P] [US3] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/diagnosis/DiagnosisActionSelectionIntegrationTest.java` 编写 link/Action/Approval/Timeline 同事务回滚、并发同请求、不同绑定冲突、拒绝后新请求、无关 Action 复用拒绝测试。
- [x] T024 [P] [US3] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/diagnosis/DiagnosisActionSecurityTest.java` 编写无权限、已关闭/过时/截断、撤回/过期语料、模型改参数、真实目标、无证据选择及不签票据测试。
- [x] T025 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/service/DiagnosisActionSelectionService.java`、`incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/contract/RunbookEligibility.java` 和 `agent/src/main/java/com/nageoffer/ai/ragent/agent/diagnosis/RunbookEligibilityAdapter.java` 实现 link 幂等、当前事实/版本/本地语料校验与缺适配器失败关闭；在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/config/DiagnosisProperties.java` 定义演示目标映射并与 B 白名单交集校验，复用既有 ActionProposalService，保留 B 绑定，运行 T023/T024。
- [x] T026 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/web/DiagnosisActionController.java` 实现仅接收选择 IDs/digests/版本的人员接口，参数由服务端建议推导，拒绝新增 decision/toolName/parameters 字段。
- [x] T027 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionQueryService.java` 增加诊断来源只读展示，通过同域应用查询访问 link，验证聊天消息丢失不影响权威来源，保持原 B 响应兼容。

## Phase 6：用户故事 4 - 离线门禁与完整黄金路径（P2）

**目标**：真实召回、受约束诊断和审批协作分别有可复现证据。

**独立测试**：检索评分测试无需数据库；黄金路径使用已固定诊断边界与真实隔离应用/MCP，二者不互相冒充。

- [x] T028 [P] [US4] 在 `rag/src/test/resources/fixtures/runbook/cases-v1.json`、`corpus/manifest-v1.json` 完成≥30例人工审阅标签、作用域/失效干扰、split、固定时钟/摘要，防止 queryFacts 泄漏 gold ID。

  已建立30例合成规则预期、20份 fixture 文档及 split/摘要；用户明确选择保留独立人工复核待办。
  本项于2026-10-04按用户“已经完成”及现有签署记录关闭；自动回归结果不能作为人工审阅记录。
  30例输入/预期/实际/引用已整理为[人工审阅表](review/human-review.md)，已收到用户提供的26通过/4需修正结论，签署0/30。
  HR-001/002已按用户指示建立独立cases-v2并复跑；v1保留。v2 34例通过、0需修正、0受阻，fOggy于2026-10-04签署，整体处置2/2确认；见[34例v2审阅表](review/v2/human-review.md)及[FR-017剩余覆盖核对](review/v2/fr-017-coverage.md)。沿用2026-10-03定向9测试运行记录，本轮未复跑Java测试。
- [x] T029 [US4] 在 `rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookOfflineEvaluationTest.java` 编写并运行真实召回 Hit@3/SectionHit@3/泄漏/覆盖率，包含缺例/零分母/低命中会失败的检验。
- [x] T030 [US4] 在 `agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisOfflineEvaluationTest.java` 验证实际抽取规则正例覆盖/支持率及固定输出的非法/矛盾/注入降级，区分两种运行类型，无真实模型调用。
- [x] T031 [US4] 在 `rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookEvaluationReport.java` 与 `agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvaluationReport.java` 输出 manifest/逐例/汇总到 target，执行失败非零，显式分母/跳过/受阻和 FIXED_TEST_RESPONSE 标志。
- [x] T032 [US4] 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentDiagnosisGoldenPathTest.java` 扩展既有隔离双 JVM 模式：两告警聚合、诊断引用、拒绝零应用、新 Action 批准一次、重复选择、主应用重启后全部证据可复核。
- [x] T033 [US4] 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentDiagnosisSafetyTest.java` 验证新告警/关闭/语料撤回竞争、来源事实注入、无票/伪卡片不能执行及结果中断恢复；保密扫描与实际应用计数不能仅靠 mock。

## Phase 7：完善与横切关注点

- [x] T034 在 `docs/ARCHITECTURE.md`、`docs/PROJECT_CONTEXT.md`、`docs/TODO.md` 同步已验证诊断能力和依赖；经确认处理 research 的旧时点冲突，未验证内容继续 TBD。
- [x] T035 在 `specs/003-incident-runbook-diagnosis/quickstart.md` 和 `docs/changelog.md` 记录实际聚焦/真实 PostgreSQL/双进程命令、数量、结果及受阻，复核生成目录/秘密和最终 diff，不提交或部署。
- [x] T036 按 `docs/development/testing.md` 运行共享 A/B 与默认全仓 Java 回归，在 `docs/changelog.md` 报告旧外部探针跳过数量；不得以跳过或构建绿色替代真实离线/黄金路径验收。

## 依赖关系与执行顺序

- T001→T002 是实施门；T003～T008 共享持久化基础。T003 测试先于 T004～T008。
- US1：T009/T010→T011→T012→T013→T014。T009/T010 可并行编写；两者只读设计，互不依赖。
- US2：T015→T016，T017→T018/T019，T020→T021，最后 T022；依赖基础和 US1。T015/T017/T020 可分别编写不同文件，实际执行依所需实现就绪。
- US3：T023/T024→T025→T026→T027；依赖 US2 完成，预置诊断可独立验收本故事。
- US4：T028的数据准备与人工标签复核已完成；人工复核仍是FR-017验收门，不因自动通过而解除；T032/T033依赖US1～3和已有A/B。执行评测必须用真实实现。
- T034～T036 依赖全部拟交付故事；验证失败不得勾选完成。

## 并行机会与示例

- US1：不同文件的语料合同测试 T009 与真实检索测试 T010 可同时准备。
- US2：快照集成测试 T015、证据校验测试 T017、编排测试 T020 可同时准备。
- US3：事务/并发 T023 与安全 T024 分属独立测试文件。
- US4：T028 标签审阅可在前序完成基础设计后准备；T029 与 T030 在依赖就绪后分别验证不同模块。
- `[P]` 仅表示无同文件写冲突，不授权自动启动多 Agent，也不允许跳过前置质量门。

## 实施策略

先确认→基础→US1 可检索引用 MVP→US2 可读诊断→US3 待审批转换→US4 完整黄金路径。检索 MVP 可以独立验证，不等于告警到处置全功能交付。初始36任务未勾选；当前状态见各任务。

## 需求追踪

| 需求 | 任务 | 故事 |
| --- | --- | --- |
| FR-001～003 / SC-001～002 | T009～T014、T028～T029 | US1/US4 |
| FR-004～007 / SC-001、003、006 | T003～T008、T015～T016、T022 | US2 |
| FR-008～011 / SC-003～004 | T017～T021、T030 | US2/US4 |
| FR-012～013 / SC-005 | T023～T027、T032～T033 | US3/US4 |
| FR-014～016 / SC-004、006 | T007～T008、T020～T022、T024、T033 | US2/US3/US4 |
| FR-017～019 / SC-002～007 | T028～T033、T035～T036 | US4/收尾 |

任务数量：准备2、基础6、US1=6、US2=8、US3=5、US4=6、完善3，共36。

## 实际收尾状态

2026-10-03：全仓805测试（21跳过）无失败/错误；最后跨模块定向19测试无失败/错误/跳过；离线报告字段复跑3测试通过。35项完成，唯一开放项T028是用户明确保留的独立人工标签复核。checklists/requirements.md 保持设计阶段记录，实施时只读，未改其标记。未授权操作均未执行，证据与限制见 `docs/changelog.md`。

## T028修正闭环跟进（不改变父任务待办状态）

- 接收并归档用户提供的26通过/4需修正/0受阻结论，签署继续TBD。
- 保留v1，新增cases-v2四个替换例与四类负例holdout；建议级gold与实际消费输入唯一性检查。
- 复跑v1/v2真实检索与实际抽取，保留运行版本/分母/命令与结果。
- 准备v2证据供人工确认；T028不自动勾选。

T028本轮进度：已归档用户意见，已生成34例v2，v1/v2定向9测试通过；v2 Hit@3/SectionHit@3=10/10，负例安全24/24，holdout负例4/4，实际消费输入34/34唯一。v2人工确认与真实签署仍TBD；父任务继续未勾选。

## 2026-10-04人工审阅收尾

用户明确回复“已经完成”，现有v2审阅表记录fOggy于2026-10-04确认34例全部通过、HR-001/002处置2/2。FR-017剩余撤回/冲突/伪造按已批准离线契约由模块安全测试承载，已静态核对源码与既有Surefire工件（9测试、0失败/错误/跳过）。据此关闭T028，当前36/36项完成；上文2026-10-03的待办记录保留为历史。

签署依据中的“最强负例”“注入检测泛化成立”仅按已测试输入变体理解，不作为强弱排名或未知分布结论；注入检测仍为固定关键词正则，症状匹配仍依据metadata.symptomTerms；建议级gold为有序数组断言，失败只令测试失败，不自动转人工。未改原人工结论或署名。

本轮仅同步说明文档，不变更代码、Schema、语料、标签或运行输出，不复跑Java测试。质量结论保持本地模拟语料/关键词检索/受约束规则抽取与服务端审批边界；不提交、推送、部署、付费或真实处置。
