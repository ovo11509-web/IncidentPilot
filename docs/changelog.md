# 变更记录

本文记录 IncidentPilot 实际完成的重要变更；计划中的实现和未通过的验收不记为已完成。

## 2026-10-03

- B12 验收范围核对：重新对照已确认的 `spec.md` FR-016/SC-009、`tasks.md` TASK-010/048 与 `quickstart.md` B12。跨主机 TLS 是强制运行约束，已有非豁免地址明文拒绝和本地双向 TLS 联合证据；B12 场景本身要求会话恢复、权威状态查询与本地响应/日志/证据保密扫描，未规定实际跨主机部署或外部日志收集器为完成门槛。因此最新场景矩阵把 B12 标为“本地场景通过”，同时继续注明实际跨主机部署和外部日志未验证；未修改已确认的需求或接口，也未启动额外容器部署。
- B12 完整 Worker 双向 TLS 联合续补：`BootstrapIsolatedStartupTest#approvedActionDispatchesOverBidirectionalTls` 复用完整主应用、Action Worker、独立 MCP JVM、隔离 PostgreSQL/Redis/对象存储和原正向断言。在 `127.0.0.2` 上以一次性证书运行受保护 MCP HTTPS 与测试专用回调代理 HTTPS；两个子进程使用只含该证书的临时信任库，代理仅在本机 `127.0.0.1` 转发到真实主应用。批准后，Worker 经 TLS 调用 MCP，MCP 经 TLS 消费主库票据并提交回执；模拟目标只应用预期次数，主应用重启后新会话仍可查询结果，子进程日志额外扫描临时证书口令。首次用密钥库兼作 JVM 信任库时 MCP 的 Tomcat 因 `trustAnchors` 为空启动失败；改为独立证书信任库后定向用例通过。最终命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#approvedActionDispatchesOverBidirectionalTls+approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 项通过、0 失败、0 错误；临时证书与信任库文件已删除。实际跨主机部署、外部日志系统未验收。
- B12 双向本地 TLS 续补：新增 `IncidentCallbackTlsTest`，在非明文豁免回环别名 `127.0.0.2` 使用一次性证书和专用信任库。真实 `IncidentCallbackClient` 向 HTTPS 假主应用回调端各发送一次票据消费与回执，端点核对独立 Bearer 服务凭据；同地址改为 HTTP 时在发包前失败关闭。随后在隔离模拟 PostgreSQL 上启动真实受保护 MCP Server 的 HTTPS 入口，用与主应用相同的 MCP SDK 传输完成 TLS 握手和服务身份认证；无票写请求拒绝，模拟应用仍为 0。首次扩充后使用 Java 21 的 `List.getFirst()` 导致项目 Java 17 编译失败，改为 `get(0)` 后同一命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=IncidentCallbackTlsTest' test` 退出码 0，1 项通过、0 失败、0 错误；本次恢复后复跑同一命令仍为退出码 0，Surefire 1 项通过、0 失败、0 错误，临时证书文件已删除。假回调不执行主库授权事务，本地 MCP 客户端也未经过完整主应用 Worker；实际跨主机部署与外部日志仍未验收。
- B12 非豁免地址 TLS 续补：`ActionPersonnelNetworkDatabaseTest` 使用一次性 PKCS12 和临时主库，把精简人员 HTTP 应用绑定到本机隔离回环别名 `127.0.0.2`。服务端出站地址守卫拒绝同地址的明文 `http`，允许 `https`；测试客户端以专用信任库完成真实 TLS 握手，获授权提议返回 201，Execution 仍为 0。单项命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPersonnelNetworkDatabaseTest#nonExemptLoopbackAliasRequiresTlsAndServesIsolatedHttps' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0；随后将原 `127.0.0.1` HTTPS 用例与新用例合并运行，命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPersonnelNetworkDatabaseTest#servesPersonnelRequestOverIsolatedHttps+nonExemptLoopbackAliasRequiresTlsAndServesIsolatedHttps' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 项通过。该别名仍在本机，TLS 握手是人员入口；跨主机服务间 TLS、外部日志系统均未据此验收。
- B12 会话恢复续补：在完整 Bootstrap、Action Worker、独立 MCP 和隔离数据库的正向用例中，完成两次模拟应用及关闭竞争后终止主应用 JVM，在同一主库和隔离服务上重新启动完整主应用。新登录会话的人员 HTTP 查询仍读到 `SUCCEEDED` Action、`CONSUMED` 票据安全状态及 `ACTION_RECEIPT_ACCEPTED` Timeline；重启后模拟应用和回执仍各 2，未重调写工具。查询响应不含测试中实际票据明文或完整原始参数；重启前主应用、重启后主应用和 MCP 的本地日志均经本次会话与双向服务凭据扫描。定向命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，1 项通过。已确认的 `openapi.yaml` 将 `executionKey` 列为人员 `ExecutionView` 必需字段，当前查询服务照此返回；此前“HTTP 响应不含执行键”仅适用于已断言的提议/决定等响应，不能扩展到 Action 详情。外部日志系统和跨主机 TLS 部署仍未验收。
- B02 完整链路时限续补：`BootstrapIsolatedStartupTest` 新增两条独立场景，在完整主应用、启用的 Action Worker、独立 MCP JVM 与隔离 PostgreSQL 中暂缓真实票据消费。授权截止场景将 Action/Approval 的授权绝对截止置于过去，在线消费返回 409，恢复 Worker 将 Action 置为 `EXPIRED`；票据截止场景保持授权有效，仅将票据签发与过期时间一同前移以满足 `ck_ticket_time`，在线消费返回 409，随后关闭 Incident 以阻止合法补票继续处置。两条场景的票据消费次数与模拟应用次数均为 0，目标保持 READY。首次双场景命令中授权场景通过，票据场景因测试只前移过期时间而违反 `ck_ticket_time`，2 项中 1 错误；修正夹具后单独重跑票据场景退出码 0、1 项通过。最终整类命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，4 项通过、0 失败、0 错误。测试只操作 Testcontainers 和本机回环进程。
- B05 联合篡改与重放续补：完整 Bootstrap、启用 Action Worker 与独立 MCP JVM 的回调代理仅在测试内暂缓一张真实票据的消费请求。使用原始请求逐项改动 `actionId`、`incidentId`、`executionId`、`executionKey`、`requestId`、`bindingVersion`、`toolName`、`toolVersion`、`targetId`、`ticket`、`parameters` 后直达主应用在线消费入口，11 次均返回 409；原票仍为 `ISSUED`、Action 为 `DISPATCHING`，模拟应用次数不增加。此前成功消费的票据重放也返回 409，应用仍仅一次。随后关闭 Incident 并放行原请求，票据过期且不产生第三次应用。命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，Surefire 为 1 项通过、0 失败、0 错误；秘密只在测试进程内存中转发，未写入日志或文档。
- B02 时限续补：完整 Bootstrap、启用 Action Worker 与独立 MCP 的隔离正向测试增加第三个 Incident。将其待审批 Action 与 Approval 的审批截止写为已过期，等待 `ActionExpiryWorker` 自动把两者置为 `EXPIRED`；真实人员 HTTP 的迟到批准返回 409，Action 没有 Execution，模拟库没有新增应用。定向正向命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，1 项通过；随后整类命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 项通过、0 失败、0 错误。授权截止与票据截止的独立定向证据仍见既有 `ActionApprovalRaceTest`、`ActionDispatchTicketTest`、`ProtectedSimulationE2ETest`，未把它们冒充本轮完整主应用验证。
- B03 消费先胜续补：完整 Bootstrap/MCP 的测试回调代理先将第二个 Incident 的票据消费请求转发到主应用，确认 Action 已 `EXECUTING` 且票据 `CONSUMED`，仅暂缓把授权响应送回 MCP；人员 HTTP 随后关闭该 Incident，再放行授权响应。首次定向正向用例退出码 0，整类重跑退出码 0、2 项通过：该 Incident 已 `CLOSED`，Action 仍可凭可信回执收敛到 `SUCCEEDED`，模拟库共 2 次独立应用；随后另一个 Incident 的关闭先胜分支使第三张票据 `EXPIRED`，应用次数不增加。补充关闭状态及回执 Timeline/Outbox 各 2 的审计断言后再次执行同一定向正向命令，退出码 0，1 项通过；两进程与代理均只用本机回环和临时容器。
- B03 联合竞争续补：在启用 Action Worker 的完整 Bootstrap/MCP 双进程用例中加入只在本地测试进程运行的回调代理。第一次批准正常完成后，代理拦住第二个已派发 Action 的票据消费请求；人员 HTTP 先关闭 Incident，再放行请求。定向正向用例命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0；随后整类命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 项通过。第二个 Action 为 `EXPIRED`，第二张票据 `EXPIRED`、首张票据 `CONSUMED`，模拟应用仍为 1、回执仍为 1。完整主应用的消费先赢竞态及外部部署边界尚未因此验收。
- 续补完整双进程正常执行：`BootstrapIsolatedStartupTest` 新增启用 Action Worker 的独立用例。首次运行停在 `DISPATCHING`（2 项中 1 失败），查明 MCP 回调客户端仅接受无路径根 URL，而主应用 HTTP 契约使用 `/api/ragent`；修正客户端仅接受空路径或该已知前缀并拒绝查询串/片段，测试回调地址指向完整前缀。单独正向用例退出码 0；随后整类命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 项通过。隔离主库的 Action 为 `SUCCEEDED`、Execution/票据/回执各 1，票据 `CONSUMED`，回执 Timeline/Outbox 各 1；模拟库 `demo` 从 READY 变为 PAUSED、应用记录 1；同来源重放返回原 Action、改参数冲突，完成后关闭 Incident 不覆盖成功事实。B03 完整竞争目录仍需另行核对。
- 扩大验证执行 `.\mvnw.cmd '-Dspotless.apply.skip=true' test` 退出码 0，九模块 `BUILD SUCCESS`，耗时 10:48。Surefire XML 汇总：`framework` 29、`infra-ai` 28、`mcp-server` 63（跳过 1）、`incident` 102、`rag` 296（跳过 19）、`agent` 261（跳过 1）、`bootstrap` 2，合计 781 项、0 失败、0 错误、21 跳过；旧外部探针仍未运行。最后又将两种 Bootstrap 场景的子进程日志改为独立文件保存，重跑上述 Bootstrap 定向命令退出码 0，2 项通过；日志分别位于 `bootstrap/target/isolated-approved-*-process.log` 和 `bootstrap/target/isolated-fail-closed-*-process.log`。`git diff --check` 退出码 0。
- `spotless:check` 在 `mcp-server` 与 `bootstrap` 模块失败，差异主要为当前工作区多处既有混合 LF/CRLF；未运行会改写整个模块的 `spotless:apply`，也不把格式检查记为通过。
- 中断恢复时核对分支 `feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba`、全部现有修改与未跟踪文件、最近提交和上次 Surefire XML；无残留 Java 测试进程。上次双进程测试已启动完整 `RagentApplication` 与独立 MCP，真实登录成功，但首个提议因测试没有注册隔离目标 `demo` 返回 422，退出码 1。补上仅限测试进程的 `incident.action.allowed-simulation-targets=demo` 后，首次重跑在 Docker Engine 停止时于容器创建前受阻；启动本机 Docker Desktop，`docker info` 返回 Server 29.4.3 后继续，没有清理无关容器或数据库。
- 新增 `BootstrapIsolatedStartupTest`：Testcontainers 创建独立主库 pgvector、模拟库 PostgreSQL、Redis 和 RustFS，分别启动完整主应用与独立 MCP JVM；按各自模块过滤测试类路径，避免同名 Mapper 与配置互相装载。启动时发现并修复空 `incident.sources: {}` 在 Spring Boot 4 下无法绑定 Map，以及 `IncidentRemediationDispatcher` 多构造器未指定注入构造器。`isolated` 测试 Profile 关闭 RocketMQ 自动装配并注入调用即报错的测试发布器，Incident/Outbox/Action Worker 均关闭；这条测试不向 Broker 发送消息。
- 同一双进程测试经真实回环 HTTP、Sa-Token 会话和两套隔离 PostgreSQL 验证：未登录请求拒绝；拒绝后不能批准、审批到期不能批准；七个执行元数据伪造字段拒绝、同一来源改参数冲突；批准与 Incident 关闭竞争结束后 Action 为 `EXPIRED`，主库 Execution 与模拟库应用记录均为 0；HTTP 响应不含执行键或原始参数，两进程日志不含本次会话令牌、测试密码或服务凭据。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 最终退出码 0，1 项通过；此前失败轮次和 Docker 受阻轮次均未算作通过。
- 扩大验证执行 `.\mvnw.cmd '-Dspotless.apply.skip=true' test`，退出码 0；`bootstrap/target/specb-full-acceptance-20261003.log` 记录九模块 `BUILD SUCCESS`，耗时 09:41。有用例的模块依次为 `framework` 29、`infra-ai` 28、`mcp-server` 63（跳过 1）、`incident` 102、`rag` 296（跳过 19）、`agent` 261（跳过 1）、`bootstrap` 1，合计 780 项，失败和错误均为 0；`system` 无测试用例。跳过的旧外部联调探针仍未验收。此前的跨 JVM 正常批准执行及故障场景证据仍由 `ProtectedSimulationE2ETest` 和 `AlertToSimulationE2ETest` 提供；本次完整主应用用例覆盖人员入口的失败关闭，不包含启用 Action Worker 后的完整 Bootstrap→MCP 正常执行、跨主机 TLS、外部日志或真实基础设施演练。

## 2026-10-02

- 本轮扩大验证执行 `.\mvnw.cmd '-Dspotless.apply.skip=true' test`，捕获退出码 0；`rag/target/specb-postacceptance-full-test-20261002.log` 于 19:00:14 +08:00 记录九模块 `BUILD SUCCESS`，耗时 08:19。Surefire 汇总：`framework` 29、`infra-ai` 28、`mcp-server` 63（跳过 1）、`incident` 101、`rag` 296（跳过 19）、`agent` 261（跳过 1），失败与错误均 0；`system`、`bootstrap` 无测试用例。RAG 19 个跳过项仍是显式外部探针，未被此结果验证。
- B02/B03/B05 的主应用网络入口补测：`ActionPersonnelNetworkDatabaseTest` 在随机回环端口启动精简 Spring/Tomcat，使用真实 Sa-Token 会话、HTTP 客户端、Controller/服务和 Testcontainers PostgreSQL。无会话请求拒绝；拒绝及到期不能批准；七项伪造元数据、工具/版本/目标改绑均拒绝；相同来源重放复用 Action，改参数重放冲突；批准与关闭的并发请求无论先后，最终未启动 Execution。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPersonnelNetworkDatabaseTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 项通过。该测试使用精简装配，未启动完整 `bootstrap` 应用或独立 MCP 进程；后者的专用入口由既有跨 JVM 测试覆盖。
- B12 本地 HTTP 响应增加会话票据、原始参数和执行键不泄露断言。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPersonnelNetworkDatabaseTest,ActionSecretLeakTest,ActionServiceAuthenticationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0。随后 `ActionPersonnelNetworkDatabaseTest` 使用 `keytool` 在 `target` 生成并删除一次性 PKCS12，由回环 Tomcat 提供 HTTPS、测试客户端用专用信任库完成真实 TLS 握手及获授权提议；同一定向类重跑退出码 0，3 项通过。该证据不代表跨主机 TLS 部署或外部日志验收。
- TST-002 首类旧 RAG 外部探针转为固定 LLM 响应的默认测试：`MultiQuestionRewriteServiceTests` 断言多问句 JSON 解析及无效输出回退，移除该类的 `RAG_LIVE_TESTS` 跳过。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl rag -am '-Dtest=MultiQuestionRewriteServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 项通过。其余九类主要为模型、Milvus 或开发数据打印式探针，仍需逐类以有断言的隔离测试替换；扩大验证结果见本节首条。
- 额度中断后核对分支 `feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba`、工作区、最近提交和测试日志。中断的全仓日志 `rag/target/specb-full-test-green-20261001.log` 停在 Incident 测试，未留下命令退出码；没有残留 Maven/Java 进程或需要清理的测试容器。Docker Engine 曾停止，启动本机 Docker Desktop 后 `docker info` 返回 Server 29.4.3，未清理无关容器或数据库。
- 随后执行 `.\mvnw.cmd '-Dspotless.apply.skip=true' test`，捕获退出码 0；日志 `rag/target/specb-full-test-20261002.log` 于 17:49:49 +08:00 记录九模块 Reactor `BUILD SUCCESS`，总耗时 05:06。Surefire 汇总：`framework` 29、`infra-ai` 28、`mcp-server` 63（跳过 1）、`incident` 99、`rag` 295（跳过 20）、`agent` 261（跳过 1），全部 0 失败、0 错误；`system`、`bootstrap` 无测试用例。默认门禁中的旧 RAG 外部联调探针被环境门跳过，未使用付费模型或共享开发库，也不能宣称这些探针已通过。`git diff --check` 退出码 0。
- B02/B03/B05 新增的 `ActionPersonnelHttpDatabaseTest` 已随全仓测试通过，覆盖人员 HTTP 请求映射、隔离 PostgreSQL 的拒绝/过期、七项伪造元数据和改绑、关闭竞争；它使用 MockMvc，不是主应用跨进程网络 HTTP 验收。B12 的非本地 TLS 部署与外部日志仍未验证。Spec B `TASK-001`～`TASK-050` 代码和任务记录均已完成；场景矩阵的“部分”状态保持不变。

