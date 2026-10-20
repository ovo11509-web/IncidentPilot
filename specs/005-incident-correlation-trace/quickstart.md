# OBS-001 本地验收与复现

状态：2026-10-05 用户已授权实施，源代码与测试已落地；27/27任务完成，最小修复回归19项及完整Java门禁通过，实际计数见末节。流程见 [testing](../../docs/development/testing.md)，作者审阅及真实失败记录见 [implementation-report](implementation-report.md)。

## 前置条件及隔离

本轮实施已获批并完成analyze、HEAD/status/diff、依赖及Docker核对；复现前仍需执行同样的环境检查。使用Testcontainers临时PostgreSQL、既有Redis/存储和独立Bootstrap/MCP JVM；端口动态分配、临时账户/服务凭据/票据只在测试内存。不得连开发数据库、真实基础设施或付费服务。

快速测试注入collecting exporter；双JVM使用仅测试classpath/夹具启用的文件exporter采集真实SpanData并输出白名单JSON。运行时不部署Collector，不自动启用Langfuse。测试exporter不是固定span数据，必须由真实SDK产生并包含有效trace/span/parent与资源身份；不能声称验收了OTLP后端存储。

## 分层场景与预期

| 场景 | 实际边界 | 核对目标 |
| --- | --- | --- |
| Q01 入口成功/错误 | MockMvc/过滤器/登录/Advice，含错误分派 | body/header/log请求ID一致；每次重发新UUID；码与状态保持 |
| Q02 两Alert/重复/非anchor先处理 | 隔离PostgreSQL实际聚合及诊断 | Alert correlation分别保持；统一incidentId；历史JSON/hash不改 |
| Q03 真实本地诊断 | Runbook实际检索/抽取/校验+collecting exporter | 有实际阶段父子；REUSED无第二次生成；不含内容 |
| Q04 拒绝与正常批准 | 完整Bootstrap Worker+MCP JVM+模拟应用 | 拒绝0应用；批准1应用；dispatch→MCP→消费/应用/回执真实父子 |
| Q05 timeout/并发/线程复用 | 实际MCP有界工作线程，确定阻塞/完成 | scope隔离/清理；未知与回滚证据结果保持，不把取消当完成 |
| Q06 B06崩溃补票 | 实际Worker死亡/重启 | 新trace+原execution/dispatch身份、旧票拒绝、1应用 |
| Q07 B09双通道丢失 | 真实回调故障/工具EOF/对账不可用/迟到回执 | UNKNOWN及可信收敛；单执行、不重调工具；receipt双路径幂等 |
| Q08 Relay发送/标记失败 | 真实Relay+PostgreSQL+固定Producer | 原eventId/envelope重投，新trace/attempt；不声称Broker验收 |
| Q09 关闭/无效/不可用 | 组合开关、无效header、本地出口503/阻塞/饱和 | 业务结果/事实/重试资格保持；原Agent装配兼容 |
| Q10 秘密扫描 | 本次完整进程日志、exported spans、快照 | 实际票据/凭据全量命中0，固定字段/预算有效，无正文/堆栈 |

每场景记录本轮实际命令、开始/结束、退出码、XML、环境、分支/HEAD/dirty源码哈希、测试层级及替身。kill前未导出span允许缺失，只检验稳定业务关联和恢复的新trace，不编造旧root/link。

## 已实现测试的复现命令

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl framework -am '-Dtest=ObservabilityScopeTest,ObservabilityConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentRequestIdentityTest,IncidentCorrelationTraceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=IncidentDiagnosisTraceTest,AgentTracingConfigurationTest,AgentTraceScenarioTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl mcp-server -am '-Dtest=ProtectedSimulationTraceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=IncidentObservabilityIsolatedTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

以上测试类均已实现。每次复现须核对实际执行数非零；不能把failIfNoSpecifiedTests=false产生的0测试BUILD SUCCESS算验收。独立MCP使用原生配置属性observability.enabled / observability.endpoint，默认关闭；本轮双JVM夹具通过命令行显式配置并关闭Agent远程Trace。

## 共享回归复现入口

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=AlertIdempotencyTest,IncidentAggregationTest,AlertRecoveryTest,ActionReceiptTest,ActionOutboxCompatibilityTest,OutboxRecoveryTest,ActionUnknownConvergenceTest,ActionRecoveryRaceTest,ActionApprovalRaceTest,ActionSecretLeakTest,ActionServiceAuthenticationTest,ActionBindingV1Test,ActionStateMachineTest,ProtectedSimulationRouteTest,ProtectedSimulationE2ETest,IncidentRemediationDispatcherTest,BootstrapIsolatedStartupTest,AgentTracingConfigurationTest,AgentTraceScenarioTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

本轮默认Java门禁包含这些共享回归；以下为完整门禁实际命令（RAG_LIVE_TESTS显式为false）：

```powershell
$env:RAG_LIVE_TESTS='false'
.\mvnw.cmd '-Dspotless.apply.skip=true' test
```

单独报告外部探针和旧可选JDBC检查的跳过，不启用RAG_LIVE_TESTS。无前端变更不重复浏览器/构建，更无部署或真实Broker门槛。格式检查遵守testing，禁止用自动格式化覆写现有工作。

