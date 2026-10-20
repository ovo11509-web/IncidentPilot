# 验收指南：审批到安全模拟处置

**用途**：实施完成后复现 Spec B 的授权、单次执行和故障收敛证据。\
**当前状态**：代码实施和定向测试已运行，2026-10-03 默认全仓 Java 测试通过；B01～B12 的实际覆盖与未验证范围逐项见 [变更记录](../../docs/changelog.md) 的场景矩阵及续跑记录。部分场景仍需补充完整验收，以下目录本身不表示已经通过。

2026-10-04 后续补充已覆盖 B06 崩溃恢复、B09 双通道故障及回执/截止竞争、B11 真实 Relay 故障恢复。最新分层证据与剩余范围见 [证据索引](evidence-index.md)，安全假设见 [威胁模型](../../docs/security/incident-action-threat-model.md)。早期段落保留其历史时点含义，不作为当前诊断能力状态。

2026-10-01 Docker Engine 恢复后，新增的跨 JVM 锁等待超时断言随 `ProtectedSimulationE2ETest` 在隔离 PostgreSQL 上通过。故障注入断言集中在该测试类中；未穷举的 B01～B12 场景仍按下方目录及变更记录标注范围。

后续隔离测试已分别验证 B08 已回滚的 `FAILED/TIMEOUT_PROVEN_ROLLED_BACK` 与证据表不可读的 `UNKNOWN/TIMEOUT_UNPROVEN`；还新增了同一数据库内“两个告警聚合→拒绝→批准→独立 MCP JVM 一次模拟应用”的 A+B 联动测试。专用 Runbook 诊断编排尚未实现，因此这条联动测试不是包含诊断步骤的完整黄金路径。

2026-10-03 又新增完整 Bootstrap 启用 Action Worker 与独立 MCP 同时运行的正向用例：主库 Execution、消费票据、回执各 1，模拟目标 READY→PAUSED、应用记录 1，同来源重放不产生第二次应用；定向类 2 项通过。MCP 回调地址须包含主应用的 `/api/ragent` 前缀；客户端仅接受空路径或此已知前缀。新增用例后全仓命令退出码 0，九模块成功，合计 781 项、0 失败、0 错误、21 跳过；下方 780 项数字属于新增用例前的一轮结果，最新细项见 `docs/changelog.md`。

同一正向用例还通过测试专用回调代理验证两种次序：主库先消费第二个 Incident 的票据、随后人员 HTTP 关闭该 Incident，MCP 放行后回执仍将 Action 收敛为 `SUCCEEDED`；另一个 Incident 先关闭再放行消费时票据失效、没有第三次模拟应用。这是完整主应用与独立 MCP 的本地 B03 证据，不能外推到跨主机部署。

同一完整主应用用例还验证 B02 的自动审批过期：启用 Action Worker 后，待审批 Action 与 Approval 到期均为 `EXPIRED`，迟到 HTTP 批准返回 409，Execution 与模拟应用无新增。另两条完整主应用与独立 MCP 用例分别验证授权截止及票据截止：被暂缓的真实消费请求到期后均返回 409，票据没有被消费，模拟目标保持 READY；授权截止由恢复 Worker 收敛为 `EXPIRED`，票据截止场景随后关闭 Incident，避免合法补票产生后续执行。整类 4 项通过，命令与初次夹具错误见变更记录。

同一用例的 B05 本地联合验收已逐项改动未消费票据请求中的 11 个绑定字段，主应用在线消费入口均返回 409，票据保持 `ISSUED`，没有新增模拟应用；成功消费的票据重放也返回 409。独立 MCP 入口的篡改拒绝由 `ProtectedSimulationE2ETest` 验证，具体命令和结果见变更记录。

B12 本地会话恢复续补：在完整主应用和独立 MCP 的隔离用例中，处置结束后重启主应用 JVM，新会话仍能查询 `SUCCEEDED` Action、`CONSUMED` 票据安全状态及回执 Timeline；模拟应用与回执次数不增加。测试进程扫描重启前后主应用及 MCP 日志中的两张实际票据、两次会话令牌和双向服务凭据。人员 Action 详情按已确认的 `openapi.yaml` 返回 `executionKey`；它不是一次性票据，不能独立取得执行许可。本机隔离回环别名 `127.0.0.2` 已验证一次性证书下人员 HTTPS、MCP→假主应用回调 TLS，以及同类 MCP SDK 客户端→真实受保护 MCP 的 HTTPS；同地址明文服务 URL 被拒绝。新增完整主应用 Worker 与独立 MCP 的跨 JVM TLS 用例：Worker→MCP 和 MCP→测试专用回调代理均经 HTTPS，代理仅在本机 `127.0.0.1` 转发到主应用；实际主库授权、单次模拟应用、回执及重启后查询均通过。实际跨主机部署和外部日志系统未运行，不能据本地证据宣称它们已验收；已确认的 B12 场景未把二者列为完成门槛。