## 2026-10-01

- 本轮门禁续跑先把 10 个旧 RAG 外部联调类改为必须显式设置 `RAG_LIVE_TESTS=true` 才运行；这些类会碰模型、Milvus 或开发数据库，默认测试不再启动它们。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl rag -am test` 退出码 0；此结果包含跳过项，不表示旧联调已通过。第一次全仓 `.\mvnw.cmd '-Dspotless.apply.skip=true' test` 到 RAG 均通过，RAG 295 项中跳过 20；随后 Agent 261 项中 1 项因 OpenTelemetry 批次 span 实际 1 ms、旧断言恰好 0 ms 而失败，Bootstrap 未运行。改为断言小于 1 秒（等待模拟为 5 分钟）后，`-pl agent -am -Dtest=AgentTraceScenarioTest` 定向退出码 0。
- 主应用 MVC 与真实隔离 PostgreSQL 新增 `ActionPersonnelHttpDatabaseTest`：拒绝、审批截止、七个伪造元数据字段、工具/版本/目标/参数改绑、关闭先于审批及批准后未执行即关闭均核对 HTTP 状态与无 Execution。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPersonnelHttpDatabaseTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 项通过。该测试经过 Spring MVC 请求映射和真实服务，但没有启动跨主机主应用网络服务。
- 第二次全仓命令在 `ProtectedSimulationE2ETest` 遇到 100 ms 双计时器竞争：证据表锁住时 PostgreSQL 语句上限先触发，实际 `UNKNOWN/EXECUTION_UNPROVEN`，旧断言预期 `UNKNOWN/TIMEOUT_UNPROVEN`，其余 Incident 测试通过。`SimulationTimeoutEvaluator` 现在识别 PostgreSQL SQLState `57014`，写入停止后仍要求只读回滚证明才可报 `FAILED`，证据不可读则报 `UNKNOWN/TIMEOUT_UNPROVEN`。`SimulationTimeoutEvaluatorTest` 与跨 JVM `ProtectedSimulationE2ETest` 各自定向退出码 0；全仓再次重跑的结果另行记录，不以定向结果代替。
- 本次续跑使用 Windows 本机 Java 21.0.12、Maven Wrapper、Docker Desktop 29.4.3，以及 Testcontainers 创建的隔离 PostgreSQL 16/pgvector 16；本轮没有连接真实基础设施或付费服务。
- 验收续跑先执行 `.\mvnw.cmd '-Dspotless.apply.skip=true' test`，退出码 1：`framework` 29、`infra-ai` 28、`mcp-server` 61（跳过 1）、`incident` 96 个测试均无失败或错误；`rag` 298 个测试中 1 失败、21 错误、2 跳过，故 `agent` 和 `bootstrap` 未进入执行。RAG 第一组 Spring 上下文错误的根因为默认 `ragent.engine.type=agent` 时测试类路径缺少 `DashboardService` Bean；其余同上下文用例因失败阈值跳过。另一个失败是旧 `McpClientToolExecutorTest` 期待 DEBUG 日志出现手机号，与保密边界冲突。没有通过开启真实模型或外部服务来掩盖上下文故障。
- B12 日志收口：`McpClientToolExecutor` 的异常日志改为只记录异常类型、工具名和参数名，不写远端异常原文或堆栈；测试改为 INFO/DEBUG 和含手机号异常均不出现手机号。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl rag -am '-Dtest=McpClientToolExecutorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0。完整仓库测试未因这一项局部修复而冒称通过。
- B08 实际补测：隔离 PostgreSQL 上的 `SimulationToolExecutorTest` 新增证据表独占锁，证明证据查询在 500 ms 语句上限内结束，命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=SimulationToolExecutorTest' test` 退出码 0。`ProtectedSimulationE2ETest` 在独立 MCP JVM 分别锁住模拟目标行与应用证据表，前者得到 `FAILED/TIMEOUT_PROVEN_ROLLED_BACK`，后者得到 `UNKNOWN/TIMEOUT_UNPROVEN`，两者均没有第三次应用，目标保持 PAUSED；首次扩充 B02/B03/B05 场景的重跑因测试在终止子进程后仍调用它而退出码 1，调整注入顺序后同一定向命令退出码 0。补上 `TIMEOUT_UNPROVEN` 精确断言后的同一定向命令再次退出码 0。
- A+B 联动：`AlertToSimulationE2ETest` 在同一隔离主库接收两个同指纹告警并聚合一个 Incident，拒绝第一个 Action 后执行记录为 0，批准第二个 Action 后经隔离 MCP JVM 写 `demo` 一次；重放票据拒绝，回执 1 条，子进程退出后重建查询服务仍读到 `SUCCEEDED`，日志扫描未出现票据或双向服务凭据。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=AlertToSimulationE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0。Runbook 诊断未实现，故这不是含诊断步骤的完整黄金路径。
- 随后扩大运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am test` 退出码 0；最新 Surefire XML 汇总：`framework` 29、`mcp-server` 62（跳过 1）、`incident` 97 个测试，失败/错误均 0。此次命令不包含 `infra-ai`、`rag`、`agent`、`bootstrap`；`infra-ai` 的 28 个测试来自前述完整仓库命令。
- 完整命令在 RAG 停止后，另行执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=IncidentActionConfirmationAdapterTest,IncidentActionStatusAdapterTest,IncidentRemediationDispatcherTest,AgentToolCatalogTest,McpToolBridgeTest,AgentToolBodyTracerTest,AgentToolBodyTracerCoverageTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0；Agent 七类合计 44 个测试，失败/错误/跳过均 0。它覆盖直接相关桥接和会话状态，不能代替未完成的全仓测试。
- 执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am -DskipTests compile`，退出码 0，验证本轮 MCP、Incident、RAG 日志与 Agent 变更仍能装配编译；该命令不证明启动环境或全仓测试通过。
- Docker Desktop 恢复后，`docker info --format 'Server={{.ServerVersion}} OS={{.OperatingSystem}}'` 返回 `Server=29.4.3 OS=Docker Desktop`，`docker-desktop` WSL 实例运行。重跑 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ProtectedSimulationE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0；Testcontainers 连接本机 Npipe、启动两套隔离 PostgreSQL 16，Surefire XML 为 1 测试、0 失败、0 错误、0 跳过（45.45 秒）。测试经独立 MCP JVM 注入双路径回执丢失、只读对账故障、消费后进程崩溃和关闭后回执；新增的 100 ms 执行上限在持有隔离目标行锁时触发超时，Action 落在 `FAILED` 或 `UNKNOWN` 的安全分类内，模拟应用总数仍为 2、目标保持 PAUSED。子进程日志中的连接重置由主动终止进程引起；票据和双向服务凭据扫描未命中。此前 Docker 受阻的 TASK-041 已关闭，故障注入用例集中在 `ProtectedSimulationE2ETest`；两种超时结论的后续补测见本节上方记录。
- TASK-050：更新 `quickstart.md` 的真实定向命令、隔离环境、受阻与恢复证据；下方 B01～B12 矩阵继续区分通过与部分覆盖。未运行仓库完整测试、非本地 TLS 部署、真实基础设施和本轮 RocketMQ Broker 故障重演；这些均不写成通过。
- 再次额度中断恢复：当前工作区仍为 `feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba`，原有改动和未跟踪文件均保留；没有残留 Java/Maven 进程或半途运行的命令。中断前最后执行的 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionStateMachineTest,ProtectedSimulationE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 未留下命令退出码，但恢复时两份 Surefire XML 分别记录 1 测试、0 失败、0 错误，写入时间为 2026-09-29 19:43 与 19:44；跨 JVM 日志没有出现测试用服务凭据或票据明文。随后新增场景的受阻重跑曾覆盖 E2E XML，当时该 XML 代表 Docker 启动失败；最新 XML 已由上述成功重跑覆盖。历史绿色结果以本条当时核对记录为准；未把它冒充已捕获的进程退出码。
- TASK-048/049 收尾：新增查询响应与子进程日志的敏感标记扫描、跨主机明文 HTTP 失败关闭断言；在 Action/Approval Mapper 的条件更新里加入已确认合法状态边白名单，避免调用方误用时终态重入或 `UNKNOWN→EXECUTING`。首个 `ActionStateMachineTest` 在非法 `APPROVED→SUCCEEDED` 上如预期失败；修正后运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionStateMachineTest,ActionApprovalRaceTest,ActionReceiptTest,ActionUnknownConvergenceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0。随后补充 Ticket 重消费/过期断言，最后一次定向 Surefire XML 仍为 0 失败。
- 扩大验证第一次运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am test` 失败于 MCP 旧 `BitSchemaScriptTest`：它以固定 LF 匹配建表语句，Windows 工作区旧表行尾为 CRLF，旧表并未缺失。将测试输入归一化换行后运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=BitSchemaScriptTest' test`，退出码 0；同一 Incident 聚合命令重新运行退出码 0。聚合运行后的 Surefire XML：`framework` 7 类/29 测试，`mcp-server` 15 类/61 测试（跳过 1），`incident` 47 类/95 测试，失败与错误均 0。既有测试中的预期异常日志不表示命令失败。
- 本次恢复运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am -DskipTests compile`，退出码 0；补充 Action 详情/Incident 列表 MockMvc 权限、跨 Incident 隐藏与安全状态断言后，运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionQueryContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0，2 个测试通过。`git diff --check` 退出码 0；Git 仅提示当前 Windows 工作区将来可能转换行尾。
- B01～B12 的最新定向覆盖状态见下方矩阵。“部分”项 B02/B03/B05/B12 仍未按完整场景目录逐项演练；本地 RocketMQ Broker 中断没有在本次续跑重演，非本地 TLS 部署和真实基础设施均未验证。完整仓库测试已运行但在 RAG 模块失败；`TASK-050` 只记录真实通过范围，不把 Spec B 全场景写成已验收。
- 后续补充 TASK-041 的跨 JVM 超时注入：在独立 MCP JVM 调用前持有隔离模拟目标行锁，用主库冻结的 100 ms 执行上限触发超时，断言 `FAILED` 或 `UNKNOWN` 均无新增应用。首次运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ProtectedSimulationE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，在创建测试 PostgreSQL 前因 Docker daemon 不可用退出码 1，业务断言未运行；`docker info` 无法连接 Linux Engine，启动 Docker Desktop 后仍未恢复，尝试启动 `com.docker.service` 被 Windows 权限拒绝。运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am -DskipTests test-compile` 退出码 0，仅证明新测试代码可编译。未清理现有容器或数据库；后续成功重跑见本节首条。

## 2026-09-29

### Spec B Phase 5～6 续跑验证（隔离环境）

- 断点恢复时分支仍为 `feature/incident-platform`、HEAD 仍为 `67624931f20812922568d3641a095adb291263ba`；工作区原有修改与未跟踪文件均保留，未发现残留 Java/Maven 进程。`ProtectedSimulationE2ETest` 的 Surefire XML 为 1 测试、0 失败、0 错误，耗时约 40 秒。
- TASK-033～040 及 TASK-041 的既有故障路径：`ActionRecoveryRaceTest`（2 个隔离 PostgreSQL 测试）、`ActionUnknownConvergenceTest`（4 个）、`SimulationTimeoutEvaluatorTest`、`SimulationReconciliationReaderTest`、`ProtectedSimulationRouteTest` 与 `ProtectedSimulationE2ETest` 的定向命令均退出码 0。跨进程测试通过批准→票据消费→一次模拟应用→回执，随后注入两条回执路径失效、只读对账和消费提交后杀死 MCP JVM；应用总数保持预期 2，后者到期为 `UNKNOWN`。故障注入断言并入 `ProtectedSimulationE2ETest`，没有另建任务建议名的测试文件。MCP 终止时的连接重置日志属于注入结果，不作为可靠性指标。用户确认票据消费响应增加非秘密 `maxExecutionDurationMs`，已同步协议和 DEC-022；缩短配置的专用路由测试退出码 0。TASK-041 新增跨 JVM 超时分支的状态见 2026-10-01 记录。
- TASK-042/044：运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionQueryContractTest,ActionApprovalContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0；随后扩充五类终态/未知原因并与 `ActionOutboxCompatibilityTest` 合并重跑，退出码 0。查询只读取权威 Action/Approval/Execution/Ticket/Receipt/Timeline，票据摘要和原始参数不在视图内；查询仍以服务端 `admin` 角色限制。
- TASK-043：`ActionOutboxCompatibilityTest` 首次因夹具未标记 `execution_started_at` 被正确拒绝为未绑定回执；补足夹具后运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionOutboxCompatibilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0。已验证审批和权威回执的 Timeline/Outbox 共用稳定身份、重复回执不新增权威结果、发布重试复用原负载。Spec A 旧事件的本地发布恢复证据仍由既有 `OutboxRecoveryTest` 提供。
- TASK-045：运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=IncidentActionStatusAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0；会话恢复仅查 Action 行，批准后等待 Worker，UNKNOWN 指向对账。
- TASK-046：运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionAuditEvidenceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0；隔离 PostgreSQL 上核查审批事实的 Incident 内顺序、稳定业务键、Outbox 同身份、禁止更新 Timeline 及白名单负载。

下表按 `quickstart.md` 场景记录截至 2026-10-03 的累计代码测试范围。最新本地环境为 JDK 21（项目支持基线为 17）、Maven Wrapper、Testcontainers PostgreSQL 16、回环 HTTP 和独立 MCP JVM；模拟目标 `demo` 初始为 READY，常规时限为审批 15 分钟、票据 2 分钟、执行 2 分钟、回执宽限 3 分钟。标为“部分”的场景不得视为完整验收。脱敏证据位于各模块 `target/surefire-reports/TEST-*.xml`；跨进程日志位于 `incident/target/protected-simulation-process.log` 与 `bootstrap/target/isolated-*-process.log`，不提交票据明文。

