# 测试与验证

> 2026-10-19阶段19最新收尾：Windows中央`.cmd`路径修复，真实RED/GREEN 176项合同、npm及后补49项候选合同通过；新候选`c1687735`（1907文件）真实无GitAll `d98d8861`已PASS/0，同run五阶段退出0，201 XML、952项/931通过/21受控外部跳过/0失败错误，独立可靠性/评测审计通过。完整来源/1906旧输入及资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED及旧Backend PASS保持；T076失败终止、SC-019未满足，新成功由T081支撑，82/82仅任务关闭。详见[本轮报告](../../specs/013-release-candidate/cmd-dispatch-fix-report.md)。后补说明不回填冻结ZIP；异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段18All阻断：固定b667新解包、真实无GitAll `7d1c8f40` 为BLOCKED/2，PRECONDITION_FAILED，0测试阶段/0XML；Windows带引号裸npm.cmd定位自身模块失败，完整路径探测成功。未改根门禁或候选，完整All/前端/专项审计仍未通过；后续最小执行器修复与新候选复验待确认。原Backend及旧Frontend、旧FAIL保持身份，1871项保护和资源事实及最终收尾见[本轮报告](../../specs/013-release-candidate/gitless-all-report.md)。异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段16/17后端续跑：原ebf/`9624289c`真实FAIL及162XML保留。两类评测reporter的隐含Git依赖已修复，新增22项来源校验，普通Git/真实无Git聚焦分别27/27；新候选`b66759af-6cfb-4cb6-8db5-65276702f443`（1905文件）的真实无GitBackend `fdbac063` 已PASS/0：201新XML、952项/931通过/21受控外部跳过/0失败错误，14必需类零跳过。额度恢复后未重跑Backend，实际补做45规则、HTTP幂等、进程/数据库恢复、400样本及16项评测外置产物的独立审计并PASS。完整来源/1200旧输入、Git、既有16容器/94卷和说明以本轮综合proof/completion绑定；Spec M73/73限已定义本地任务。详见 [本轮报告](../../specs/013-release-candidate/candidate-backend-report.md)。前端204文件与ebf字节相同，未新跑Frontend/完整All、异机或发布；旧TBD、安全历史整合及正式Git授权仍开放，冻结候选不含后补最终说明。以下摘要保留原时点。

## 文档职责

本文档是仓库级测试策略、执行约定和环境要求的权威入口，回答以下问题：

- 不同类型的改动应该运行哪些检查；
- 哪些测试依赖 Docker、数据库、中间件或外部服务；
- IncidentPilot 的幂等、审批、审计和 AI 诊断如何获得可重复验证；
- 执行结果应如何记录，哪些未执行的验证必须明确说明。

功能级内容继续由 Spec Kit 管理，不在仓库中建立第二套测试计划格式：

- `spec.md` 记录可观察、可验收的业务场景；
- `plan.md` 记录该功能采用的测试技术、环境和边界；
- `tasks.md` 记录需要实现和执行的具体测试任务；
- 本文档只保存适用于整个仓库的稳定策略和通用命令。

本文档同时包含“当前已经可以执行的命令”和“IncidentPilot 新功能必须达到的目标测试要求”。尚未接入的测试工具或场景不得被描述成已经通过；只有真实执行并留有结果的检查才能作为完成证据。

## 测试原则

- 本地Compose挂载改动先运行 `scripts/test-local-demo.ps1` 的实际配置/归属合同，再对新Name做依赖生命周期验证；记录完整卷集合和镜像实际Mounts，不能只数项目前缀卷。2026-10-17日志命名卷修复的30项合同、代理2项及三轮依赖结果见 [记录](../../specs/013-release-candidate/storage-volume-fix-report.md)，不等于新应用构建或Backend/All。

