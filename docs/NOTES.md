# IncidentPilot 开发笔记

本文保存可复用的调试经验、踩坑记录和临时技术说明。它不是需求或架构决策的权威来源；
需要长期约束的结论应提升到测试、Schema、`DECISIONS.md` 或 `ARCHITECTURE.md`。

## 2026-10-19：源码包验收与评测报告的隐含Git依赖

真实无GitBackend暴露两类离线评测reporter仍强制读取Git：Runbook在写报告时出现2个错误，Maven停在rag，不能仅因来源门禁和Frontend已通过就推定Backend可运行；Diagnosis存在同类依赖。门禁下使用已校验的`lab.runId/scenarioId/sourceDigest`并显式置空Git字段，普通非门禁仍读取真实Git；不得捕获异常后填假HEAD或跳过评测。修复及实际验证状态见 [本轮记录](../specs/013-release-candidate/candidate-backend-report.md)。

评测自身UUID与门禁runId属于不同身份；模块`target/*-eval/`产物不自动进入门禁`artifacts.json`，须另取当次manifest/summary/cases/报告的摘要并关联来源。原失败必须保留，新的候选及运行不能回填旧证据。辅助脚本也须使用自有输出根，Maven的`-D`前缀不能随布尔值一并转为小写。

## 2026-10-18：接手包与验证器自身的边界

- Git默认会转义中文路径。逐文件基线须用UTF-8/NUL读取，不能把文本行中的带引号八进制路径直接交给Get-FileHash。本轮首采57项无效已明确拒用，保留原记录后重采1903项有效摘要。
- Windows的EncodedCommand仍受进程命令行长度限制。本轮将整份指南代码塞入参数达到44,224字符，spawn返回ENAMETOOLONG；改用忽略证据目录内脚本文件验证，9段语法/公开固定HMAC签名通过，不修改指南去迁就核验器，也不执行HTTP。
- 源码ZIP没有`.git`，嵌套解包目录却可能继承外层HEAD。原门禁在CheckOnly前读HEAD，不能据嵌套通过推断真正独立ZIP可运行All。通过子进程Git发现上限区分此条件，保留失败形态，不初始化假Git历史。Linux解包还需关注mvnw执行位；文字修补不等于Linux验收。见 [接手手册](../specs/013-release-candidate/second-machine-handoff.md)。
- `Get-Command git -CommandType Application`可能返回多个应用。需要单个可执行文件的ProcessStartInfo须按实际命令解析规则明确选择单项，不能把`.Source`数组拼接为文件名。本轮辅助器在进程启动前因此收到Win32 2，保留失败与去值诊断后独立续跑；不是Git查询失败或门禁运行结果。续跑实际确认无父Git时discovery128、CheckOnly1/FAIL且工具与阶段均0。

## 2026-10-18：已验证制品复用与完整静态覆盖

- 同一冻结源码及已核验构建可在新实例复用，无需反复重建：先绑定manifest、构建目录与原All，在同Name生命周期锁内调用原`Publish-DemoArtifacts`，逐项比较JAR/dist并由原函数生成制品标记。库模式不会自动取得CLI锁，不能省略手动锁；明确记录`rebuilt=false`，不能称新CLI Build或新All。
- 完整dist HTTP核验应按文件清单覆盖，不能只取JS/CSS后缀。本轮117文件包括favicon.svg和PDF worker.mjs；index由/login核对，其余116逐项比字节摘要。文件读取通过不等于浏览器/PDF功能验收，也不能解释旧静态异常根因。
- 持久链路同时核对HTTP结果和只读SQL不变量，重启前后比较稳定快照，排除重试/claim等可变技术字段但保留业务身份/状态/摘要。Outbox relay关闭时PENDING是本机配置事实，不代表Broker投递成功。见 [阶段12记录](../specs/013-release-candidate/persistent-candidate-runtime-report.md)。

## 2026-10-18：固定候选构建与中断恢复