| 场景 | 实际测试入口 | 状态及关键结果 |
| --- | --- | --- |
| B01 正常批准执行 | `ProtectedSimulationE2ETest`, `BootstrapIsolatedStartupTest` | 通过：完整 Bootstrap 启用 Action Worker，经独立 MCP 使 READY→PAUSED，应用 1 次，Action SUCCEEDED，消费票据与回执各 1；完整告警入口至诊断不在本场景内 |
| B02 拒绝与过期 | `ActionApprovalRaceTest`, `ActionExpiryWorkerTest`, `ActionPersonnelHttpDatabaseTest`, `ActionPersonnelNetworkDatabaseTest`, `BootstrapIsolatedStartupTest`, `ProtectedSimulationE2ETest` | 本地通过：完整 Bootstrap 与独立 MCP 同时运行时，人员 HTTP 拒绝、审批自动过期、授权截止及旧票据截止均被服务端拒绝，迟到批准返回 409；授权截止使未启动的 Action 过期，旧票据到期在 Incident 关闭前的在线消费返回 409；这些失败路径无新增模拟应用。票据自然到期后的合法补票语义另由 `ActionRecoveryRaceTest` 覆盖 |
| B03 关闭竞争 | `ActionApprovalRaceTest`, `ActionPersonnelHttpDatabaseTest`, `ActionPersonnelNetworkDatabaseTest`, `BootstrapIsolatedStartupTest`, `ProtectedSimulationE2ETest` | 本地通过：完整 Bootstrap 下批准/关闭并发使未派发 Action 过期；启用 Worker 后，关闭先于消费使票据过期且副作用为 0，消费先于关闭则已消费执行凭可信回执在 `CLOSED` 后收敛，独立模拟应用仅一次；均由隔离数据库与独立 MCP JVM 断言 |
| B04 入口旁路 | `ProtectedSimulationRouteTest`, `ProtectedSimulationE2ETest` | 通过本地已知名称、未知注册、匿名/无票/伪票，副作用 0；未验证非本地部署 |
| B05 绑定篡改与重放 | `ActionBindingV1Test`, `ActionPersonnelHttpDatabaseTest`, `ActionPersonnelNetworkDatabaseTest`, `BootstrapIsolatedStartupTest`, `ProtectedSimulationE2ETest` | 本地通过：完整 Bootstrap 与独立 MCP 同时运行时，七个伪造提议元数据字段拒绝；未消费的真实票据在主应用在线消费入口逐项改动 11 个绑定字段均返回 409，原票仍可证明为 `ISSUED`、模拟应用无新增；成功票据重放返回 409、没有第二次应用；同源提议重放返回原 Action，改参数冲突。独立 MCP 入口篡改仍由 `ProtectedSimulationE2ETest` 覆盖 |
| B06 补票竞争 | `ActionRecoveryRaceTest` | 通过隔离主库替代票据、旧票迟到、已消费拒补；未另起 MCP JVM 注入此竞争 |
| B07 消费后故障 | `ProtectedSimulationE2ETest` | 通过跨进程杀死已消费 MCP、无第二次副作用；到期 UNKNOWN |
| B08 超时证明 | `SimulationTimeoutEvaluatorTest`, `SimulationToolExecutorTest`, `ProtectedSimulationRouteTest`, `ProtectedSimulationE2ETest` | 通过本地隔离两种跨进程结论：已回滚为 `FAILED/TIMEOUT_PROVEN_ROLLED_BACK`，证据查询受阻为 `UNKNOWN/TIMEOUT_UNPROVEN`，副作用次数均不增加 |
| B09 双路径与迟到 | `ProtectedSimulationE2ETest`, `ActionReceiptTest` | 通过双路径丢失→UNKNOWN、迟到可信回执收敛、相反终态隔离 |
| B10 对账与人工结论 | `ActionUnknownConvergenceTest`, `ProtectedSimulationE2ETest` | 通过只读已应用/证实未应用/无法判断、人工权限和版本冲突的定向断言 |
| B11 Outbox 恢复 | `ActionOutboxCompatibilityTest`, `OutboxRecoveryTest` | 通过模拟发布失败/重投稳定身份；本次未重跑旧 RocketMQ Broker 中断实验 |
| B12 保密与会话恢复 | `ActionQueryContractTest`, `ActionPersonnelNetworkDatabaseTest`, `BootstrapIsolatedStartupTest`, `IncidentActionStatusAdapterTest`, `ActionAuditEvidenceTest`, `ActionSecretLeakTest`, `IncidentCallbackTlsTest`, `ProtectedSimulationE2ETest`, `AlertToSimulationE2ETest`, `McpClientToolExecutorTest` | 本地场景通过：完整主应用重启后新会话仍可读取 Action、票据安全状态和 Timeline，模拟应用次数不增加；本地响应与进程日志不含本次会话/服务凭据、票据明文或完整原始参数；提议/决定响应不回显执行键，Action 详情按已确认合同返回该字段。本机隔离回环别名 `127.0.0.2` 的人员 HTTPS、MCP→假主应用回调 TLS、MCP SDK→真实受保护 MCP HTTPS 均通过，无票写请求仍拒绝；完整主应用 Worker→MCP→测试专用回调代理双向 TLS 联合链路通过，代理到主应用为本机回环转发。实际跨主机部署和外部日志未验证，也不是已确认 B12 场景门槛 |


- 中断恢复：核对分支 `feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba`、工作区修改和未跟踪文件，未发现残留 Maven/Java 进程；`ProtectedSimulationE2ETest` 的 Surefire 报告为 1 测试、0 失败、0 错误。保留全部已有文件和改动，没有切换工作树。
- Spec B TASK-022：启动 Docker Desktop 后运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=ProtectedSimulationRouteTest' test`，退出码 0，5 个用例通过。实际本地 HTTP 下只读 `current_date` 可调用，十个旧写名称和未知名称直调拒绝；专用入口匿名、无票、伪造票均不能改变隔离目标，应用次数为 0。最初尝试时 Docker daemon 尚未启动，容器测试在业务断言前失败；启动后重跑通过。
- Spec B TASK-031 修正票据摘要按 32 字节原始 secret 计算，而非对十六进制文本计算；运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=IncidentRemediationDispatcherTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0。
- Spec B TASK-032：`ProtectedSimulationE2ETest` 用独立主库和模拟库、Incident 测试 JVM 与另一个实际 MCP Server JVM 验证旧 `/mcp` 入口不能直调写工具，专用入口匿名请求 401、绑定篡改不消费票据、合法请求单次改变 READY→PAUSED、回调回执落库、响应路径同回执幂等和旧票重放不二次应用。运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ProtectedSimulationE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0，1 个跨进程用例通过；子进程日志在 `incident/target/protected-simulation-process.log`。子进程终止时 Reactor 曾报告连接重置的异步日志，断言与测试进程均成功；后续全链路故障验证不把该日志当作可靠性结论。

## 2026-09-28

- Spec B TASK-001：核对 Spec A `TASK-001`～`TASK-050` 均标为完成，当前工作区存在 `incident` 模块、五张主库表、升级脚本、状态机、Timeline 和 Outbox。重新执行 `.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=PostgresSmokeTest,IncidentSchemaTest,IncidentTransitionTest,OutboxRecoveryTest,IncidentEventContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，Incident 模块 8 个聚焦测试通过，失败/错误/跳过均为 0；其中 Schema 测试使用隔离 PostgreSQL/pgvector 16 验证全量及升级路径。此前 56 测试与跨 JVM/Broker 中断记录见下方历史证据，本次没有重演 Broker 故障。首次 PowerShell 命令未引用带逗号的 `-Dtest` 参数，发生解析错误，修正引号后通过。
- Spec B TASK-002：复用 `incident/pom.xml` 已有的 Testcontainers PostgreSQL 测试依赖，新增 `ActionPostgresSmokeTest`。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPostgresSmokeTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0；测试两次新建隔离 PostgreSQL 16、应用 Spec A 迁移、核对五张基线表、写入独立夹具表并关闭容器。Spec B 主库迁移尚未创建，故本项只证明其隔离测试基线可用，完整新迁移留给 TASK-005/006 验证。
- Spec B TASK-003：MCP 测试模块加入 Testcontainers PostgreSQL 依赖，`SimulationTestFixture` 只连接自身新建的隔离容器，预置 `SIM_SERVICE` 演示目标的 `READY` 状态和零应用记录，并提供状态与应用次数读取。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=SimulationTestFixtureTest' test` 退出码 0，1 个冒烟断言通过。此夹具目前以测试专用 SQL 建表；正式模拟库 Schema 在 TASK-028 实现后仍需核对。
- Spec B TASK-004：新增 `ActionProperties` 和主应用中的非秘密默认配置：审批 15 分钟、授权 5 分钟、票据 2 分钟、最长执行 2 分钟、回执宽限 3 分钟、消费前最多重试 3 次；配置校验拒绝非正时限、票据超过授权窗口和过多重试。处置 Worker 默认关闭，双向服务凭据未写入配置或源码。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionPropertiesTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，2 个测试通过。
- Spec B TASK-005/006：全量 Schema 与追加迁移新增 Action、Approval、Execution、Ticket、Receipt 五表、来源和执行键唯一约束、单张 `ISSUED` 票据部分唯一索引、状态/CHECK/外键，扩展共享 Timeline/Outbox 的受限类型，普通应用角色对 Receipt 与 Timeline 只有只读/插入权限。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionSchemaTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0；2 个隔离 PostgreSQL/pgvector 用例分别覆盖全量和升级路径，实际插入旧/新 Timeline 与 Outbox 类型并验证唯一、外键、状态、只追加权限。增强 FK 断言后同一命令重跑退出码 0。
- Spec B TASK-007：新增五个受限 MyBatis Mapper，并在主应用 Mapper 扫描中注册；Action/Approval 更新需匹配预期状态和版本，Ticket 只允许从 `ISSUED` 消费或过期，Receipt Mapper 没有更新或删除方法。扩展 `ActionSchemaTest` 后执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionSchemaTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0；2 个隔离 PostgreSQL 用例通过，实际执行 Mapper 读取、锁定和条件更新。
- Spec B TASK-008：加入 `ActionBindingV1` 的严格模拟参数解析、提议摘要与长度前缀执行绑定；提议摘要不含 Action ID 或执行键，完整绑定包含二者，未知字段、重复 JSON 键、非法工具/状态失败关闭。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionBindingV1Test' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，3 个测试通过。
- Spec B TASK-009：`ActionTransactionGuard` 在单个本地事务中按 Spec A 既有的同键 advisory lock → Incident 行锁顺序进入 Action 决策，锁后读取 PostgreSQL `clock_timestamp()`，用预期 Incident 版本与序号条件预留 Timeline 序号；网络调用须置于该事务回调外。增强 `ActionSchemaTest` 后执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionSchemaTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：首次因测试中 `assertEquals` 重载歧义编译失败，修正类型后重跑退出码 0，2 个隔离 PostgreSQL 用例通过。
- Spec B TASK-010：主应用回调身份只接受 `INCIDENT_MCP_CALLBACK_TOKEN` 当前/上一值，MCP 执行端只接受独立的 `INCIDENT_MCP_OUTBOUND_TOKEN` 当前/上一值；缺失或短凭据失败关闭，非回环 HTTP 地址被拒绝，敏感头名有排除判定。分别执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionServiceAuthenticationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 与 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=ProtectedServiceAuthenticationTest' test`，退出码均为 0，各 2 个测试通过。HTTP 入口接线与端到端强制仍属于后续任务，当前测试不证明旁路已封闭。
- Spec B TASK-011：`ActionTimelineWriter` 复用 Spec A 同一只追加 Timeline，提议、审批决定和权威回执使用稳定独立键；`ActionOutboxWriter` 只为审批决定和首次生效回执写两类新事件，Event ID 与对应 Timeline 事实相同，负载为白名单安全字段。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionEventWriterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0；补充结果码白名单后同命令重跑仍为 0。其他 Action 事实类型将在对应业务任务接入时扩展，尚未声明审批链路已可用。
- Phase 2 扩大验证：执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am test` 退出码 0，Incident Surefire XML 合计 32 类、67 测试、失败/错误/跳过均 0，含 Spec A 旧链路回归。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am -DskipTests compile` 退出码 0，主应用装配层编译通过。Phase 2 只验证基础持久化、绑定、安全配置和事件边界；HTTP 审批与 MCP 执行尚未实现。

- Spec B Phase 3 进展：先建 `ActionApprovalContractTest`、`ActionApprovalRaceTest` 失败断言；初次定向运行因待实现的服务/控制器类缺失而测试编译失败。TASK-014 新增服务端会话提议、显式模拟目标白名单、规范绑定、来源幂等及同事务 Action/Approval/Timeline；`ActionProposalTest` 在隔离 PostgreSQL 上退出码 0，验证重复不新建、改绑定冲突和未注册目标拒绝。TASK-015 在锁后复算完整绑定、使用数据库时钟并原子写批准/拒绝、Timeline 和 Outbox；`ActionApprovalRaceTest#concurrentApprovalAndRejectionPersistOneDecision` 退出码 0。TASK-016 提议/详情/决定控制器使用人员会话、严格 JSON 和安全摘要；`ActionApprovalContractTest` 与 `ActionExpiryWorkerTest` 合并定向命令退出码 0。当前契约测试为 Spring MockMvc HTTP 状态验证，尚未单独运行网络套接字验收。
- TASK-017 先加入只含 `actionId` 与权威状态的卡片模型，后改为直接查询主库 Action Mapper；`McpToolBridge` 对旧写和未知名称在执行器之前拒绝，普通 Agent 目录也不注册 `simulation.set-state`。`IncidentActionConfirmationAdapterTest,McpToolBridgeTest` 定向命令退出码 0。现有聊天会话仍可能保留旧卡片展示，但其写调用被桥拒绝；MCP 服务端各入口的停用和旁路测试属于 TASK-022/027，未在本阶段宣称完成。
- TASK-018 在实际 Spec A 类 `IncidentTransitionService`（任务路径中的 `IncidentLifecycleService` 已过时）的关闭事务中调用 `ActionClosureInvalidator`，使未启动 Action、待审批 Approval、已签发 Ticket 失效并追加 Timeline，已消费执行不在扫描范围。TASK-019 新增默认关闭的到期 Worker，只清扫 PENDING_APPROVAL/APPROVED。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionApprovalContractTest,ActionApprovalRaceTest,ActionProposalTest,ActionExpiryWorkerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0；后增加批准后派发票据关闭及锁等待越过审批截止的断言，`ActionApprovalRaceTest` 最终定向命令退出码 0。一次新增测试因夹具先算摘要后赋执行键导致错误，调整夹具后通过。尚未调用 MCP 工具或写模拟目标；完整 Phase 3 和 B01～B12 未验收。
- Phase 3 最终定向重跑同一 Incident 命令退出码 0：`ActionApprovalContractTest` 2、`ActionApprovalRaceTest` 4、`ActionProposalTest` 1、`ActionExpiryWorkerTest` 1，合计 8 个测试，失败/错误/跳过均为 0。Agent 卡片改为直接从主库 Action Mapper 读取状态，普通聊天 MCP 桥使用显式只读名称允许清单，旧写与未知名称在执行器之前拒绝；执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=AgentToolCatalogTest,AgentToolBodyTracerCoverageTest,McpToolBridgeTest,IncidentActionConfirmationAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0。先前运行 `AgentToolCatalogTest,AgentToolBodyTracerCoverageTest` 发现两条旧断言与现有“未声明只读即确认”的代码行为不符，另两条追踪断言仍假定旧写工具可执行；调整断言以对应当前实现和 Spec B 禁用旧写后重跑通过。Agent 全模块尚未运行。
- Phase 3 扩大验证：`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am test` 退出码 0；Incident Surefire XML 为 36 类、75 个测试、失败/错误/跳过均 0。`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am -DskipTests compile` 与 `git diff --check` 均退出码 0。此阶段 Action 事务测试没有连接 MCP Server，故只能说明没有执行调用；模拟目标应用次数及全部入口旁路验证留在 Phase 4，不能把本阶段当成端到端零目标变化验收。

- Spec B Phase 4 进展：TASK-021/022/023 先建派发票据、普通 MCP 注册和回执集成的失败断言；分别观察到缺少 `ActionDispatchWorker`、`McpServerConfig.ordinaryTools` 与 `ActionReceiptService` 时的预期失败，回执测试保留为可编译的反射式红灯，不能据此声称回执已实现。TASK-024/025 已在 Incident 锁内完成单一 Execution、随机 256 位票据仅摘要落库、批准/截止/绑定复核、一次性消费与 `EXECUTING`/回执截止原子转换；`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionDispatchTicketTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 最终退出码 0，2 个隔离 PostgreSQL 测试通过。首次消费测试因 PostgreSQL `jsonb::text` 读回空格与规范 JSON 字符串不同而拒绝合法请求，改为规范化数据库参数再比较，重跑通过。
- TASK-026 已接上仅服务凭据可用的票据消费 HTTP 入口与 Sa-Token 精确本地鉴权放行；`ActionInternalContractTest` 定向命令退出码 0，验证身份优先于 JSON 解析、匿名 401、坏 JSON 400 与响应不回显票据。回执提交入口尚未接入，因此 TASK-026 保持未完成。TASK-027 普通 `/mcp` 入口现按经检查的只读名称清单过滤，写工具、受保护工具与未知自报只读工具均不进入该注册表；`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=ProtectedSimulationRouteTest,McpServerConfigTest' test` 退出码 0。专用 `/mcp/incident` 尚未建成，全部入口直调与跨进程证明未完成，TASK-022/027 仍未勾选。
- 用户确认 TASK-028 模拟库遵循 Spec B 数据模型。`bit-schema.sql` 中旧商城表的 BIGINT/无 CHECK 约定已限定作用域，新增 `simulation_target.target_id` 与 `simulation_application.execution_key` 主键、READY/PAUSED 数据库 CHECK、版本和前后状态记录；Schema 版本从 7 升至 8。`SimulationToolExecutor` 仅对该隔离库内已登记目标在同一事务中更新状态并写执行键记录，重复键返回既有结果；`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=SimulationToolExecutorTest,SimulationTestFixtureTest' test` 退出码 0，随后增加数据库 CHECK 断言，单跑 `SimulationToolExecutorTest` 退出码 0。执行端对外受保护入口尚未注册，以上只是隔离存储层证据。