## 1. 前置条件与隔离

- 先实现并验证 Spec A 的 Incident、主状态、Timeline 和 Outbox 基线，再应用 Spec B 的追加式主库迁移。
- 使用 JDK 17、仓库 Maven Wrapper、本地 Docker/Testcontainers PostgreSQL、隔离的 `ragent_bit` 演示库与本地 MCP Server。不要连接共享开发数据、远程模型、付费服务或真实基础设施。
- 两个方向的服务凭据仅通过测试进程的临时环境/秘密注入，彼此不同；验证跨主机配置必须使用 TLS。本地回环可以按 DEC-011 使用明文 HTTP，但不得把该例外用于远程环境。
- 验收数据固定一个可查询的未 `CLOSED` Incident、一个 `SIM_SERVICE` 演示目标和 `simulation.set-state` v1。每个场景从已知隔离状态开始，记录输入标识和当前配置；不要把票据明文写入运行清单或日志。

Docker 不可用时，数据库约束、并发和跨进程场景明确标为“环境受阻”；不得用 H2 或共享库替代。

## 2. 执行入口

已实际执行的隔离入口（从仓库根目录执行；所有命令均跳过自动格式化写入）：

```powershell
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionStateMachineTest,ProtectedSimulationE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ProtectedSimulationE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=AlertToSimulationE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=SimulationToolExecutorTest' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionQueryContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPersonnelHttpDatabaseTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPersonnelNetworkDatabaseTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=SimulationTimeoutEvaluatorTest' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=BitSchemaScriptTest' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=IncidentActionStatusAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am -DskipTests compile
```