- 可测试性属于架构要求，不把测试集中推迟到功能开发结束后。
- 优先用数据库约束和真实 PostgreSQL 集成测试证明幂等、事务和并发正确性。
- 普通测试不得依赖真实模型、付费接口或远程环境；外部边界使用固定响应、模拟服务或本地安全资源。
- RAG 的旧模型、Milvus 和开发数据库联调探针默认不运行；只有显式设置 `RAG_LIVE_TESTS=true` 才会尝试执行，启用前须另行确认资源隔离和费用授权。默认门禁的跳过数量要在结果中单独报告。
- 随机性的 AI 评测与确定性的构建测试分开运行，不用逐字文本匹配验证模型回答。
- 对安全边界测试失败采取失败关闭：未经服务端审批、缺少幂等键或参数被篡改时，MCP 操作必须拒绝执行。
- 测试数据、容器、端口和凭据与开发环境隔离，测试结束后可以安全清理。
- 先运行最小且有针对性的检查，再根据改动风险扩展到集成、端到端或发布级验证。

## 测试分层

| 层级 | 主要目标 | 依赖 | 典型内容 |
| --- | --- | --- | --- |
| Java 单元测试 | 快速验证确定性领域规则 | JUnit、Mockito | 告警标准化、指纹、聚合规则、状态转换、权限判断、幂等键 |
| PostgreSQL 集成测试 | 验证真实 SQL、约束、事务和并发 | Docker、Testcontainers、PostgreSQL | 唯一约束、乐观锁、并发聚合、事务回滚、只追加时间线 |
| 接口与安全契约测试 | 验证输入边界、认证、授权和错误响应 | Spring Test、MockMvc 或等价能力 | 告警鉴权、载荷校验、越权审批、审批绕过、错误码 |
| 消息与恢复测试 | 验证异步链路不会丢失或重复处理业务 | 模拟发布器；必要时使用本地 RocketMQ | 重复消息、发送失败、消费重试、补偿、死信或人工处理 |
| RAG 与 Agent 测试 | 分离确定性编排验证和概率性质量评测 | 固定检索结果、模拟模型；评测时才使用已授权模型 | Runbook 引用、工具选择、拒绝越权、固定事件集评测 |
| MCP 执行测试 | 验证审批、参数完整性、幂等和不确定结果处理 | 安全模拟资源、本地 MCP 服务 | 未审批拒绝、重复执行、票据过期、`FAILED`、`UNKNOWN` |
| 前端主流程测试 | 验证用户实际可完成核心流程 | 前端构建；后续选择一种浏览器测试工具 | Incident 列表、详情、时间线、诊断、审批和结果刷新 |
| 端到端黄金路径 | 验证告警到处置的完整协作 | 隔离数据库、固定模型响应、模拟 MCP；按需启动中间件 | 告警聚合、诊断、拒绝、批准、单次执行、完整时间线 |

只有真实外部边界才需要适配接口；不要为了测试给每个类增加一层接口。时间、随机 ID、模型、检索、消息和 MCP 等天然不稳定边界应允许注入固定实现，核心状态转换和聚合规则保持为普通 Java 逻辑。

## Docker 与 Testcontainers

普通 Java 单元测试不依赖 Docker。验证 PostgreSQL 约束、事务和并发行为时，使用 Testcontainers 自动创建临时 PostgreSQL：

1. 本机安装并启动 Docker Desktop；
2. 测试启动时由 Testcontainers 创建隔离的 PostgreSQL 容器；
3. 测试在临时数据库中应用所需 Schema 并执行断言；
4. 测试结束后自动销毁容器，不连接或清理日常开发数据库。

首次运行可能需要下载容器镜像。Docker 未运行时，只应将依赖 Testcontainers 的集成测试报告为环境受阻，不得改连共享开发库或远程数据库来掩盖问题。

引入第一项 Incident 持久化功能时，应先增加一个最小冒烟测试，证明测试进程可以启动 PostgreSQL 容器、建立连接、执行 SQL 并完成清理。随后再添加并发和事务场景。

RocketMQ 容器不作为所有测试的默认前置条件。业务层优先使用模拟消息发布器；只有验证 Broker 投递、重试或跨进程行为时才启动本地 RocketMQ 集成环境。

