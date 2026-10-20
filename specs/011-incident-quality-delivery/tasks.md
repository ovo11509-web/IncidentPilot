# 任务：事件查询、复盘体验与本地交付完善

**输入**：本目录spec/plan/research/data-model/contracts。
**测试**：规格明确要求聚焦自动化、真实并发及本地运行验证；先覆盖变化再实施。

## Phase 1：准备

- [x] T001 检查工作区并读取上下文，完成 `specs/011-incident-quality-delivery/spec.md` 与质量检查。
- [x] T002 冻结 `specs/011-incident-quality-delivery/contracts/`、计划及所有权边界。

## Phase 2：基础

- [x] T003 对 `specs/011-incident-quality-delivery/` 运行一致性核对和任务覆盖检查，进入实施。

## Phase 3：用户故事1——一致查询（P1）

**独立测试**：真实两连接旧/旧→新/新、多动作/历史共享读取、安全投影。

- [x] T004 [US1] 在 `incident/src/test/` 增加Action交错读取、Timeline预算及Receipt复用检查（FR-001/002）。
- [x] T005 [US1] 在 `incident/src/main/java/` 实施Action只读快照与安全Timeline/Receipt复用（FR-001/002）。
- [x] T006 [US1] 在 `incident/src/test/` 增加多诊断共享观察与批查检查（FR-003）。
- [x] T007 [US1] 在 `incident/src/main/java/` 实施诊断页共同事实/结果批查，保留失效/权限（FR-003）。
- [x] T008 [US1] 实际运行新增与既有相关查询/安全检查，结果写入本目录实施报告（FR-015）。

## Phase 4：用户故事2——复盘完善（P1）

**独立测试**：目标区分、不可读参数、超时、阶段/取消与错误分类。

- [x] T009 [P] [US2] 在 `frontend/tests/incident-view.check.mjs` 及请求检查添加目标/超时/进度/错误回归（FR-004～007）。
- [x] T010 [US2] 更新 `frontend/src/lib/incidentReview.ts` 和复盘页白名单、超时及阶段计数（FR-004～006）。
- [x] T011 [US2] 更新 `frontend/src/services/incidentApi.ts`/错误辅助，安全区分读写与超时/传输/响应，无自动重试（FR-007）。

## Phase 5：用户故事3——本地持久运行（P1）

**独立测试**：真实打包Init/Start/Verify、Stop-Start后同事件历史保留。

- [x] T012 [P] [US3] 在 `scripts/test-local-demo.ps1`/代理检查增加所有权、重复初始化、秘密及持久停止契约（FR-008～011）。
- [x] T013 [US3] 在 `resources/local-demo/` 实施Compose与外置配置、回环端口/自有持久卷（FR-008/009）。
- [x] T014 [US3] 在 `bootstrap/src/main/`/`mcp-server/src/main/` 增加local-demo装配、HTTP范围及普通远程工具关闭（FR-011）。
- [x] T015 [US3] 实施 `scripts/local-demo.ps1` 的Init/Build/Start/Status/Stop/Verify及Linux薄入口（FR-008～010）。
- [x] T016 [US3] 实际生成真实JAR/dist并运行脚本/profile/代理契约（FR-008/011/015）。
- [x] T017 [US3] 独立本机实际启动/登录/工作台/目录及事件读取，保存脱敏结果（FR-008/009/015）。
- [x] T018 [US3] 实际Stop-Start并核对同账户/Incident/诊断/审批/回执保留（FR-010/015）。

## Phase 6：用户故事4——检索与加载（P2）

**独立测试**：服务端跨页组合匹配、特殊字符、角色守卫及拆分制品。

- [x] T019 [US4] 在 `incident/src/test/` 添加组合筛选/锚点摘要/游标/敏感字段真实SQL检查（FR-012/013）。
- [x] T020 [US4] 更新 `incident/src/main/java/` 查询/Controller/Mapper的摘要与绑定筛选（FR-012/013）。
- [x] T021 [US4] 更新 `frontend/src/types/incident.ts`、service及列表页的服务器筛选/摘要/取消（FR-012/013）。
- [x] T022 [US4] 更新 `frontend/src/router.tsx`/`index.html` 的页面lazy、加载失败及品牌标题（FR-014）。
- [x] T023 [US4] 实际运行筛选检查和前端自检/TypeScript/lint/build，记录主包及chunk产物（FR-013～015）。

## Phase 7：完善与跨功能验证

- [x] T024 更新 `docs/PROJECT_CONTEXT.md`/`ARCHITECTURE.md`/`DECISIONS.md`/`TODO.md`/`NOTES.md`/交付说明，区分上游与新增（FR-015）。
- [x] T025 以实际本地环境核对筛选/报告/进度取消/角色路由，单列浏览器或平台不可用项（FR-004～007/012～015）。
- [x] T026 完成 `specs/011-incident-quality-delivery/implementation-report.md`、diff/秘密/来源及任务实际结果核对（FR-015）。
- [x] T027 修补 `mvnw.cmd` 普通Maven缓存目录Target=null的启动错误并实际核对Wrapper版本（FR-008/015）。

## 依赖与并行

T001→T002→T003后，US1、US2、US3独占目录并行。US4后端由US1负责人顺序实施，前端等契约后由US2负责人实施；不并发改同文件。T016等Java/前端修改完成后构建，避免Maven同target竞争。T017→T018，T023/018完成后T025/026。

## 实施策略

优先完成1/2/3/6建议形成可用本地运行，再验证4/5。失败先修复本轮阻塞，不将未完成验收勾为完成；无提交/推送/生产部署。