Incident 定向夹具新建隔离 PostgreSQL 时依次应用 `resources/database/upgrades/v2.0.0/260923_incident_core.sql` 与 `resources/database/upgrades/v2.0.0/260923_incident_safe_action.sql`；Bootstrap 双进程测试在临时主库应用完整 `resources/database/schema_pg.sql`。独立模拟库使用 `mcp-server/src/main/resources/db/bit-schema.sql`。运行结果以对应 Surefire XML 和[变更记录](../../docs/changelog.md)为准。仓库完整 Java 测试命令为：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' test
```

2026-10-03 完整命令退出码 0，九个 Maven 模块均 `SUCCESS`；有测试的模块分别为 `framework` 29、`infra-ai` 28、`mcp-server` 63（跳过 1）、`incident` 102、`rag` 296（跳过 19）、`agent` 261（跳过 1）、`bootstrap` 1，合计 780 项、0 失败、0 错误，`system` 无测试用例。19 个 RAG 跳过项仍包括默认隔离的旧模型、Milvus 和开发库联调探针，不能据此宣称外部联调已通过。`BootstrapIsolatedStartupTest` 使用独立主库、模拟库、Redis、RustFS 与两个回环 JVM，验证真实登录、拒绝、到期、伪造元数据、关闭竞争及零副作用；Action Worker 关闭，正常批准执行和故障路径由 `ProtectedSimulationE2ETest` 与 `AlertToSimulationE2ETest` 跨 JVM 验证。`ActionPersonnelHttpDatabaseTest` 使用 MockMvc，`ActionPersonnelNetworkDatabaseTest` 使用精简 Spring/Tomcat 与一次性本地 HTTPS 证书；均不代表跨主机 TLS 部署验收。`-Dspotless.apply.skip=true` 防止编译期格式化器修改源码。默认不要求 RocketMQ Broker；Outbox 发布语义可用固定响应发布器验证，Broker 边界另行运行。B08 两种结论已跨进程验证；其他部分覆盖场景不能据此视作全部 B01～B12 验收。

## 3. 场景目录

| 场景 | 注入/操作 | 必须核对 |
| --- | --- | --- |
| B01 正常批准执行 | 提议、批准、Worker 派发、首次消费、模拟工具成功 | 一个 Action/Approval/Execution；一次副作用；`SUCCEEDED`；权威回执、Timeline 和 Outbox 各按契约唯一 |
| B02 拒绝与过期 | 分别拒绝、等待审批/授权/票据截止 | `APPROVAL_REJECTED` 与 `EXPIRED` 区分；模拟目标不变；迟到确认不执行 |
| B03 关闭竞争 | 在关闭前后并发审批、派发、消费 | 关闭先胜出则无新启动；消费先胜出则结果或 `UNKNOWN` 仍可收敛；Incident 主状态与 Action 事实一致 |
| B04 入口旁路 | 枚举全部注册表；旧确认卡、RAG 直调、旧 `/mcp`、专用入口无身份/无票；逐个以已知名称直调旧写工具并试未知新注册 | 普通入口只含经核查的只读工具；旧写工具与模拟工具在错误入口均不可执行且副作用为零；未知注册默认拒绝，未认证自报 ID 不终结 Action |
| B05 绑定篡改与重放 | 逐项改工具、版本、目标、参数、Execution、幂等键；重放票据 | 消费前拒绝；同一票据和幂等键不二次启动 |
| B06 补票竞争 | 派发后使 Worker 崩溃丢明文；旧票据迟到、消费与恢复并发 | 最多一个 `ISSUED`；替代票据保留 Execution/幂等键；已消费时绝不补发 |
| B07 消费后故障 | 消费后中断 MCP/主应用，丢普通响应或回调 | 不重调写工具；最终可信回执或 `UNKNOWN`，副作用最多一次 |
| B08 超时证明 | 分别提供已回滚证据与证据不足的超时 | 前者 `FAILED/TIMEOUT_PROVEN_ROLLED_BACK`；后者立即 `UNKNOWN/TIMEOUT_UNPROVEN`；仅缺少应用记录不能冒充回滚 |
| B09 双路径与迟到 | 回调/主应用响应分别或同时丢失，随后投递同一或迟到确定回执 | 同一回执一份权威结果；超过 `receiptDueAt` 为 `UNKNOWN`；合法迟到回执可收敛，冲突终态只告警 |
| B10 对账与人工结论 | 只读观察已应用、证明未应用、无法判断；有权限人员提交证据 | 对账不改模拟目标；`UNKNOWN` 仅按证据收敛；相反结论不覆盖 |
| B11 Outbox 恢复 | 审批或回执提交后使发布失败、发送成功但标记失败 | 业务事实不回滚；相同事件 ID 重投；Spec A 两类事件仍符合原契约 |
| B12 保密与会话恢复 | 重启 Agent，查询 Action、Timeline、票据安全状态；扫描日志/证据 | 不重新调用工具；不泄露票据、服务凭据、完整不安全参数或堆栈 |

## 4. 每次运行的证据

运行清单应包含场景版本、Spec/合同版本、命令或入口、环境、演示目标初始状态、配置的实际审批/票据/执行时限、固定输入标识、起止时间，以及通过/失败/受阻判定。逐场景保存预期和实际 HTTP/MCP 分类、Action/Approval/Execution/Ticket 最终状态、模拟目标与应用次数、回执数量和处置、Timeline 序号/稳定键、Outbox 事件 ID 与发布次数、冲突告警，以及禁止内容扫描结果。所有证据必须脱敏；运行目录应放在临时或构建输出中，不提交秘密或票据明文。

## 5. 停止与报告

任一越权副作用、第二次启动、冲突终态覆盖、无证据将超时判 `FAILED`，或秘密出现在响应/日志/证据中，均阻止验收。只报告真实执行的命令、环境和结果；未运行的场景保持“未验证”，不能据设计文档声称可靠性或性能已达标。

## 6. B06 补充复现入口

2026-10-04 按既有 FR-027/028、SC-007 补充验收，不改变执行协议：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionRecoveryRaceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#workerCrashAfterTicketIssueRecoversSameExecutionAndAppliesOnce' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

并发入口先用 Incident 事务锁确定胜出者，再从 `pg_stat_activity` 观察另一个线程实际阻塞在数据库锁上。消费先胜出时恢复不补票；恢复先胜出时旧票拒绝，新票保留原 Execution、执行键和 requestId。两种次序都只消费一次，不把线程启动顺序当并发证据。

完整运行入口使用临时主库、模拟库、Redis、存储、完整 Bootstrap Worker 与独立 MCP JVM。测试代理截住首次真实消费请求；确认票据已签发、未消费、模拟应用为 0 后杀死主应用 JVM。票据 TTL 通过既有配置设为 5 秒，自然到期后新 JVM 的原有调度器恢复，不手动调用 recover 或 SQL 伪造成功。恢复保持一个 Execution，票据为一张 EXPIRED 和一张 CONSUMED；应用、回执各一，重复来源请求返回原 Action，迟到旧票返回 409，Incident 保持 OPEN。

每个成功运行的安全制品位于 `bootstrap/target/b06-evaluation/<runId>/`，包含 `summary.json`、`action.json`、`timeline.json` 及重启前后主应用/MCP 日志。票据明文仅在测试进程内用于重放与泄露扫描，不写入制品。新会话、双向凭据、两张实际票据逐项扫描。

首次完整运行因复用只接受新建 201 的提案辅助方法而在同来源重放 200 处失败；修正为明确核对 200 和原 Action。失败日志 `bootstrap/target/b06-worker-recovery.log`、XML 与进程日志 `bootstrap/target/b06-first-failure/` 保留，不能算通过。最终运行结果以 T052/T053、最终 Surefire XML 和 `docs/changelog.md` 为准。

最终运行目录为 `bootstrap/target/b06-evaluation/3fbfcb09-8099-4b0a-9bfc-f1f381bb9fc0/`。共享边界 15 项和 B06 真实恢复均通过；联合运行因旧正常 Worker 测试仍禁止 Spec D 已批准的 `desiredState` 白名单投影而退出 1，原失败 XML 保存在 `bootstrap/target/b06-regression-first-failure/`。修正为明确断言 `parametersAvailable=true`、唯一参数 `desiredState=PAUSED`，保留票据秘密拒绝断言；正常 Worker 单独重跑 1 项通过、退出 0。17 个不同测试最终均有通过记录，没有跳过；联合运行不改写为通过。精确联合/重跑命令与清单见 changelog 和 `manifest.json`。

## 7. B09 双通道故障补充复现入口

使用现有完整 Bootstrap Worker、独立 MCP 和临时数据库，生产执行协议不变。最小入口：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#receiptChannelsFailIndependentlyAndTogetherWithoutRedispatch' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

测试专用代理的三个子场景：

| 场景 | 实际故障 | 核对项 |
| --- | --- | --- |
| callback-only | 三次原回执提交均在代理返回 503，不到主应用 | Dispatcher 收到真实工具响应并接受原回执，SUCCEEDED，一次应用 |
| response-only | 回调先成功；代理读取真实 MCP 结果后宣布 Content-Length 并断开，不发送正文 | 主应用日志出现真实 EOF/接收 0 字节；SUCCEEDED，不重调工具 |
| both | 三次回调阻断，加工具响应 EOF；同时令只读对账 HTTP 暂时返回 503 | 真实绝对截止清扫为 UNKNOWN；保存快照，直接向主应用重投原 MCP 回执后 SUCCEEDED |

第三个场景的对账不可用是明确的额外故障条件：正常只读对账可依据真实应用记录迅速将 UNKNOWN 收敛为 SUCCEEDED，不能要求它持续停留 UNKNOWN。没有删除应用记录、改写 Action 状态或关闭生产对账守卫。配置通过既有属性冻结 `maxExecutionDurationMs=5000`、`receiptGracePeriodMs=2000`，截止自然到达。

每个子场景核对稳定 Execution/执行键/requestId/receiptId、一个真实应用记录、一张 CONSUMED 票、一份权威回执、一个回执 Timeline 事实和 Outbox 事件。重复原回执不增加事实；最后构造带合法格式失败证明码的反向回执作为不可信冲突负例，要求 409、只追加冲突审计、原成功不覆盖。这不是实际回滚证据。

成功制品位于 `bootstrap/target/b09-evaluation/<runId>/`：三个子场景的 before-late/action/receipt/timeline/outbox JSON，summary、冲突 Timeline、主应用/MCP 日志，以及归档 XML/运行清单。真实会话、服务凭据及三张票据逐项扫描，不保存票据明文。原失败日志 `b09-channels-first.log`、`b09-channels-second.log`、`b09-channels-final.log` 及三份 failure 目录保持历史失败；第三份的 final 名称不是通过声明。最终状态以 T054/T055 和 `b09-final-regression.log` 为准。B09 截止/回执并发竞争仍未在此入口覆盖。

2026-10-04 最终运行 `9963cf7f-016e-4cf1-90da-1375a452a740` 的三个子场景均通过，联合回归 12 项（Incident 7、Agent 3、Bootstrap 2）0 失败/错误/跳过，Maven 退出 0，4m56s。实际联合命令：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=ActionReceiptTest,ActionUnknownConvergenceTest,IncidentRemediationDispatcherTest,BootstrapIsolatedStartupTest#receiptChannelsFailIndependentlyAndTogetherWithoutRedispatch+workerCrashAfterTicketIssueRecoversSameExecutionAndAppliesOnce' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## 8. 回执/截止竞争与 B11 Relay 故障补充

2026-10-04 按既有协议执行 T056～T060，先运行 ActionReceiptTest 最小 5 项及 ActionOutboxCompatibilityTest 最小 2 项，再执行 [证据索引中的共享回归命令](evidence-index.md#本轮-g03g06-运行清单)。最终 38 项、0 失败/错误/跳过，Maven 退出 0，3m43s；没有重跑前端、浏览器、完整 Bootstrap 或全仓门禁。

回执和截止清扫的两种胜出次序均观察到真实 PostgreSQL Lock 等待。回执先提交不追加 UNKNOWN、不请求对账；清扫先提交只追加一份 UNKNOWN 并请求一次只读对账，随后迟到成功收敛。最终只有一个权威回执/回执事件，重复回执和再次清扫无新事实。本入口的回执、截止由隔离夹具构造，对账为计数替身，没有真实工具执行；与第 7 节自然截止和一次真实模拟应用的证据分开。

B11 从真实批准和回执事务产生的两类 Action 事件开始，由真实 Relay 依次遇到测试 Producer 发送失败、真实 PostgreSQL 触发器拒绝发布标记、恢复成功。每个事件尝试三次，事件 ID/envelope 保持且最终 PUBLISHED，原 Action/Approval/Receipt 不回滚。Producer 是替身；不是 Broker 送达或下游消费证明。

归档 `incident/target/safety-closeout/run-742defc2-0468-4e47-b99d-34cb1fe77d8a/` 保存 manifest、14 份选定 XML、三个场景的安全 summary/事件与发送尝试、三份日志及哈希。历史未统一格式的逐场景 manifest、更多攻击变体和跨主机验证仍为 TBD；不能由该目录宣称 LAB-000 全部完成。

## 9. 统一清单复现与当前范围收尾

2026-10-05 补充 T061～T064，七个完整应用入口和独立 MCP/对账证据均使用每次运行的独立目录，旧日志保留。最小命令依次执行：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ProtectedSimulationE2ETest,ActionUnknownConvergenceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

分别 5 项、1 项通过。首次 Docker 引擎未运行导致 5 个环境错误，保留 XML/日志，`docker desktop start` 后重跑通过，不用共享数据库替代。扩大回归与归档：

```powershell
$startedAt = Get-Date
$testClasses = 'ActionReceiptTest,ActionOutboxCompatibilityTest,OutboxRecoveryTest,ActionUnknownConvergenceTest,ActionRecoveryRaceTest,ActionApprovalRaceTest,ActionSecretLeakTest,ActionServiceAuthenticationTest,ActionBindingV1Test,ActionEventWriterTest,ActionStateMachineTest,ProtectedSimulationRouteTest,ProtectedSimulationE2ETest,IncidentRemediationDispatcherTest,BootstrapIsolatedStartupTest,AlertIdempotencyTest,IncidentAggregationTest,AlertRecoveryTest'
$actualCommand = ".\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=$testClasses' '-Dsurefire.failIfNoSpecifiedTests=false' test"
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am "-Dtest=$testClasses" '-Dsurefire.failIfNoSpecifiedTests=false' test > incident/target/catalogue-regression.log 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Maven 未通过，不生成成功清单' }
./specs/002-approval-safe-simulation/scripts/collect-evidence.ps1 -Since $startedAt -Log incident/target/catalogue-regression.log -Command $actualCommand
```

`$actualCommand` 只作为命令元数据保存，不作为脚本执行。本轮归档直接读取 `incident/target/catalogue-regression-start-20261005.json` 的 startedAt/command，原日志是 `catalogue-regression-20261005.log`。脚本拒绝失败日志、旧 XML、失败/跳过报告或不完整场景，不能用历史 XML 冒充当前执行。

本轮联合 54 项通过，0 失败/错误/跳过，退出 0，14m29s；归档 `incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/`。包含 B01～B12 统一清单、A 核心故障清单、18 XML、15 证明目录、实际命令/环境与哈希；具体快照及替身边界见 [证据索引](evidence-index.md)。当前目录/本地安全模拟门槛收尾，LAB-001 实验室及生产/跨主机/真实 Broker 仍未实施。无前端/生产协议/Schema 变化，没有重跑浏览器或全仓门禁。
