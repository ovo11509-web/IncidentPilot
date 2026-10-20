# OBS-001 研究：现状证据与待确认选择

日期：2026-10-05。以下保留设计阶段的只读盘点及方案比较；随后用户以“实施”确认推荐方案，见 DEC-028 和 tasks 实施记录。历史盘点不等于本次运行验收。

## 基线及信息来源

- 实际分支 `feature/incident-platform`，HEAD `67624931f20812922568d3641a095adb291263ba`，暂存为空。起始 35 个 tracked 修改文件、大量 untracked；1744 个现存 tracked/untracked 文件建立 SHA-256 保留基线。未切分支、清理或覆盖其他工作。
- 已读根 AGENTS、PROJECT_CONTEXT、TODO、ARCHITECTURE、DECISIONS、NOTES、testing、changelog、A/B/C 相关合同、当前 Schema和调用者。长期规则见 [宪章](../../.specify/memory/constitution.md)。
- 最新本地验收为 [Spec B 证据索引](../002-approval-safe-simulation/evidence-index.md) 和 [changelog](../../docs/changelog.md) 的 2026-10-05 收尾：本机已有 `incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/manifest.json` 白名单元数据核对为 PASS/退出0，54项、0失败/错误/跳过，finishedAt 10:35:15+08:00，JDK21/release17、隔离容器与独立 JVM。B10 部分身份/证据、B11 Producer 为替身；本轮没有复跑，也不是 OBS 证据。
- Trace、RAG Trace 和 AgentScope 编排基础属于上游能力；Incident 主链关联与新增追踪属于拟新增贡献。

## 当前实现能力矩阵

| 边界 | 已确认能力与代码 | 缺口/设计影响 |
| --- | --- | --- |
| 告警接入 | `incident/.../alert/web/AlertIngressFilter.java:44,66-67` 分配 request attribute；`AlertReceiveService.java:60,122-126` 分配/复用 Alert correlation | filter只覆盖接入；其他请求 helper可能每次取值都新建 UUID |
| HTTP 响应 | `AlertController.java:132-134` 缺 attribute时新 UUID；`core/web/IncidentController.java:208-210` 总新 UUID；`action/web/ActionController.java` 多处新 UUID；`diagnosis/web/DiagnosisResponses.java:24-27` 和 `DiagnosisErrorAdvice.java:28-30` 自行分配 | 没有共享入口尝试标识及 MCP Trace header，正文 source/dispatch requestId不能替代 |
| Incident 聚合 | `core/service/IncidentAggregationService.java:120` 保存 episode anchor；`:130,132` created事实使用当前处理 target correlation | Incident 无独立 correlation列；创建事件不必来自 anchor。不能回写历史统一值 |
| 诊断 | `diagnosis/service/IncidentSnapshotService.java:46-48` 从首条 PROCESSED member取 correlation；`alert/dao/AlertMapper.java:126-129` 排序/过滤；`agent/.../diagnosis/IncidentDiagnosisOrchestrator.java:30-39` 同步实际生成 | 首成员不一定是anchor。诊断不经过 AgentScope/ReAct Trace中间件；禁止重写冻结快照/hash |
| Action/票据 | `action/service/ActionDecisionService.java:50-52` 取持久 anchor关联；`:37` 参数名虽为 correlationId但不作可信来源 | Controller传入UUID不能据此声称审批事实保存该UUID。保持数据库可信绑定 |
| 调度/恢复 | `action/worker/ActionDispatchWorker.java:87-101,119-129` 保存逻辑Execution及dispatchRequestId；`ActionDispatchRecoveryWorker.java:125-128` 复用身份、替票据 | 日志须区分当前尝试和逻辑request；不能改签票规则、复用秘密或扩大重试 |
| Outbox | `outbox/service/IncidentEventWriter.java:70-86`、`action/timeline/ActionOutboxWriter.java:89-105` 稳定envelope；`outbox/worker/OutboxRelay.java` 提交后事务外发送、重发原payload | 无 traceparent保存/消费者；仅观测单条event发送，不改payload/digest或依赖Broker |
| 受保护 MCP | `agent/.../tool/IncidentRemediationDispatcher.java:65-67,100-107` 传七字段meta、受保护HTTP；`mcp/.../simulation/ProtectedSimulationTool.java:23-24,66,91` 精确keySet并全量转consume | 直接加meta字段会拒绝/污染consume，推荐标准HTTP header，不修改业务正文 |
| 回调 | `mcp/.../simulation/IncidentCallbackClient.java:89-94` 原生HttpClient，只有认证/Content-Type；`incident/.../action/web/ActionInternalController.java:51-55,96-106` 白名单 | 需要明确CLIENT/SERVER传播；认证与正文绑定继续强制 |
| 模拟执行线程 | `mcp/.../simulation/SimulationTimeoutEvaluator.java:23-29,69-77` 有界原生线程池 | 无显式OTel/MDC包装，取消不能证明停止；需要实际完成时结束和清理 |
| RAG Trace | `framework/.../trace/RagTraceContext.java` TTL和节点栈；`rag/.../trace/StreamChatTraceRunner.java:89` Snowflake traceId；`resources/database/schema_pg.sql:349,372` 自有Trace表 | 自有ragTraceId不是OTel32位traceId，普通聊天协议不纳入统一改造 |
| Agent Trace | `agent/.../trace/AgentTracingConfiguration.java:49-50,97-107,118-132` AgentEngine+开关门控、Langfuse必填、Global注册；`AgentRunTracer.java:71,140-145` Reactor与task/message身份 | 不可无协调新增第二个SDK；现有装配未设置W3C propagator，官方桥接不覆盖Scheduled/HttpClient/timeout线程 |
| 依赖/开关 | `pom.xml:33,81-86` OTel1.62.0；`agent/pom.xml:36-44` 自持SDK/exporter；`bootstrap/src/main/resources/application.yaml:81-90,214-216` Agent默认关闭、RAG默认开启 | framework/incident/MCP当前无显式OTel依赖；不复制默认凭据，不自动启用远程出口 |

