# 本地验证指南

2026-10-03 用户已批准实施。实际结果见 `docs/changelog.md`；此处提供可复现命令，不能用“测试类存在”代替通过证据。资源由 Testcontainers 隔离，不启用 RAG_LIVE_TESTS、付费模型或真实基础设施。

## 环境与工作区

在仓库根目录 PowerShell 执行。保留已有修改，不清理、不切换分支；迁移仅应用到本轮临时数据库。

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
git diff --stat
git diff --cached --stat
git -c core.safecrlf=false diff --check
& ./.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks
docker info --format '{{.ServerVersion}}'
```

需要 Docker Engine 和 Java/Maven Wrapper。不能改连开发库弥补 Docker 缺失。
PowerShell 中给 Maven `-D` 参数分别加单引号，关闭根 POM 绑定的自动 Spotless apply。

## 定向和全仓检查

```powershell
$env:RAG_LIVE_TESTS='false'
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=*Diagnosis*,IncidentSnapshotIntegrationTest,Runbook*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=IncidentDiagnosisGoldenPathTest,IncidentDiagnosisSafetyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' test
```

缺类、零例、受阻或跳过不能当通过。默认全仓旧外部探针跳过数量单列报告。
升级链测试执行 A+B+C SQL；双 JVM 执行全量 schema，诊断场景主应用使用非 owner `ragent_app`。
密钥和端口临时生成，结束只清理本轮进程/容器。

## 模拟目标与人员 API

默认无可转换目标映射。演示配置 `incident.diagnosis.simulation-targets[incident-demo:demo:sim_service]=demo`，
B 白名单也必须包含 demo，模拟库已注册 demo。Alert resource.type 是 sim_service，Action targetType 是 SIM_SERVICE。

登录 admin 后，POST `/api/ragent/incident/v1/incidents/{incidentId}/diagnoses`，body 仅
`sourceRequestId,expectedIncidentVersion`。GET `/api/ragent/incident/v1/diagnoses/{diagnosisId}`
返回冻结快照、全文快照、引用/hash、候选与建议；列表仅摘要。HTTP 输出普通 Map/List，完成 token 不公开。

选择 POST `/api/ragent/incident/v1/diagnoses/{diagnosisId}/recommendations/{recommendationId}/actions`，
body 仅 `sourceRequestId,expectedIncidentVersion,diagnosisDigest,recommendationDigest,originalActionId?`。
服务端固定推导工具/参数，初次生成 PENDING_APPROVAL；审批沿用 B decision API，REJECT 必填 reason。
诊断和选择不签票据、不执行 MCP。

## 黄金路径

`IncidentDiagnosisGoldenPathTest`：真实签名两个同指纹 Alert→Worker 聚合→真实本地检索/抽取→
引用诊断→选择后拒绝（0应用）→新 Action 批准，经 B Worker/票据/MCP/回执成功（1应用）→
模拟目标从 PAUSED 变为 READY；重复选择不增加应用→主应用重启后全文/片段 hash、诊断来源和应用数保持。

`IncidentDiagnosisSafetyTest`：匿名/伪 decision/真实 target 字段拒绝、新告警 stale、来源注入降级、
关闭后拒绝、无身份/无票直接 MCP 失败、实际应用/票据为0，最后重启与日志保密扫描。
撤回/过期/旧版本、证据篡改、截断、中断与事务回滚由聚焦测试覆盖，不冒充生产事故。

## 离线证据与局限

数据集 `rag/src/test/resources/fixtures/runbook/cases-v1.json`：五类各6例，共30例。
语料 fixture 包括5个有效和15个跨范围/过期/撤回同症状干扰文档；gold 只供评分。
development/holdout 明确标记；标签是已批准模拟规则的合成预期，没有独立专家盲评。

- 最新报告：`rag/target/runbook-evaluation/report-v1.json`、`agent/target/diagnosis-evaluation/report-v1.json`。
- runId 的 manifest/cases/summary：`rag/target/runbook-eval/<runId>/`、`agent/target/diagnosis-eval/<runId>/`。
- 黄金路径安全证据：`bootstrap/target/incident-golden-path/<scenario-runId>/diagnosis.json`、`actions.json`、`summary.json`。
- 双进程日志：`bootstrap/target/isolated-diagnosis-golden-*-process.log`、`isolated-diagnosis-safety-*-process.log` 与 Surefire XML。

10个正例 Hit@3/SectionHit@3 ≥90%，规则支持/引用完整性/必要建议覆盖/安全降级100%，
越界/失效泄漏、模型调用、未审批应用为0。报告有逐例/分母/摘要，负例空引用为 N/A。
不宣称真实模型或未知分布质量。GET 只读；30秒超时后显示 RUNNING/超时，
下次同 Incident 诊断 POST 惰性追加 FAILED，没有后台自动收敛保证。

报告复现时将实际完整命令放入 `RAG_EVAL_COMMAND` 环境变量；报告器记录该值、只读 Git HEAD/dirty、起止时间、固定时钟与配置摘要。未提供命令时明确为 TBD，不推测。T028 独立人工标签复核由用户明确保留待办。

源码候选的门禁验收按 [来源兼容合同](contracts/offline-evaluation.md) 使用严格校验的`DELIVERY_GATE`来源及显式null Git字段，评测UUID与门禁runId分开；普通非门禁Git运行方式保持。门禁实际命令以对应run记录为准，报告中的TBD不自动推断为某条命令。

## v2复跑与审阅

RunbookOfflineEvaluationTest与DiagnosisOfflineEvaluationTest默认分别复跑cases-v1及cases-v2。v1为历史30例/26消费输入组，v2为34唯一消费输入；报告最新别名按数据集区分为report-v1.json、report-v2.json，runId目录不覆盖。原v1审阅时点raw报告保存为report-v1-reviewed-snapshot.json，原SHA已验证。v2实际命令与结果见docs/changelog.md，逐例审阅见review/v2/human-review.md。用户提供结论未签署，新增标签未人工确认，不自动关闭T028。