- Maven命令成功与辅助产物核验分开记录。项目没有配置`Implementation-Version`时，用JAR内`pom.properties`核对版本/坐标、manifest及Boot类核对入口；不能因辅助脚本错误否定已完成构建，也不能将未启动的JAR声称为运行通过。reactor成功行使用不跨行的空白匹配。
- pwsh7.6的`ConvertFrom-Json`会自动识别日期；时间来源/精度核验用`-DateKind String`保留原值。辅助脚本按真实`gateRunId`与状态/候选/摘要绑定，不猜字段；StrictMode下读取可选字段前应检查或使用字典索引。
- PowerShell的XML适配器可能让元素的`name`属性覆盖`.Name`取值；用`LocalName`判定节点种类、`GetAttribute('name')`读取声明的类身份。独立审计曾因此误报结构错误，修正辅助读取后复核同批产物，不改XML或重跑已通过的All。
- 点入带验证参数的库脚本还会影响调用作用域中的同名变量；辅助循环不要复用`Name`等库参数名。收尾脚本曾因此拒绝文件名`candidate.json`，改为专属循环变量并保留首轮记录，不能误改演示实例命名规则。
- 中断后先看自有进程、实际新XML与终态摘要；进程已退出而run仍RUNNING不能改写为PASS。保留部分目录，新UUID补跑未完成门禁，已核对的package/合同不重跑。Docker Desktop启动不等于Engine已就绪；恢复后先比原容器/卷基线，不做全局清理。
- 源码ZIP无`.git`时，嵌套运行可能读到外层Git HEAD；它仅为来源基线。完整候选manifest与门禁受检源码子集分别核对，并绑定本轮run及制品。详见 [固定候选完整验收](../specs/013-release-candidate/full-candidate-acceptance-report.md)。

## 2026-10-17：历史扫描范围与SHA影响口径

- 授权净化使用新单分支副本，工具显式禁网络fetch及新replace refs；最终只导出白名单bundle，再在空库恢复并扫描所有存储对象，防止中间库的旧对象误带出。实测805映射/4merge、804SHA变化、30签名剥离。历史tip不包含尚未提交新增实现，源码仍需按固定manifest核对。见 [实施记录](../specs/013-release-candidate/history-sanitization-report.md)。
- `HEAD`对象闭包不等于全部本地refs；工具checkpoint也可能直接指向tree。本轮两条tree refs比HEAD额外带来455个对象，应明确其不属于分发白名单。
- 直接含疑似凭据的提交快照数与改写SHA数量不同：本轮分别为184和内容替换后代闭包804。后者是执行前估计，实际以净化副本的commit-map、签名处理和元数据核对为准。
- 固定源码ZIP覆盖历史checkout并不保证路径集合一致；本轮HEAD另有6个被候选排除的工具/IDE文件。只在隔离副本按完整manifest处理额外路径，不能因此删原工作区文件。详见 [历史移交审查](../specs/013-release-candidate/history-handoff-review.md)。

## 2026-10-17：固定候选复现与验收器的采集边界

- 后续受控Mounts已证实旧模板的`/logs`匿名卷机制，新模板显式使用第五命名卷并通过三轮依赖重建/全局集合/日志标记留存；不倒推旧`e98d…`卷的历史唯一归属。Redis缓存镜像的`Config.Volumes`为JSON `null`，取证器应视为零声明路径，仍检查实际Mounts；不要把PowerShell `@($null.Keys)`误当作有效路径。辅助函数还应避开`cli`等系统别名。见 [修复与失败记录](../specs/013-release-candidate/storage-volume-fix-report.md)。

- 同一保留实例的服务进程首次/热态静态读取各115项成功，不能倒推旧泛型失败已修复；真实浏览器结果和HTTP读取另行留证。563×506视口实际侧栏滚动从0到768，退出后事件路由回到登录；本轮未下载Markdown，不借用历史导出证据。见 [后续记录](../specs/013-release-candidate/browser-followup-report.md)。
- Docker命名卷仍为4不代表卷集合无新增。镜像自身可声明匿名卷：本轮RustFS声明`/data`、`/logs`，Compose只显式挂载`/data`；停止后原80卷保留但新增匿名卷1。创建时段和声明只能支持来源推断，缺少Mounts/历史事件时归属仍TBD；保留严格检查FAIL，不删除卷或把新增差异藏在“原资源未变”中。
- 点入PowerShell库脚本可能覆盖同名参数；辅助封装应先保存请求动作，并对返回READY/STOPPED/VERIFIED作对应断言，避免本想Start却只执行Status。此项修正限本轮辅助脚本。

