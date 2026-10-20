# 验证指南：B 风格值班工作台

**状态**：实施前计划；新增类/接口尚不存在。以下命令不是已执行或通过记录。

## 前置

按 `docs/development/testing.md` 使用Wrapper、Java和前端依赖。真实PostgreSQL/双JVM夹具需本机Docker Linux引擎；规划时不可用，不得替换为开发/远程数据库或外部模型。

保留当前工作区，记录实际branch/HEAD/status；构建禁用自动spotless apply。

## 最小检查（实施后）

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentWorkbenchContractTest,IncidentWorkbenchQueryIntegrationTest,IncidentQueryContractTest,ActionQueryContractTest,IncidentOperationsQueryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

新契约验证401/403、先鉴权、白名单、ID和503。真实SQL测试复用ActionDbFixture加载A+B，覆盖状态、重复身份、桶边界、非UTC、排序限额与实际两连接快照。诊断Timeline类型须另加载C或最终全量Schema。

```powershell
Set-Location frontend
node tests/incident-view.check.mjs
npm run lint
npm run build
```

新响应/状态helper检查整合既有assert入口与类型配置。结果绑定本轮输出；Docker受阻不是通过。

## 浏览器与业务事实

复用`bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentOperationsUiFixture.java`的四依赖、真实主应用/MCP及前端，不另建mock-only验收网站。端口与临时凭据取本轮受控输出；凭据不进入日志/文档/截图。

1. 管理员进入首页，四卡对照隔离数据库事实，核对24个区间、时区与更新时间。
2. 空、已知、超过8条、重复告警及边界时刻检查计数/排序/限额/链接。
3. 多诊断/动作时明确所选来源；失效建议禁用，UNKNOWN仍未知。
4. 拒绝零应用、批准另提案一次模拟应用；无新自动审批或写重试。
5. 请求失败、取消、登录过期区分显示，不用假零或示例数字。
6. 匿名/普通用户拒绝；响应只含允许字段。
7. 1440×900、1280×720、768×1024、390×844核对滚动/折叠/键盘、长ID/证据和局部横滚。
8. 旧知识库、系统设置和AI运行概览表格/弹窗/滚动回归。

实际截图、业务核对和命令结果保存本轮本地target证据，implementation-report列环境、来源、退出与未验证项；不上传原始日志/配置/凭据。

## 收尾

`git diff --check`及范围/许可证审阅；同步长期文档。执行并验证后才勾任务。提交、推送、部署、远程CI和常驻Compose未由视觉选择授权。