## IncidentPilot 关键验证场景

2026-10-12 [Spec L](../../specs/012-incident-reliability-acceptance/spec.md) 补充独立固定规则预期和真实HTTP/进程/自有数据库暂停验收；新增两类进入H必需检查。规则案例为真实PG直接调用及SQL时间fixture，运行基线为测试classpath跨JVM/本机闭环批次；不称语义关联质量、生产可靠率或容量上限。最终状态见 [实施记录](../../specs/012-incident-reliability-acceptance/implementation-report.md)。

### 告警幂等与聚合

- 相同 `source + externalAlertId` 重复提交，只保存一条告警事件并返回已有处理结果。
- 多个不同外部告警具有相同标准化指纹时，只关联一个符合聚合规则的活动 Incident。
- 多线程并发提交相同告警或相同指纹时，最终结果由数据库唯一约束兜底，而不是仅依赖进程内锁。
- 消费相同消息多次不会重复创建 Incident、Action 或 Timeline 记录。
- 数据库事务失败时，Incident、关联关系、Timeline 和待发送事件不得留下部分提交状态。

### 状态机与时间线

- 每个允许的 Incident 和 Action 状态转换都有正向测试，每个禁止的跨越转换都有拒绝测试。
- 乐观锁或等价机制能够阻止并发请求静默覆盖新状态。
- Timeline 只允许追加；业务代码不提供修改和删除入口，数据库约束或权限策略应有对应集成验证。
- 时间线记录包含事件类型、操作者、时间、关联对象和安全的结构化摘要，但不保存模型思维链、密钥或未经处理的敏感载荷。

### 人工审批与 MCP 安全

- 未经审批、审批被拒绝、审批已过期或审批参数摘要不匹配时，MCP 写操作必须拒绝。
- 绕过前端直接调用后端或 MCP 时，仍然不能跳过服务端审批。
- 每次有副作用的调用必须携带幂等键；相同幂等键再次调用时不得重复产生副作用。
- 批准人、批准时间、原始工具参数摘要、执行结果和关联 Incident 都可以通过审计记录追溯。
- 网络中断导致无法确认执行结果时记录为 `UNKNOWN`，不得自动重试有副作用的操作。

### RAG 与 Agent

确定性构建测试使用固定检索结果和模拟模型，验证：

- Agent 输入包含正确的 Incident 快照；
- 诊断只引用实际返回的 Runbook 证据；
- 工具参数与建议内容可追溯到本次诊断；
- Agent 只能提出处置建议，不能绕过审批直接执行写操作；
- 没有足够证据时返回不确定结论，而不是伪造事实。

真实模型评测使用版本化的固定事件集，至少覆盖资源过载、Java OOM、数据库连接池耗尽、健康检查失败和消息积压等场景。评测记录模型、Prompt、知识库和样例集版本，并观察 Runbook Top-K 命中、证据引用、处置安全性和不确定性表达。真实模型评测不进入默认 CI，未经授权不得调用付费服务。

### 端到端黄金路径

至少保留一条能够重复执行的完整场景：

1. 提交两个相同指纹的告警；
2. 两个告警聚合为一个 Incident；
3. 使用固定 Runbook 和模型响应生成带证据的诊断；
4. 第一次拒绝处置，验证 MCP 没有执行且 Timeline 记录拒绝；
5. 第二次批准处置，验证模拟 MCP 操作只执行一次；
6. 验证 Incident 状态、Action 结果和 Timeline 在应用重启后仍然完整。

端到端自动化默认使用固定模型响应和模拟资源，以保证可重复性。真实模型或真实外部系统只作为单独、明确授权的评测或演练。

## 常用命令

除非命令主动切换目录，否则均从仓库根目录执行。

