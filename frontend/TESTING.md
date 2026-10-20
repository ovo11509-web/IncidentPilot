# 前端验证

遵循 `docs/development/testing.md`。当前仓库Vite为127.0.0.1:5173，/api代理到127.0.0.1:9090；主应用context为/api/ragent。旧8080说明与当前配置不符，以vite.config.ts和application.yaml为准。VITE_API_BASE_URL缺失时Incident专用客户端使用/api/ragent，原全局客户端保持现有行为。

## 最小检查

在frontend目录执行（使用package-lock.json锁定依赖，不通过npx临时下载）：

```powershell
npm ci --no-audit --no-fund
node tests/incident-view.check.mjs
node node_modules/typescript/bin/tsc --noEmit -p tsconfig.app.json
npm run lint
npm run build
```

Incident逻辑检查以Node assert和本地TypeScript覆盖成功码、错误元数据、长ID、安全整数、选择/批准资格、冻结意图、分页去重、串行读取及写请求不自动重试。全局tsc原有WorkflowDashboard缺少ComponentType类型import，本轮仅补类型import使类型门禁可执行。

## Incident真实隔离验收

实际流程及启动命令见 `specs/004-incident-operations-ui/quickstart.md`。显式Java测试夹具复用Bootstrap/MCP双JVM模式及临时PostgreSQL、Redis和存储，使用普通ragent_app角色；tests/incident-browser-fixture.mjs仅把Vite代理指向本次临时主应用，不修改日常代理或连接开发数据库。临时账号凭据位于ignored target内，停止后删除，证据中不得保存。

通过真实浏览器操作 `/admin/incidents`：两签名告警→本地诊断/引用→选择→拒绝零应用→明确新Action/独立批准→一次模拟应用→刷新复核。应用次数取模拟数据库及可信回执；截图/mock/预置UNKNOWN只证明展示，不能替代真实执行。权限、旧版本、无映射、HTML纯文本、UNKNOWN场景分开记账。

实际命令结果保存在frontend/.output/spec-d-*-final.log；浏览器/服务器安全制品在incident/target/ui-evaluation/<runId>/。构建原有大chunk警告不等于失败，不在此阶段调整打包策略。没有运行记录的命令或场景不得标为通过。

## DEP-001统一门禁

在仓库根目录运行 `pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Frontend`，固定执行npm ci --no-audit --no-fund、Incident本地TypeScript/Node断言、lint、build；All按Backend→Frontend顺序执行。无需Docker，不使用npx临时下载检查工具。当前本机Node20.19.5/npm10.8.2，CI定义Node22；Node22/Linux/托管运行未经实测不得视为通过。

四阶段真实退出码及源码摘要存于bootstrap/target/delivery-gates/<UUID>；CI只上传safe/summary.json，不能上传构建dist、日志、配置/XML或临时账号。确定性门禁不代替本节真实浏览器验收。详见 [交付说明](../docs/development/delivery.md) 和 [Spec H结果](../specs/008-local-delivery-gates/quickstart.md)。

## Spec J：复盘与处置手册

沿用上述命令，incident-view.check.mjs 追加跨页采集/游标异常/数量上限/途中取消/首尾变化/跨事件/导出字段与 Markdown 转义、历史 UNKNOWN/失效证据及 Runbook 来源/章节/精确版本不回退检查。测试读取仓库打包语料，无付费或外部模型。

浏览器入口：事件详情→查看事件复盘报告→下载 Markdown；侧栏→处置手册；展开历史引用→核对同版本手册。复盘注明非事务快照，变化/失败禁导出；语料为模拟手册。实际运行和限制见 [Spec J 实施记录](../specs/010-incident-review-runbooks/implementation-report.md)。
