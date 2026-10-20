# 任务：DEP-001 隔离本地编排与交付门禁

**输入**：`specs/008-local-delivery-gates/` 设计产物
**前置条件**：用户完整方案批准、speckit-analyze无阻塞，保留现有修改。
**状态**：20/20；本地Smoke、完整Backend及Frontend新入口验收完成，Linux/托管CI/部署未运行。
**测试**：规格明确要求入口/报告负例及真实主链/前后端门禁。
**组织方式**：按故事和依赖；路径相对仓库。

## Phase 1：准备

- [X] T001 在 `specs/008-local-delivery-gates/plan.md` 记录批准、analyze及分支/HEAD/status/diff保留基线；列出禁止动作及Linux/远程未验证边界（FR-007/012）。

## Phase 2：基础约束

- [X] T002 在 `scripts/test-check-delivery.ps1` 增加前置、参数、假子进程失败及环境恢复fixture检查，证明尚未实现入口不能通过；不运行服务（FR-001/003/004；SC-001/003）。
- [X] T003 新增 `scripts/check-delivery.ps1` 固定Gate/CheckOnly、脚本定位仓库/Wrapper选择、工具/Docker/安全选项检查，finally恢复环境，排除外部许可/连接/复用，无全局清理（FR-001/003/004）。
- [X] T004 在 `scripts/check-delivery.ps1` 创建新UUID受控输出，防链接/越界/覆盖，记录受控源码摘要与工具版本，不输出内容/环境值（FR-007；SC-004）。

## Phase 3：用户故事1 - 本地安全入口（P1，MVP）

**独立测试**：CheckOnly不启动业务；Smoke真实一项通过，所有失败环境恢复。

- [X] T005 [US1] 在 `scripts/test-check-delivery.ps1` 增加Smoke/Backend固定命令、错误退出传播、原值不泄露与恢复的契约fixture（FR-001/002/004/005）。
- [X] T006 [US1] 在 `scripts/check-delivery.ps1` 实现Smoke与Backend，固定Maven参数，复用lab.reportRoot/proofRoot；捕获raw输出不刷CI控制台，拒绝任意命令透传（FR-001/002/004/007）。
- [X] T007 [US1] 在 `scripts/test-check-delivery.ps1` 增加空/旧报告、目标缺失/跳过、重复类/计数冲突、外部实体及意外跳过拒绝fixture（FR-005/006；SC-003）。
- [X] T008 [US1] 在 `scripts/check-delivery.ps1` 实现本轮XML安全解析、Smoke精确目标、Backend必需12类及源码核对的手工跳过目录，失败不能PASS（FR-005/006）。
- [X] T009 [US1] 实际运行契约、CheckOnly及Smoke，在 `specs/008-local-delivery-gates/quickstart.md` 记录命令/退出/新XML/临时资源退出及失败记录；不套历史Spec G结果（FR-002/003/005/007/012；SC-001/002）。

## Phase 4：用户故事2 - CI定义与安全结果（P1）

**独立测试**：前端失败传播、安全摘要敏感fixture、CI只用同一入口。

- [X] T010 [US2] 在 `scripts/test-check-delivery.ps1` 为Frontend/All顺序、首个失败保留及npm本地工具选择增加fixture（FR-001/009）。
- [X] T011 [US2] 在 `scripts/check-delivery.ps1` 实现npm ci --no-audit --no-fund、incident-view检查/lint/build及All，平台正确调用npm，不下载测试工具回退、不伪造浏览器验收（FR-009）。
- [X] T012 [US2] 在 `scripts/test-check-delivery.ps1` 增加XML正文/Exception/环境/临时文件秘密fixture，证明safe字段白名单及摘要失效不发布raw（FR-010；SC-004）。
- [X] T013 [US2] 在 `scripts/check-delivery.ps1` 实现run/stages/summary/safe产物，固定reasonCode与PASS/FAIL/BLOCKED/CHECKED/INCOMPLETE；原始日志/XML/TLS仅本地（FR-005/007/010）。
- [X] T014 [US2] 新建 `.github/workflows/incidentpilot-ci.yml`，官方Actions完整SHA实施时核实，Ubuntu/pwsh/Java21/Node22、contents:read、不持久化checkout凭据、无Secrets/部署，后端90/前端20分钟，同一入口及仅safe artifact（FR-008/010）。
- [X] T015 [US2] 审查workflow语法/权限/触发/命令/产物及负例，在 `specs/008-local-delivery-gates/research.md` 记录官方依据与本地实际验证平台；不发布或触发托管CI，不宣称Linux通过（FR-008/010；SC-005）。
- [X] T016 [US2] 实际运行完整Backend/Frontend或All，在 `specs/008-local-delivery-gates/quickstart.md` 记录新源码/命令/计数/退出/跳过名单及安全摘要，失败分别保留；全后端成功后不机械重跑（FR-005/006/007/009/012；SC-002/004）。

## Phase 5：用户故事3 - 部署准备说明（P2）

**独立测试**：说明可追溯当前配置/Schema/入口，不把示例当生产或实际部署。

- [X] T017 [US3] 新增 `docs/development/delivery.md`，说明既有编排依赖/角色/初始化升级/启动/模拟/清理，常驻与生产准备TBD，区分真实Broker/模型/向量/Trace和浏览器未验收；不修改历史SQL或提供真实凭据（FR-011）。
- [X] T018 [US3] 同步 `docs/development/environment.md`、`docs/development/testing.md` 和 `frontend/TESTING.md` 的门禁入口/版本/结果安全说明，核对相对链接与命令；不静默改历史生产宣称，说明当前依据（FR-009/011；SC-005）。

## Phase 6：收尾

- [X] T019 在 `specs/008-local-delivery-gates/implementation-report.md` 做作者走读、真实失败/取舍及3自检题，核对基线摘要/差异/许可/秘密/目标/资源；同步PROJECT_CONTEXT/TODO/changelog，只有本Spec门槛满足才关闭DEP-001（FR-007/010/012）。
- [X] T020 在 `specs/008-local-delivery-gates/tasks.md` 按实际结果收尾，复核分支/HEAD/空暂存、全部原修改保留、无生产或远程越界；仅文档变化不再全仓复跑，托管CI/部署未验证明确列出（FR-011/012；SC-005）。

## 依赖关系与顺序

T001→T002→T003→T004→T005～009→T010～016→T017～020。先契约再实现，每组实际验证和记录后继续；所选MVP为US1，但只有三故事及收尾完成才关闭DEP-001。

## 并行机会

无必要并行实施代理。研究只读可独立；脚本/Maven报告/资源共用，实施串行，不标[P]。

## 实施策略与覆盖

FR-001:T002/003/005/006/010；FR-002:T005/006/009；FR-003:T002/003/009；FR-004:T002/003/005/006；FR-005:T005/007/008/009/013/016；FR-006:T007/008/016；FR-007:T001/004/006/009/013/016/019；FR-008:T014/015；FR-009:T010/011/016/018；FR-010:T012/013/014/015/019；FR-011:T017/018/020；FR-012:T001/009/016/019/020。
SC-001:T002/003/009；SC-002:T009/016；SC-003:T002/005/007/008；SC-004:T004/012/013/016；SC-005:T015/017/018/020。

2026-10-06用户批准后正式analyze无阻塞；逐项状态按本轮契约与实际运行更新，历史结果不替代本入口验收。
