# 实施计划：OBS-001 统一关联标识与 Trace

**分支**：`feature/incident-platform` | **日期**：2026-10-05 | **规格**：[spec.md](spec.md)

**状态**：2026-10-05 用户已授权按本稿实施；本地隔离验收完成，27/27任务，实际结果见quickstart。Spec Kit 脚本的 BRANCH 字段为功能目录名，不是当前 Git 分支。

**输入**：`specs/005-incident-correlation-trace/spec.md`。

## 摘要

推荐复用现有稳定业务 ID，以 incidentId 汇总聚合后的流程，保留 Alert correlationId 和所有历史事实；统一 HTTP 尝试 requestId，补充白名单结构化日志和 OTel 短时 span。跨受保护 HTTP 使用 W3C traceparent，现有 MCP _meta/消费/回执正文不增加字段。数据库异步、审批间隔与恢复重新建短时 Trace，靠持久业务身份检索；首版不持久化 OTel 上下文，也不承诺跨重启 Span Link。

这是 brainstorming 三方案中的推荐方案，比较及当前能力证据见 [research.md](research.md)。用户随后明确指示“实施”，技术设计已确认；正式记录见 DEC-028。

## 技术背景

**语言/版本**：Java 17；现有验收 runtime 为 JDK 21.0.12+8-LTS、release 17，设计阶段未启动 Java 应用，实施阶段的实际结果另记 quickstart。

**主要依赖**：现有 Spring Boot 4.1.0、Spring MVC、SLF4J MDC、OTel BOM 1.62.0、AgentScope 2.0.2 和 MCP SDK；不新增 Java Agent、Collector/Jaeger/Langfuse 服务或日志框架。framework 显式声明 OTel API/SDK/既有 OTLP exporter，incident 使用公共能力；独立 mcp-server 自持同版本 API/SDK/exporter，不引入 framework/incident 运行依赖。

**存储**：现有 PostgreSQL 数据与身份保持；无 Schema/迁移。测试 exporter 在隔离测试目录输出白名单 span，不新增运行时 Trace 数据库或业务双写。

**测试**：JUnit/Mockito、SDK 收集 exporter；已有 Testcontainers PostgreSQL 和独立 Bootstrap/MCP JVM。实际跨进程 fixture 使用测试 exporter 输出实际 SpanData，区别于模拟 span/固定业务回执。

**目标平台**：现有 Java 主应用与独立 MCP 进程，本地安全模拟环境。

**项目类型**：模块化后端的横切增强。

**性能目标**：不设未经实测的吞吐/p95 数字；采用有界异步导出，网络导出不位于业务事务/行锁内，无逐次同步 flush。

**约束**：不得修改业务幂等与安全绑定；无真实基础设施、开发数据库、付费调用、Git 提交或部署。本轮只写 Spec/项目任务记录及 Spec Kit 功能指针。

**规模/范围**：现有告警、聚合、查询/状态/重放、诊断/选择、Action 决定、分发/恢复/清扫/对账、MCP 模拟应用、回执和 Outbox Relay。不统一普通聊天协议，不增加前端。

## 宪章检查

| 质量门 | Phase 0 前核对 | Phase 1 设计复核 |
| --- | --- | --- |
| 上游署名/历史保留 | 沿用上游 RAG/Agent Trace，区分 OBS 新增贡献 | 不移除许可证与源码许可头 |
| 持久化正确性/审计 | 数据库是事实来源 | 无新业务事务/表，span 不写 Timeline |
| 副作用人工控制 | 原审批/票据/身份/幂等保持 | traceparent 不能授权；消费后不允许写重试 |
| 范围与 Spec | 当前用户只授权设计 | 全部产物草案，无实现/部署 |
| 有证据后结论 | 最新 54 项仅历史 B 验收 | 本轮仅静态设计校验，未来测试另行实施 |

静态设计无已知宪章违规；进入实施仍需用户确认、speckit-analyze 及实时工作区核对。

## 项目结构

### 当前功能的文档

```text
specs/005-incident-correlation-trace/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/observability.md
├── checklists/requirements.md
└── tasks.md
```

### 源代码（仓库根目录）

以下是拟修改边界，不代表文件已创建：

```text
framework/src/main/java/com/nageoffer/ai/ragent/framework/observability/
incident/src/main/java/com/nageoffer/ai/ragent/incident/{alert,core,diagnosis,action,outbox}/
agent/src/main/java/com/nageoffer/ai/ragent/agent/{diagnosis,tool,trace}/
mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/{config,simulation}/
bootstrap/src/main/resources/application.yaml
bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java
resources/database/                         # 保持
frontend/                                   # 保持
```