```powershell
# 运行单个 Java 测试类；按实际情况替换模块名和测试类名。
.\mvnw.cmd -Dspotless.apply.skip=true -pl framework -Dtest=TaskCancellationTest test

# 运行全部 Java 测试，并禁止绑定在 compile 阶段的格式化器改写源码。
.\mvnw.cmd -Dspotless.apply.skip=true test

# 显式检查或应用格式化。
.\mvnw.cmd spotless:check
.\mvnw.cmd spotless:apply

# 前端检查。
Set-Location frontend
npm ci
npm run lint
npm run build
```

根 POM 将 `spotless:apply` 绑定在 `compile` 阶段。只读测试或构建验证应使用 `-Dspotless.apply.skip=true`，只有确实需要修改源码格式时才显式执行格式化。

依赖 PostgreSQL、Redis、RocketMQ、模型供应商、对象存储或 Testcontainers 的测试，需要先满足其声明的本地前置条件。不得为了让本地检查通过而擅自改用付费或远程服务。

## 检查选择

- 实施非平凡变更前，先验证 Spec Kit 产物之间的一致性。
- 首先运行能够覆盖当前变更的最小检查。
- 跨模块或公共契约变更需要运行共享测试或默认测试集。
- 持久化、Schema、外部适配器或跨进程变更需要运行集成测试。
- 前端可见变更需要运行生产构建和必要的交互检查。
- 只有在前置条件和授权边界均满足时才运行发布级检查。

## 按功能阶段落实测试

测试不是最后一个独立里程碑。每项能力在进入下一阶段前应同时完成对应证明：

| 功能阶段 | 最低测试要求 |
| --- | --- |
| 数据模型与状态机 | Schema、数据库约束、允许和禁止的状态转换、时间线只追加 |
| 告警接入与聚合 | 认证、输入校验、重复提交、并发幂等、事务回滚 |
| 异步处理 | 重复消费、发送失败、消费失败、重试和补偿恢复 |
| Runbook RAG 诊断 | 固定检索与模型测试、证据引用、无证据时的安全降级 |
| MCP 处置 | 审批不可绕过、参数完整性、幂等执行、失败和未知状态 |
| 前端主流程 | lint、生产构建以及一条告警到处置的浏览器主流程 |

功能 Spec 应把这些要求改写为具体、可观察的验收场景；实现计划说明使用哪些测试技术；任务列表则包含测试代码、测试数据、环境准备和执行证据。

## CI 与外部服务边界

- 默认 CI 运行不依赖付费服务的 Java 单元测试、适用的 PostgreSQL 集成测试、静态检查和前端构建。
- 需要 Docker 的 CI 任务必须明确启动条件，并与普通快速检查分开报告。
- RocketMQ、浏览器端到端和跨进程测试可独立成较重的检查，但不得用完全模拟的低层测试冒充已经验证真实边界。
- 真实模型、Embedding、Rerank、对象存储和远程环境验证仅在明确授权、凭据安全和成本可控时运行。
- 任何性能、可靠性或评测结论都必须附带可复现命令、环境、数据集、配置和实际结果。

## 验证证据

报告实际执行的命令、结果、相关数量以及跳过或受阻的检查。局部或过期的执行结果不能证明整体已经就绪。

## 隔离与清理

- 主后端和 MCP 服务默认使用 9090、9099 端口。
- 测试数据库、容器和凭据必须与开发数据隔离。
- Maven `target/`、前端 `node_modules/`、`dist/` 和 `.vite/` 都是生成内容。
- 执行格式化或大范围构建后，先检查 `git status` 和 `git diff`，再判断哪些变化属于当前任务。

## 查询与调整指南

后续查询时按问题类型定位：

- 查执行命令和环境前置条件：阅读“常用命令”“Docker 与 Testcontainers”；
- 查某类能力应如何验证：阅读“测试分层”“IncidentPilot 关键验证场景”；
- 查开发到什么程度才能进入下一阶段：阅读“按功能阶段落实测试”；
- 查哪些检查可以进入 CI 或调用外部服务：阅读“CI 与外部服务边界”；
- 查一次具体功能的验收条件和测试任务：进入该功能的 `spec.md`、`plan.md` 和 `tasks.md`。