- 本机新源码/新配置/新卷可证明隔离资源复现；Maven/npm及镜像缓存复用必须单列，不能称第二机器或全空白机器验收。Build成功不等于新的Backend/All。
- Windows外层PowerShell捕获Start子进程输出时可能等待服务后代继承的管道，不能仅凭外层未返回认定启动失败。应同时核对锁、直接子PID、配置标识、端点和READY；辅助验收器可用隐藏子进程、文件重定向并只等待直接PID，不修改产品脚本或随意杀进程。
- 首轮仅记录泛型HTTP_STATIC失败不足以判断根因，必须保留失败与TBD。加入安全错误类型/步骤/资源序号后，成功复核与3轮只读一致不能倒推首轮已修复。详见 [本机隔离复现](../specs/013-release-candidate/reproduction-report.md)。

## 2026-10-03：诊断 HTTP 与 PowerShell 边界

- Spring Boot 4 的 HTTP JSON 使用 Jackson 3；业务内部的 Jackson 2 `JsonNode` 不能直接放进响应 DTO，
  否则可能被当作普通 POJO 输出节点属性。诊断边界通过 `DiagnosisContracts.wire` 转为 Map/List，
  用真实双 JVM GET 和 MockMvc JSON 路径检查可见快照/原文结构；内部 canonical/hash 仍使用冻结树。
- PowerShell 的 Maven `-D` 属性应逐个用单引号包裹，特别是 `-Dspotless.apply.skip=true`；
  未加引号的点号属性曾被拆分为 Maven 生命周期参数，不能把这种失败当业务测试失败。
- 同名 Spring 命令行属性不会简单取最后值，两个布尔开关可能绑定为 `false,true` 并阻断启动；
  隔离测试在启用告警 Worker 时先移除既有 false 参数。
- 诊断不保留原始 summary/labels/payload；来源标题只用于候选匹配，不能证明测量值或确认根因。
  常见密钥赋值、Bearer 和带空格的引号值须在冻结快照前脱敏。

## 2026-09-21：仓库与构建

- 根 Maven 工程要求 Java 17，并提供 Maven Wrapper。
- 根 POM 把 `spotless:apply` 绑定到编译阶段。只读测试或构建时应使用
  `-Dspotless.apply.skip=true`，避免检查命令意外改写源码。
- Windows PowerShell 中把 `*/pom.xml` 直接传给 `rg` 可能触发非法路径错误。可使用
  `rg -g pom.xml <pattern> .`，或先用 `Get-ChildItem` 枚举文件。
- 当前指导、Spec Kit 和本批文档仍是未跟踪文件，执行清理、切换分支或创建工作树时必须保留。

## 2026-09-21：运行端口和入口

- 主应用默认端口是 9090，Servlet context path 是 `/api/ragent`。
- MCP 服务默认只绑定 `127.0.0.1:9099`，Streamable HTTP 端点为 `/mcp`。
- 前端开发服务器默认运行在 5173，只把 `/api` 代理到主应用 9090；前端当前不直接调用 9099。

## 2026-10-01：本地验证环境与脚本换行