## 制品要求

本轮在 `bootstrap/target/obs-evaluation/354648e4-9f73-45ca-ba0a-bd23eae2fd00/` 汇总，场景在 `bootstrap/target/obs-evaluation/<scenario>-<UUID>/` 独立归档两进程实际span、安全日志、业务白名单事实、XML及manifest/hash。历史B制品继续保留，不覆盖旧文件。秘密扫描使用本次内存中的具体值，报告数量/命中而不输出凭据；测试finally仅清理本次容器/JVM。

## 当前实际结果

2026-10-05：定向业务32项通过；full-chain-2的51项定向通过不掩盖4项测试classpath启动错误；修正后第一轮双JVM3通过/1测试断言失败；默认门禁首次254项、0失败/4启动错误/1跳过，因人员网络夹具缺少Spec D查询Bean而在Incident停止。实际问题与保留日志见实施报告。

修正后的真实最小命令：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ObservabilityScopeTest,ObservabilityConfigurationTest,ProtectedSimulationTraceTest,IncidentCorrelationTraceTest,ActionPersonnelNetworkDatabaseTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

该命令退出0，framework9、MCP4、Incident6，共19项，0失败/错误/跳过；包含scope外不泄漏、真实超时任务生命周期、人员HTTP/HTTPS和SpanExporter失败下的真实PostgreSQL聚合/幂等/历史兼容。随后完整门禁与四场景已通过，最终结果如下。

## 最终本轮结果

完整默认Java门禁退出0，840项，0失败/错误，21跳过；结束于 `2026-10-05T12:49:20+08:00`。模块计数：framework38、infra-ai28、MCP68（1跳过）、Incident122、RAG302（19跳过）、Agent269（1跳过）、Bootstrap13（均通过）。Bootstrap含既有七项双JVM、诊断黄金/安全两项及OBS四项；各模块XML另行归档，跳过不是通过。

跳过范围：真实You.com需要YDC_API_KEY，19项旧RAG探针受RAG_LIVE_TESTS环境门隔离，Dashboard PostgreSQL检查需要显式ragent.dashboard.test.jdbc-url；本轮不补外部密钥、不连接开发库。

| 场景 | 实际Span数 | 模拟应用次数 | 本次实际秘密扫描数 | 命中 | 制品 |
| --- | --- | --- | --- | --- | --- |
| Q01-Q05 | 68 | 1 | 6 | 0 | [diagnosis-golden-b68e5f63-97ea-49a4-a300-f85c19d50839](../../bootstrap/target/obs-evaluation/diagnosis-golden-b68e5f63-97ea-49a4-a300-f85c19d50839/trace-proof.json) |
| Q01-Q02-negative | 56 | 0 | 5 | 0 | [diagnosis-safety-b169d57c-b47d-49f8-8ee1-bb54da76469a](../../bootstrap/target/obs-evaluation/diagnosis-safety-b169d57c-b47d-49f8-8ee1-bb54da76469a/trace-proof.json) |
| Q07 | 88 | 3 | 7 | 0 | [receipt-channels-463c42d9-592f-4900-bf35-1279171b6ae3](../../bootstrap/target/obs-evaluation/receipt-channels-463c42d9-592f-4900-bf35-1279171b6ae3/trace-proof.json) |
| Q06 | 37 | 1 | 7 | 0 | [worker-recovery-c55599a9-1aee-47fd-801b-f5590f901a9f](../../bootstrap/target/obs-evaluation/worker-recovery-c55599a9-1aee-47fd-801b-f5590f901a9f/trace-proof.json) |

实际CLIENT→SERVER和MCP SERVER→native execute父子关系按SpanData核对；Q06证明两个主应用进程身份、新Trace及原execution/dispatch身份；Q07保持UNKNOWN/迟到可信回执收敛且三个逻辑执行各只应用一次。两Alert不同correlation、历史Timeline/Outbox原样及诊断快照/摘要跨重启保持均已断言。Q08使用固定Producer，Q09实际本地503/100ms延迟/5000 Span队列压力，加真实PostgreSQL exporter失败兼容；这不是Broker或性能验收。

制品汇总：[manifest](../../bootstrap/target/obs-evaluation/354648e4-9f73-45ca-ba0a-bd23eae2fd00/manifest.json)、[源码/保留审计](../../bootstrap/target/obs-evaluation/354648e4-9f73-45ca-ba0a-bd23eae2fd00/preservation.json)、[SHA256清单](../../bootstrap/target/obs-evaluation/354648e4-9f73-45ca-ba0a-bd23eae2fd00/SHA256SUMS.json)。默认门禁日志与XML、失败历史保留在同一汇总目录，场景目录独立，不覆盖旧验收。target制品受Git忽略，重新clone需要按以上命令再生成。

结论限本机临时容器、独立Bootstrap/MCP JVM、测试classpath实际SpanData采集。没有OTLP后端存储、跨主机、生产、真实性能或真实模型/工具验收；本轮没有前端变更或浏览器门禁。未提交、推送、合并、部署、调用付费服务、操作真实基础设施或开发数据库。