- TASK-023/026 回执集成断言和内部回调入口已接入：`ActionReceiptTest` 以隔离 PostgreSQL 验证版本拒绝、已认证迟到回执、双路径同一回执幂等、同 ID 异内容及相反确定终态不覆盖；`ActionInternalContractTest` 验证两条内部入口均先认证再解析请求，回执响应仅含安全状态。`ActionReceiptService` 现按 v1 校验工具、目标、执行键、请求 ID、绑定摘要、摘要字段与失败证明码，并追加回执、Timeline、适用 Outbox。首次测试因 Docker Desktop 停止而无法启动 Testcontainers，`docker info` 证实 daemon 不可用；启动本机 Docker Desktop 后，`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionInternalContractTest,ActionReceiptTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0；扩充回执测试后单跑 `ActionReceiptTest` 退出码 0（2 个隔离 PostgreSQL 用例）。TASK-030 仍未勾选：执行前拒绝及可核查失败证明尚需与 MCP 执行端贯通。

- Phase 4 TASK-027/029/030/031 进展：`ProtectedSimulationMcpConfig` 为 `/mcp/incident` 建独立 MCP Server、唯一工具注册与服务凭据过滤器；`ProtectedSimulationTool` 严格解析参数与 `_meta`，在线票据消费非 `GRANTED` 时不执行模拟 SQL，成功后先有限次回调再返回同一版本化回执。`IncidentCallbackClient` 使用单独回调凭据并限制回环 HTTP/跨主机 TLS。`ActionReceiptService` 接受已认证回调或进程内 Worker 第二路径，校验 v1、完整绑定、安全摘要及失败/拒绝证明码；冲突回执只追加核查事实，不覆盖确定终态。`IncidentRemediationDispatcher` 只连专用 MCP URL，响应回执经进程内服务再次提交；仅主库证明同一票仍 `ISSUED` 时重试，消费或状态不明不重调。定向执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=ProtectedSimulationRouteTest' test`、`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionReceiptTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`、`.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=IncidentRemediationDispatcherTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 均退出码 0；分别为 4、3、3 个用例。`-pl agent -am -DskipTests compile` 退出码 0。上述仍未验证双进程实际 MCP HTTP 和完整入口旁路，TASK-022/032 保持未完成；失败证明的跨进程核查属 Phase 5。

- TASK-022 HTTP 核查进展：在 `ProtectedSimulationRouteTest` 中用隔离 Testcontainers 模拟库实际启动本地 MCP Server，逐个直调 10 个已知旧写工具，核对普通入口均拒绝且发现清单只有经核查的只读项；匿名专用入口拒绝，认证专用入口仅发现 `simulation.set-state`，无票调用不改变目标或应用次数。首次启动测试因 `SpringApplicationBuilder.properties` 优先级低于 `application.yml` 而误指默认本机数据库，连接被拒绝且未触碰该库；改用测试进程命令行覆盖隔离容器地址。随后真实 HTTP 测试发现 MCP SDK `builder(url)` 会默认发往 `/mcp`，并不会使用 URL 中的 `/mcp/incident`；同时服务端 transport 默认只接受 `/mcp`。已将客户端显式设 `.endpoint("/mcp/incident")`、服务端设 `.mcpEndpoint("/mcp/incident")`。重跑 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl mcp-server '-Dtest=ProtectedSimulationRouteTest' test` 退出码 0，5 个用例通过；认证成功得到 `incident-simulation` Server 信息。完整绑定篡改与重放仍需 TASK-032 双进程验证，TASK-022 暂不勾选。

## 2026-09-23

- 开始 Spec A 代码实施。TASK-001 已完成：注册 `incident` Maven 模块并接入 `bootstrap`；执行 `.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident,bootstrap -am -DskipTests compile`，reactor 八个模块均 `SUCCESS`。首次未给 PowerShell 中的 `-Dspotless.apply.skip=true` 加引号导致 Maven 参数解析失败，修正命令后通过。
- TASK-002 已加入隔离 PostgreSQL 冒烟测试，但未完成运行验证。执行 `.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=PostgresSmokeTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：测试编译通过，1 个测试因 `Could not find a valid Docker environment` 报错；`docker info --format '{{.ServerVersion}}'` 同样无法连接 Docker Desktop Linux Engine。未使用共享或远程数据库替代。
- 启动本机 Docker Desktop 后，重跑 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=PostgresSmokeTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 通过；同一测试连续启动两次隔离 PostgreSQL 16 容器，完成连接、建表读写及关闭断言。TASK-002 已完成。
- TASK-003 实施前发现权限边界冲突：当前默认 datasource 用户为 `postgres`，Schema/迁移没有独立应用角色；Spec A 要求 Timeline 与 Alert 审计对普通应用角色只追加，不能以对超级用户的 `REVOKE` 证明。已提请确认运行角色方案，迁移和任务状态暂未修改。
- 用户确认引入独立非 owner 应用角色。TASK-003/004 已完成：全量 Schema 与新增升级脚本加入五张 Incident 表、约束、索引和 `ragent_app` 权限；应用 datasource 可通过 `DB_USERNAME`/`DB_PASSWORD` 指定该角色，角色密码须在运行环境单独设置。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentSchemaTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，2 个隔离 PostgreSQL/pgvector 用例通过，分别覆盖全量初始化与旧 Schema 升级，并验证唯一/CHECK/外键及普通角色不可更新或删除两张只追加表。
- TASK-005 已完成：加入无秘密默认配置、来源 key 环境变量引用、窗口/重试计划和 Worker/Outbox 开关。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentPropertiesTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，1 个配置绑定测试通过。
- TASK-006 已完成：Alert Mapper 提供限定的接收插入、身份/ID 查询、到期候选和一次性关联 SQL；审计 Mapper 只提供插入与查询，并纳入主应用 Mapper 扫描。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=AlertMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，1 个真实 PostgreSQL 测试通过，覆盖接收/审计同事务提交、读取和来源幂等唯一冲突。
- TASK-007 已完成：Incident Mapper 提供受保护的创建、活动位和版本条件状态更新；Timeline Mapper 只提供插入与顺序查询。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，1 个真实 PostgreSQL 测试通过，覆盖版本冲突不更新及连续 Timeline 事实。
- TASK-008 已完成：Outbox Mapper 提供插入、`SKIP LOCKED` 批量领取、过期 claim 恢复和令牌条件发布/重调度。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=OutboxMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，1 个真实 PostgreSQL 测试通过，覆盖并发领取拒绝、过期重领、旧令牌无效和发布终态。
- TASK-009/010 已完成：同键 advisory transaction lock 内分配单调接收与首次封存时间，对非有限上界安全失败。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=AggregationClockTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，2 个真实 PostgreSQL 测试通过，覆盖 20 路同键并发、预置未来时间模拟回拨、解决与接收交错、不同键独立锁及 `infinity` 上界拒绝。

- Spec A 明确：所有已持久化 Alert（包括终态 `FAILED`）参与 episode 划分；只有至少一条成员成功处理后才形成 Incident，且仅 `PROCESSED` Alert 必须关联。同步修订实施计划、数据模型和验证场景。
- 对齐共用 Alert 查询路由的来源 HMAC 与现有人员登录认证；修订 OpenAPI 登录令牌表达、协议版本验签顺序和领域事件版本示例。
- 最终一致性检查发现同键接收时间与 `alertId` 的排序稳定性问题；用户批准同键业务排序时间单调化，已追加 DEC-016 并同步 Spec、计划、数据模型与验收场景。
- Spec A 已建立 v1.0 基线，生成 50 项稳定编号的依赖有序任务；静态检查确认 48 条 FR 和 11 条 SC 均映射至少一项任务。所有任务仍为未实施状态。
- 经用户确认，仅追加 DEC-017（指纹、聚合键、episode）和 DEC-018（Incident 主状态机与封存），并同步架构文档和任务治理说明；Spec A v1.0 业务规则未改动。
- 建立 Spec B“审批到安全模拟处置”中文规格草案及质量清单，覆盖人工审批、一次性票据、安全模拟执行、结构化回执与 `UNKNOWN` 证据收敛；尚待利益相关方确认，未生成计划或任务。
- 本次仅修改规格与设计文档；没有修改业务代码、数据库迁移或前端。

## 2026-09-24

- 断点核对：分支仍为 `feature/incident-platform`；TASK-001～010 的基础模块、Schema、Mapper 与既有测试文件完整，未发现写到一半的 US1 文件；首个未完成项为 TASK-011。修正 `docs/PROJECT_CONTEXT.md` 对已创建模块、Schema 和任务进度的过时描述。
- TASK-011/012：加入 Webhook HTTP 响应矩阵、复合失败优先级、安全错误封装和固定七帧 HMAC 向量，以及方法、路径、目标、原始体、时效、活动/停用 key、重复头与混合凭据测试。先写失败断言时目标类缺失导致测试编译失败；加入实现后执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=AlertIngressContractTest,HmacSigningContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，11 个测试通过。TASK-011/012 完成。
- TASK-014～016、018/019：实现严格 v1 JSON 规范化、来源 HMAC 验证、接入顺序过滤、Alert 提交/查询 HTTP 接口、受限错误响应和共用 GET 路由本地鉴权。全局 Sa-Token 仅对精确机器 POST 与数字 ID 的共用 GET 放行；人员查询本地强制原始令牌和 `admin` 角色，来源跨租与不存在均返回相同 404。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=AlertV1CanonicalizerTest,AlertIngressContractTest,HmacSigningContractTest,AlertQueryAuthContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，测试通过；这些任务已完成。
- TASK-013/017 的代码已开始：增加隔离 PostgreSQL 的 100 路同身份并发、规范载荷等价/冲突与审计回滚测试，接收服务在同键事务锁内原子插入 Alert 和首条审计事实，唯一约束仲裁重复。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=AlertIdempotencyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，测试编译通过，但两个用例均因本机 Docker daemon 不可用在容器启动前报错，业务断言未运行；TASK-013/017 保持未完成。尝试启动 Docker Desktop 后其进程立即退出，当前会话也无法启动 `com.docker.service`，未改用共享或远程数据库。
- 为接收事务提供指纹依赖，已先写 `AlertFingerprint`；TASK-024 的规则测试和聚合阶段尚未完成，未将该任务标记完成。此处未实施 Spec B，也未提交、推送、合并或部署。
- Docker Desktop 后续由用户启动，`docker info --format '{{.ServerVersion}}'` 返回 `29.4.3`。重跑 TASK-013 的命令成功，两个隔离 PostgreSQL 16 用例通过：100 路相同身份提交只落一条 Alert 和一条首收审计事实，规范等价/冲突判断、注入审计写入失败的事务回滚及后续安全重试均通过；TASK-013/017 随实测结果完成。
- 执行 US1 汇总命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=AlertIngressContractTest,HmacSigningContractTest,AlertIdempotencyTest,AlertQueryAuthContractTest,AlertV1CanonicalizerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，全部目标用例通过，TASK-020 完成。`AlertQueryAuthContractTest` 还以实际 `SaTokenConfig` 注册的拦截器验证：共用 GET 被全局登录路径放行后，匿名请求仍由路由本地认证返回 401。
- TASK-021～030 已完成：版本化指纹、基于可信接收顺序与封存边界的 episode 划分、数据库 Worker 二次校验、同键事务锁下 Incident 唯一创建/关联，以及同事务 Timeline/Outbox 写入。初写窗口测试误把“晚于 anchor 30 分钟”当成滑动窗口分段条件，测试失败后按相邻 Alert 间隔修正测试数据，实现未为错误断言改动。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=FingerprintEpisodeTest,IncidentAggregationTest,IncidentAtomicityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 两次，目标用例均通过；覆盖 100 条同键并发、FAILED anchor、全失败无 Incident、回放后关联、迟到处理、历史创建即封存、唯一开放位、Worker 重扫不重复，以及关联/Timeline/Outbox 故障逐项整体回滚。
- TASK-031～036 已完成：八条合法 Incident 状态边和禁止边、预期状态/版本冲突、同键首次解决封存、重开时较新未关闭 Incident 检查、首次解决独立双 Timeline 事实、人员查询/转换/只读时间线 HTTP 接口。初次控制器接口测试发现 Spring Boot 4 默认 Jackson 3 不能直接读取 Jackson 2 `JsonNode` 参数；改为接收 JSON 文本后用模块内严格解析，重跑成功。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentStateMachineTest,IncidentTransitionTest,IncidentControllerContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，目标用例全部通过。
- TASK-037～044 已完成：补齐接收后 Worker 重扫、五次可配置重试及有界抖动、一次终态失败审计、受版本保护的人工重放及接受/拒绝审计、人员重放 HTTP、Outbox 短事务 claim 与稳定事件 ID 重投、数据库对账运行状态和人员权限拒绝。执行 `AlertRecoveryTest`、`OutboxRecoveryTest`、`OperationsSecurityTest`、`OperationsReconciliationTest`、`AlertReplayContractTest` 的 Maven 定向测试均通过。首次 Outbox 测试因人工重置已发布行时未清除 `published_at` 而触发 `ck_outbox_status`，仅修正测试预置；首次运行状态测试因独立 MockMvc 缺少 Sa-Token 上下文及 PostgreSQL `greatest(0, NULL)` 返回 0 而失败，补齐权限桩与 SQL 空值判断后通过。
- TASK-045～047 已完成：隔离 PostgreSQL 证据测试生成 `incident/target/incident-evidence/<run-id>/` 的清单、场景结果、表计数、事件 ID 和禁止内容扫描摘要；并加入两类 v1 事件 envelope/负载及未知 Schema 版本的数据库拒绝回归。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentEvidenceCatalogTest,AlertRecoveryTest,OutboxRecoveryTest,AlertReplayContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，目标用例全部通过。证据测试首次因测试构造 `List.of` 包含无审计行时的 null 而失败，修正后通过；证据清单只声称本次真正执行的独立场景，不把未执行项写成通过。模拟发布器验证 Outbox at-least-once 重投，未连接真实 RocketMQ Broker。
- 校验 `contracts/incident-events.md` 时发现初始 Incident 行及首事务两条事件的版本实现为 `0`，与契约要求的最终版本不符；现已改为 `1`。执行 `IncidentEventContractTest` 与 `IncidentAcceptanceSuiteTest` 均通过，后者串联真实接收、幂等、Worker、生命周期、模拟发布、终态失败与重放。首次模块级命令 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am test`：Incident 模块 56 个测试中 2 个旧测试仍按版本 `0` 转换而失败，其余通过；已修正 `AggregationClockTest` 与 `IncidentMapperTest` 的旧预期，待重跑模块级检查。此轮未执行真实 Broker、远程环境或 Spec B。
- TASK-048～050 已完成：再次运行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am test`，退出码 0；Incident 模块 Surefire XML 合计 26 个测试类、56 个测试，失败 0、错误 0、跳过 0，并执行了上游依赖模块适用测试。`IncidentAcceptanceSuiteTest` 覆盖本地接收到发布与恢复的组合路径；其他并发、契约、Schema、权限与故障场景由同轮模块测试覆盖。证据目录中的独立运行清单只标记自身实际执行的场景。`git diff --check` 退出码 0；所有现有修改及未跟踪文件保留，未提交、推送、合并、部署或实施 Spec B。真实 RocketMQ Broker、真实多进程故障演练和生产可靠性结论未验证。
- 收尾格式检查 `.\mvnw.cmd -q -pl incident -am spotless:check` 首次失败：`SaTokenConfig.java` 混合换行及新建 `incident` 文件的许可证/换行格式不符。恢复该已有文件的统一换行，仅对 `incident` 运行 `.\mvnw.cmd -q -pl incident spotless:apply` 后，格式检查退出码 0。随后发现 Relay 向 MQ 适配器传 JSON 字符串会改变 envelope 的消息体形状，改为发送已持久化 envelope 对象，并校验其版本、事件类型与稳定 ID；执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=OutboxRecoveryTest,IncidentAcceptanceSuiteTest,IncidentEventContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 退出码 0，捕获发送体测试确认对象和 Broker key 的 `eventId` 相同。
- 最终复查 `.\mvnw.cmd -q -pl incident -am spotless:check` 与 `git diff --check` 均退出码 0；`tasks.md` 的 50 项任务均为 `[X]`。最后一次修改仅涉及 Relay 的消息体边界及其定向测试，因已有针对性回归通过，未再次运行全 56 项测试。

## 2026-09-28

- 在保留现有工作区和旧容器的前提下，启动 Docker Desktop 29.4.3；使用本轮独立的 PostgreSQL/pgvector 16、Redis、RustFS 和 RocketMQ 5.2.0 容器进行 Spec A 本地运行验收。对隔离库应用 `resources/database/schema_pg.sql`，以非 owner 的 `ragent_app` 启动主应用；`pg_stat_activity` 可见 5 条该角色连接。执行 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl bootstrap -am -DskipTests package` 退出码 0，主应用以 JDK 21 启动成功。首次两次启动分别因隔离 Redis 密码配置和未启动对象存储而失败；对齐隔离依赖后启动成功。仓库默认 MCP 地址 `localhost:9099` 不可用，应用记录跳过工具注册并继续启动；未运行任何远程或付费服务。
- 真实 HTTP 接口按复合失败优先级分别返回 `415/413/401/400/422`；无凭据查询返回 401，来源 HMAC 签名查询返回 200。两个不同外部 ID 的有效告警均返回 202，相同告警重提交返回原 `alertId` 且 `duplicate=true`。异步处理收敛后，隔离库中为 2 条 `PROCESSED` Alert、2 条 `ALERT_RECEIVED` 审计、1 个 `OPEN` Incident、连续序号 1～3 的 3 条 Timeline，以及 3 条 `PUBLISHED` Outbox；3 条 Outbox 均有不同的 Broker 消息 ID，RocketMQ topic 队列最大偏移合计为 3。审计、Timeline、Outbox 的敏感字段名扫描命中 0 行。
- 管理员登录后查询 Alert、Incident、Timeline 和运行状态均返回 200；匿名 Incident/运行状态查询、无令牌人工重放均返回 401，管理员请求不存在告警的重放返回 404。运行状态与数据库一致：待处理 Alert、待发布 Outbox 和失败 Alert 均为 0。以 `ragent_app` 实际执行 Timeline `UPDATE` 与 Alert 审计 `DELETE`，均被 PostgreSQL 权限拒绝。
- 重跑 `.\mvnw.cmd -q '-Dspotless.apply.skip=true' -pl incident -am test` 退出码 0；Incident 模块 Surefire XML 合计 26 个测试类、56 个测试，失败 0、错误 0、跳过 0。`git diff --check` 退出码 0。本轮真实 Broker 证据覆盖生产者确认与 topic 持久化偏移，不代表下游消费或多进程故障演练；未实施 Spec B，未提交、推送、合并或部署。
- 随后补做 Spec A 跨进程与 Broker 中断演练，记录于未提交的 `incident/target/fault-recovery/result.json`。隔离库中先通过真实 HTTP 接收 2 条 Alert，并在 Worker 停用时强制终止进程：保留 2 条 `RECEIVED`，Incident/Outbox 均为 0。另一个 JVM 启用 Worker 后，无来源重发即收敛为 2 条 `PROCESSED`、1 个 Incident、3 条连续 Timeline 与 3 条 `PENDING` Outbox。再次终止进程，停掉隔离 Broker 后启动发布器：3 条 Outbox 保持 `PENDING`，实际观察到 `PUBLISH_UNAVAILABLE` 和每条 5 次尝试。恢复 Broker 后，同一批 3 条事件均为 `PUBLISHED`、每条 6 次尝试、3 个不同 Broker 消息 ID；topic 最大偏移合计 3。最终仍仅有 1 个 Incident、3 条 Timeline、2 条接收审计，安全字段名扫描命中 0 行。三个阶段使用同一隔离库和 Redis，均未重新提交来源告警。
- 代码搜索与 Spec A 范围核对确认：目前没有订阅 `incident-domain-events` 的业务消费者，`spec.md` 明确将消费者实现排除在 Spec A 之外，因此下游业务处理不能以 Broker 投递成功冒充已验证。此次是一次本地定向故障演练，不代表生产可靠性或完整故障回放实验室；消费侧验证应在首个真实消费者实现时随其集成验收执行。