- Windows 工作区中 `bit-schema.sql` 可同时包含 CRLF 与 LF；检查建表语句时先归一化换行，再匹配语义。`BitSchemaScriptTest` 曾把存在的 `t_product` 误报为缺失，修正测试后 MCP 全模块测试通过。
- Testcontainers 的 Docker daemon 失败发生在业务断言之前，应报告为环境受阻。2026-10-01 Docker Desktop 进程虽能启动，Linux Engine 未就绪，当前 Windows 权限无法启动 `com.docker.service`；不能改用共享数据库或把 `test-compile` 当成集成测试通过。
- 运行新的故障注入前先用 `docker info` 核对本地 Engine，保存该次运行的 Surefire XML 结果；同名测试重跑会覆盖前次 XML，历史通过结论须在变更记录中注明核对时间。
- 随后 Docker Engine 29.4.3 恢复，Testcontainers 的 Npipe 连接与隔离 PostgreSQL 实际启动均通过；`com.docker.service` 显示停止不等于当前 Linux Engine 不可用，应以 `docker info` 和容器测试为准。
- 跨进程超时验收要同时控制执行写入和回滚证明查询。仅给写入设置语句超时仍可能使证据查询被数据库锁无界阻塞；证明查询也需独立短时限，超时或不可读时保持 `UNKNOWN/TIMEOUT_UNPROVEN`。
- 完整仓库 `mvn test` 在 RAG 模块出现默认 `ragent.engine.type=agent` 下缺少 `DashboardService` 的 Spring 上下文失败；多个同上下文错误是失败阈值后续表现。不要仅靠修改配置启动可能调用外部模型的旧集成测试来宣称全仓绿色；应先确认隔离替身和测试边界。旧 DEBUG 日志断言要求手机号明文，与现行安全边界冲突，已改为 INFO/DEBUG 都不记录参数值。
- 旧 RAG `@SpringBootTest` 联调探针会调用模型、Milvus 或开发数据库；默认测试以 `RAG_LIVE_TESTS` 环境门隔离，显式启用前须逐项核对外部资源和费用。Agent Trace 的批次 span 使用真实 OpenTelemetry 时钟，等待确认场景只需证明其耗时不包含模拟的五分钟等待，恰好 `0 ms` 的断言会因执行开销偶发失败。
- 精简真实 HTTP 测试若复用 MCP Server 的测试类路径，Spring Boot 自动配置可能尝试连接默认 Redis，且模块 `application.yaml` 的端口会覆盖 `SpringApplicationBuilder.properties` 的低优先级值。测试须排除不需要的外部存储自动配置，并以命令行参数指定回环地址和随机端口；Sa-Token 的 `createLoginSession` 可在非请求线程创建测试会话，再通过 HTTP 头传给 Servlet 请求。
- 2026-10-03 完整 Bootstrap/MCP 双进程验收：`bootstrap` 的测试依赖会把独立 `mcp-server` 放入测试类路径；启动各自 JVM 时须过滤另一应用的模块 classes，避免两套 `TicketMapper` 同名 Bean 与 `application.yaml` 串入。`incident.sources: {}` 在 Spring Boot 4 被绑定为空字符串而不是空 Map，应省略空占位并使用 `IncidentProperties` 默认值。没有 Broker 时，完整主应用的上游 MQ 消费者仍会尝试启动；仅在 `isolated` 测试 Profile 关闭 MQ 自动装配并注入调用即报错的测试发布器，配合关闭 Worker，保证启动验收不访问共享 Broker。完整启动还发现双构造器的 `IncidentRemediationDispatcher` 需要显式选择注入构造器。
- 完整主应用默认 Servlet 前缀为 `/api/ragent`。若 MCP 回调根 URL 只给 `http://127.0.0.1:<port>`，票据消费请求会落到错误路径，Action 已派发却停在 `DISPATCHING`；隔离正向测试暴露了此问题。回调根 URL 应包含 `/api/ragent`，客户端仅接受该前缀或无路径根；隔离测试保留 Incident/Outbox Worker 关闭，但在正向用例启用 Action Worker。根 POM 的 `spotless:check` 在 Windows 混合 LF/CRLF 工作区会报已有文件换行差异，避免为了本次验收对全模块运行 `spotless:apply`。

## 2026-09-21：数据库边界

- 主应用默认使用 PostgreSQL 数据库 `ragent`；全量 Schema 和升级脚本位于
  `resources/database/`。
- MCP 电商演示工具使用独立数据库 `ragent_bit`。
- `mcp-server/src/main/resources/db/bit-schema.sql` 明确说明该库不做增量迁移，结构版本不匹配时
  需要重建。因此它当前不适合作为需要长期保留的 Incident、审批或审计主库。
- Incident 主数据确定放在主库 `ragent`，但由 `incident` 模块独占表、Mapper 和事务。

### 未来拆库高风险操作

代码评审发现以下做法时，应立即指出其拆库影响；没有新决策不得引入：

