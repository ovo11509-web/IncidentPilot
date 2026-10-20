# 任务：Incident操作界面与安全演示闭环

**输入**：specs/004-incident-operations-ui/下spec/plan/research/data-model/contracts/quickstart。

**状态**：2026-10-04本地实施及隔离验收完成，31/31任务；实际命令、22项浏览器场景、服务器证据和范围限制见quickstart.md。审阅检查清单标记未代改。

**前置条件**：用户确认spec.md、plan.md及本任务清单；正式speckit-analyze无阻塞；按docs/development/testing.md准备隔离验证。

**测试**：规格FR-019明确要求接口契约、纯逻辑、类型/lint/build及真实浏览器路径。测试失败需记录真实原因，不能仅凭不存在文件宣称正确红测。

## Phase 1：准备

- [x] T001 核对 `docs/PROJECT_CONTEXT.md`、`docs/development/testing.md`、`frontend/package-lock.json` 和当前A/B/C接口，记录实施基线与隔离环境，禁止改现有未授权文件。
- [x] T002 在 `frontend/tests/incident-view.check.mjs`、`frontend/tests/tsconfig.incident.json` 建立既有Node assert+本地TypeScript模式的最小检查，覆盖envelope错误、长ID、写请求不自动重试；不新增依赖。

## Phase 2：共享基础

- [x] T003 在 `frontend/src/services/incidentApi.ts` 实现专用逐接口成功码（A为"0"、B/C为"SUCCESS"）解包、认证header、语义错误/status/requestId、401/403、AbortSignal和无POST自动重试；不修改全局 `frontend/src/services/api.ts` 行为。
- [x] T004 在 `frontend/src/types/incident.ts` 定义当前契约类型、字符串ID/时间/安全整数校验，以及列表/告警/诊断/审批/回执投影；对齐 `contracts/personnel-api.md`。
- [x] T005 在 `frontend/src/lib/incidentView.ts` 实现最小分页去重、操作资格/状态投影，保留未知状态并禁用命令，扩展 `frontend/tests/incident-view.check.mjs` 的边界检查。

## Phase 3：US1浏览事件（P1，首个MVP）

**目标**：不启动诊断或MCP即可查看事件、关联告警与时间线。

**独立测试**：两条隔离告警归属同一Incident；分页、筛选、空态、403/404和刷新均能核对。

