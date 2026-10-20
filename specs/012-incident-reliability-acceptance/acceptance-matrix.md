# 可靠性要求与已有证据矩阵

基线run：`f989e36a-9e11-4f92-a719-2149762870f1`；2026-10-12只读核对1360个受检文件零变化。它不包含本Spec新增测试。

| 要求 | 已有检查 | 证据层级 | 本轮缺口/工作 |
| --- | --- | --- | --- |
| 唯一受理/冲突不覆写 | AlertIdempotencyTest | PG/领域服务线程池 | 固定载荷真实HTTP并发 |
| 30分钟/封存/FAILED分组 | IncidentAggregationTest、AggregationClockTest | PG/受控时间 | 独立固定预期及调度排列 |
| 事务完整性/状态并发 | IncidentAtomicityTest、IncidentTransitionTest | PG/事务/并发 | 复用Backend，不改协议 |
| 审批与参数完整性 | ActionApprovalRaceTest、ActionDispatchTicketTest | PG/服务 | 复用，不重复建设 |
| 单次模拟副作用 | ProtectedSimulationE2ETest、IncidentObservabilityIsolatedTest | 本机HTTP/双JVM/模拟库 | 每逻辑Execution≤1，成功恰好1，拒绝0 |
| 未知结果保持与可信收敛 | ActionUnknownConvergenceTest、三通道故障 | PG/双JVM，部分时间fixture | 无证据保持UNKNOWN，不要求全部成功 |
| 告警恢复 | AlertRecoveryTest | PG/直接服务，不是完整进程崩溃 | 确认受理后真实应用PID中断重启 |
| Outbox恢复 | OutboxRecoveryTest | PG/Relay/固定Producer | 不证明真实Broker/消费者 |
| 数据库不可用恢复 | 未找到完整实际暂停入口 | TBD | 新自有临时容器暂停/恢复 |
| 真实JAR常驻启动/重启 | Spec K记录 | JAR/dist/同库持久演示 | 保留既有证据，不操作其资源 |
| 热键历史与多键基线 | 未有量化基线 | TBD | 四组固定工作负载；不是容量上限 |
| 原始证据来源/跳过保护 | H入口及契约 | XML/源码哈希/安全摘要 | 新两类纳入必需列表 |

分母：独立输入身份清单与持久受理身份核对；受理=PROCESSED+FAILED+明确未结，分别统计。不能只对同一数据库自加计数证明没有丢失。
来源：Spec A/B/F/H/K、实际代码和该run的新XML；场景稳定性次数不冒称生产可靠率。

## 本轮闭合结果

最终Backend `1d0cd914-3565-4e70-9f73-e98fe4db02b9` PASS。上述HTTP、独立规则、受理后进程恢复、自有数据库暂停及热键/多键基线缺口已在声明的本机测试层级补齐；数据库不可用入口不再TBD，具体实现为`IncidentReliabilityRuntimeTest`。既有事务/状态/审批/单次副作用/UNKNOWN/Outbox检查同轮复核，未用固定Producer代替真实Broker证据。

15个规则case各3组、40重复请求唯一受理、冲突409、进程及积压恢复重投0、四组400样本全部完成；统计及未验证范围见 [实施记录](implementation-report.md)。本表“本轮缺口/工作”保存实施前缺口，不能理解为最终仍缺失；真实JAR故障、生产分布、容量上限与跨平台继续保留边界。
