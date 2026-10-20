# 实施计划：IncidentPilot B 风格值班工作台

**分支**：`feature/incident-platform` | **日期**：2026-10-10 | **规格**：[spec.md](spec.md)

**输入**：`specs/009-incidentpilot-workbench/spec.md`

**状态**：用户于2026-10-10明确要求“进行实施”，首阶段范围与计划已批准；2026-10-12本地29/29完成，运行截图、原生滚轮、角色及旧配置视觉复核已补齐。证据见 [实施记录](implementation-report.md)；没有提交、推送或部署。

## 摘要

B 风格覆盖公共后台外壳、新真实数据值班首页及已有事件页面。增加一个管理员只读汇总接口，读取现有主 PostgreSQL 事实；不新增表、Worker、状态转换或写操作。保留原 RAG/Agent 配置功能与上游归属。

## 技术背景

**语言/版本**：Java release17/本机实际Java21；React18/TypeScript5/Vite5；本机Node20.19.5。Java17运行、Node22/Linux和托管CI不在本次结论内。

**主要依赖**：复用 Spring MVC、Sa-Token、MyBatis、Spring事务、Tailwind、lucide-react、Recharts和Incident专用客户端，不新增包。

**存储**：现有 t_incident、t_incident_action、t_incident_alert、t_incident_timeline，只读，无Schema或历史SQL修改。

**测试**：MockMvc/JUnit、ActionDbFixture隔离PostgreSQL、前端assert/tsc、ESLint、build和隔离真实应用浏览器核对。

**目标平台**：当前Windows开发环境与浏览器，四种视口按规格核对。规划核对时Docker Linux引擎不可用，集成检查须先恢复本机引擎，不替换成开发/远程数据库。

**项目类型**：现有Java多模块及React控制台。

**性能目标**：每次刷新一个GET；固定24桶和三个最多8条的列表，不做容量或响应时长承诺，不加高频全量轮询。

**约束**：保留工作区、字符串ID、先鉴权、一致快照、安全字段；概念数据不入正式页面。主题范围限定公共外壳和Incident页面，不全局替换通用CSS。

**规模/范围**：公共外壳、首页、既有列表/详情/诊断/动作页面、一个只读端点、客户端及相应测试文档。

## 宪章检查

| 原则 | Phase 0 前 | Phase 1 后 |
| --- | --- | --- |
| 署名可追溯 | 上游历史/许可证/声明保留 | 通过：品牌与来源分列 |
| 持久化正确性 | 首页只读现有事实 | 通过：REPEATABLE_READ，无写锁 |
| 人工控制副作用 | 原审批/票据/MCP不变 | 通过：首页无写操作，详情原校验保留 |
| Spec控制范围 | 视觉选择与实施批准分列 | 通过：计划待审阅，任务未执行 |
| 有证据再结论 | 新查询与主题需本轮验证 | 通过：契约/真实SQL/快照/浏览器任务齐全 |

无宪章豁免。

## Phase 0：研究

已核对路由、主题、现有人员查询、DAO/最终Schema和测试夹具；查询研究子任务仅做只读检查。决策及替代方案见 [research.md](research.md)。技术澄清已解决；等待首阶段范围审阅，不重复确认B视觉。

## Phase 1：设计

### 外壳与导航

- 新路由 /admin/workbench，后台index、管理员登录和首页面包屑指向它；普通用户仍到/chat，RequireAdmin与服务端鉴权保留。
- 主导航为值班工作台、事件中心、待审批（首页#pending真实区域）；原Dashboard标为“AI运行概览”，与已有模型/知识/智能体/审计等归入配置分组，地址保留。
- IncidentPilot / 运维事件响应品牌与简单自有SVG；保留nageoffer/ragent和Apache-2.0归属。来源GitHub Stars不能改标为本项目Stars。
- 现有知识文档搜索保留准确用途，不冒充全局事件/服务搜索；不添加未实现的Runbook/故障演练GUI。

### 主题与页面