调整规则如下：

- 跨功能、长期稳定的测试原则、环境要求和通用命令修改本文档；
- 只影响单一功能的场景和技术选择修改对应 Spec Kit 产物；
- 新增测试命令前先确认它在当前仓库真实可执行，不记录尚不存在的类名或脚本；
- 引入新的外部依赖、付费服务、真实处置或安全边界时，先更新设计和 Spec，再更新本文档中的适用规则；
- 每次调整后检查是否与 `AGENTS.md`、`.specify/memory/constitution.md` 和实际 CI 配置冲突。


## TST-002：旧RAG探针分类与安全门（2026-10-05）

九类旧RAG探针作为手工示例保留；生产规则的确定性覆盖使用实际服务配固定Mapper/cache/LLM/SDK，协议检查使用本轮自有127.0.0.1随机端口HttpServer。11类目录、具体断言及范围见 [Spec G研究记录](../../specs/007-rag-test-isolation/research.md)。不要用仅打印结果、测试内向量/专家算法或删去跳过来宣称生产能力覆盖。

RAG_LIVE_TESTS未设置或不为true时，旧探针及You.com/MinerU均在联调入口前跳过。You.com还需key非空，MinerU还需mineru.test.pdf参数；key或文件本身不构成执行授权，真实资源/费用须另行确认。本轮未启用联调。

默认全仓执行窗口设置RAG_LIVE_TESTS=false并暂时移除YDC_API_KEY（MCP的旧YouCom探针仍单靠key）；finally恢复原环境，不输出原值。不得提供ragent.dashboard.test.jdbc-url等开发连接参数。保留的RAG十九跳过与其他模块条件跳过逐类报告，不计通过。

新增聚焦用例需实际执行且零跳过，目标类与方法必须存在；上游模块的surefire.failIfNoSpecifiedTests=false不得掩盖目标未运行。结果应关联新XML、实际命令/退出码/环境、HEAD及未提交源码摘要。[Spec G复现指南](../../specs/007-rag-test-isolation/quickstart.md) 保存该阶段历史结果：模块392项（373通过、19跳过），全仓864项（843通过、21跳过），均零失败/错误、退出0；跳过不计通过。后续功能与候选需使用各自的新运行记录。

## DEP-001固定门禁

源码ZIP接手限制（2026-10-18）：固定070不携带`.git`，而门禁在CheckOnly之前无条件读取Git HEAD；禁止向父仓库发现时不能得到CHECKED或运行测试。源码候选Verify和本地演示Build/Verify不依赖该门禁来源检查，分别记录。不得用git init/commit或借外层HEAD把未提交实现冒充提交；后续无Git来源适配另行明确。接手材料及所有异机待执行项见 [执行手册](../../specs/013-release-candidate/second-machine-handoff.md)。阶段13只有本机文件/语法/固定签名与负向预检，不是新All。

2026-10-18阶段12另行验证固定070候选的持久运行：新空库/配置、真实`java -jar`、HTTP拒绝零应用/批准唯一一次、完整117静态读取、独立SQL及重启快照一致，最终停止且5卷保留。该轮复用阶段11构建及All来源，没有新增930项测试执行；具体本轮命令、检查点与证据见 [持久运行报告](../../specs/013-release-candidate/persistent-candidate-runtime-report.md)。本机模拟、Broker关闭、浏览器及平台边界分别报告。

2026-10-18固定070候选的完整新All见 [阶段11实际记录](../../specs/013-release-candidate/full-candidate-acceptance-report.md)：87合同、独立打包及All分别统计；199新XML/930项、14必需类零跳过、13类21受控跳过，前端四阶段通过；可靠性证明另行按runId/sourceDigest和完整分母复核。冻结候选manifest与门禁源码子集分别绑定，不用旧报告计数代替新运行。本机缓存和测试classpath边界保留。

