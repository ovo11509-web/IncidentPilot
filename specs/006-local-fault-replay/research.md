# 现状、作者审阅与方案取舍

状态：2026-10-05 用户“执行”批准并完成本地验收；采用方案见DEC-029及实施报告，以下保留设计取舍。

## OBS-001 作者走读

1. `framework/.../observability/ObservationScope.java` 从 request attribute 复用服务端请求 UUID；可信 scope 内才填充业务身份。两个 Alert 聚合后仍保留各自 correlationId，用 incidentId 汇总，不能用一次 HTTP UUID 表示整个 Incident。
2. `mcp-server/.../simulation/SimulationTimeoutEvaluator.java` 在工作线程实际退出时关闭 execute scope，finally 才递减 finished latch；cancel(true) 只提出中断请求。没有停止及回滚正面证明就返回 UNKNOWN，不能据 Trace 缺失推断安全重试。
3. `incident/.../action/worker/ActionDispatchRecoveryWorker.java` 恢复原 executionId/dispatchRequestId；`IncidentObservabilityIsolatedTest#actualWorkerCrashRecoversStableExecutionInNewTrace` 断言新 Trace 和原身份，并核对实际应用一次。持续父子关系只属于真实连续调用。

此为当前源码静态走读。既有实际运行记录见 [Spec E 实施报告](../005-incident-correlation-trace/implementation-report.md)；本轮未新运行 Java 门禁或认定用户已经理解上述内容。

## 已有能力与缺口

- `specs/002-approval-safe-simulation/scripts/collect-evidence.ps1` 可归档固定的完整 B 目录；它要求18类、多个证明齐全，拒绝失败日志/过期 XML，但仅生成成功清单，不能作为选定单场景通用运行入口。
- `AlertRecoveryTest` 两项真实数据库检查覆盖已提交告警的 Worker 恢复、五次重试、终止与并发人工重放；并不是完整 JVM 杀进程演练。
- `OutboxRecoveryTest` 覆盖真实 Relay、数据库触发器导致发布标记失败和过期 claim；Producer 为固定替身，没有 Broker。
- `BootstrapIsolatedStartupTest` 已能运行安全本地双 JVM，生成 UUID 证明目录及白名单数据库快照。
- `IncidentObservabilityIsolatedTest` 四项覆盖黄金链、安全拒绝、Worker 崩溃、回执通道，并导出实际 SpanData。首版直接复用这四项，保留实际秘密扫描。
- 当前不同目录与 schema 由历史功能拥有。新实验室应建立统一引用和本轮来源契约，保留原始证明，不重写历史或把所有 JSON 强行转换为同一业务模型。

## 三种方案

| 方案 | 收益 | 代价 | 推荐 |
| --- | --- | --- | --- |
| 只补命令文档 | 几乎无需维护代码 | 单场景选择、失败归档和来源核验仍靠人工 | 作为对照，不足以满足统一入口 |
| 本地 PowerShell 入口及 Markdown/JSON 报告 | 复用 Maven/JUnit/Testcontainers、现有证据及 Windows 脚本习惯 | 需严格处理来源、退出码、故障结果和路径 | 首版推荐 |
| 管理员页面及服务端任务调度 | 浏览器可操作 | 新增权限、进程/资源隔离、持久运行状态和部署风险 | 延后，独立设计 |

## 推荐设计

采用一个脚本、一个固定场景清单和一组脚本契约检查，不新增依赖、Java 模块、生产服务或故障插件体系。仅必要改动现有测试的输出位置与证明投影。

来源判断使用入口分配的 runId、scenarioId、测试 JVM 写入的本轮证明标记和运行前后源码摘要；XML来自本轮隔离报告目录，绑定精确测试名。修改时间只能辅助，不能当作唯一来源。

失败运行也有安全结果与原始证据；已有成功专用收集器保持原行为。正式结论按证明逐项计算，不能只搜索 BUILD SUCCESS。先完成来源校验，再生成可读报告；只读验证不得修改归档。

首版支持六类场景和既有故障点，不能接收任意 Maven 参数、JDBC URL、远程地址或 shell 片段。仓库锁与唯一目录解决现有 Maven 默认输出碰撞，不扩展成并行调度平台。

## 不确定性与限制

- 本地命令入口仍待用户确认；本草案不代表 LAB-001 实施已授权。
- Testcontainers 镜像、启动耗时和资源占用沿用已有环境；运行时如环境不足，按受阻报告，不能替换为开发数据库。
- 明确保存证据层级，特别是告警恢复直接服务调用、Outbox 固定 Producer、回执三个子执行与跨重启允许缺失 Span。
