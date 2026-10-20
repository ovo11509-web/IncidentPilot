# 任务：LAB-001 本地故障回放实验室

**输入**：`specs/006-local-fault-replay/` 设计文档
**前置条件**：用户确认spec/plan/tasks；speckit-analyze无阻塞；现有修改保留。
**状态**：用户已批准，本地验收完成，18/18任务；实际结果见 implementation-report.md。
**测试**：规格FR-011明确要求自动负例与六类真实本地复跑。
**组织方式**：按用户故事划分，前置完成后依赖顺序实施。

## 格式：`[ID] [P?] [Story] 描述`

所有任务含文件路径；不因写好计划勾选实施任务。

## 路径约定

入口/清单/契约检查位于本Spec的scripts/；仅必要扩展现有Java测试的证据输出，保持生产模块与Schema。

## Phase 1：准备（共享基础设施）

- [X] T001 核对批准范围并在 `specs/006-local-fault-replay/plan.md` 记录analyze与本轮分支/源码保留基线；未批准不得执行后续。
- [X] T002 在 `specs/006-local-fault-replay/scripts/scenarios.json` 固定六场景及精确方法/数量/证明映射，核对当前源码而非复制历史PASS（FR-001）。

## Phase 2：基础能力（阻塞性前置任务）

- [X] T003 在 `specs/006-local-fault-replay/scripts/test-run-lab.ps1` 添加参数互斥、未知ID、路径穿越、重解析点与重复运行锁的最小失败检查（FR-002/004/011）。
- [X] T004 在 `specs/006-local-fault-replay/scripts/run-lab.ps1` 实现固定参数、本地路径/运行锁、UUID独立目录及安全RUNNING记录；无任意命令或连接选项（FR-002/003/004）。
- [X] T005 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java` 传播本轮runId/scenarioId/proofRoot至隔离进程与证明标记，默认非LAB路径行为兼容；最小路径传播检查（FR-003/012）。

## Phase 3：用户故事 1 - 选择并重放既有故障（P1，MVP）

**独立测试**：List、单独ALERT-REPLAY及环境受阻路径，精确测试选择且独立保存结果。

- [X] T006 [US1] 在 `specs/006-local-fault-replay/scripts/test-run-lab.ps1` 添加失败退出、环境受阻、跳过、未完成及旧报告隔离负例（FR-005/010/011）。
- [X] T007 [US1] 在 `specs/006-local-fault-replay/scripts/run-lab.ps1` 以结构化固定参数执行Maven，通过 `pom.xml` 中仅LAB激活的报告profile隔离本轮XML，记录实际退出码与前置条件；强制关闭外部探针并禁止自动格式改写（FR-002/003/005/012）。
- [X] T008 [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/AlertRecoveryTest.java` 导出本轮安全恢复事实/来源证明；必要时复用 `incident/src/test/java/com/nageoffer/ai/ragent/incident/evidence/EvidenceManifestWriter.java`，定向运行2项并记录真实结果（FR-004/006/008）。
- [X] T009 [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/OutboxRecoveryTest.java` 导出同event/envelope、尝试/发布状态及固定Producer限制，定向运行1项并记录真实结果（FR-004/006/008）。

## Phase 4：用户故事 2 - 解释故障与恢复证据（P1）

**独立测试**：单独Worker、回执与黄金/拒绝四项，事实及实际Trace均可核对。

- [X] T010 [US2] 在 `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentObservabilityIsolatedTest.java` 写本轮来源与精确场景证明；将新增制品加入既有秘密扫描，保持父子/线程/稳定身份原断言（FR-006/007/009）。
- [X] T011 [US2] 在 `specs/006-local-fault-replay/scripts/test-run-lab.ps1` 验证三个回执子执行分别计数、缺少必需Trace/事实拒绝和证据层级报告，不用总Span数推断执行（FR-006/007/008/011）。
- [X] T012 [US2] 在 `specs/006-local-fault-replay/scripts/run-lab.ps1` 验证来源及场景事实，生成安全manifest/report，记录精确测试/环境/命令/源码与实际限制；All综合判定不能覆盖失败（FR-005/006/007/008/009）。
- [X] T013 [US2] 用 `specs/006-local-fault-replay/scripts/run-lab.ps1` 单独复跑四个双JVM场景，在 `specs/006-local-fault-replay/quickstart.md` 记录实际结果、路径、扫描及失败处理（FR-011；SC-002/003/005）。

## Phase 5：用户故事 3 - 独立核验与再次复现（P2）

**独立测试**：正常本轮归档只读验证；七类负例非零；两轮轻量实验不覆盖。

- [X] T014 [US3] 在 `specs/006-local-fault-replay/scripts/test-run-lab.ps1` 添加缺失、篡改、错配来源、未列额外文件和源码运行中变化负例；保留之前旧报告/失败/跳过/未完成检查（FR-003/009/010/011）。
- [X] T015 [US3] 在 `specs/006-local-fault-replay/scripts/run-lab.ps1` 实现SHA256清单与只读Verify，核对声明范围、精确XML、本轮标记及必需证明；不修改归档或启动服务（FR-009/010）。
- [X] T016 [US3] 实际全量六场景、轻量场景重复运行、成功归档Verify与副本篡改拒绝，在 `specs/006-local-fault-replay/quickstart.md` 保存本轮环境/命令/退出码及限制（SC-001～005）。

## Phase 6：完善与横切关注点

- [X] T017 更新 `docs/PROJECT_CONTEXT.md`、`docs/ARCHITECTURE.md`、`docs/TODO.md`、`docs/changelog.md` 与 `specs/006-local-fault-replay/quickstart.md`，追加获批决策至 `docs/DECISIONS.md`；只基于真实本轮验收关闭LAB-001。
- [X] T018 对 `specs/006-local-fault-replay/` 做作者审阅、相对引用/差异/秘密与原文件保留核对，记录最小及扩大检查；确认分支/HEAD、空暂存及授权边界不变。

## 依赖关系与执行顺序

T001→T002→T003→T004→T005；US1 T006～T009先于US2 T010～T013；US3 T014～T016最后核验；T017～T018收尾。
来源协议和单个入口文件共享较多，首版串行实施，未标[P]；不创建无必要的并行代理。

## 并行示例：用户故事 1

无必要并行任务；两个数据库场景分别执行，避免共享Maven输出竞争。用户故事可以独立验收，但正式首版交付需三故事全齐。

## 实施策略

先完成List/单场景和真实失败归档，再扩展报告与Trace引用，最后Verify和All验收。每任务先最小有效检查，实际通过才勾选；无生产代码改动时不机械重跑全仓门禁。

## 需求覆盖

FR-001:T002；FR-002:T003/004/007；FR-003:T004/005/007/014；FR-004:T003/004/008/009；FR-005:T006/007/012；FR-006:T008～012；FR-007:T010～012；FR-008:T008/009/011/012；FR-009:T010/012/014/015；FR-010:T006/014/015；FR-011:T003/006/011/013/014/016；FR-012:T005/007/018。
SC-001:T002/007/016；SC-002:T008/009/013/016；SC-003:T010/011/013/016；SC-004:T006/014/015/016；SC-005:T010/012/013/016/018。