表中 `incident/...`、`agent/...`、`mcp/...` 分别以该模块现有包路径为前缀，仅用于压缩证据表；实施任务提供完整文件路径。

## 文档歧义与兼容策略

Spec A spec第63行描述 correlationId 在Alert及其形成Incident流程中稳定；该规则支持Alert重放/幂等，但不能由此推出同一Incident全部历史记录同值。代码显示 created target、PROCESSED首成员和episode anchor三种派生来源。旧文档的“全链路/全局”表述需按标识层级理解。

待确认推荐：新OBS契约使用incidentId作为聚合后统一检索键，保留已有correlationId的原值和局部事实语义。新采集既输出entityId也输出incidentId，接收阶段通过attachment建立桥接。未获批前不修改DEC/架构正文或旧Spec；若用户要求真正单一correlationId，必须重新确认兼容、历史数据和Schema范围。

ARCHITECTURE 的旧 Agent流程及部分 NOTES保留早期时点，后续段落已补服务端票据协议；OBS以当前ProtectedSimulationTool/服务绑定为准，不重新启用旧写工具。TODO顶部日期旧于10-05收尾段，本轮只把OBS更新为设计待确认，不把历史进行中段落当当前缺口。

## Brainstorming：三个方案

| 方案 | 收益 | 成本与边界 | 建议 |
| --- | --- | --- | --- |
| A：分层身份+短时Trace+安全日志 | 从业务ID查到阶段，连续跨进程可看真实父子；可独立本地验收 | 需协调SDK owner、入口/线程/HTTP；跨重启不保证旧Trace可见 | 推荐，符合OBS和现有安全主链 |
| B：只统一日志与HTTP请求标识 | 变更最少、完全不依赖Exporter | 无真实跨进程span和阶段父子，不能完成“与Trace” | 可作US1增量，不能关闭OBS |
| C：持久追踪上下文+所有聊天+查询平台 | 提供跨数据库队列link和统一追踪入口 | 新Schema/契约/历史兼容、UI及部署范围显著增加 | 不符合用户选定首版，延期 |

## 待确认技术选择及理由

1. **统一业务检索键**：采用incidentId；理由是现有持久关系已正确且不存在全事件唯一correlation；替代方案是anchor衍生incidentCorrelationId，额外别名没有首版收益。
2. **异步恢复**：新root+稳定业务字段；理由是无持久trace上下文也能定位执行；不从业务数字ID编码OTel ID，不伪造跨重启Link。
3. **跨进程传播**：只在已认证内部链使用W3C traceparent；理由是避开严格MCP meta与consume/receipt合同。无baggage，无调用者业务身份授权。
4. **SDK复用**：保持Agent单开模式，双开时一owner多exporter；理由是Global实例唯一和上游AgentScope官方读取方式。不装Java Agent、不增平台模块。
5. **本地验收出口**：JUnit collecting exporter及双JVM测试exporter输出真实SpanData；理由是能验证父子和安全而不部署Collector。不把这组结果称作真实OTLP后端持久化或Langfuse验收。
6. **无Schema**：现有关系足够串联，trace是辅助；网络出口不可用不能导致业务失败。数据库队列仅加运行scope，不改变历史或Outbox重发内容。

## 核对的外部标准

2026-10-05只读打开官方来源：

- [W3C Trace Context](https://www.w3.org/TR/trace-context/)：traceparent传播独立于业务ID；trace-id/parent-id格式及无效输入处理。
- [OpenTelemetry Traces](https://opentelemetry.io/docs/concepts/signals/traces/)：span/parent/link的关系。
- [OpenTelemetry Java API](https://opentelemetry.io/docs/languages/java/api/)：Context、Scope及显式传播。

使用标准格式不代表当前库/框架会自动贯穿Scheduled、SDK callback或timeout线程；适配必须通过当前依赖及实际双JVM测试。未调用付费服务、开发库或真实基础设施。