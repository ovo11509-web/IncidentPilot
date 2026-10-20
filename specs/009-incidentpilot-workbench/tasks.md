# 任务：IncidentPilot B 风格值班工作台

**输入**：`specs/009-incidentpilot-workbench/` 的规格、计划、研究及契约。

**前置条件**：用户已选择B视觉，并明确批准首阶段功能与技术计划实施。

**测试**：spec FR-016要求契约、真实数据库与浏览器验证，保留相应任务。

**状态**：2026-10-12，29/29已完成；T027已补齐规定四视口截图、原生滚轮、角色及旧配置视觉回归。此前业务主链与本次只读视觉夹具分开记录，范围和限制见implementation-report.md。

## Phase 1：准备

- [x] T001 在 `specs/009-incidentpilot-workbench/plan.md` 记录首阶段明确批准，并在未来 `implementation-report.md` 记录实际分支/HEAD/status和变更前基线，保留所有已有文件。
- [x] T002 按 `specs/009-incidentpilot-workbench/quickstart.md` 核对本机Docker/工具与隔离资源；不可用则记录阻塞，不改连开发/远程库。

## Phase 2：基础能力

- [x] T003 在 `frontend/src/styles/globals.css` 定义范围主题、布局/焦点/对比度与窄屏基础，不改全局通用类或旧页内容；保留侧栏min-h-0独立滚动。（FR-001、013、014）

## Phase 3：US1 - 品牌与值班入口（P1）

**目标**：实际管理员入口与公共导航体现IncidentPilot，既有配置及普通聊天保持可用。

**独立测试**：管理员/普通用户登录和直接路由；侧栏滚动/折叠及上游归属。

- [x] T004 [US1] 在 `frontend/src/components/common/IncidentPilotMark.tsx` 创建简单自有SVG标识，不复制其他品牌或用概念图当页面资产。（FR-001、002）
- [x] T005 [US1] 修改 `frontend/src/pages/admin/AdminLayout.tsx` 的品牌、导航分组、首页面包屑和归属说明；原知识搜索/Github Stars准确标注，所有菜单有真实目标。（FR-002、003）
- [x] T006 [US1] 修改 `frontend/src/router.tsx` 和 `frontend/src/pages/LoginPage.tsx` 的管理员默认入口与role跳转，保留RequireAdmin、普通/chat及已有地址。（FR-003、008）
- [x] T007 [US1] 在 `frontend/src/pages/admin/AdminLayout.tsx` 完成B公共外壳样式、可滚动折叠侧栏与窄屏导航；旧配置内容区不受全局深色覆盖。（FR-001、013、014）

## Phase 4：US2 - 真实首页数据（P1）

**目标**：固定口径、固定列表限额、一个只读快照，能安全导航到实际事件和提案。

**独立测试**：已知集合/空集合/错误/权限/两连接变化，逐项与隔离数据库事实核对。