## 2026-10-03 — Spec C：Incident Runbook 诊断本地实现与自动验收

- 用户“确认执行”批准 `specs/003-incident-runbook-diagnosis/` 的 spec/plan/tasks；按 brainstorming 与仓库 Spec Kit 产物完成正式一致性复核后实施。最初31个已跟踪修改和未跟踪 A/B 内容均保留，分支仍为 `feature/incident-platform`，HEAD 仍为 `67624931f20812922568d3641a095adb291263ba`，未暂存、提交、推送、部署、调用付费模型或真实基础设施。
- 新增5类中文模拟 Runbook、不可变 manifest 与全文/片段摘要、范围/有效期过滤和 `lexical-v1` 检索。`agent` 只在事务外检索/抽取候选与只读建议；`incident` 冻结来源报告事实、持久请求/唯一终态/可复核全文和引用，并只追加 Timeline。并未接入生产 Runbook、自由生成模型或前端页面。
- 新增 C 升级脚本 `resources/database/upgrades/v2.0.0/261003_incident_diagnosis.sql`，同步全量 Schema；真实 PostgreSQL 验证 A→B→C 升级、`ragent_app` 冻结列保护、结果/link 只追加、事务回滚、同来源并发幂等、过期请求 POST 惰性收敛。GET 保持只读；已完成诊断不会仅因经过请求截止而显示运行超时。目标映射默认空，缺本地适用性适配器或映射时失败关闭。
- 建议选择仅接受 IDs/digests/版本，服务端重新核对事实、当前版本、语料和模拟白名单，并推导工具/参数；创建 PENDING_APPROVAL Action、Approval、link 与 Timeline 是同域事务。诊断不批准、不签票、不调用 MCP。原 B 审批/票据/Worker/回执协议保留，拒绝后需新请求创建新 Action；历史幂等重试返回原 Action。

### 实际执行与结果

所有命令均在仓库根目录 PowerShell 执行，`RAG_LIVE_TESTS=false`，Java 21.0.12（Maven release 17），Docker Engine 29.4.3；禁用根编译绑定的自动格式化，避免覆盖已有修改。

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=IncidentDiagnosisGoldenPathTest,IncidentDiagnosisSafetyTest,*Diagnosis*,IncidentSnapshotIntegrationTest,Runbook*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
$env:RAG_EVAL_COMMAND=".\mvnw.cmd '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=RunbookOfflineEvaluationTest,DiagnosisOfflineEvaluationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test"
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=RunbookOfflineEvaluationTest,DiagnosisOfflineEvaluationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

| 运行 | 实际结果 | 证据 |
| --- | --- | --- |
| 默认完整仓库回归 | BUILD SUCCESS；805测试，0失败/错误，21跳过；22:47，结束22:14:19 +08:00 | `incident/target/diagnosis-full-regression.log`、`diagnosis-full-summary.json` |
| 最后生产调整的定向回归 | BUILD SUCCESS；Incident 8、RAG 5、Agent 4、Bootstrap 2，共19测试，0失败/错误/跳过；06:36，结束22:26:02 +08:00 | `incident/target/diagnosis-final-focused.log`、`diagnosis-focused-summary.json` |
| 报告字段补全后的离线复跑 | BUILD SUCCESS；真实检索2测试、实际抽取1测试，0失败/错误/跳过；01:22，结束22:31:05 +08:00；实际命令、Git HEAD/dirty、输入摘要、版本、固定时钟、配置摘要和起止时间由报告器记录 | `incident/target/diagnosis-report-verification.log`、下述 runId 报告 |

全仓各模块统计：framework 29、infra-ai 28、mcp-server 64、incident 111、rag 301、agent 265、bootstrap 7。21跳过中 MCP 的 YouCom 联调1、RAG旧外部探针19、AgentDashboardReaderTest 1；跳过既有外部探针不代表其已验收，新增 C 定向场景无跳过。全仓回归后仅调整了快照脱敏、完成期限显示、黄金路径证据输出和离线报告字段；前两类与黄金路径由上述19项定向回归覆盖，最后报告变化由离线复跑覆盖，未再次重复全仓长回归。

### 真实隔离应用黄金路径

复用既有独立主应用/MCP JVM，使用本轮 Testcontainers PostgreSQL/pgvector、Redis、RustFS；不连接共享开发数据。主应用真实连接角色为 `ragent_app`，实际登录、签名 HTTP 告警、Worker 聚合及 MCP 调用均有断言。

- Golden：两条告警形成一个 Incident；真实本地检索/抽取生成可复核诊断。首次选择经人员 REJECT 后，模拟应用/票据均0；另一来源请求建新 Action 并由人员批准，Worker 通过一次性票据/MCP/结构化回执完成一次应用，隔离目标从 PAUSED 变为 READY。重复选择没有额外应用。主应用重启后原文/片段 hash、两条 Action 的诊断来源与状态保持；实际1份诊断结果、2条 link、1条回执、1次模拟应用。
- Safety：匿名、伪 decision/真实 target 字段、新告警导致 stale、来源注入、CLOSED、无身份/无票直接 MCP 被拒绝或安全降级；重启后证据可读。实际2份诊断结果、0 link、0回执、0应用，目标仍PAUSED。过程日志保密扫描通过。语料过期/撤回、篡改、截断、中断与同事务回滚另由聚焦测试覆盖。
- 安全证据文件：`bootstrap/target/incident-golden-path/diagnosis-golden-394e48ad-1d47-44a4-88a1-b0067b667ca4/` 与 `diagnosis-safety-2c6fec8a-9587-4ba2-9d65-89baccc3a838/`，各有 `diagnosis.json`、`actions.json`、`summary.json`；不保存登录 token、票据明文或敏感 payload。

### 离线自动基线与人工待办

- 数据集30例，5类各6例，语料含5有效文档和15跨范围/过期/撤回干扰；gold不传入检索器。10正例 Hit@3=10/10、SectionHit@3=10/10，每类均2/2；development 25例中5正例全命中，holdout 5正例全命中。适用性泄漏0，真实检索片段摘要验证73个。
- 实际抽取30例：必要候选/只读建议覆盖10/10；规则支持22/22，引用原文/行号/全文及片段摘要42/42，负例安全降级20/20。另1例明确标为 FIXED_TEST_RESPONSE 的篡改写建议被拒绝，不冒充真实召回。模型与诊断直接写工具调用均0。
- 最新报告为 `rag/target/runbook-evaluation/report-v1.json` 和 `agent/target/diagnosis-evaluation/report-v1.json`；每次报告分配新 runId，manifest/cases/summary 分别位于 `rag/target/runbook-eval/<runId>/`、`agent/target/diagnosis-eval/<runId>/`，属于可重建的生成内容。
- 用户明确选择“保留人工复核待办”。标签为 SYNTHETIC_RULE_BASELINE，reviewer 字段只表示规则基线来源；没有独立人工复核记录。**T028保持未勾选，FR-017人工质量验收未完成**；自动指标不证明真实模型质量、未知分布泛化、生产可靠性或性能。

### 排错与范围复核

先用缺 C Schema 的红测暴露缺失，再补迁移；实际 PG 暴露 nullable 游标需显式 varchar CAST。双 JVM 首次因重复 Worker 配置导致未启用，修正测试启动参数；HTTP 测试发现 Boot 4 Jackson 3 对 Jackson 2 JsonNode 的形状不兼容，应用服务转换为普通 Map/List 输出并补契约测试；REJECT缺 reason 返回422，修正请求，保留 B 的强制理由要求。快照排除 raw summary/labels/payload，额外验证 Authorization Bearer 和带空格的引号密钥值脱敏。同步上下文/架构/决策/任务/笔记和 Spec C 现状；旧研究时点保留，未做全仓格式化或重写历史决策。

收尾：Spec Kit prerequisites 确认当前功能产物齐备，35/36任务已完成、仅T028待办；`git diff --check`退出0，文档相对路径/链接/尾随空格检查通过，41个新增诊断/Runbook及黄金路径Java文件的Apache-2.0头检查通过，全量Schema的C后缀与增量SQL一致。分支/HEAD与最初一致，暂存区仍为空。

## 2026-10-03 — T028人工标签审阅材料准备

- 用户“执行”授权将30例整理成人工审阅表。本轮仅整理现有数据/报告与必要任务记录，未开始前端Spec或新增安全功能，未代签人工复核。
- 新增 `specs/003-incident-runbook-diagnosis/review/human-review.md`（30例索引、人工结论/审阅者/日期/修正依据填写区）、`evidence.md`（输入、合成预期、实际候选/建议/排序、引用原文/位置/摘要、20份语料适用性元数据与5份有效全文）、`provenance.json`（运行标识、输入/报告文件摘要和逐例输出摘要）。原报告仍在生成目录，证据文档保存生成时点摘录，非新运行报告。
- 来源为真实检索runId `5a6c0901-af65-4f7b-acbb-23ace25afee9` 与实际规则诊断runId `14bc08e4-2ba9-4c29-8d57-a95334ede7ae`。数据集与报告canonical摘要均为 `b7caeb1b310838800ebc3cff02b17987cfe0c4b1810f22e4a9cfcc818376226b`；没有重跑Java评测，也没有把历史通过改写为本轮执行结果。
- 逐例核对唯一ID、输入/预期与两份报告一致，验证20份fixture全文摘要、73条检索片段与42条诊断引用的原文/行号/全文和片段摘要，全部一致。原报告的检索inputDigest是完整case（含gold）的摘要，来源清单明确记为retrievalReportedCaseDigest；实际检索调用不传gold。
- 发现HR-001：5个无依据例的输入、范围、时钟和全部预期相同，只有案例标识/类别归属不同；30唯一caseId对应26个输入+预期签名组，不等于30独立场景。与契约避免重复凑例的要求存在待审问题。HR-002：holdout仅5个正例，不覆盖负例。已明确告知并列入审阅表；未静默改标签、split或降低门槛。
- 人工审阅仍0/30，T028未勾选，Spec C仍35/36。变更仅为3份审阅材料及TODO/tasks/changelog的追加说明；现有代码/Schema/数据集/运行报告保持不变。未提交、推送、部署、调用付费模型或真实基础设施。

本轮最小检查：`python incident/target/prepare-human-review.py`生成并核对原文；`python incident/target/verify-human-review.py`复核30个待审记录、30份合成快照输入摘要、来源文件摘要、115条引用、相对链接与Markdown围栏，退出0；`git diff --check`退出0。基线1709个现有文件中，仅tasks/TODO/changelog按授权追加，另外1706个文件摘要未变；分支/HEAD/空暂存区均保持。未运行Java测试，因为没有代码、Schema或gold变更。

## 2026-10-03 — 用户审阅意见与cases-v2修正复跑

