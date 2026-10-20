# 任务：本地可靠性专项验收

输入：[spec.md](spec.md)、[plan.md](plan.md)。用户已批准完整计划，测试为本功能核心；不切分支。

## 阶段1：准备与来源
- [x] T001 核对现有工作区、上游归属和基线报告，写入 `acceptance-matrix.md`。
- [x] T002 完成 `research.md`、`data-model.md` 与 `contracts/acceptance.md`。
- [x] T003 分析 `spec.md/plan.md/tasks.md` 一致性并记录覆盖。

## 阶段2：US1/US2规则与证据
- [x] T004 [US2] 写定 `incident/src/test/resources/fixtures/reliability/aggregation-cases.json` 独立输入与预期。
- [x] T005 [US2] 实现 `IncidentReliabilityAcceptanceTest.java` 实际数据库/不同种子排列并对账。
- [x] T006 [US2] 验证规则测试，不把FAILED未关联误判丢失，记录 `implementation-report.md`。

## 阶段3：US3真实恢复
- [x] T007 [US3] 在 `BootstrapIsolatedStartupTest.java` 增加最小测试场景钩子，复用归属/清理。
- [x] T008 [US3] 在 `IncidentReliabilityRuntimeTest.java` 实现固定载荷并发重复/冲突与全身份对账。
- [x] T009 [US3] 实现受理后PID中断/新PID开启Worker恢复，不重新提交告警。
- [x] T010 [US3] 实现自有数据库暂停/恢复，有限等待、实际注入证明及finally恢复。

## 阶段4：US4基线及门禁
- [x] T011 [US4] 在 `IncidentReliabilityRuntimeTest.java` 实现四组固定HTTP基线及全样本事实。
- [x] T012 [US1] 在 `scripts/check-delivery.ps1` 增加两必需类，跳过预算不变。
- [x] T013 运行 `scripts/test-check-delivery.ps1` 与最小新检查，修复仅本轮问题。
- [x] T014 新Backend回归，核对新XML/sourceDigest及每个固定case/组，写入 `implementation-report.md`。
- [x] T015 更新 `docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、`docs/development/testing.md` 与本Spec任务状态。

## 依赖与实施策略
T001-003→T004-006→T007-010→T011-014→T015。前置正确性未通过不得把性能基线当合格可靠性证据。
不同文件的数据与矩阵可并行，但本轮默认顺序执行；无新增业务架构、Schema、远程资源或Git发布操作。
