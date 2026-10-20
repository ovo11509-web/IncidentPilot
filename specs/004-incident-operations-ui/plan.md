# 实施计划：Incident操作界面与安全演示闭环

**分支**：`feature/incident-platform` | **日期**：2026-10-04 | **规格**：[spec.md](spec.md)

**输入**：specs/004-incident-operations-ui/spec.md。**状态**：2026-10-04用户批准；本地实施及隔离验收完成，结果与限制见quickstart.md。

## 摘要

复用现有/admin框架新增列表、详情及诊断/审批/结果区。incident补全白名单只读查询；其余使用A/B/C已实现接口。新增专用客户端逐接口适配A的code="0"及B/C的code="SUCCESS" envelope，避免扩大全局HTTP改动。已有审批、Worker、MCP协议和Schema保持。

## 技术背景

**语言/版本**：TypeScript 5.5/React 18.3、Java 17支持基线；已有验收JDK21运行记录不代表版本迁移。

**主要依赖**：既有Vite5、React Router6、Axios、Radix/Tailwind、Zustand；Spring Boot/MyBatis/Sa-Token。无新依赖。

**存储**：既有主PostgreSQL Incident表、隔离模拟库；无迁移或新表。浏览器不持久化证据副本/票据。

**测试**：JUnit与隔离PostgreSQL、现有Node assert+tsc模式、lint/tsc/Vite build、通过可用浏览器工具记录真实隔离主流程。

**目标平台**：本地桌面浏览器与主应用；相同/api代理访问，不跨域直连MCP。

**性能目标**：有界分页、单请求串行轮询和停止机制；本轮不作未经实测的吞吐/延迟承诺。

**约束**：代码实施待设计确认；无付费、真实写、提交/推送/部署。全部现有工作区修改保留。

**规模/范围**：两个路由、四区操作详情、少量只读后端投影与一个客户端；目标映射仅隔离模拟。

## 宪章检查

| 原则 | Phase 0前 / Phase 1后 |
| --- | --- |
| 上游署名 | 通过：不替换原前端/许可证，新贡献可追溯 |
| 持久化边界 | 通过：不前端创造状态，不跨模块访问Mapper |
| 人工副作用控制 | 通过：选择/审批分离，原服务端授权不可绕过，UNKNOWN不重试 |
| Spec范围 | 通过：004唯一规格目录，待确认，零空平台脚手架 |
| 证据结论 | 通过：纯逻辑、接口、真实浏览器各层分开验收 |

## 项目结构

### 当前功能的文档

spec.md、plan.md、research.md、data-model.md、quickstart.md、contracts/personnel-api.md、tasks.md、checklists/requirements.md。

### 源代码（计划新增或定向修改）

```text
frontend/src/
  router.tsx
  pages/admin/AdminLayout.tsx
  pages/admin/incidents/IncidentListPage.tsx
  pages/admin/incidents/IncidentDetailPage.tsx
  pages/admin/incidents/DiagnosisPanel.tsx
  pages/admin/incidents/ActionPanel.tsx
  services/incidentApi.ts
  services/incidentService.ts
  types/incident.ts
  lib/incidentView.ts
frontend/tests/incident-view.check.mjs
frontend/tests/tsconfig.incident.json
incident/src/main/java/com/nageoffer/ai/ragent/incident/
  core/web/IncidentController.java
  core/service/IncidentQueryService.java
  core/dao/IncidentMapper.java
  alert/dao/AlertMapper.java
  diagnosis/service/IncidentSnapshotService.java
  action/service/ActionQueryService.java
incident/src/test/java/com/nageoffer/ai/ragent/incident/
  contract/IncidentQueryContractTest.java
  action/contract/ActionQueryContractTest.java
```

**结构决策**：查询投影由incident持有；IncidentSnapshotService现有安全清洗规则在同模块按最小方式复用，不复制不同脱敏规则。页面局部state处理在途请求，无新增全局store；incidentView只含状态/分页/请求资格纯逻辑，无自建通用框架。

## 阶段与验证

1. **Phase 0研究已完成**：对照当前前端、Controllers/Schema/查询服务、A/B/C及测试指南，记录research中的四个视图缺口和envelope冲突。研究子任务仅只读。
2. **Phase 1设计已完成**：只读实体/投影与接口见data-model、contracts，验收见quickstart；均为待确认设计。
3. **实施准备**：确认Spec、定向检查已有测试，准备纯逻辑测试入口；不升级依赖。
4. **US1**：只读列表/关联告警/时间线+路由导航，形成首个MVP。
5. **US2**：历史诊断、显式诊断、引用与适用性控制。
6. **US3**：安全Action预览、选择幂等、批准/拒绝与冲突恢复。
7. **US4**：回执安全摘要、UNKNOWN提示、后台读取轮询与刷新重建。
8. **联合验收**：隔离浏览器fixture以test-scope的显式启动器准备，不把等待人工浏览器操作的长驻任务放入默认Surefire；明确启动/终止临时资源。lint、tsc、build、相关Java门禁、真实隔离浏览器黄金路径及负例；记录真实工具/命令/请求身份/应用次数/证据摘要，不把截图当成执行次数证明。

## 错误与刷新策略

专用HTTP错误保留status/code/requestId用于冲突提示，不记录Authorization或原载荷。401清理现有会话并转登录，403保持权限错误，404清楚显示不存在，409重新GET并要求复核。命令超时提示结果未知，仅允许人员原请求重发或先查看历史；没有自动写重试。审批冲突不自动重发decision。

所有路由切换取消旧请求；后台读轮询5秒、一次在途、隐藏暂停、错误/终态停止。当前服务返回部分简化写响应，命令后必须GET完整实体。Timeline/分页按稳定身份去重，筛选切换清游标。

## 复杂度跟踪

无宪章违规或新增架构例外。候选全局Axios成功码兼容改动被放弃，因为新专用客户端即可满足需求且保留上游页面行为。浏览器验证使用已有工具，不添加测试框架依赖；如果实际环境缺失必须报告未完成，不能降低黄金路径门槛。
