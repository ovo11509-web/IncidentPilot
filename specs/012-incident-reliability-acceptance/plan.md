# 实施计划：本地可靠性专项验收

**分支**：`feature/incident-platform` | **日期**：2026-10-12 | **规格**：[spec.md](spec.md)
**输入**：`specs/012-incident-reliability-acceptance/spec.md`。用户批准前轮完整方案，保持实际Git分支/HEAD。

## 摘要
复用已有H证据与安全门禁，新增独立JSON规则预期和真实HTTP/告警进程/自有数据库暂停验收。性能先固定四组本机基线，不宣称容量上限。

## 技术背景
- 语言/版本：Java21执行、release17；PowerShell7。
- 依赖：现有JUnit5/Jackson/Testcontainers/HttpClient，不增加依赖。
- 存储：仅测试自有PostgreSQL及ignored target白名单结果。
- 测试：incident规则测试、bootstrap真实双JVM测试、H完整Backend。
- 平台：当前Windows；Linux/Java17/Node22不计本轮通过。
- 性能目标：完整分母、四组100唯一告警/10HTTP工作线程，历史0/500；不预设QPS/P95通过阈值。
- 约束：不触碰持久演示、不操作远程/付费、不改变业务协议/Schema、不提交/部署。

## 宪章检查
前后检查均通过：保留署名；测试实际持久化事实；人工审批与UNKNOWN协议不变；每轮有来源；仅自有安全资源。不增加业务架构。

## 项目结构
- `incident/src/test/resources/fixtures/reliability/aggregation-cases.json`：显式预期数据。
- `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentReliabilityAcceptanceTest.java`：复用IncidentDbFixture，同库隔离案例及处理排列。
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentReliabilityRuntimeTest.java`：HTTP、PID恢复、数据库暂停与四组基线。
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java`：只增加测试场景钩子，继续负责资源所有权/清理。
- `scripts/check-delivery.ps1`：增加两类必需检查，仍动态统计且21项跳过预算保持。
- 本Spec目录：矩阵、研究、数据定义、契约、快速指南、任务和实际实施记录。

## 复杂度跟踪
无宪章例外。沿用测试classpath启动，明确不冒称executable JAR故障；常驻真实JAR既有证据独立引用。