- 其他模块直接使用 Incident Mapper、DO 或表名。
- SQL 跨 Incident、用户、Agent、RAG 或知识库领域 JOIN。
- Incident 表对其他领域表建立外键，或反向被其他领域外键引用。
- 一个 `@Transactional` 流程同时修改 Incident 与其他领域表。
- 跨领域数据库触发器、存储过程或无法替换的视图。
- 共享数据库序列、临时表或依赖默认 Schema 的隐式行为。
- 将 Incident 主数据、审批记录、执行回执或时间线写入 `ragent_bit`。
- MCP、Agent 或 RAG 绕过 `incident` 应用服务直接更新 Incident 状态。
- 没有幂等、校验和回切方案的缓存、数据复制或双写。

允许的做法是：Incident 内部外键和本地事务、跨领域稳定 ID、必要历史快照、应用服务调用，
以及后续经决策确认的可靠领域事件。

## 2026-09-21：认证与 MCP 安全

- 主应用通过 Sa-Token 进行登录检查；公开告警 Webhook 将来不能直接依赖浏览器登录态，具体认证方式为 `TBD`。
- MCP 请求中的 `_meta` 由调用方提供。现有代码注释已明确：它可用于当前本机演示的数据范围传递，
  不能单独用于身份认证或授权决策。
- 现有 Agent 已支持写工具确认卡，但 Incident Action 所需的审批持久化、一次性执行授权、幂等回执
  和超时后的未知状态仍未实现。

## 2026-09-21：配置安全

- 当前运行配置包含便于本地开发的默认账户或密钥示例，不能作为生产安全基线。
- 文档、日志、Spec 和提交中不得新增真实数据库密码、模型密钥、对象存储凭据或追踪服务密钥。
- 在做公开部署前，需要单独检查配置外置、默认凭据和日志内容；当前状态不能据此宣称生产就绪。

## 隔离基线与待补充边界（2026-10-06更新）

- 已验证：完整主应用/MCP在Testcontainers临时四依赖下启动，角色/Schema初始化与顺序见 `docs/development/delivery.md`；常驻开发/生产启动仍为TBD。
- 已验证：Spec H新入口完整Java 864项（843通过/21受控跳过，零失败/错误）；早期RAG及门禁失败保留历史含义，不因后续通过删除。
- 已验证：Spec H最终前端安装、incident-view、lint、build均退出0；Node22/Linux/浏览器本次未复验。

## 2026-10-04：Incident界面恢复与证据核对

Codex闪退后日志只有transforming不代表build成功；检查进程、输出与制品后，对退出码丢失的最后前端门禁重新运行。真实隔离服务器证据已由STOP归档时不重复执行批准。列表筛选切换需取消当前分页请求，而不只取消初始请求；写后刷新应排队等待当前读，不能忙碌时直接丢弃。诊断证据刷新不能顺带清掉命令冲突提示，否则STALE_SNAPSHOT虽被拒绝，人员却看不到原因。

未知结果演示种子也必须满足真实Schema授权时间约束。展示fixture不代表真实执行UNKNOWN；真实断连实验应在服务端已持久化后关闭响应并以相同sourceRequestId重发，再用数据库/可信回执证明没有新增Action或副作用。

## 2026-10-05：隔离验收制品与环境恢复

Docker Desktop 窗口存在过不代表当前 Engine 可用；本轮 Testcontainers 报找不到 Linux Engine 管道，先保留错误 XML/日志，再 `docker desktop start`，以 `docker info` 成功为依据重跑。不要改连开发数据库。新测试把进程日志存进每次独立目录，避免复跑覆盖历史日志；同一执行的替代票和消费先于关闭的票也要加入实际秘密扫描。

统一清单必须验证选定 XML 属于本轮、无失败/错误/跳过，并关联实际快照。归档脚本对失败 Maven 日志和未来 Since 下的旧 XML 均正确拒绝；目录或截图不能替代成功断言/服务器事实。Spec Kit 在本任务使用 `-PathsOnly` 做只读路径解析、再显式核对产物，保留 Spec D 功能指针；脚本返回的功能名不是实际 Git 分支，实际分支从 Git 读取。