- [x] T006 [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/contract/IncidentQueryContractTest.java` 覆盖分页上限/非法游标/状态、admin权限、精确incident_id告警关联、秘密脱敏及跨episode排除；对适用SQL使用隔离PostgreSQL验证。
- [x] T007 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/dao/IncidentMapper.java`、`core/service/IncidentQueryService.java`、`core/web/IncidentController.java`（后两路径同incident包）实现列表二元游标、状态筛选、limit+1及安全投影，不读全库或逐行snapshot。
- [x] T008 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/dao/AlertMapper.java` 与 `core/service/IncidentQueryService.java`、`core/web/IncidentController.java`（同incident包）补分页已关联PROCESSED告警；复用 `diagnosis/service/IncidentSnapshotService.java` 现有清洗规则，不泄漏原payload/labels/summary。
- [x] T009 [US1] 在 `frontend/src/services/incidentService.ts`、`frontend/src/pages/admin/incidents/IncidentListPage.tsx` 实现列表、状态筛选、更多结果、空/错态；不以N次详情请求模拟列表。
- [x] T010 [US1] 在 `frontend/src/pages/admin/incidents/IncidentDetailPage.tsx` 实现详情、关联告警分页和Timeline序列分页，控制旧请求晚到；未知ID/权限失败不保留上一事件数据。
- [x] T011 [US1] 在 `frontend/src/router.tsx`、`frontend/src/pages/admin/AdminLayout.tsx` 加入/admin/incidents和详情路由、导航及面包屑；用 `quickstart.md` 的US1场景验收并保留现有RequireAdmin。

## Phase 4：US2诊断与引用（P1）

**独立测试**：预置Incident/Runbook，无需模拟执行；查看正例、降级及历史引用。

- [x] T012 [US2] 在 `frontend/tests/incident-view.check.mjs` 补诊断COMPLETED/SUPPORTED、stale、evidenceApplicability、deadlineExceeded、不可转换建议与CLOSED事件的选择资格测试。
- [x] T013 [US2] 在 `frontend/src/services/incidentService.ts` 接入诊断历史/详情及显式发起诊断；sourceRequestId和expectedIncidentVersion取本次真实意图与最新权威视图，网络未知不自动新建请求。
- [x] T014 [US2] 在 `frontend/src/pages/admin/incidents/DiagnosisPanel.tsx` 实现来源快照、根因未确认候选、缺失事实、限制、分页历史与安全文本引用，展示ID/版本/章节/行号/摘要。
- [x] T015 [US2] 在 `frontend/src/pages/admin/incidents/IncidentDetailPage.tsx`、`DiagnosisPanel.tsx`（同目录）处理旧诊断失效/超时、手动重新诊断及当前资格；历史仍可查看，原HTML/远程资源不执行或加载。
- [x] T016 [US2] 运行 `agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvidenceValidatorTest.java` 和适用 `incident/src/test/java/com/nageoffer/ai/ragent/incident/diagnosis/` 既有契约测试，确认新增只读投影未改变证据/来源边界，并按 `quickstart.md` 核对UI。

## Phase 5：US3显式选择和审批（P1）

**独立测试**：关闭执行Worker，检查冻结预览、拒绝、幂等及冲突。

- [x] T017 [US3] 扩展 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/contract/ActionQueryContractTest.java`：READY/PAUSED冻结预览、非法JSON预览不可用、不泄漏测试secret-value/ticket秘密；保留原安全断言。
- [x] T018 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionQueryService.java` 增加来自committed parametersJson的parametersAvailable与合法desiredState投影，不改绑定/审批语义或输出任意JSON。
- [x] T019 [US3] 在 `frontend/src/services/incidentService.ts` 接入选择建议、Action列表/详情及decision；只发既有允许字段；写响应后GET完整视图，不用简化响应覆盖安全预览。
- [x] T020 [US3] 在 `frontend/src/pages/admin/incidents/ActionPanel.tsx` 展示冻结目标/工具/参数/绑定摘要与来源链接，分离创建提案和批准/拒绝，禁用缺失预览或非PENDING_APPROVAL的批准。
- [x] T021 [US3] 在 `frontend/src/pages/admin/incidents/DiagnosisPanel.tsx`、`ActionPanel.tsx`（同目录）与 `frontend/tests/incident-view.check.mjs` 验证同意图请求复用、拒绝后的明确新提案/originalActionId、409刷新后重新审阅及审批不自动重放。

## Phase 6：US4结果和刷新（P1）

**独立测试**：预置回执可验证显示；真实副作用次数由后续隔离联动验收。

- [x] T022 [US4] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/contract/ActionQueryContractTest.java` 补回执occurredAt/白名单summary、UNKNOWN的applied=null、异常摘要不泄漏及disposition保留测试。
- [x] T023 [US4] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionQueryService.java` 增加安全回执投影summaryAvailable，按既有 `ActionReceiptService.java` 格式校验，异常摘要不返回原JSON，历史状态仍可读。
- [x] T024 [US4] 在 `frontend/src/pages/admin/incidents/ActionPanel.tsx` 展示权威审批/执行/当前结果/历史回执与UNKNOWN警示，无写重试、无自动Incident生命周期转换。
- [x] T025 [US4] 在 `frontend/src/pages/admin/incidents/IncidentDetailPage.tsx`、`frontend/src/lib/incidentView.ts` 及 `frontend/tests/incident-view.check.mjs` 实现并检查5秒串行读轮询、后台暂停、路由取消、错误/终态停止和刷新从服务器恢复。

## Phase 7：联合验收与收尾

- [x] T026 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentOperationsUiFixture.java` 按已有隔离双JVM/安全模拟测试模式准备浏览器可用的临时账号、两告警/白名单目标映射及UNKNOWN演示数据；主应用/MCP均隔离，不读写用户开发库，不绕过审批；fixture仅显式启动，非默认Surefire测试，结束后关闭本次临时资源并归档证据。
- [x] T027 按 `specs/004-incident-operations-ui/quickstart.md` 使用实际浏览器登录隔离应用，完成告警→诊断引用→拒绝零应用→明确新Action批准一次应用→刷新复核；安全运行制品归档至 `incident/target/ui-evaluation/<runId>/`，应用次数来自模拟库/可信回执。
- [x] T028 按 `specs/004-incident-operations-ui/quickstart.md` 记录真实页面无映射/旧版本/冲突/注入文本/401/403/UNKNOWN等拒绝或展示场景，区分真实API门禁与预置显示fixture，禁止把缺环境/跳过记为通过。
- [x] T029 依据 `docs/development/testing.md` 运行相关Java查询/安全门禁及 `frontend/tests/incident-view.check.mjs`、tsc、lint、build；先最小检查，再按实际影响扩大，保存真实命令/结果。
- [x] T030 同步 `docs/ARCHITECTURE.md`、`docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、`docs/changelog.md` 与 `frontend/TESTING.md`：已实现字段/路由、9090默认配置、隔离浏览器证据和限制；保留既有历史/上游署名，不写机器绝对路径。
- [x] T031 核对 `specs/004-incident-operations-ui/tasks.md` 的FR/SC覆盖及真实证据、全部差异和秘密扫描；仅实际完成且通过的项勾选，无提交/推送/部署/付费或真实基础设施调用。

## 依赖与并行机会

T001→T002～T005→US1；US2可用预置Incident独立验收，集成依赖US1共享入口；US3用预置诊断独立验收，联动依赖US2；US4用预置Action独立验收，联动依赖US3。完整旅程US1→US2→US3→US4→T026～T031。

本清单不标[P]：大量任务共享service/查询/测试文件，不能将其误报为无依赖并行。真正不同文件的准备读取可并行；例如US1后端查询与页面实现须先定接口且各自前置就绪，实施时可人工协调，不在本轮自动派发实现代理。

## 覆盖映射

| 需求/标准 | 任务 |
| --- | --- |
| FR-001 | T003、T006、T011、T028 |
| FR-002 | T006、T007、T009 |
| FR-003 | T006、T008、T010 |
| FR-004 | T005、T010、T027 |
| FR-005 | T013、T015、T021 |
| FR-006～007 | T014、T015、T016、T028 |
| FR-008 | T012、T015、T021、T028 |
| FR-009～010 | T019、T020、T021、T027 |
| FR-011 | T017、T018、T020 |
| FR-012 | T019、T020、T021、T028 |
| FR-013 | T022、T023、T024、T027 |
| FR-014 | T024、T028 |
| FR-015 | T006、T008、T017、T018、T022、T023、T028 |
| FR-016～017 | T005、T010、T025、T027、T028 |
| FR-018 | T002、T003、T004、T005、T021 |
| FR-019 | T006、T012、T016、T017、T022、T026～T029 |
| FR-020 | T001、T030、T031 |
| SC-001 | T006～T011、T027 |
| SC-002 | T012～T016、T028 |
| SC-003 | T017～T021、T026、T027 |
| SC-004 | T021、T024、T028 |
| SC-005 | T008、T015、T018、T023、T025、T027、T028 |
| SC-006 | T026～T029、T031 |
| SC-007 | T001、T026、T030、T031 |

## 实施策略

先交付US1只读MVP，再按US2～US4扩展同一详情页。不得以US1通过宣称阶段完成；全阶段需SC-001～007实际证据。无实际浏览器/隔离应用门禁时保持相关任务开放。所有任务仅在用户确认设计后执行。

## 实施证据索引（2026-10-04）

- T001～008、T012、T017～018、T022～023：最小契约5项通过，扩大Incident31+Agent3共34测试，0失败/错误/跳过；`incident/target/spec-d-java-final.log`。成功码按用户选择保留，DEC-027，原全局客户端不变。
- T009～016：真实列表20→25/筛选空态、两签名告警归属、Timeline、显式诊断及引用/失效资格；首及最终run DOM/服务器快照，前端逻辑/tsc/lint/build通过。
- T019～025：真实201响应丢失后原意图重发同Action，拒绝后originalActionId/独立审批，旧页ACTION_CONFLICT，新事实STALE_SNAPSHOT保留提示；冻结预览/可信回执/UNKNOWN无写重试；实际活跃读轮询及刷新证据。串行队列与终态资格由Node逻辑验证，未宣称覆盖所有网络/浏览器时序。
- T026～028：3次成功独立临时资源运行，最终22/22场景通过，无映射零应用；匿名/普通用户12个真实HTTP断言401/403。UNKNOWN为仅展示fixture；告警注入纯文本，未另行构造Runbook HTML浏览器样例。失败启动及视口能力限制完整列quickstart，不计通过。
- T029：34项Java回归及前端4项检查通过。闪退中断build保留interrupted日志，恢复后tsc/lint/build退出0；未重跑无关全仓或付费外部探针。
- T030：项目状态/架构/决策/变更/测试指南已同步；T031差异、基线保留及秘密扫描结果见最终audit.json。

最终运行 `incident/target/ui-evaluation/spec-d-2026-10-04T12-35-13-135553Z/`：server-evidence-1～5的模拟计数0/0/1/1/1，只含拒绝和成功两Action；acceptance-summary.json含IDs与分母，07-receipt.jpg为真实截图。本地模拟验证不等于生产验证或新增人工签署。

最终静态审计：`python incident/target/verify-spec-d.py`及`git diff --check`退出0。设计基线1720个文件中1700个逐字节不变，20个既有文件仅授权范围变化，0丢失/0越界；Schema、锁文件、许可证、原全局客户端和其他既有修改保留。31/31标记核对，暂存区为空；3个运行目录秘密扫描通过、日志归档、临时凭据不存在。末次诊断晚到回调取消防护补充后逻辑/tsc/lint/build再通过，build57.45s。