- 接收用户提供的v1结论26通过/4需修正/0受阻及HR-001/002处置指示，归档为 `specs/003-incident-runbook-diagnosis/review/user-review.md`；主审阅表逐例标为用户提供、未签署。真实审阅者与签署日期继续TBD，助手不签署，不把这些材料标为独立人工验收。
- 保留cases-v1及原证据；原两份v1完整报告移作reviewed-snapshot别名，原文件SHA-256逐字节一致（真实检索原报告可由原runId的summary/cases重新组合，并匹配原SHA，含原CRLF格式）。原runId目录不改写。审阅来源清单更新到固定快照，最新运行别名与数据集版本分开。
- 按指示新增cases-v2：保留OVERLOAD垃圾token代表，四个重复例替换为disk space full 磁盘写满、error 异常 unknown failure、天气/节假日两条无关事实、符号数字输入；caseId/family中立。额外新增无依据/范围/过期/注入holdout各1例，共34例：25 development（5正例/20负例）、9 holdout（5正例/4负例）。当前fixture仍只消费首条queryFact，第二条无关事实不声称被处理；按实际消费输入/scope/clock检查34/34唯一。
- 新增建议级gold expectedRecommendationRules，评分器验证HEALTH的checks+simulation-recovery及其他族只读建议；gold不传运行生成器。原门禁把无gold但有任何词法返回当泄漏，会把“异常”的合法弱匹配误判；已改为按真实语料scope/currentVersion/status/有效期计算适用性泄漏。运行检索/生成器/Schema/审批链不改。原因码细化作为后续观察保留。
- 红测：`.\mvnw.cmd '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=RunbookOfflineEvaluationTest,DiagnosisOfflineEvaluationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，RAG parameterized v2因尚无输入失败，退出1；证据 `incident/target/review-v2-red.log`。随后新增v2及评分检查，明确需求缺口被覆盖。
- 绿测命令如下，RAG_LIVE_TESTS=false，并将同一完整命令放入RAG_EVAL_COMMAND。执行真实本地语料/关键词检索、实际规则抽取与固定篡改检查，没有外部模型调用：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl agent -am '-Dtest=RunbookOfflineEvaluationTest,DiagnosisOfflineEvaluationTest,RunbookCorpusTest,RunbookRetrievalTest,DiagnosisEvidenceValidatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

BUILD SUCCESS，RAG 6、Agent 3，共9测试，0失败/错误/跳过；01:09，结束2026-10-03 23:50:02 +08:00。v1/v2参数化实际检索与诊断均执行。仅改测试/fixture/文档，未重跑无关全仓或双JVM黄金路径；此前运行不冒充本轮结果。

| v2实际结果 | 分母/计数 |
| --- | --- |
| Hit@3 / SectionHit@3 | 10/10 / 10/10，每个正例族2/2 |
| 适用性泄漏 | 0；合法弱匹配不作为泄漏 |
| 实际消费输入唯一性 | 34/34 |
| 安全降级 | 24/24；development 20/20，holdout 4/4 |
| 有效建议覆盖 / 规则支持 | 10/10 / 22/22 |
| 引用原文、位置、全文/片段摘要 | 检索89条、诊断42条；均验证通过 |
| 实际规则例 / 固定测试响应 | 34 / 1（单独标记FIXED_TEST_RESPONSE） |
| 模型调用 / 诊断直接写调用 | 0 / 0 |

v2数据集canonical SHA-256=`f8154160bdb4605d302e458b08778b27a9fc876f1109cc075a0e87cd3b59decc`；语料仍 `21d725266293084cd1d25b3e25724408958f3fd5323a7e8217e8a341ced9882c`。真实检索runId `e1cf5f14-80f3-4227-ad71-a829cc6e072d`，实际诊断runId `0b72f960-7bcc-47ef-9c59-20e43ca28491`。按runId保存manifest/cases/summary/full report；最新别名分别是runbook-evaluation/report-v2.json、diagnosis-evaluation/report-v2.json。

新增v2审阅材料位于 `specs/003-incident-runbook-diagnosis/review/v2/`，逐例列出预期/实际/新建议级gold/引用，131条片段原文与摘要核对一致。原v1材料保持证据时点；仅追加意见与稳定来源指针。DEC-026部分替代DEC-025的重复不足例组织方式，原决策不删除。公开设计的holdout不是独立盲评，自动通过不证明未知分布泛化。

**待办**：四个修正例、四个新增holdout及建议级gold仍需人工确认；真实签署TBD；T028仍未勾选。未提交、推送、部署、调用付费模型或真实基础设施。

2026-10-04收尾静态复核：`python incident/target/verify-review-v2.py`退出0，核对v1原标签/证据及原报告摘要保留、26/4未签署意见、34唯一消费输入、131条v2片段核验、相对文档链接/围栏，以及T028继续开放；`git diff --check`退出0。原有文件仅本轮授权的测试/功能文档变化，其余基线文件摘要一致；分支/HEAD/空暂存区保持。

## 2026-10-04 — Spec C人工审阅确认与T028收尾

- 用户回复“已经完成”；核实v2 34例全部通过、0需修正、0受阻，fOggy签署日期2026-10-04，HR-001/002处置2/2及FR-017剩余覆盖核对已落表。保留既有人工记录与v1历史，助手未代签。
- 静态核对离线评测契约、撤回/冲突/伪造安全测试源码与5份Surefire XML：既有定向9测试、0失败/错误/跳过。沿用2026-10-03运行证据，本轮未复跑Java测试。
- T028关闭，Spec C当前36/36项完成；同步tasks/spec、v2表头与收尾状态、PROJECT_CONTEXT及TODO。未改变需求、安全边界、代码、Schema、gold标签、语料或运行输出。
- 保留公开设计非盲评、首条queryFact消费、本地关键词与抽取规则范围。人工依据中的“最强负例”“注入检测泛化成立”收窄为已测输入变体通过，不推出未知分布质量；当前检测仍使用固定关键词正则，建议规则门禁为有序数组断言，无自动转人工流程。
- 未提交、推送、部署、调用付费模型或真实基础设施。

本轮静态检查：34条签署与2项处置记录、36/36任务标记、4份来源文件SHA-256保持一致，审阅材料相对链接/围栏均通过；`git diff --check`退出0。分支与HEAD保持，暂存区为空；未运行新的Java测试。

## 2026-10-04 — Incident操作界面设计与Spec D草案

- 用户“确认执行”授权推进UI-001设计；读取当前代码/Schema、A/B/C、宪章和中文覆盖模板，按brainstorming比较现有后台、独立控制台、聊天主入口，建议扩展既有管理员后台。
- 新建 `specs/004-incident-operations-ui/` 下spec/plan/research/data-model/contracts/quickstart/requirements检查清单及31项待实施任务；20项FR、7项SC，全部为待确认设计，尚无页面或接口改动。
- 已确认接口缺口：Incident列表及关联告警、安全冻结参数与回执摘要；现有全局前端客户端仅接受code=0而Incident返回SUCCESS。计划使用专用客户端、最小只读投影，不改变上游页面HTTP行为或A/B/C审批协议。旧frontend/TESTING.md的8080/外部机器目录与当前9090配置冲突，计划实施时修正，以代码为准。
- 使用Spec Kit resolve-template/setup-plan/setup-tasks/check-prerequisites；当前功能指针从003转为004，实际Git分支仍feature/incident-platform，HEAD不变，未创建分支或提交。子研究仅只读前端/安全查询契约。
- 同步PROJECT_CONTEXT和TODO；没有新建决策ADR，因为设计尚待确认。代码、Schema、样例与运行工件保持；未执行新的Java/前端/浏览器门禁，不声明运行通过。

产物静态自检：20 FR/7 SC均有任务映射，31任务编号连续（US1=6、US2=5、US3=5、US4=4，共享准备5、收尾6），8份Markdown的相对链接、围栏、无占位/绝对机器路径、UTF-8及尾空白检查通过；标准`git diff --check`退出0。1720文件基线中仅3份当前项目文档和本轮新建的spec/plan填充发生变化，其余1715文件摘要一致；物理分支/HEAD/空暂存区保持。UI运行验收未执行，不构成实施完成声明。

## 2026-10-04 — Spec D本地实施与真实隔离浏览器验收

- 用户明确确认spec/plan/tasks并授权本地实施、测试和隔离验收。使用speckit-analyze只读一致性检查（20 FR/7 SC/31任务全覆盖、无阻塞），再按speckit-implement执行；不重复brainstorming。检查清单仍9/11，沿用用户明确授权，不代改审阅者标记。当前功能指针004，实际分支feature/incident-platform、HEAD=`67624931f20812922568d3641a095adb291263ba`保持；原有dirty和未跟踪文件保留。
- 实施核对发现草案将IncidentController成功码误写为SUCCESS，而现有代码/测试为"0"。经用户选择保留契约，DEC-027记录专用客户端逐接口接受唯一成功码（A="0"、B/C="SUCCESS"），不改全局HTTP客户端或A/B/C安全协议。前一设计日志为当时草案判断，本次以代码核对修正。
- 既有管理员后台新增事件列表与详情、关联告警/Timeline分页、诊断历史/冻结快照/候选/引用原文/hash、显式选择建议、独立审批、执行及回执。新增两个有界只读查询和持久Action参数/回执安全投影；不新增表、权限体系、模型调用或基础设施能力。UNKNOWN明确未知，无写重试，成功不自动解决Incident。
- UI串行读排队、5秒活跃轮询、后台暂停、路由取消、失败/终态停止。写请求不自动重试，同意图复用绑定；409刷新后重新审阅。实际验收发现诊断刷新清掉冲突提示，已修复并在真实STALE_SNAPSHOT场景保留语义码/requestId；修复筛选分页旧请求取消与读取中写刷新不能丢失。WorkflowDashboard仅补缺少的ComponentType类型import以修复全局tsc基线。
- 先运行最小契约再扩大：真正Action预览红测为parametersAvailable缺失；修复后5项契约通过。最终命令及日志如下，Incident31+Agent3=34项，0失败/错误/跳过，BUILD SUCCESS：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=IncidentOperationsQueryTest,IncidentQueryContractTest,IncidentControllerContractTest,Action*ContractTest,ActionSecretLeakTest,ActionApprovalRaceTest,ActionReceiptTest,ActionUnknownConvergenceTest,Diagnosis*Test,IncidentSnapshotIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`incident/target/spec-d-java-final.log`保留实际结果。Node v20.19.5/JDK21.0.12，Docker/Testcontainers临时PostgreSQL16；禁止绑定compile的spotless改源码。前端实际 `npm ci --no-audit --no-fund`、`node tests/incident-view.check.mjs`、本地 `tsc --noEmit -p tsconfig.app.json`、`npm run lint`、`npm run build`通过；日志frontend/.output/spec-d-*-final.log。无新依赖。Codex闪退时最后一轮build中断且会话退出码丢失，未计通过；恢复核对后tsc/lint/build重跑均退出0，build1m1s，仅原有大chunk警告。

- 显式IncidentOperationsUiFixture复用真实Bootstrap/MCP双JVM、普通ragent_app和独立模拟PostgreSQL、Redis及存储；签名Webhook两告警、实际本地Runbook/规则诊断、真实Worker/票据/MCP/回执。3次成功隔离运行（首黄金/无映射/最终分页+断连+黄金），22/22浏览器场景最终通过、0最终失败/0跳过；另12个真实HTTP权限断言全部401/403。最终五份可信快照计数0/0/1/1/1，仅拒绝与成功两Action，executionId=`2106725697257996288`、receiptId=`2106725699494191104`。Incident始终OPEN；不是截图或前端计数推断。
- 响应丢失由测试代理读取真实201响应后主动断开连接；页面TRANSPORT_UNKNOWN，手动重发返回原actionId，不自动重调。旧审阅页审批ACTION_CONFLICT；新第三告警使version2→3，旧选择STALE_SNAPSHOT；无映射UNSUPPORTED_RECOMMENDATION/零应用。HTML告警纯文本及注入图片/脚本0节点；UNKNOWN仅隔离SQL展示fixture，运行恢复另由Java回归覆盖。匿名/普通用户浏览器路由及真实API均拒绝。
- 安全制品见 `incident/target/ui-evaluation/spec-d-2026-10-04T12-35-13-135553Z/acceptance-summary.json`、server-evidence-1～5.json、lost-response.json、security-gates.json、07-receipt.jpg；另首run `spec-d-2026-10-04T12-07-07-227268800Z`及无映射 `spec-d-2026-10-04T12-26-21-121557300Z`。完整22场景、精确命令和限制见Spec D quickstart。
- 首夹具缺UNKNOWN authorization_due_at被Schema真实拒绝，随后修复重跑；一次测试类尚未编译导致启动失败，日志均保留，不算通过。浏览器viewport override未改变实际454px宽，最终使用折叠导航截图，未声称桌面断点通过。未构造额外Runbook HTML浏览器样例，不扩张现有注入验证范围。临时服务/资源关闭、凭据删除、日志归档；恢复时Docker引擎不可达，沿用已完成真实证据，不改连开发库。

31项任务按实际证据收尾，同步ARCHITECTURE/PROJECT_CONTEXT/TODO/frontend TESTING和Spec状态。没有生产性能/跨浏览器/真实模型或真实基础设施验收，没有独立人工签署新增结果；保留上游署名及Apache-2.0历史。本轮未提交、推送、合并、部署或付费调用。

最终作者复核：补充诊断详情then回调的AbortSignal检查，阻止取消后晚到结果覆盖当前选择；其后Node逻辑、tsc、lint、build再退出0（57.45s）。`python incident/target/verify-spec-d.py`及`git diff --check`退出0：1720文件基线中1700逐字节不变，20既有文件均为授权改动，0丢失/越界；Schema、锁文件、原客户端、上游许可证及其他原修改保持。31/31任务、空暂存区、3个运行目录秘密扫描及凭据删除核对通过；audit.json与artifact-sha256.json记录安全制品摘要。

## 2026-10-04 — B06 并发票据消费与真实 Worker 崩溃恢复补充

沿用用户确认的 Spec B 执行协议，使用 speckit-converge/analyze/implement 补充 T051～T053；一致性检查无实质契约/Schema 冲突，原任务编号和 Spec D 功能指针保持。变更仅为两个既有测试文件及验收/状态文档，无生产故障开关、依赖或表变更。

- `ActionRecoveryRaceTest` 增加两种胜出次序：用真实 PostgreSQL 事务锁和 `pg_stat_activity.wait_event_type='Lock'` 证明另一操作确实阻塞。消费胜出不补票；恢复胜出时旧票拒绝、新票保留 Execution/执行键/requestId，最终仅消费一次。
- 完整 Bootstrap/MCP 双 JVM 使用独立临时主库、模拟库、Redis、存储。测试代理截住首次消费；实际签票未消费、应用为 0 时杀死 Worker JVM。原票自然超过 5 秒 TTL，重启后既有调度器恢复，PID 改变；原 Execution/执行键/requestId 保持。两张票最终 EXPIRED/CONSUMED，0 ISSUED；应用、回执各 1。旧票迟到 409、同来源提议返回原 Action、重复审批 409，新会话详情与 Timeline 可复核，Incident 为 OPEN。
- 最小类检查 4 项通过，日志 `incident/target/b06-race.log`。联合命令运行 17 项，15 项 Incident + B06 通过，旧正常 Worker 测试 1 项失败，所以该 Maven 退出 1；未记作整轮通过：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=ActionRecoveryRaceTest,ActionDispatchTicketTest,ActionApprovalRaceTest,ActionReceiptTest,ActionExpiryWorkerTest,ActionStateMachineTest,BootstrapIsolatedStartupTest#workerCrashAfterTicketIssueRecoversSameExecutionAndAppliesOnce+approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

日志 `bootstrap/target/b06-final-regression.log`；失败是正常 Worker 旧断言禁止响应包含 `desiredState`，而已批准 Spec D 明确输出持久化白名单参数。按当前安全投影核对 `parametersAvailable=true`、唯一参数 `desiredState=PAUSED`，保留票据泄露拒绝断言。随后实际重跑：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`bootstrap/target/b06-normal-worker-regression.log`：1 项通过，0 失败/错误/跳过，BUILD SUCCESS、退出 0，2m47s，于 22:32:36 +08:00 完成。合计 17 个不同测试最终都有通过记录；没有声称最后一次 Maven 同时运行 17 项。初次 B06 又曾因测试提议 helper 错把幂等重放 200 当成新建 201 而失败；改为明确断言 200 和原 Action 后通过。两次失败日志/报告分别保存在 `b06-first-failure/`、`b06-regression-first-failure/`，不删除或计通过。

运行制品 `bootstrap/target/b06-evaluation/3fbfcb09-8099-4b0a-9bfc-f1f381bb9fc0/` 包含 summary/action/timeline、三份主应用/MCP 进程日志、原联合 XML 与单独重跑 XML、Maven 日志、`manifest.json` 和 SHA-256 清单。真实账号会话、服务凭据及两张票据仅在内存用于逐项扫描，成功运行响应/日志/JSON 未泄露。JDK 21、release 17，Docker/Testcontainers 临时 PostgreSQL 16；不连接用户开发库。历史 B06 矩阵是当时快照，本补充增加真实崩溃证据，不覆盖历史。

范围限制：测试代理仅用于阻塞消费通道，不能据此宣称 B09 实际响应网络丢失、所有双路径故障已验证。回执/截止并发、B11 Action 事件 Relay 联合恢复、正式威胁模型及全场景清单仍开放；LAB-000 / ACT-003 未关闭。未改变前端，因此不重复 Spec D 的前端/浏览器检查；无新增生产能力、性能或真实跨主机结论。保留原 dirty 工作区及 HEAD，未提交、推送、合并、部署或调用付费服务。
## 2026-10-04 — B09 真实回执双通道故障补充

用户在 B06 收尾后要求继续；按 speckit-converge/analyze/implement 追加 T054/T055，沿用 Spec B FR-023/030/031/037 与 SC-004/005/006/009。检查清单 20/20，限定范围 8 个要求均映射，0 阻塞冲突；功能指针保持 004、Git 分支保持 feature/incident-platform。仅改 BootstrapIsolatedStartupTest 及任务/验收/项目状态文档，未改生产 Dispatcher、工具、依赖或 Schema。

JDK 21.0.12、release 17、Docker/Testcontainers PostgreSQL 16，完整主应用和独立 MCP JVM，临时主库/模拟库、Redis、存储；本机回环测试代理。复用既有夹具的容器数据库账号，不将本轮称为生产普通角色或跨主机验收。工具仅修改隔离 demo，初始 READY。配置冻结 maxExecutionDurationMs=5000、receiptGracePeriodMs=2000，不以 SQL 改时间或状态冒充截止/成功。

| 子场景 | 实际注入与结果 | 唯一事实 |
| --- | --- | --- |
| callback-only | 原回执回调 3 次均被 503 阻断，真实工具响应由 Dispatcher 接受，SUCCEEDED | 工具调用/应用/权威回执/回执 Timeline/Outbox 各 1 |
| response-only | 回调先提交成功，代理读完真实 MCP 响应后宣告 Content-Length 并关闭，不发正文；应用日志为 fixed content-length、bytes received: 0 | SUCCEEDED，工具不重调；上述事实各 1 |
| both | 三次回调 503 + 同样响应 EOF；额外令只读对账返回 503 一次，真实截止清扫进入 UNKNOWN，保存详情快照，再提交原 MCP 回执收敛 SUCCEEDED | 一个 Execution、一个消费票、一次应用、一份原 receiptId；重复不新增 |

每个子场景保留服务端 Execution/执行键/requestId/receiptId 绑定及真实模拟应用查询；Incident 始终 OPEN。重复原回执保持原事实；另构造带合法格式 proofCode 的相反回执负例，409、只追加 ACTION_RECEIPT_CONFLICT、原成功不覆盖。负例声明 ROLLED_BACK 不是实际回滚证明。

实际最小入口和最终联合命令见 Spec B quickstart，第 7 节。最终运行：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=ActionReceiptTest,ActionUnknownConvergenceTest,IncidentRemediationDispatcherTest,BootstrapIsolatedStartupTest#receiptChannelsFailIndependentlyAndTogetherWithoutRedispatch+workerCrashAfterTicketIssueRecoversSameExecutionAndAppliesOnce' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`bootstrap/target/b09-final-regression.log`：12 项（Incident 7、Agent 3、Bootstrap 2），0 失败/错误/跳过，BUILD SUCCESS、退出 0，4m56s，于 22:59:20 +08:00 结束。Bootstrap 包含 B09 一个测试内的三个故障子场景，以及 B06 真正 Worker 崩溃重启复跑；测试数不等于场景数。B06 新运行目录 `bootstrap/target/b06-evaluation/e465d5ec-926f-4f41-921f-85e5765444c6/`。

三次前序最小运行均失败并保留：first 的 Outbox 查询误把 executionId 当顶层字段（实际 envelope.payload）；second 误认为 UNKNOWN 应持续，但现有只读对账立即依据真实应用记录收敛成功；third（日志名称 b09-channels-final）反向 FAILED 负例缺 proofCode，正确被 422 拒绝。分别修正测试 SQL、明确对账不可用故障条件、补全负例格式后联合通过，生产行为不变。原日志和 XML/进程日志位于 `bootstrap/target/b09-{first,second,third}-failure/`，三个未完成运行目录无 summary，不当作成功。

成功制品 `bootstrap/target/b09-evaluation/9963cf7f-016e-4cf1-90da-1375a452a740/` 包含三组 before-late/action/receipt/timeline/outbox JSON、summary、冲突 Timeline、双 JVM 日志、最终 XML/Maven 输出、manifest 和 SHA-256。成功测试逐项扫描真实会话、独立双向凭据、三张消费票据；未在响应/JSON/日志发现秘密。基线文件保留审计和一致性检查另见 `incident/target/b09-baseline/`。

T054/T055 完成，LAB-000 / ACT-003 仍进行中。未验证 G03 回执/截止并发、B11 Action 事件 Relay 故障联合链路、完整威胁模型及真实跨主机/外部日志。没有改前端，未重跑浏览器/前端或默认全仓门禁，不重复扩张历史 Spec D 结果。隔离资源在 finally 关闭，原 dirty 与 HEAD 保留；未提交、推送、合并、部署、操作开发数据库/真实基础设施或调用付费服务。

## 2026-10-04：G03～G06 并发、Relay 与安全证据补充

按用户“继续完成”在原分支/dirty 工作区推进 Spec B T056～T060；实施前 converge 将已有协议的验收缺口追加为五个任务，analyze 未发现新的生产协议或 Schema 冲突。功能指针保持 `specs/004-incident-operations-ui`，实际分支 `feature/incident-platform`，HEAD `67624931f20812922568d3641a095adb291263ba`。只修改两个既有 Java 测试与文档，无生产代码/Schema/前端变更。

`ActionReceiptTest` 新增回执先提交和截止清扫先提交两个真实 PostgreSQL 锁竞争测试，从 `pg_stat_activity` 观察竞争线程 Lock 等待。前者无 UNKNOWN/对账请求，后者一份 UNKNOWN 和一次只读请求后由迟到回执收敛；最终一份权威回执及回执 Outbox，重复无新增。回执由夹具构造，截止用隔离 SQL 提前，对账为计数替身，不声称实际 MCP 应用。

`ActionOutboxCompatibilityTest` 新增真实 Relay 的两类 Action 事件恢复。真实批准/回执事务后，测试 Producer 首次发送失败；第二次模拟发送成功但真实数据库触发器拒绝发布标记；第三次恢复。各事件三次尝试，eventId/envelope 保持，最终 PUBLISHED；Action/Approval/回执事实不回滚。不是 Broker 或下游消费者恢复证明。

实际最小命令：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionReceiptTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionOutboxCompatibilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

两轮分别 5 / 2 项通过，0 失败/错误/跳过，退出 0；日志 `incident/target/safety-receipt-race.log`（1m12s，23:12:42）、`safety-action-relay.log`（1m，23:14:30）。本轮新增检查没有失败的 Maven 运行。

按共享边界扩大：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=ActionReceiptTest,ActionOutboxCompatibilityTest,OutboxRecoveryTest,ActionUnknownConvergenceTest,ActionRecoveryRaceTest,ActionApprovalRaceTest,ActionSecretLeakTest,ActionServiceAuthenticationTest,ActionBindingV1Test,ActionEventWriterTest,ActionStateMachineTest,ProtectedSimulationRouteTest,ProtectedSimulationE2ETest,IncidentRemediationDispatcherTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

最终日志 `incident/target/safety-closeout-regression.log`：38 项（MCP 6、Incident 29、Agent 3），0 失败/错误/跳过，BUILD SUCCESS、退出 0，3m43s，于 23:21:41 +08:00 结束。14 个选定类均有本轮 XML，含独立 MCP JVM 真实模拟与故障路径。没有运行完整 Bootstrap 测试、默认全仓、前端或浏览器；已有 B06/B09 和 Spec D 记录继续作为历史证据。

归档 [manifest](../incident/target/safety-closeout/run-742defc2-0468-4e47-b99d-34cb1fe77d8a/manifest.json) 含 14 XML、三份 Maven 日志、三个新增成功证明目录及 SHA256。新增 B11 summary 在测试内扫描实际票据明文；认证/秘密投影回归通过。未把全量 Maven 日志归档当作逐实际凭据扫描承诺；历史完整 Bootstrap 的逐会话/服务凭据/票据扫描依据其原 manifest。

新增 [威胁模型](security/incident-action-threat-model.md) 核对 11 类威胁、控制与回归，说明服务/数据库信任假设及剩余风险；新增 [B01～B12 索引](../specs/002-approval-safe-simulation/evidence-index.md) 区分历史、本轮、替身和完整 JVM 证据。SEC-001 当前安全模拟范围完成，外部安全评审 TBD。历史全场景同格式 manifest 及更广变体仍未齐，LAB-000 / ACT-003 保留进行中，LAB-001/OBS-001/DEP-001/旧探针工作保持开放。隔离容器/JVM 已退出，未提交、推送、合并、部署、操作开发数据库/真实基础设施或调用付费服务。

文件保留核对：1735 个起始文件，1728 个哈希不变、7 个为本轮预期编辑、0 丢失/意外变化；另新增威胁模型和证据索引。首次检查因基线反斜杠与白名单正斜杠比较误报，归一化后通过，原失败检查保留。文档相对链接和 `git diff --check` 通过，暂存为空，功能指针/分支/HEAD 保持，`.specify/extensions.yml` 不存在，无实施后钩子待执行。新证明 JSON 的凭据形态扫描与制品哈希复核结果收录 manifest，不代表完整日志逐秘密扫描或第三方认证。

## 2026-10-05：B01～B12 统一证据与当前安全范围收尾

用户授权继续验收收尾。converge 将完整应用、独立 MCP 和人工结论缺少独立制品的三处 partial 缺口映射 FR-037/SC-009，追加 T061～T064；analyze 核对四项均有来源，无生产需求/Schema 冲突或 CRITICAL 项，清单 20/20。只读 prerequisites 路径解析后显式核对 spec/plan/tasks，功能指针保持 Spec D，实际 Git 分支仍 `feature/incident-platform`，HEAD `67624931f20812922568d3641a095adb291263ba`。

只改四个既有测试/夹具和文档，新增一个依赖现有 PowerShell 的归档脚本。完整 Bootstrap 七入口、独立 MCP 八个阶段、对账四入口导出真实数据库白名单投影；保留稳定 Action/Execution/requestId、冻结目标/截止、票据安全状态、回执、Timeline 和 Outbox。每次运行使用独立日志目录，扫描本次 MCP 全部六张票据与双向凭据，普通/TLS 链路另补消费先于关闭票据。已扫描进程日志/快照泄露命中 0，不把 Maven 总输出当作逐实际凭据扫描证明。

实际最小命令：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ProtectedSimulationE2ETest,ActionUnknownConvergenceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=BootstrapIsolatedStartupTest#approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

首次 5 项均在创建临时 PostgreSQL 前报 Docker 环境错误，退出 1；`docker info` 证实 Linux Engine 管道不存在。`docker desktop start` 成功后 `docker info` 返回 29.4.3，原失败日志/XML 保存于 `incident/target/catalogue-first-environment-failure/`。重跑 5 项通过，0 失败/错误/跳过、退出 0，1m30s，10:15:29；完整普通 Worker 最小 1 项通过，退出 0，3m43s，10:19:52。日志分别为 `catalogue-minimum-retry-20261005.log`、`catalogue-bootstrap-minimum-20261005.log`，均位于 incident/target。

实际扩大命令：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=ActionReceiptTest,ActionOutboxCompatibilityTest,OutboxRecoveryTest,ActionUnknownConvergenceTest,ActionRecoveryRaceTest,ActionApprovalRaceTest,ActionSecretLeakTest,ActionServiceAuthenticationTest,ActionBindingV1Test,ActionEventWriterTest,ActionStateMachineTest,ProtectedSimulationRouteTest,ProtectedSimulationE2ETest,IncidentRemediationDispatcherTest,BootstrapIsolatedStartupTest,AlertIdempotencyTest,IncidentAggregationTest,AlertRecoveryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`incident/target/catalogue-regression-20261005.log`：54 项（MCP 6、Incident 38、Agent 3、Bootstrap 7），0 失败/错误/跳过，BUILD SUCCESS、退出 0，14m29s，10:34:56 +08:00。实际 Java runtime 21.0.12+8-LTS，编译 release 17；Testcontainers PostgreSQL/pgvector、Redis/RustFS，独立 Bootstrap/MCP JVM。本轮没有运行付费服务、用户开发数据库、真实 Broker、前端、浏览器或默认全仓门禁。

七个完整应用入口均通过：拒绝/授权截止/票据截止各 0 应用；普通和 TLS 链路各两个不同执行、各一次应用，关闭先于消费的执行不应用；真实 Worker 崩溃恢复 1 应用；B09 回调丢失/响应 EOF/双丢失三个执行各一次应用，可信迟到/重复/冲突处理保持。独立 MCP 的消费后实际死亡、正面回滚与证据不足两种超时、只读查询也有阶段快照。B10 部分身份/证据为替身，B11 Producer 为替身；这些不冒充真实人工 HTTP 或 Broker 验证。

执行 [归档脚本](../specs/002-approval-safe-simulation/scripts/collect-evidence.ps1)，Since/Command 从实际 start JSON 读取。失败日志与旧 XML 的两个守卫检查通过。归档 [manifest](../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/manifest.json) 含 12 个 B 场景清单、A 核心可靠性清单、18 XML、15 证明目录、实际环境/命令与源码/制品哈希；首轮 103 个制品哈希全部一致，后续保留审计/最小日志也归档并复核。HEAD 不足以标识 dirty 实现，manifest 明确 uncommitted，不虚构提交。

最新 [证据索引](../specs/002-approval-safe-simulation/evidence-index.md) 保留历史盘点，改以本轮新运行填齐统一清单，不补造旧运行缺失字段。已批准 Spec A/B 默认固定发布器，无需把真实 Broker、跨主机/外部日志和未来攻击变体增加为核心门槛。LAB-000 当前目录定义与 ACT-003 本地模拟验收收尾，Spec B 64/64；LAB-001、OBS-001、DEP-001 和剩余旧探针仍开放。只有测试/文档变更，生产代码/Schema/前端保持；隔离 JVM/容器退出，Docker Desktop 保持运行。无提交、推送、合并或部署。

最终文件保留检查：1686 个起始哈希，加 57 个被 Git 默认转义遗漏的中文路径；后者均与此前保存的哈希一致且 Git 差异为空。1730 个原文件不变、13 个本轮预期测试/文档编辑、0 丢失或意外变化，新增仅归档脚本。第一次枚举误报中文文件为新文件，另一次路径检查把 HTTP URL 误判为盘符；修正检查器后通过，没有删除或覆盖这些文件。文档相对链接、`git diff --check`、暂存为空、HEAD/分支/功能指针和无实施后钩子配置均核对通过。

## 2026-10-05：OBS-001 统一关联标识与 Trace 设计（待确认）

按用户要求保持 `feature/incident-platform` 和 HEAD `67624931f20812922568d3641a095adb291263ba`，读取AGENTS/项目上下文/TODO/架构/决策/测试约定/最新验收，检查status和tracked diff。起始35个tracked修改文件及大量untracked保留，1744个现存源码/文档/配置等文件建立SHA-256基线。最新B验收manifest白名单元数据确认54项PASS、0失败/错误/跳过；仅历史证据复核，不复跑且不代表OBS通过。

brainstorming按架构级设计，用户确认首版为现有安全模拟主链与本地采集。三个方案比较后形成推荐草案：分层稳定业务ID+统一HTTP requestId+白名单日志/短时OTel；单纯日志不足以验收Trace，持久父上下文/聊天/UI/部署扩张不纳入。现有correlationId在Alert、created target、诊断首PROCESSED成员、Action anchor之间不保证同值；新契约建议以incidentId汇总，旧事实/快照/摘要/Outbox原样，不静默改写。

执行仓库speckit-specify/plan/tasks流程，使用overrides中文模板，新增 `specs/005-incident-correlation-trace/` 的spec/plan/research/data-model/contracts/quickstart/checklist/tasks：14项FR、5项SC、27项全未实施。`.specify/feature.json` 从004切换005用于下游路径解析，不切Git分支；setup-plan返回的BRANCH是功能目录名，实际分支单独从Git核对。`.specify/extensions.yml` 不存在，无钩子。Spec Kit Phase 0仅派发一项只读集成研究，不派实施代理。

实际执行：`git branch --show-current`、`git rev-parse HEAD`、`git status --short`、tracked/cached diff、模板解析、`setup-plan.ps1 -Json`、`setup-tasks.ps1 -Json` 和 `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`；后者正确指向005且所需文档齐全。文档任务格式、FR/SC映射、相对链接、文件保留和最终Git检查记录在收尾段。

没有运行Maven/Java/Testcontainers/浏览器、启动或修改基础设施、连接开发数据库；quickstart新增类名与命令明确为未来计划，测试数0不称通过。未提交、推送、合并、部署或调用付费服务。技术方案及correlation兼容策略待用户确认；正式analyze及代码实施留到获批后，DEC/架构保持已有确认事实。
本轮静态收尾：PowerShell文档检查确认T001～T027唯一且连续，US1/US2/US3分别5/9/6项（共享/准备/收尾7项），14/14 FR与5/5 SC有任务映射，8份新Markdown的相对链接、尾随空白及机器绝对路径检查通过；`git diff --check`退出0。首次路径检查器将HTTPS中的s:/误当盘符而失败，增加字母前边界后复核通过，文档内容未因此改写。1744个起始文件中1742个hash不变，仅TODO/changelog为预期编辑，0丢失/意外变化；新增8份Spec文档。被Git忽略的功能指针单独核对为005，其有意更新不计入tracked/untracked文件哈希统计。最终分支/HEAD保持、暂存为空；生产代码、测试、Schema、前端、旧Spec与已接受DEC均未改。正式实施前仍需用户确认与实时analyze，本轮没有OBS运行测试结果。
## 2026-10-05：OBS-001 实施及本地Trace验收完成

用户明确“实施”确认Spec E，analyze 14 FR/5 SC覆盖19/19，无阻塞；DEC-028和27/27任务完成。统一服务端HTTP requestId与incidentId检索，保留Alert correlation、历史事实与业务意图/执行身份；复用OTel1.62.0单SDK owner，OBS默认关闭，主应用/独立MCP短时Span、认证traceparent及显式线程Context/MDC恢复落地。无Schema、页面或真实工具范围扩张。

实际命令 `$env:RAG_LIVE_TESTS='false'; .\mvnw.cmd '-Dspotless.apply.skip=true' test` 退出0，840项，0失败/错误、21跳过，结束于2026-10-05T12:49:20+08:00。首次默认门禁254项含4项既有网络夹具装配错误而停止；补Bean并修复scope外identify残留后，最小19项通过再重跑完整门禁。之前子Span属性继承、测试exporter子进程classpath与内部回调断言失败记录均保留。跳过为19项RAG外部探针、1项真实You.com、1项可选Dashboard JDBC，不算通过。

OBS四项真实双JVM通过：诊断安全56 Span/0应用、重启恢复37 Span/1应用、黄金主链68 Span/1应用、回执通道88 Span/3逻辑执行各1应用；实际父子/线程、冻结历史、稳定身份、UNKNOWN收敛与秘密扫描0命中均已断言。固定Producer验证Relay原event重投；本地HTTP503/延迟/队列压力及失败exporter真实数据库幂等验证辅助观测不改业务。环境JDK21.0.12+8/release17、Docker Desktop29.4.3、Testcontainers2.0.5，临时数据库与独立JVM。

[实施与作者审阅](../specs/005-incident-correlation-trace/implementation-report.md)、[复现与场景制品](../specs/005-incident-correlation-trace/quickstart.md)指向本轮 `bootstrap/target/obs-evaluation/354648e4-9f73-45ca-ba0a-bd23eae2fd00/manifest.json`、源码/保留审计、XML与SHA256；原1752文件无丢失，54个有意编辑、1698个不变，新增16个功能源代码/测试/报告文件。上游Trace/Agent/MCP基础和署名保留，本次Incident主链观测属于本项目新增贡献。

结论仅限本地安全模拟与测试classpath实际SpanData，不代表OTLP后端存储、生产/性能/跨主机、真实模型/工具/Broker验收。本轮无前端变更；保留全部既有工作区修改，分支/HEAD/空暂存保持，未提交、推送、合并、部署、调用付费服务或操作真实基础设施/开发数据库。

## 2026-10-05：OBS作者走读与LAB-001设计草案

用户要求继续此前建议。通过develop-project/brainstorming/ponytail核对当前代码、LAB-000目录及Spec E验收记录：聚合后用incidentId汇总，cancel不证明停止，跨重启凭原Execution/调度ID及数据库事实证明单次应用。此次为静态走读，不重新声称执行Java门禁或用户已掌握实现。

使用仓库speckit-specify/plan/tasks形成 [Spec F](../specs/006-local-fault-replay/spec.md) 待确认方案，12 FR、5 SC、18任务均未实施。比较纯文档、本地入口、管理员调度页面后推荐本地PowerShell入口及Markdown/JSON证据；六类复用告警/Outbox检查和四个OBS双JVM检查。保留真实PG、实际进程、SQL fixture与Producer替身层级，失败/跳过/未完成/来源错误均不能通过。新入口命令只是实施后的契约，当前没有运行器代码。

实际运行setup-plan.ps1 -Json、setup-tasks.ps1 -Json、check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks，解析到Spec F及中文覆盖模板；脚本返回的BRANCH是功能目录标签，实际分支仍feature/incident-platform，HEAD保持67624931f20812922568d3641a095adb291263ba。extensions.yml不存在，无前后hooks。功能指针有意切换，OBS历史产物保持。

静态检查8份新增文档的相对链接、18项任务格式及4个OBS方法映射均通过；12 FR/5 SC均有任务覆盖，git diff --check通过。1762个起始源码/文档文件的内容摘要用于保留核对；本轮仅有意编辑PROJECT_CONTEXT/TODO/changelog，另切换功能指针与新增Spec文档。没有Java/容器/数据库/故障运行，也没有提交、推送、合并、部署或付费调用。用户确认当前方案后再analyze/implement，待确认方案未追加为已确认DEC或运行架构。


## LAB-001本地实验入口与验收（2026-10-05）

完成Spec F LAB-001 18/18：固定六场景命令入口、来源绑定的安全事实/Trace报告、SHA256及只读Verify；无生产代码/Schema/UI变更，根POM仅新增LAB-only测试报告profile。统一运行2fba02a7-4681-409b-9030-10212de0b51f六场景7项、零失败/错误/跳过，249实际Span、25个实际秘密扫描零命中；两个PowerShell版本各30项契约及未启用LAB的5项兼容性通过。最终ALERT轻量复跑独立归档9a2619ae-5b12-4249-a45d-26e21adc1b1f，Verify均通过。真实失败目录保留；统一运行后仅契约脚本退出码修复，原源码摘要不重写。实际命令、作者审阅与限制见 [Spec F quickstart](../specs/006-local-fault-replay/quickstart.md) 和实施报告。保留原修改、分支/HEAD/空暂存，无提交、推送、合并、部署、付费服务、开发库或真实基础设施操作；不代表生产/真实性能/真实Broker/OTLP后端验收。


## TST-002分类设计（2026-10-05）

开始TST-002收尾设计，读取现状/最新验收，核对分支feature/incident-platform、HEAD 67624931f20812922568d3641a095adb291263ba、空暂存与原dirty差异。用户选择按行为分类；形成Spec G的9 FR/4 SC/20待实施任务和11类覆盖目录。仅设计与静态检查，未运行Java/Docker/外部联调，不把历史19跳过或840门禁当成本轮结果。1776文件建立保留基线，功能指针有意切007；未修改生产代码/测试/Schema/依赖/正式架构或DEC，无提交/推送/合并/部署/付费/开发库操作。

## 2026-10-05：TST-002分类隔离实施完成

用户授权检查遗漏并按推荐直接执行；analyze核对9 FR/4 SC与20任务、13/13覆盖无阻塞，Spec G 20/20完成。新增4测试类、扩展2类，共24项确定性检查，保留旧手工探针；You.com/MinerU显式许可门负例2项因RAG=false跳过且无Spring启动，不算外部通过。六组聚焦40通过；真实失败分别保留，MyBatis夹具下划线映射及局部inline MockMaker修复，不改生产/全局配置。

实际执行 `./mvnw.cmd -Dspotless.apply.skip=true -pl rag -am test`：392项/0失败/0错误/19跳过；`./mvnw.cmd -Dspotless.apply.skip=true test`：864项/0失败/0错误/21跳过、退出0，843实际通过，2026-10-05T23:49:05+08:00结束。JVM 21.0.12、编译17，RAG=false，YDC key临时移除并finally恢复，可选Dashboard JDBC未开放；隔离本地HTTP、固定SDK/Mapper/模型与临时Testcontainers资源，不操作开发库或真实基础设施。

证据根 `bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/` 保存每次实际命令、独立新XML、失败及成功，full-regression-bc26d580-6143-4b7e-811a-f1393ad8823f为本轮全仓。1159源码摘要用于运行冻结核对，1784起始文件用于保留检查。详细调用路径、跳过名单、真实失败、3自检题与未验收范围见 [实施报告](../specs/007-rag-test-isolation/implementation-report.md)。生产代码、Schema、UI、POM、依赖及上游许可未改，原dirty/未跟踪文件保留；无提交、推送、合并、部署或付费调用。priority注释与实际逆序比较差异留为TBD，未静默改语义。

## 2026-10-06：DEP-001本地编排与CI设计

用户“继续”启动DEP-001审查设计，选择隔离本地编排＋CI＋说明。读取上下文、TODO、架构/决策/笔记、测试/环境及最新验收，核对feature/incident-platform、HEAD 67624931f20812922568d3641a095adb291263ba、空暂存及dirty差异，1788文件建立保留摘要。现有Testcontainers已编排四临时依赖和主/MCP双JVM；仓库无CI。Brainstorming比较CI-only、复用既有编排及常驻Compose，推荐复用；用户确认首版范围，完整设计待审阅。

使用仓库speckit-specify/plan/tasks及中文overrides形成 `specs/008-local-delivery-gates/` 草案，12 FR、5 SC、20任务全部未实施。setup-plan/tasks及模板解析实际执行，extensions.yml不存在，无钩子；功能指针切008，不切Git分支。plan Phase 0派发一项只读研究，说明Wrapper跨平台、外部门控、最小CI权限和白名单摘要。原始日志/XML、临时TLS/账号/classpath文件不得作为CI制品；Linux/托管CI尚未实际运行。

本轮只有方案与静态检查，没有新Java/容器/数据库/前端/托管CI验收，不复用历史864项冒充新入口通过。生产代码、Schema、POM、配置及原LAB/OBS/TST证据保持，正式架构/DEC未变；无提交、推送、合并、部署、付费服务或真实基础设施/开发库操作。旧容器说明生产宣称及数据库发布版本口径差异在research标明，不静默改写历史。

## 2026-10-06：DEP-001实施与真实门禁进行中

用户明确批准Spec H完整方案，17/17 FR/SC、20任务正式analyze无阻塞，清单16/16只读；实施固定PowerShell门禁、CI同一入口和安全白名单摘要、部署准备说明。初期缺入口/XML Name遮蔽/provider缓存/Docker未就绪记录保留；Smoke1项及前端四阶段通过，后续契约87项和真实三恢复检查通过。

完整入口首先暴露JUnit DisplayName病例类名与规范suite类名不同；仅停止本轮自有Maven进程树，部分218项不计全仓。重跑在共享Incident证明写入时出现3错误、261项部分结果/退出1，补固定DELIVERY场景及本轮源码属性，并在Bootstrap/Incident两处测试helper保留受控delivery/fault-lab路径。无业务/Schema/POM/依赖/配置修改，不禁用证明、原报告不改写。最终Backend/Frontend以新UUID和最终源码进行中，未声称完成；详细实际命令/失败见Spec H quickstart/实施报告。

## 2026-10-06：DEP-001本地验收收尾

Spec H 20/20，前段进行中由本段更新。最终Backend `5576b210-ed88-4ef9-b841-e0a755d8f627` PASS/门禁子进程0，190份新XML共864项：843通过、0失败、0错误、21受控跳过；12必需类零跳过、13手工类逐类核对，不引用历史计数。最终Frontend `42f23a1f-a861-4849-a674-1e0753b5836d` PASS/0，四阶段均0；契约87项/0，Smoke1项/0及恢复最小3项/0另有独立新记录。

Backend14:54:20～15:25:46、Frontend14:59:01～15:03:44（Asia/Shanghai），1339文件sourceDigest均 `D9FC783F358E7595172452A91281ED49F6A0074E50993737E788E25F7608D3F6`。本轮Backend534/Frontend11制品哈希及安全摘要字段独立复核；五份proof-origin、四个OBS场景和资源退出已核对。1745份哈希基线文件存在，仅18个预期文件延续修改；57份初始未入哈希的中文名受跟踪资料按初始status/当前diff确认未改，未补造旧摘要。AGENTS/许可证/需求清单、分支/HEAD/空暂存保持。详细命令、失败、跳过及作者自检见 [Spec H quickstart](../specs/008-local-delivery-gates/quickstart.md) 和 [实施报告](../specs/008-local-delivery-gates/implementation-report.md)。

新入口、两个测试helper及文档为此次贡献，无生产代码/Schema/POM/配置/依赖改动。CI只有定义/官方SHA核实/静态检查；Linux、Node22、Java17运行、托管CI、浏览器、生产/跨主机和真实外部服务均未验收。无提交、推送、合并、部署、远程CI触发、付费、真实基础设施/开发库或全局清理；全仓成功后只收尾文档，未机械重跑。

保留1745起始文件及原dirty/未跟踪内容，分支/HEAD/空暂存保持。CI仅本地定义/静态审查，官方Action SHA已读核实；Linux、Node22、Java17执行、托管CI/部署及浏览器本轮未验收，无提交/推送/合并/部署/远程CI/付费/开发库/真实基础设施或全局容器清理。

## 2026-10-06：项目整体本地完成度核对

用户指示执行整体核对及过时状态修正。当前Spec A～H任务分别50/64/36/31/27/18/20/20，合计266；175 FR/57 SC按实现边界与现行检查对应。读取代码/Schema与声明的真实性层级，核对实际制品而非只凭复选框；SPC-001及M0在本地隔离、受约束Runbook、人工审批与安全模拟范围完成，核心已完成行迁入TODO完成表。

实际执行`python bootstrap/target/project-closeout/0d0ee477-ca59-4e8a-a92b-17617a3eee2b/verify-evidence.py`退出0：B目录113制品/54项、D最终26制品/22浏览器场景、H Backend534/Frontend11制品哈希均匹配；H 1339源码与当前内容一致，190 XML重算864项（843通过/21跳过、零失败错误）。C v2 34例既有签署和Hit@3=1.0/leakCount=0记录保留，未重签或扩大质量结论。

实际执行`pwsh -NoProfile -File specs/006-local-fault-replay/scripts/run-lab.ps1 -Verify bootstrap/target/fault-lab/2fba02a7-4681-409b-9030-10212de0b51f`，VERIFIED/退出0；只读核验六场景归档，没有重新启动故障、Maven或业务。修正Context/Architecture/Notes当前摘要、TODO状态及A quickstart遗漏SC-011，保留分日历史/全部DEC/需求清单。补100任务与20/16工作池的实际并发口径，不宣称100同时连接或性能。

本轮仅新增 [项目核对报告](development/local-acceptance-review.md) 和六份既有说明文档修正。1807份起始文件及原dirty/未跟踪内容保留，分支`feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba`、空暂存保持；相对引用、需求索引完整性、基线白名单及`git diff --check`核对通过。一次性复核代码/输出只留`bootstrap/target/project-closeout/0d0ee477-ca59-4e8a-a92b-17617a3eee2b/`，不形成第二套门禁或Spec体系。

作者独立演示、可交付版本整理、Linux/Node22/Java17运行、托管CI及生产/外部范围仍未完成或未验证，priority遗留TBD保留。本次无业务/测试/Schema/POM/配置/CI改动，没有新Java/前端/浏览器运行，也未提交、推送、合并、部署、触发远程CI、调用付费、操作真实基础设施/开发库或全局清理。详细要求映射、命令、证据来源、限制与三道自检题见核对报告。

- 本次报告最终核对脚本退出码0：232条要求索引、44个报告测试类和68个相对链接通过。首轮临时脚本的分段AssertionError修正经过及未独立捕获退出码的限制已记入报告，没有修改规格要求或业务代码。