## LAB-001本地实验入口与验收（2026-10-05）

Windows PowerShell 5.1读取JSON数组后应显式return对象，避免管道形成嵌套数组；脚本以UTF-8 BOM兼容中文。Surefire 3.5.6 reportsDirectory无CLI user property，LAB以lab.reportRoot激活test-only profile隔离XML，默认不启用。BUILD SUCCESS不足以证明实验通过，须核对本轮来源、精确检查数、零跳过、事实/Trace。模拟Docker负例必须恢复global:LASTEXITCODE；否则断言全过但外层退出1。Worker在真实MCP前崩溃时不应要求两次MCP dispatch Span，应核对dispatch/recover阶段的新Trace及稳定业务ID/单次应用。失败归档保留，不回写PASS。


## TST-002测试夹具与未确认排序差异（2026-10-05）

手工初始化MyBatis TableInfo时，Configuration应明确mapUnderscoreToCamelCase=true，与Spring下划线列名一致；否则条件值正确但列名断言失败。仓库rag测试默认mock-maker-subclass，两个局部Factory静态mock可用withSettings().mockMaker(MockMakers.INLINE)并try-with-resources关闭；不改全局Maker或依赖。

QueryTermMappingDO的priority注释称小值优先，但QueryTermMappingService.loadMappings当前逆序比较。优先级期望语义TBD，本轮不改生产行为；无歧义缓存/映射检查通过不表示此差异解决。后续若修正排序需单独确认优先级及重叠词规则并补失败用例。

## 2026-10-06：PowerShell门禁与证据边界

XML节点的Name可能被同名name属性遮蔽，testsuite类型检查使用LocalName；属性用GetAttribute，病例节点用SelectNodes。入口XML禁DTD及Resolver，类/方法计数与实际节点交叉核对，不能仅信任tests总数或Maven退出0。原lab.proofRoot只允许fault-lab，与delivery设计冲突，按固定测试开关增加delivery目录白名单并按场景分离，不取消路径限制。

Testcontainers的用户properties可能只是自动保存的本地provider缓存；严格允许Npipe/Unix/Desktop类名，不把任何其他连接/复用属性一起放行，也不改用户全局文件。Docker Desktop存在不等于Linux Engine就绪；先保留BLOCKED，实际info成功后再跑隔离Smoke，不改连开发库。

执行外层PowerShell命令时，宿主退出1可能只表示内部native命令非零，不能把它直接当门禁子进程退出。需在调用后立即捕获LASTEXITCODE；早期未记录的结果保持未记录，不补造。摘要必须新建白名单，不直接序列化含Exception/XML/环境的对象；生成失效非零且删除本轮自有上传目标，不能回退raw。82项契约及敏感fixture/CI负例见Spec H。

完整入口的lab.*复用必须全仓检索使用点，不能只核对Bootstrap helper。Incident的labProof要求scenario/sourceDigest/受控fault-lab路径，OBS也会生成origin并读取两属性；缺失后会在业务断言后写证明时出错。DELIVERY模式传固定场景及实际源码摘要，并在两处测试helper按固定开关选delivery目录，不能为了通过关闭证明。JUnit的testcase.classname可能是DisplayName，报告身份用testsuite.name；显示名不发布。真实失败及最小3项修复回归见Spec H。

## 2026-10-06：项目状态与证据核对

分日追加记录保留历史含义，PROJECT_CONTEXT/TODO的当前摘要应同步收尾，不能让已完成的LAB、威胁模型或SPC-001长期显示待办。Spec任务勾选、代码存在与实际验收是不同证据；项目级核对需同时读取当前Schema、源摘要、XML/业务事实、签署和浏览器服务器记录。本次B/D/H哈希与F只读Verify均通过，范围及命令见 [本地完成度核对](development/local-acceptance-review.md)。

Git文件清点使用未转义路径（如Python读取`git ls-files -z`的UTF-8字节）；不能把默认引号转义的中文路径误认成缺失/新增。源代码哈希结束核对能支持使用刚通过的门禁，文档收尾不机械重跑全仓；原始制品依然只留本地。

