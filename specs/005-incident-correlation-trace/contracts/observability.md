# OBS-001 可观测契约草案

状态：待确认。只覆盖主应用 `/api/ragent/incident/**`（含agent.diagnosis Controller）与独立MCP `/mcp/incident`。不改变A/B/C/D业务正文和状态码。

## HTTP尝试标识

| 字段 | 规则 |
| --- | --- |
| Result.requestId | 服务端单次分配UUID，成功与范围内错误共用request attribute；不再由各Controller/Advice重复生成 |
| X-Request-Id响应header | 与该响应Result.requestId一致；早期非JSON/框架错误仍有该header和日志 |
| X-Trace-Id响应header | 仅当前有效录制Trace时输出，32位小写十六进制；关闭/无效时省略 |
| 入站X-Request-Id | 不采纳、不回显、不用于业务幂等 |
| 正文requestId/sourceRequestId | 保留原人工结论/来源意图/dispatch请求含义和校验，不用尝试UUID替换 |

同一次Controller→Service→Advice及ERROR/ASYNC分派共用尝试身份；分派scope清理后恢复外层上下文。主链无新增SSE契约。Result成功码仍逐接口为0或SUCCESS，不宽泛接受两种、不改上游全局客户端。

## W3C传播与信任边界

- 公共Webhook和人员入口不接续外来traceparent，产生新root；服务端选择采样，不允许外部采样位控制。
- 已认证 `/mcp/incident` 与 `/incident/internal/v1/**` 可接续有效traceparent。身份认证仍按现有规则先于工具/业务资格，不能因追踪header绕过。
- 使用已有版本OTel W3C propagator验证traceparent。上限256字符，多个重复header、格式错误、全零trace/span ID、无效版本降级为新root；仍执行原认证/业务验证。
- 首版不采纳/传播baggage与tracestate，不用业务ID组装traceId，不打印原传播字段。
- 出站scope使用已捕获的有效Context注入traceparent；回调每次尝试创建新的CLIENT span，HTTP请求尝试requestId由接收服务独立分配。
- Dispatch CLIENT span包裹实际MCP client initialize+callTool。transport customizeRequest只注入该span不可变父Context，不假设构造request时线程仍有ThreadLocal；初始化SERVER请求可以记录传输，但不带可信Execution字段/模拟执行节点。
- MCP SERVER scope须按单次HTTP请求进入工具callback；超时任务显式包裹捕获的Context，不能依赖全局Reactor hook。真实SDK父子传播在双JVM测试中证明。
- MCP `_meta` 仍精确七字段：actionId、incidentId、executionId、executionKey、requestId、ticket、bindingVersion。consume/receipt JSON不增加trace字段；不修改action-binding-v1与回执摘要。trace信息即使缺失/非法，不能改变原工具准入或安全状态。

## 白名单事件与属性

日志是单条结构化JSON事件，span属性与日志来自同一已校验快照；无任意Map追加接口。

| 分类 | 日志字段 | OTel属性 |
| --- | --- | --- |
| 运行 | service、operation、phase、outcome、reasonCode | service.name资源；ragent.operation/phase/outcome/reason_code |
| 当前请求/Trace | requestId、traceId、spanId | ragent.request_id；trace/span身份使用SDK标准字段 |
| 业务归属 | incidentId、alertId、correlationId、diagnosisId | ragent.incident_id/alert_id/correlation_id/diagnosis_id |
| 审批/执行 | actionId、approvalId、executionId、dispatchRequestId | ragent.action_id/approval_id/execution_id/dispatch_request_id |
| 来源/回执/事件 | sourceRequestId、receiptId、eventId | ragent.source_request_id/receipt_id/event_id |
| 尝试 | attempt、processingEpoch | ragent.attempt/processing_epoch |

每条记录最多24字段、字符串值≤128字符；requestId UUID36，trace/span固定格式。只填实际已知身份，空值省略。scope关闭恢复外层MDC，禁止泄漏到调度线程或下一次请求。OTel business字段是检索属性而不是度量标签，不新增高基数metric标签。

禁止：票据明文/摘要、服务凭据、Authorization/HMAC/签名、executionKey、原始URL及query、IP/用户姓名、targetId、参数JSON、payload/labels/summary、Runbook原文、模型prompt/response、任意异常文本/堆栈、自由文本审批/人工结论reason/evidenceRef。只输出现有安全枚举结果码；OBS不得套用上游Agent content serializer。

未认证请求只输出opaque request/trace、固定route/operation/结果；自报业务ID不进入可信字段。服务端权限与持久绑定验证完成后才能补充对应entity/Incident身份。

## 固定span边界

| span名称 | Kind | 边界与业务字段 |
| --- | --- | --- |
| incident.http | SERVER | 单次HTTP与固定route模板；requestId，认证/业务查询后可加实际实体 |
| incident.alert.receive | INTERNAL | 实际接收/幂等判定；alertId/correlationId，已关联时incidentId |
| incident.alert.process | INTERNAL | 每条processOne/故障记录；alertId/correlationId/incidentId/processingEpoch |
| incident.diagnosis | INTERNAL | begin→实际生成或幂等重读→complete；incidentId/diagnosisId |
| incident.runbook.retrieve | INTERNAL | 实际RunbookCorpus检索；不记录query/原文 |
| incident.diagnosis.generate / validate | INTERNAL | 实际抽取与证据校验；REUSED不创建generate/validate节点 |
| incident.action.propose / decide | INTERNAL | 选择/提议/人员决定事务调用，输出权威身份/结果 |
| incident.action.dispatch / recover / sweep / reconcile | INTERNAL | 每条实际Action阶段，从持久记录构造可信上下文 |
| incident.mcp.dispatch | CLIENT | 实际保护工具调用及初始化生命周期，不保留ticket/meta |
| incident.ticket.consume | INTERNAL | 原消费服务调用，GRANTED后补可信Action/Execution字段 |
| incident.mcp.execute | INTERNAL | GRANTED后实际operation工作线程，结束/超时语义如计划 |
| incident.callback.consume / receipt / reconcile | CLIENT | 每次实际HTTP回调/只读查询尝试，实际有效父Context |
| incident.receipt.accept | INTERNAL | 正常/响应双路径/迟到/重复/冲突结果，不重复宣称应用 |
| incident.outbox.publish | PRODUCER | 单条event的发送/标记/重试，eventId/incidentId及本次attempt |

另外人员状态转换/重放/人工结论可使用固定 incident.transition / alert.replay / action.manual span；纯查询只用HTTP span，不逐字段/分页行建span。无业务操作的worker轮询不创建业务span。手动对账保持只读，有有效结果才追加既有事实。

业务拒绝/UNKNOWN用明确outcome，不能凭span ERROR判断副作用FAILED。SDK异常记固定安全reasonCode，禁用recordException原文。断进程的未结束span允许缺失，不能补发虚构历史成功节点。

## 兼容与故障

- 默认OBS关闭也统一请求身份/业务安全日志；既有Agent/RAG开关独立。
- 运行期Exporter异常、饱和、停止，仅影响遥测；不进入业务事务、改变业务exception或触发写重试。
- 单个进程一SDK owner，组合开关按plan矩阵验证；显式错误配置启动校验与运行期出口失败分开报告。
- 无新业务表/trace上下文列；Outbox envelope及已有digest/hash原样，消费前重试/UNKNOWN/只追加规则不变。