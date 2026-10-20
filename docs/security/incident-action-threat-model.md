# Incident / Action 安全模拟威胁模型

日期：2026-10-04。范围：已批准 Spec A/B/C/D 的当前本地实现，重点是人工审批到 `simulation.set-state` 的协议。本文是作者对代码和实际测试的核对，不是第三方安全认证；外部安全评审为 TBD。没有新增权限体系、数据库表或真实基础设施能力。

依据：[宪章](../../.specify/memory/constitution.md)、[Spec B](../../specs/002-approval-safe-simulation/spec.md)、[执行合同](../../specs/002-approval-safe-simulation/contracts/)、[决策](../DECISIONS.md)中的 DEC-011/012/019/020/026/027。运行证据入口见 [B01～B12 索引](../../specs/002-approval-safe-simulation/evidence-index.md)，测试分层见 [测试指南](../development/testing.md)。

## 资产与信任边界

| 资产 | 权威持有者 | 必须保持的属性 |
| --- | --- | --- |
| Incident、Action、冻结目标和白名单参数 | 主 PostgreSQL / Incident 模块 | 浏览器、诊断文本不能补造或替换；Action 成功不自动解决 Incident |
| Approval、授权截止与批准者 | Incident 模块 | 独立人员审批；过期、拒绝或关闭先胜出时不能新启动 |
| Execution、executionKey、requestId | Incident 模块 | 同一执行身份保持；已消费的执行不能因未知结果重新派发写工具 |
| 一次性 Ticket | Incident 持久化摘要及调度进程临时明文 | 服务身份加在线绑定验证；只消费一次；明文不进入人员接口或日志 |
| 模拟目标、应用记录及结构化回执 | 隔离模拟库 / 受保护 MCP | 应用最多一次；回执绑定执行；确定结果不能被相反结论覆盖 |
| Timeline 和 Outbox | 主数据库 | 只追加业务事实；重复接收不重复追加；发布重试保持事件 ID 和 envelope |
| 人员会话、双向服务凭据、TLS 信任材料 | 现有认证与运行环境 | 不泄露；方向分离；远程服务地址必须使用 TLS |

浏览器跨人员认证边界调用主应用。服务端重新判断建议资格、读取持久化目标和参数、校验版本并提交审批。诊断只能提供可引用的候选与建议，不确认根因、不批准 Action、不调用写工具。

主应用 Worker 经另一服务边界调用专用受保护 MCP。MCP 认证请求后向主应用在线消费票据，校验版本、目标、参数摘要、Execution 与执行键等绑定。普通 MCP 注册表只开放已核查的只读工具；旧确认卡不能授予受保护工具的执行权限。

票据消费后，模拟数据库事务形成应用与结果证据。回调及普通工具响应都使用同一结构化回执。截止清扫无法证明结果时进入 UNKNOWN，只读对账可查询事实；人工结论须有当前管理员权限、版本与证据。不存在由 UNKNOWN 自动重试写操作的路径。

## 威胁、控制与回归映射

下表的测试类均位于对应模块 `src/test/java`；具体路径、实际运行层级和历史结果见证据索引。测试通过只支持表中明确的边界，不支持所有未来攻击变体。

| ID / 威胁 | 当前控制与代码定位 | 回归入口 / 场景 |
| --- | --- | --- |
| TM-01 伪造人员或服务身份，绕过审批 | 现有人员登录/管理员校验；`ActionServiceAuthentication`、`ProtectedServiceAuthentication` 分别校验方向凭据，认证前不信任自报执行 ID | ActionServiceAuthenticationTest、ProtectedSimulationRouteTest；B02/B04 |
| TM-02 篡改已批准工具、目标、参数或执行绑定 | `ActionTicketConsumptionService` 读取冻结持久化绑定；`ProtectedSimulationTool` 核对请求，不从模型文本恢复参数 | ActionBindingV1Test、ProtectedSimulationE2ETest；B05 |
| TM-03 重放、并发消费或补票引发第二次应用 | 数据库唯一约束、Incident 事务守卫、一次性票据；`ActionDispatchRecoveryWorker` 保持原执行且仅恢复未消费票据 | ActionRecoveryRaceTest、真实 Worker 崩溃恢复；B05/B06 |
| TM-04 关闭、批准、消费和到期竞争 | 统一数据库守卫校验状态/版本和截止；关闭先提交阻止新启动，消费后仍接收结果 | ActionApprovalRaceTest、BootstrapIsolatedStartupTest；B02/B03 |
| TM-05 通过旧 MCP、确认卡或新增注册旁路写工具 | `McpServerConfig` 和 `ProtectedSimulationMcpConfig` 分离普通/受保护入口，未知注册失败关闭 | ProtectedSimulationRouteTest、ProtectedSimulationE2ETest；B04 |
| TM-06 将网络超时当失败或自动重试已消费写操作 | `IncidentRemediationDispatcher` 不重调已消费执行；`ActionReceiptDeadlineWorker` 标 UNKNOWN；失败结论需正面回滚证明 | Dispatcher、ProtectedSimulationE2ETest；B07/B08/B09 |
| TM-07 回调/响应丢失、迟到、重复或反向回执 | `ActionReceiptService` 校验身份与绑定，唯一回执/事件键；确定终态冲突仅审计；与截止共享数据库锁 | ActionReceiptTest、B09 三通道故障；回执先到与清扫先到均验证 |
| TM-08 利用只读对账或人工结论覆盖确定结果 | `ActionReconciliationService` 只读获取证据；`ActionManualConclusionService` 限管理员、UNKNOWN、版本、幂等 requestId 及有界证据字段 | ActionUnknownConvergenceTest、ProtectedSimulationE2ETest；B10 |
| TM-09 发布失败丢失业务事实或产生不同事件 | `OutboxRelay` 在提交后发布，租约领取、失败重排；eventId/envelope 持久化后保持，允许至少一次投递 | ActionOutboxCompatibilityTest、OutboxRecoveryTest；B11 |
| TM-10 响应、日志、证据暴露凭据或不安全参数 | 安全 DTO 投影、参数白名单、票据摘要存储、内部认证和安全 URL 校验；秘密仅在隔离测试进程内逐项扫描 | ActionSecretLeakTest、完整 Worker 日志扫描和双向 HTTPS；B12 |
| TM-11 诊断/Runbook 注入指令，或引用撤回后仍选建议 | `DiagnosisActionSelectionService` 服务端重校验范围、版本、资格；历史引用可查看不授予当前选择权限，审批另行完成 | Spec C 安全契约与 Spec D 浏览器/接口记录 |