100个提交/处理任务不等于100个同时活跃请求：当前幂等/聚合测试工作池为20/16线程。报告应保留这个实际宽度，不把精确事实计数提升为性能、容量或所有调度穷举。Linux、Node22、Java17运行、真实模型和托管CI/生产范围继续分列。

## 2026-10-10：后台侧栏滚动

- 页面 `body` 使用 `overflow: hidden` 时，固定视口侧栏中的导航需要独立滚动。Flex 列中的菜单仅设置 `flex-1` 会受默认 `min-height: auto` 限制；增加 `min-h-0 overflow-y-auto`，让菜单在品牌区和底部按钮之间的剩余高度内滚动。
- 本次只修改 `frontend/src/pages/admin/AdminLayout.tsx` 的导航类名。当前本机浏览器中，菜单可见高度约 418px、内容高度 806px；真实滚轮可到“系统设置”并返回顶部，品牌区和底部按钮保持可见，折叠/展开正常。定向 ESLint 和前端生产构建通过；未重跑后端验收。

## Spec J：深色输入框与局部 MockMaker（2026-10-11）

Runbook 搜索框使用既有 ui-input 时，旧 `.admin-layout .ui-input` 的双 class 选择器优先于 `.incidentpilot-page input`，出现白底浅字；仅添加 Tailwind bg 类也未解决。以 `.incidentpilot-shell .incidentpilot-page input` 提升作用域内优先级，实测背景 rgb(16,29,51)、文字 rgb(237,244,255)，不要修改全局浅色后台输入契约。视觉改动除构建外还应读实际 computedStyle。

rag 测试保留 subclass MockMaker；新静态 StpUtil/final Corpus 测试显式 `withSettings().mockMaker(MockMakers.INLINE)`，复用既有局部模式，不能为一个测试改全模块资源配置。实际运行6项通过，见 Spec J 实施记录。

## Spec K：Windows Maven Wrapper 普通目录（2026-10-11）

`mvnw.cmd` 将 `(Get-Item $MAVEN_M2_PATH).Target` 直接索引 `[0]`；普通 Maven 缓存目录没有链接目标，Windows PowerShell 报 `Cannot index into a null array`，尚未进入 Maven 或测试。先取第一个存在的链接目标，再检查空值；保留普通目录和符号链接分支及上游许可。修补后 `./mvnw.cmd -v` 实际退出0，Maven3.9.11/JDK21。此结果只验证Wrapper启动，不代表依赖缓存、业务测试或交付运行通过。

## Spec K：从测试装配到真实JAR（2026-10-11）

测试classpath中的隔离MQ配置不会进入真实制品。`isolated`排除RocketMQ配置时，知识入库事务检查器仍依赖 `DelegatingTransactionListener`；但它的 `@RocketMQTransactionListener` 元注解已包含 `@Component`，真实主应用包扫描会自动注册。首轮最小context没有扫描该包，错误推断缺Bean；额外补Bean反而让真实JAR重复注册。最终复用上游监听器，测试覆盖实际producer包扫描、恰好一个监听器与两个checker，并继续拒绝Producer副作用。以真实JAR/实际HTTP就绪验证装配，不能由不等价的局部context推断完整装配。

本地启动环境应分别处理Java配置继承与Compose插值环境，不能以清理Spring前缀推断Compose固定文件不受shell覆盖。启动配置完整性与停止资源归属也应分开：Schema/app配置变化可以阻止再次启动，但不应阻挡已经确认归属的资源停止；Stop仍须核对PID创建时刻、命令行标识和容器/卷标签。

在本机PowerShell中，将环境变量设为null仍可能给原生命令留下空值。Compose调用前通过 `Env:` 真正删除受控插值键，并在finally恢复；契约使用真实Node子进程核对环境键不存在，而非只检查PowerShell读值。

本轮Docker Desktop下，internal网络的依赖虽然容器内健康，却没有宿主机可用的端口发布。改用自有普通bridge并保留所有 `127.0.0.1` 绑定，实际宿主TCP及应用HTTP就绪分别验证；不能以容器health替代宿主连接。普通bridge不提供操作系统级出站防火墙，不应宣称网络级零出口。