Spec M版本/交付检查：`pwsh -NoProfile -File scripts/test-release-candidate.ps1` 与既有`test-local-demo.ps1`分别核对候选安全/完整性和当前版本JAR选择。实际生成后Verify并从源码ZIP解包离线package；复用本机缓存、跳过业务测试的构建不等于新的Backend/All或干净环境链路。历史因疑似凭据不随候选分发，范围见 [Spec M](../../specs/013-release-candidate/spec.md) 与 [源码候选](../releases/source-candidate-v0.1.0.md)。

```powershell
pwsh -NoProfile -File scripts/test-check-delivery.ps1
pwsh -NoProfile -File scripts/check-delivery.ps1 -CheckOnly -Gate All
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Smoke
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Backend
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Frontend
```

组合范围使用-Gate All。Smoke精确黄金路径1项；Backend全仓test核对14必需类零跳过及固定13手工类目录，禁止沿用历史计数来判定本轮。Frontend固定npm ci --no-audit --no-fund、node tests/incident-view.check.mjs、npm run lint/build；不能替代真实浏览器。退出0=PASS/CHECKED，1=失败，2=受阻；任一失败不被后续成功覆盖。

本轮独立runId/sourceDigest、新XML及退出在bootstrap/target/delivery-gates/<UUID>，源码结束复核；raw/reports/proof及artifacts完整性清单仅本地。XML禁DTD，空/旧/重复/计数冲突/目标未执行或意外跳过均失败。只构造safe/summary.json白名单发布，摘要失效无raw回退。检查前关闭RAG许可并移除MCP You.com key，finally恢复；不提供Dashboard JDBC、不连开发库、不全局清理。工具/资源、CI静态与实际平台区别见 [交付说明](delivery.md) 和 [Spec H运行记录](../../specs/008-local-delivery-gates/quickstart.md)。

## Spec M阶段15：无Git候选门禁

开发Git工作树运行`test-check-delivery.ps1`（包含默认Git模式回归）和`test-release-candidate.ps1`。无Git源码包使用显式`-CandidatePath`，不要求先运行开发者合同：

```powershell
pwsh -NoProfile -File scripts/check-delivery.ps1 -CandidatePath ../candidate -Gate All -CheckOnly
pwsh -NoProfile -File scripts/check-delivery.ps1 -CandidatePath ../candidate -Gate Frontend -CheckOnly
pwsh -NoProfile -File scripts/check-delivery.ps1 -CandidatePath ../candidate -Gate Frontend
```

必须核对完整候选/实际解包源码的起止身份，不能借父Git或仅用受检子集`sourceDigest`代替。候选安全摘要为schema2/head=null，Git模式仍schema1；开始来源不合格时不得进入工具探测，结束漂移不得得到CHECKED/PASS。工具受阻、Frontend成功、Backend/All成功分别记录；本机缓存不代表异机环境。命令、退出及本轮范围见 [无Git门禁记录](../../specs/013-release-candidate/gitless-delivery-report.md)。

本轮实际为159门禁合同/49候选合同退出0，无GitFrontend CheckOnly为CHECKED、四阶段为PASS；All CheckOnly因Docker引擎不可用BLOCKED/2，T062仍未完成。首候选四阶段0但完整树结束FAIL保留；精确补入既有页面检查输出`frontend/.output/incident`后另建候选重验，不放宽整个`.output`。完整Backend/All、第二机器及承诺平台未在本轮执行。

2026-10-19后续补跑：Docker由用户启动后，固定ebf1901输入的新All CheckOnly `f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`实际CHECKED/0、0阶段，无父Git/无Git工具且完整来源起止一致。T062关闭（阶段15为7/7、Spec M64/64）；此前BLOCKED和原完成证明保留。本轮没有实现变更，未重跑159/49合同或Frontend/Backend/完整All；仅补这一前提检查并独立复核safe/artifacts、旧输入、Git与用户启动后Docker集合，不能升级为完整All或异机通过。
