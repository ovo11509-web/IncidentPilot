# 任务：事件复盘与处置手册中心

**输入**：[plan.md](plan.md)、[spec.md](spec.md)、研究及契约。

## Phase 1：准备

- [x] T001 核对工作区、现有查询与语料；完成 specs/010-incident-review-runbooks/research.md。
- [x] T002 生成并检查 specs/010-incident-review-runbooks/spec.md、plan.md、contracts/api.md 一致性。

## Phase 2：US1 事件复盘（P1）

独立验收：分页后事实可见，变化/失败禁导出，UNKNOWN 不转为成功。

- [x] T003 [US1] frontend/tests/incident-view.check.mjs 添加采集分页/取消/游标/变化/安全导出检查。
- [x] T004 [US1] frontend/src/lib/incidentReview.ts 实现采集、数量上限和首尾复核（FR-001～004）。
- [x] T005 [US1] frontend/src/pages/admin/incidents/IncidentReviewPage.tsx 实现事实、候选、审批结果、未解决事项与导出（FR-002、008）。
- [x] T006 [US1] frontend/src/router.tsx 和 IncidentDetailPage.tsx 接入复盘入口。

## Phase 3：US2 处置手册（P2）

独立验收：五类语料真实内容，精确引用不回退，管理员鉴权在读取前。

- [x] T007 [US2] rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookCatalogControllerTest.java 编写鉴权、投影和不可用检查。
- [x] T008 [US2] rag/src/main/java/com/nageoffer/ai/ragent/rag/runbook/RunbookCatalogController.java 只读目录接口（FR-005、007）。
- [x] T009 [US2] frontend/src/lib/runbookView.ts、types/runbook.ts、services/runbookService.ts 实现验证/精确引用及检查（FR-005、006）。
- [x] T010 [US2] frontend/src/pages/admin/runbooks/RunbookCenterPage.tsx、AdminLayout.tsx、router.tsx、DiagnosisPanel.tsx 接入 B 主题页面及历史引用（FR-006、008）。

## Phase 4：验证与收尾

- [x] T011 按 quickstart.md 运行聚焦 Java、自检、类型、lint、生产构建，并记录 specs/010-incident-review-runbooks/implementation-report.md。
- [x] T012 检查可用浏览器运行及交互/窄屏边界，记录 implementation-report.md；不可用如实标注。
- [x] T013 更新 docs/PROJECT_CONTEXT.md、ARCHITECTURE.md、DECISIONS.md、TODO.md 及 frontend/TESTING.md；检查最终差异，无提交/推送。

## 依赖与实施策略

T001→T002→T003→T004→T005→T006→T007→T008→T009→T010→T011→T012→T013。
按用户推荐顺序先完成 US1 后 US2。首版不做额外演练 GUI。
可并行示例：完成共享设计后，US1 前端与 US2 Java 在不同文件可独立开发；本轮顺序实施以便核对。
