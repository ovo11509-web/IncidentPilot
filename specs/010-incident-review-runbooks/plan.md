# 实施计划：事件复盘与处置手册中心

**分支**：`feature/incident-platform` | **日期**：2026-10-11 | **规格**：[spec.md](spec.md)

## 摘要

依授权按 US1→US2 实施。前端复用现有安全查询分页形成只读报告，明确非事务快照；Java rag 模块提供管理员只读 Runbook 目录，复用已校验语料，避免 incident→rag 循环依赖。

## 技术背景

**语言/版本**：Java release17、TypeScript、React18。
**主要依赖**：现有 Spring MVC、Sa-Token、RunbookCorpus、React Router、Tailwind；不加依赖。
**存储**：读取既有 PostgreSQL/API 与打包语料，不新增 Schema。
**测试**：JUnit/Mockito、Node assert、TypeScript、lint、Vite 构建。
**目标平台**：现有 Web 管理后台；本机检查不代表生产。
**性能目标**：不声明吞吐/延迟；采集有数量上限，诊断逐个读取，超限禁导出。
**约束**：只有 GET，导出本地 Blob；不得使用原始 payload、票据或认证信息。
**规模/范围**：两条只读旅程，五类现有 Runbook。

## 宪章检查

研究前/设计后均通过：保留上游、只读无事件状态转换、无 MCP 副作用、Spec 控制两项授权功能、聚焦自动化证明；无违规。报告非事务快照限制作为产品契约明确展示。

## 项目结构

- `frontend/src/lib/incidentReview.ts`：分页采集、首尾复核、显式字段导出。
- `frontend/src/pages/admin/incidents/IncidentReviewPage.tsx`：报告页。
- `rag/src/main/java/com/nageoffer/ai/ragent/rag/runbook/RunbookCatalogController.java`：鉴权及只读目录投影。
- `frontend/src/lib/runbookView.ts`、`frontend/src/services/runbookService.ts`、`frontend/src/types/runbook.ts`：目录验证、引用链接。
- `frontend/src/pages/admin/runbooks/RunbookCenterPage.tsx`：目录、搜索、精确版本与正文。
- `frontend/src/router.tsx`、`frontend/src/pages/admin/AdminLayout.tsx`、事件详情与诊断面板：入口与引用链接。
- `frontend/tests/incident-view.check.mjs`、rag Runbook 测试：自动化契约。

## 实施顺序

1. 测试分页、变化、取消、导出白名单，完成采集及报告页。
2. 测试 Java 鉴权与语料投影，完成手册中心与历史引用联动。
3. 运行聚焦 Java、前端自检/类型/lint/build；检查可用运行环境，记录浏览器边界。

## 复杂度跟踪

无新增抽象/包/表；首版没有反向全局引用索引。复用 API 的采集不是原子快照，首尾复核不能消除所有并发窗口，页面和导出均声明这一限制。