**结构决策**：framework 只保存无业务 Mapper 的通用 scope、属性白名单和条件 SDK 装配；Incident 从自身权威记录填充业务 ID。MCP 在独立进程使用薄装配，不复制 Incident 查询能力。不新增平台模块或通用可观测 SPI。

## Phase 0：现状研究与方案比较

完成只读 Git、文档、现有 Trace/业务身份/线程/协议核对，及 Spec Kit 要求的只读研究协作。结论与来源见 [research.md](research.md)。历史文档把 correlationId 称为贯穿流程的标识，代码实际有多值：该歧义在新契约中显式限定，旧 Spec/DEC/历史事实不静默改写；用户若要求把全部历史 correlationId 统一，则属于额外 Schema/历史兼容方案，不能按本稿直接实施。

## Phase 1：设计与契约

### 1. 关联模型与请求入口

- `incidentId` 是已聚合流程的统一检索键；首次接收时只有 alertId/correlationId，关联成功日志同时输出 alertId、该 Alert correlationId 和 incidentId，形成桥接。
- `requestId` 为当前 HTTP 尝试 UUID，一次分配到 request attribute；入站 filter 早于告警拒绝逻辑和登录拦截，AlertIngressFilter 复用该值，Controller/Advice 不重新生成。当前服务器请求 ID不采纳客户端 X-Request-Id。
- 支持成功、早拒绝、错误分派复用；入口路径含主应用 context path，匹配人员/内部/诊断路由。内部 HTTP 的每次回调有新 requestId，正文 requestId 保持调度语义。
- 不更改全局 Result 结构和前端成功码。范围内统一 `Result.requestId` 与响应 `X-Request-Id`；录制有效时返回 `X-Trace-Id`，关闭时省略，不返回全零/伪造 traceId。
- 安全日志输出单条 JSON 事件，字段采用 contracts 白名单；MDC 只用于当前 scope，不安装覆盖整个应用的全局异步 hook。外层值关闭时恢复，不做无差别 MDC.clear。

### 2. SDK 装配与兼容

每个进程一套 SDK owner，禁止 agent 与 OBS 抢占 GlobalOpenTelemetry。

| obs.enabled | agent.trace.enabled | 主应用 SDK 与聊天行为 |
| --- | --- | --- |
| false | false | 不创建 SDK，不读取/注册全局实例；仅请求/业务安全日志 |
| false | true | 现有 Agent 装配保持 Langfuse 必填、资源/限额/内容开关/中间件行为 |
| true | false | framework OBS owner；Incident 骨架开启，普通 Agent tracing middleware 仍不开启 |
| true | true | framework OBS owner；Agent SDK 工厂退让并复用同一 SDK，Agent serializer/middleware 仍受原开关控制；保留所需 Langfuse 配置校验与 exporter 行为 |

同一 SDK 可持有两个明确配置的 exporter（OBS 本地与已有 Agent exporter）；不在同一进程注册两套 Global。双开启模式需独立装配测试证明 Agent 官方 span 与业务 span 都来自同一 SDK，禁止靠启动顺序碰运气。实际启用外部 Langfuse 不属于本轮授权；兼容测试使用本地替身出口。

本计划的 obs.enabled 是 `observability.enabled` 配置的简称，两进程各自配置；OBS 默认关闭；启用无 exporter 时使用有真实 ID 的本地 SDK但不导出，可用于单测注入 collecting exporter。配置本地出口时使用已有 OTLP HTTP exporter、BatchSpanProcessor 和有界队列；配置格式错误可启动时报错，运行期出口失败仅丢弃辅助遥测，不改变业务。测试以确定采样记录全链；首版显式 alwaysOn，不接受公网采样位强制提高采集量。SDK 随进程关闭，不每次请求 flush。

MCP 独立配置 `observability`，独立 service.name/instance.id；沿用父 POM 的版本。不得把 Agent 的 capture-content=true 应用到 Incident/MCP span；主链只白名单骨架。SDK owner 本身早于任何 Global.get() 和 tracer 使用。

### 3. span 与阶段结果

采用 contracts 中固定名称；以实际发生操作为节点，不逐行 SQL、字段、token 或 Timeline 事件建 span。