- [x] T008 [P] [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/contract/IncidentWorkbenchContractTest.java` 先定义401/403、读库前鉴权、白名单、字符串ID、空值及503契约，确认新增能力实现前检查不通过。（FR-008、009、016）
- [x] T009 [P] [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/IncidentWorkbenchQueryIntegrationTest.java` 复用ActionDbFixture先定义全状态、桶边界/非UTC、去重、连接/限额排序、实际两连接快照场景；按需要加载C/最终Schema。（FR-004～007、016）
- [x] T010 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/dao/IncidentWorkbenchMapper.java` 实现数据库时刻、四卡、24桶、三个有界列表的固定只读SQL/投影，不读取秘密或任意JSON。（FR-004～008）
- [x] T011 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentWorkbenchQueryService.java` 实现先鉴权与readOnly REPEATABLE_READ，一次时刻/快照、白名单响应，无写锁或副作用。（FR-005、008、012）
- [x] T012 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/web/IncidentWorkbenchController.java` 实现GET及既有code=0/requestId/安全错误契约，不改机器鉴权豁免。（FR-008、009）
- [x] T013 [US2] 在 `frontend/src/types/incident.ts` 与 `frontend/src/services/incidentService.ts` 增加workbench类型/单GET，复用专用客户端取消/错误规则，不改全局拦截器。（FR-004～009）
- [x] T014 [US2] 在 `frontend/src/lib/workbenchView.ts`、`frontend/tests/incident-view.check.mjs` 和 `frontend/tests/tsconfig.incident.json` 覆盖必要响应/状态/时间映射，拒绝不合法ID/计数/桶结构，错误不变零。（FR-005、006、009、016）
- [x] T015 [US2] 在 `frontend/src/pages/admin/workbench/IncidentWorkbenchPage.tsx` 实现B四卡、关注事件、明确窗口时区的曲线及最近活动；加载/空/错误/旧时间明确，只读刷新。（FR-001、004～009、015）
- [x] T016 [US2] 在 `frontend/src/pages/admin/workbench/IncidentWorkbenchPage.tsx` 实现真实待审批区域和事件/动作深链；展示完整ID及单位，不照搬假标题、编号、搜索或流程事实。（FR-003、007、011）
- [x] T017 [US2] 按 `specs/009-incidentpilot-workbench/quickstart.md` 运行新增契约/SQL与现有IncidentQueryContractTest、ActionQueryContractTest、IncidentOperationsQueryTest，保存本轮真实结果。（FR-016）

## Phase 5：US3 - 统一事件主题与安全交互（P1）

**目标**：列表、详情、诊断和动作风格统一，安全操作/证据与实际关联保持完整。

**独立测试**：多动作/诊断、失效/过期、拒绝/批准与UNKNOWN，长ID和各视口。

- [x] T018 [US3] 在 `frontend/src/pages/admin/incidents/IncidentListPage.tsx` 应用范围主题及清晰状态/单位，保留分页、筛选、刷新与abort。（FR-001、010、013、014）
- [x] T019 [US3] 在 `frontend/src/pages/admin/incidents/IncidentDetailPage.tsx` 统一来源/Timeline面板并按实际actionId在数据就绪后聚焦，保留分页与原轮询停止条件。（FR-007、010、011）
- [x] T020 [P] [US3] 在 `frontend/src/pages/admin/incidents/DiagnosisPanel.tsx` 统一深色证据/建议/失效提示，保留可选资格、来源与请求幂等交互。（FR-010～013）
- [x] T021 [P] [US3] 在 `frontend/src/pages/admin/incidents/ActionPanel.tsx` 统一冻结提案/独立审批/回执/UNKNOWN视觉，保留原校验与拒绝理由，无新写重试。（FR-010～013）
- [x] T022 [US3] 在 `frontend/src/lib/workbenchView.ts`、`frontend/src/pages/admin/incidents/IncidentDetailPage.tsx` 与 `frontend/tests/incident-view.check.mjs` 实现并检查明确关联的流程摘要，覆盖无诊断、多动作、失效及UNKNOWN，不凭历史事实画完成。（FR-011、012、016）
- [x] T023 [US3] 在 `frontend/src/styles/globals.css` 核对实际对比度、长ID/证据、局部横滚与reduced-motion，修复范围内问题，不扩大旧配置页换肤。（FR-013、014）

## Phase 6：验证与收尾

- [x] T024 调整 `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentOperationsUiFixture.java` 的新首页真实数据覆盖，保持隔离/随机凭据和生命周期，不创建生产或mock-only启动栈。（FR-016）
- [x] T025 依据 `specs/009-incidentpilot-workbench/quickstart.md` 完成真实PostgreSQL全部场景及快照验证，并在 `implementation-report.md` 记录命令、环境、来源、退出和限制。（SC-002、003、007）
- [x] T026 运行 `frontend/tests/incident-view.check.mjs`、全量ESLint与production build，在 `implementation-report.md` 保存本轮结果，不复用旧绿色报告。（SC-007）
- [x] T027 按 `specs/009-incidentpilot-workbench/contracts/ui.md` 做四视口、角色、滚动/折叠、旧配置回归及主链真实浏览器核对，记录实际截图和拒绝零/批准一次应用事实。（SC-001、004～006）
- [x] T028 在 `specs/009-incidentpilot-workbench/implementation-report.md` 与 `docs/PROJECT_CONTEXT.md`、`docs/ARCHITECTURE.md`、`docs/TODO.md`、必要 `docs/NOTES.md` 同步已验证实现及未验证项，按实际完成勾任务。（SC-007）
- [x] T029 根据 `specs/009-incidentpilot-workbench/quickstart.md` 审阅差异及git diff --check，核对上游归属、无秘密/无额外范围；未授权不提交/推送/部署。（FR-002、012、015）

## 依赖关系与执行顺序

- T001～003为前置；不得在范围批准前实施。
- US1外壳可独立验证；新首页路由在US2完成后整体可用，不能把加载错误的中间态发布为交付版。
- US2：T008/009测试先行，T010→011→012；T013/014→015/016→017。
- US3复用外壳与已有服务；T019先定义所选动作，T022在新helper已有后补摘要；T020/021只改各自页面，可并行。
- 最终验证依赖三个故事全部完成；新增真实查询需真实数据库通过后才关闭。

## 并行示例

- US1：图标T004与已有CSS范围基础完成后的路由T006可独立处理；AdminLayout的T005/T007串行避免冲突。
- US2：T008契约与T009集成检查不同文件可并行准备；SQL、事务、端点顺序实现。
- US3：T020诊断与T021动作主题不同文件可并行；helper/详情共享文件串行。

## 实施策略

批准后先外壳，再真实汇总/首页，再既有事件页与流程摘要；逐层检查后做完整浏览器主链。任何Docker阻塞明确报告，不借外部库凑绿。不引入新框架、全局搜索、Runbook编辑/演练GUI、生产接入或常驻部署。