Java `.properties` 的键中冒号是分隔符；CLI参数里有效的诊断映射直接写入文件会被错误解析。生成时转义冒号，并用JDK `java.util.Properties` 读取真实文件验证；只核对文本包含预期字符串不足以证明配置绑定正确。
# 2026-10-12：Spec K 最终页面与环境恢复

- 原生滚动条会占用侧栏导航可用宽度；收起项的横向padding与SVG默认flex-shrink组合可把图标挤成宽0。仅IncidentPilot范围缩减收起项横向padding并禁用图标收缩，实际390视口测得普通图标16×16、Bot18×18，侧栏稳定宽54。
- 额度中断后已保存的READY/重启证明不意味着当前进程仍在。先核对Docker Engine和自有进程，再对同一实例Start；不重新Init或播种。2026-10-12原实例恢复后只读验证仍为一个匹配模拟应用。

## Spec L：隔离测试的预选端口竞态（2026-10-12）

用`ServerSocket(0)`选端口后立即关闭，再初始化随机端口HTTP代理，代理可能占用应用预选端口。首轮Backend出现应用54327端口已占用、HTTP就绪前退出；应保留失败报告，不能吞掉启动异常或把局部通过计成整轮成功。

测试夹具在代理绑定期间保留应用及MCP两个端口，到各自启动前释放，并断言代理端口不重合。定向回执丢失场景实际通过；完整回归及源码摘要见 [Spec L记录](../specs/012-incident-reliability-acceptance/implementation-report.md)。保留至释放缩小夹具自身竞态，释放与子进程绑定仍有系统调度间隙，不能宣称防御所有本机进程抢占。

## Spec M：源码候选与历史交付（2026-10-17）

- `git status --porcelain`默认把未跟踪目录折叠，目录内新增源码不一定改变状态文本。源码快照应在复制前后重新枚举完整NUL/UTF8路径集合，并逐文件复核字节摘要；不能仅凭status或HEAD判断完整。
- 当前源码排除`.git`不等于Git bundle安全。旧上游配置的可达历史存在潜在凭据时，保留原历史、不分发bundle；窄规则无命中也不宣称全面秘密认证。边界与状态见 [源码候选](releases/source-candidate-v0.1.0.md)。
- 沙箱默认用户的Maven缓存可能不同于原运行用户。离线缺依赖先核对缓存身份，显式复用已有缓存并记录，不暗中联网后仍声称离线成功。候选不携带私有settings或缓存。
- npm package-lock的packages含空字符串键，PowerShell需`ConvertFrom-Json -AsHashtable`；同时核对根版本及packages空键版本。JAR本轮以准确文件名和Maven pom.properties核对版本，不假定build-info一定在BOOT-INF/classes下。


## 2026-10-19：Windows门禁裸.cmd名称的自定位失败

固定b667真实All的npm前提检查失败，未开始Backend或前端。相同Node20环境及源码cwd下，cmd.exe双层引用的裸npm.cmd查找cwd下的npm模块并退出1；引用完整路径或不再引用裸名称则返回10.8.2，PATH目录斜杠形式不改变失败。候选保持，未在源码目录复制npm或引入shim。后续建议在中央Invoke-DeliveryCommand解析.cmd完整应用路径，并保留解析后路径/参数元字符校验，补带空格PATH与不同cwd的真实回归；尚未实施。详见[阻断报告](../specs/013-release-candidate/gitless-all-report.md)。

## 2026-10-19阶段19：命令定位修复实证

- Windows `.cmd`先解析完整Application路径，再对实际路径及参数保留原元字符拒绝；不同cwd/空格路径无需改全局配置。
- 旧代码RED、修复后相同测试SHA的176项合同GREEN及真实npm通过；49项候选合同为封存和All启动后补跑，不倒写成前置检查。
- 新候选`c1687735-1ad9-4881-a620-81702d39d1ab`的真实无GitAll `d98d8861-aa83-4168-82f5-c84e2b62022b`为PASS/0（201 XML、952项/931通过/21受控外部跳过/0失败错误），独立审计通过；旧b667/7d1c阻断保留，边界见[本轮报告](../specs/013-release-candidate/cmd-dispatch-fix-report.md)。