- [B参考](design/b-workbench.png)：背景#070e1a、面板#0e1930、边框#223656；文字#edf4ff/#a5b5cc，强调#38bdf8/#6366f1，警示#fbbf24。实施以实际对比度核对为准。
- 使用incidentpilot-shell、incidentpilot-page范围类及现有组件，不覆盖全部.bg-white等通用类；旧配置页表格/弹窗/滚动需要回归。
- 首页四卡、关注事件、24h趋势、明确所选事件/动作的处理入口和最近活动，补实际待审批列表。列表/详情/诊断/动作统一深色。
- 流程摘要明确所选Diagnosis/Action；无记录显示缺失，历史成功或诊断候选不能推定当前事件完成。
- 静态装饰优先，键盘焦点及状态文字可见；UNKNOWN不以成功色表示。正文对比度>=4.5:1，大字/控件边界>=3:1。

### 只读首页

- 新GET /incident/v1/workbench，当前Spec A code="0"信封、requestId及安全错误；不改全局拦截器或已有成功码。
- 一个短时readOnly=true、REPEATABLE_READ事务。第一SELECT读数据库clock_timestamp为observedAt，全部计数/列表共享MVCC快照；这个时刻不是历史回放承诺。
- 四卡严格按spec状态/单位；关注事件预聚合Action，防止连接放大，unknown优先、pending其次，再updated_at DESC,id DESC限8。待审批及最近事实也限8。
- 24个相对observedAt的60分钟区间，覆盖[T-24h,T)，按持久化received_at计并补零，不依赖session时区。
- 不返回标题、载荷、labels、summary_json、actor身份、票据、目标参数或内部证明；不复用旧宽Timeline投影。
- 模型、诊断生成、Action创建/审批、MCP、Outbox和Timeline写入均不参与首页查询。

### 验证

契约检查401/403、先鉴权、白名单、ID字符串、真实零/空和503。真实SQL覆盖全部状态、重复身份、窗口/桶边界、非UTC时区、连接与排序限额；真实两连接协调证明本次旧快照、下次新快照，不靠sleep猜并发。前端覆盖新响应/流程分支、取消与错误。Docker可用后复用真实隔离UI夹具做四视口及主链，见 [quickstart.md](quickstart.md)。

## 项目结构

### 当前功能的文档

```text
specs/009-incidentpilot-workbench/
  spec.md, plan.md, research.md, data-model.md, quickstart.md, tasks.md
  design/b-workbench.png
  contracts/workbench-api.md, contracts/ui.md
  checklists/requirements.md
```

### 源代码（仓库根目录）

```text
incident/src/main/java/com/nageoffer/ai/ragent/incident/core/
  dao/IncidentWorkbenchMapper.java
  service/IncidentWorkbenchQueryService.java
  web/IncidentWorkbenchController.java
incident/src/test/java/com/nageoffer/ai/ragent/incident/
  contract/IncidentWorkbenchContractTest.java
  action/integration/IncidentWorkbenchQueryIntegrationTest.java
frontend/src/
  pages/admin/AdminLayout.tsx
  pages/admin/workbench/IncidentWorkbenchPage.tsx
  pages/admin/incidents/{IncidentListPage,IncidentDetailPage,DiagnosisPanel,ActionPanel}.tsx
  pages/LoginPage.tsx
  components/common/IncidentPilotMark.tsx
  services/incidentService.ts
  types/incident.ts
  lib/workbenchView.ts
  styles/globals.css
  router.tsx
frontend/tests/{incident-view.check.mjs,tsconfig.incident.json}
bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentOperationsUiFixture.java
```

**结构决策**：复用现有模块/权限/客户端；ActionDbFixture为包可见类型，真实SQL测试放同包，不复制夹具或另建验收服务。

## 复杂度跟踪

无新服务、表、依赖或宪章违规。全量计数/窗口可能扫描；不宣称生产负载性能。需要规模证据时再评估索引，不提前扩展Schema。