## 2026-10-04 新增故障证明的边界

回执/清扫测试使用真实临时 PostgreSQL、事务锁和 `pg_stat_activity` 的 Lock 等待。分别确定回执先提交与清扫先提交，后者追加 UNKNOWN 再接收迟到成功；最终回执唯一，重复无新事实。执行截止由测试夹具 SQL 调整，回执由测试构造，只读请求由计数替身观察；这里没有真实 MCP 应用，不能用此入口证明模拟应用次数。真实自然截止和一次应用由此前 B09 完整 Worker/MCP 制品支持。

B11 使用真实 `OutboxRelay`、实际业务事务、临时 PostgreSQL 和数据库触发器。先让测试 Producer 发送失败，再让发送返回成功但数据库拒绝 PUBLISHED 更新，最后恢复发布。两种 Action 事件各经历三次尝试，事件 ID/envelope 保持，审批/成功回执事实不回滚。Producer 是替身，没有真实 Broker 或消费者投递证明；至少一次投递要求下游按稳定事件 ID 幂等消费。

## 信任假设与剩余风险

- 主应用、受保护 MCP、数据库宿主及其服务秘密在当前模型中可信。被攻陷且持有合法身份的 MCP 可以伪造证据；当前协议不是抵御恶意授权执行器的拜占庭证明。
- 人员审批沿用现有管理员体系；人工 UNKNOWN 结论映射当前管理员业务权限，未声称实现独立角色、双人审批或多租户隔离。管理员滥权、服务秘密失窃需要运维与更完整权限治理。
- 双向 TLS 已在本机隔离 JVM / 回环别名验证，不能外推跨主机证书部署、外部日志平台、真实轮换值班流程或生产网络策略。
- 自动只读对账可能迅速收敛 UNKNOWN。要观察持续未知必须有证据不可得条件；不能通过删除应用记录制造假象，也不能把缺少记录当未应用证明。
- Outbox 业务事实恢复不等于消息只投递一次。真实 Broker 宕机、生产消费者重复投递与全链路压测为 TBD；不能由测试 Producer 推断吞吐或高可用指标。
- 分布式限流、拒绝服务、备份恢复、模拟库丢失后的运维恢复与真实基础设施工具均未在本轮验收。未来扩展需另行设计和授权。
- Runbook 为本地模拟语料，诊断为受限实现；没有付费模型、真实生产根因确认或未知分布质量证明。

## 失败关闭与作者核对

发现未经审批的应用、同一执行第二次应用、确定结果被覆盖、无证明判 FAILED，或秘密泄露时停止验收并保留失败证据。UNKNOWN 只能等待可信迟到回执、只读证据或有权限人工结论；页面不提供自动写重试。

作者核对：代码入口与回归映射已核查，新增证明与替身边界单独说明；实际命令、结果和未验证范围由证据索引记录。未改生产代码或 Schema，没有提交、推送或部署。人工/第三方安全审阅结论为 TBD，不代签审阅者清单。

## 2026-10-05 证据更新

本轮 54 项定向检查和七个完整应用入口通过，B01～B12 统一清单见证据索引。独立 MCP 测试扫描本次六张票据与双向凭据；完整普通/TLS 链路补扫消费先于关闭的票据，各场景进程日志/快照命中 0。历史报告不冒充本轮执行，失败 Docker 环境记录保留；实际替身、fixture 截止和本机 HTTPS 的边界保持。

LAB-000/ACT-003 当前本地范围可收尾。此更新不修改信任假设：授权执行器被攻陷、真实 Broker、跨主机部署、生产权限/密钥运维及第三方评审仍未验证；不据测试通过扩大安全保证。