HTTP SERVER span 覆盖单次请求；AlertWorker 每条 processOne、Action Worker 每条 claim+dispatch/recovery、Relay 每条 event 有独立 operation scope。批量领取与锁内写保持原代码和顺序，网络仍在事务外。Trace scope 只包装业务调用，不参与回滚、SQL 条件或数据库时钟。

诊断 orchestrator 显式包裹实际生成器/检索/校验，不接入 AgentScope ReAct，不调用模型。业务降级/拒绝/UNKNOWN 写稳定 outcome/reasonCode；不从 HTTP 200 或 OTel ERROR 推定业务失败。重复读标记 REUSED，不生造 generate span。

正常派发下，当前业务操作→CLIENT dispatch→MCP SERVER→consume CLIENT/SERVER→execute（工作线程）→receipt CLIENT/SERVER→主应用 accept；响应通道收同一 receipt 时标记幂等结果，不能重复标为应用一次。模拟工作线程若因超时仍未停止，其 execute span 只能按实际线程结束关闭，不能伪造已回滚或成功。

### 4. 传播与可信身份

主应用受保护 MCP transport 及 MCP 回调 HttpClient 注入标准 traceparent，使用 OTel propagator，不手写解析器。初始化/通知/工具请求各为实际 HTTP 边界，不把初始化成功当作工具执行。

公共/人员入口始终创建新 Trace，忽略外来 traceparent/X-Request-Id/baggage；已认证受保护内部链可提取有效 traceparent。首版不传播业务身份 baggage或任意 tracestate。认证失败不采用远端业务 ID，仍有本地 opaque 请求 Trace。内部解析错误降级为新 root，不影响原请求拒绝规则。

MCP SDK transport 回调/线程可能不保留 ThreadLocal：dispatch CLIENT span包裹 initialize+callTool，client 建立时捕获不可变Context，customizeRequest 只在真实请求构造时注入该父上下文，不承担无法由该回调结束的逐请求span；server scope按 HTTP 请求隔离并传入工具 callback。必须用实际 SDK 双 JVM验收父子与工具工作线程关系，不能只检查 header 字符串。

MCP 工具身份只在服务认证、在线票据消费 GRANTED 后提升为可信业务属性；派发来自 incident 自身可信 DispatchClaim，callback 接收端仍重新绑定验证。_meta 七字段、consume/receipt 字段白名单、action-binding-v1、receipt digest和 Outbox schemaVersion=1 全部保持。

### 5. 异步、恢复与历史兼容

数据库队列不保存父 span；Worker 从已持久记录构造关联 scope，产生新的 Trace。每次恢复/重投产生新 attempt scope，复用 executionId/dispatchRequestId/eventId，日志标明阶段和当前尝试。Span Link 仅在内存中确有有效已捕获 SpanContext 时可用，不是跨重启门槛。

SimulationTimeoutEvaluator 的实际任务提交显式捕获 Context及已校验字段快照，在工作线程 makeCurrent 并 finally 恢复/关闭。取消调用不等于工作线程停止；清理在真正结束的线程完成。两并发/多次复用测试证明无身份泄漏。

历史 Timeline correlationId、Incident created event 的 target correlation 和诊断快照 correlation不回写；新日志分别给出当前关联实体与 incidentId，不追加第二套审计。Outbox envelope原样重发，仅发送阶段加 eventId/incidentId/attempt span，首版无业务消费者或 Broker 新门槛。

### 6. 错误与安全预算

标准 scope在 finally 结束，导出错误走异步处理；无自由文本异常/exception.stacktrace。只写固定 operation/outcome/reasonCode、已验证 ID、阶段数字和真假标志。不得打印 DispatchClaim、_meta、请求/回执 Map、参数 JSON 或诊断正文。固定 span 名与 route 模板，不把原 URL/目标/ID放进名称。

单 scope最多 24 个白名单字段，每个 ID/固定文本≤128字符，trace/span ID固定长度；metadata不符合资格则省略。秘密扫描覆盖本次真实会话、两向服务凭据、初次/替代/已消费票据及测试故障输出；不是只扫关键词。

## 验证和交付

详见 [quickstart.md](quickstart.md)。US1 可先独立交付关联日志；US2 验证短时调用链；US3 必须以现有安全恢复场景收尾。每项先最小有效测试，再扩大共享回归。正常链、拒绝、B06/B09及本地 Relay 为核心；无需真实 Broker、真实模型、生产部署或 Trace UI。

设计阶段只执行静态检查。2026-10-05 获批后已执行 analyze，并按 tasks 推进本地 implement；正式 DEC、实际命令和验收结果随实施归档。