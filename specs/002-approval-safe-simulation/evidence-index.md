# Spec B 安全模拟证据索引

更新日期：2026-10-05。本文件索引已有 Spec B 场景与实际执行记录，不另建规格体系。依据 [quickstart](quickstart.md)、[tasks](tasks.md)、[项目变更记录](../../docs/changelog.md)；控制及信任假设见 [威胁模型](../../docs/security/incident-action-threat-model.md)。最新统一清单见文末“2026-10-05 验收收尾”；下方 2026-10-04 盘点与运行记录保留历史时点含义。

## 证据阅读规则

- `target/` 是本机生成制品，干净检出时可能不存在，需按入口重跑。制品不提交秘密，不把截图或 fixture 当真实执行证明。
- 历史记录只支持当时测试与环境，不冒充本轮重跑。失败日志永久保留其失败含义；命名含 final 不等于成功。
- PostgreSQL 真实不代表所有边界真实。固定回执、认证/发布替身和实际 MCP/JVM 分开标注。未覆盖的攻击变体、部署和性能均为 TBD。
- 本轮联合报告以归档清单中的 Surefire XML 为准；目录内其他旧 XML 不计入本轮数量。

## 2026-10-04 盘点：B01～B12 实际入口及边界

Java 入口：`incident` 的 Action 集成测试位于 `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/`；完整应用测试位于 `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java`。表中的类/方法是可搜索执行标识。

| 场景 | 实际入口 / 证据 | 已观察的层级 | 限制或本轮重跑状态 |
| --- | --- | --- | --- |
| B01 正常执行 | BootstrapIsolatedStartupTest#approvedActionDispatchesOnceThroughRealBootstrapWorkerAndMcp；[Spec D 记录](../004-incident-operations-ui/quickstart.md) | 历史完整 Worker/MCP 与真实隔离浏览器；拒绝 0、批准/重复/刷新 1 应用 | 本轮 ProtectedSimulationE2ETest 重跑独立 MCP，未重跑浏览器和完整 Bootstrap |
| B02 拒绝/过期 | Bootstrap 同类普通 Worker、authorizationDeadlineRejectsPendingMcpConsumption、ticketDeadlineRejectsPendingMcpConsumption；ActionApprovalRaceTest | 历史人员 HTTP、自然截止、独立 MCP；本轮真实数据库竞争 | 未覆盖所有未来时间配置和部署变体 |
| B03 关闭竞争 | Bootstrap 普通 Worker 用例；ActionApprovalRaceTest | 历史消费前/后关闭 HTTP；本轮领域锁竞争 | 不声明任意分布式调度全部交错都穷举 |
| B04 旁路 | ProtectedSimulationRouteTest；ProtectedSimulationE2ETest | 本轮真实受保护 MCP；普通注册表/旧写工具/未知注册失败关闭 | 没有新增真实基础设施工具；未来注册仍需审查 |
| B05 篡改/重放 | ActionBindingV1Test；ProtectedSimulationE2ETest；Bootstrap 普通 Worker 用例 | 本轮绑定/重放与独立 MCP；历史完整在线消费 11 个字段负例 | 测试集合不等于所有任意载荷攻击穷举 |
| B06 补票竞争 | ActionRecoveryRaceTest；[B06 复现](quickstart.md#6-b06-补充复现入口)；`bootstrap/target/b06-evaluation/3fbfcb09-8099-4b0a-9bfc-f1f381bb9fc0/` | 本轮真实数据库双顺序；历史真正杀 Worker JVM、重启原调度器，同执行一次应用 | 最近联合重跑见 B09 manifest；本轮不再重跑完整 Bootstrap |
| B07 消费后故障 | ProtectedSimulationE2ETest | 本轮跨 JVM 消费后故障、不重调与 UNKNOWN | 精简主服务，不等于完整 Bootstrap 的每一种宕机点 |
| B08 超时证明 | ProtectedSimulationE2ETest；历史完整 Bootstrap/MCP 记录见 changelog | 本轮独立 MCP、真实锁等待，回滚证明 FAILED 与证据不足 UNKNOWN | 缺少应用记录不是回滚证明；非性能测量 |
| B09 双路径/迟到/截止竞争 | [三通道复现](quickstart.md#7-b09-双通道故障补充复现入口)；`bootstrap/target/b09-evaluation/9963cf7f-016e-4cf1-90da-1375a452a740/`；ActionReceiptTest | 历史完整 Worker 的回调 503、响应 EOF、双丢失；本轮数据库真实锁两顺序及重复回执 | 持续 UNKNOWN 的历史场景另注入只读对账不可用；本轮竞争回执是 fixture、截止 SQL 调整，没有 MCP 应用 |
| B10 对账/人工结论 | ActionUnknownConvergenceTest；ProtectedSimulationE2ETest；B09 双丢失 | 本轮真实数据库的证据收敛/管理员人工结论，独立 MCP 只读查询；历史自动真实对账 | 非独立权限体系；跨主机人员人工结论全流程和更多证据治理为 TBD |
| B11 发布恢复 | ActionOutboxCompatibilityTest#realRelayRecoversBothActionEventsAfterSendAndPublishMarkFailures；OutboxRecoveryTest | 本轮真实 Relay、PostgreSQL 发布标记触发器失败，审批/回执两事件稳定重投；兼容 A | Producer 为替身，不计真实 Broker/消费者恢复；无 MCP 应用 |
| B12 保密/会话恢复 | ActionSecretLeakTest、ActionServiceAuthenticationTest；Bootstrap#approvedActionDispatchesOverBidirectionalTls；Spec D 刷新 | 本轮安全投影/认证回归；历史重启人员查询及逐秘密日志扫描，双向 HTTPS 完整 Worker/MCP | TLS 是本机隔离回环；外部日志、跨主机证书与生产秘密轮换未运行 |

## 2026-10-04 G03～G06 运行清单

最小检查依次执行，Maven 退出 0：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionReceiptTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=ActionOutboxCompatibilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

分别 5 项、2 项通过，0 失败/错误/跳过。日志 `incident/target/safety-receipt-race.log` 与 `incident/target/safety-action-relay.log`。新增三个测试分别生成 `incident/target/safety-closeout/receipt-race-<runId>/`（两次序）、`action-relay-<runId>/`；summary、Timeline/Outbox 和发送尝试均在断言成功后写入。

适用共享回归命令：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=ActionReceiptTest,ActionOutboxCompatibilityTest,OutboxRecoveryTest,ActionUnknownConvergenceTest,ActionRecoveryRaceTest,ActionApprovalRaceTest,ActionSecretLeakTest,ActionServiceAuthenticationTest,ActionBindingV1Test,ActionEventWriterTest,ActionStateMachineTest,ProtectedSimulationRouteTest,ProtectedSimulationE2ETest,IncidentRemediationDispatcherTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

2026-10-04 23:21:41 联合命令退出 0，3m43s，38 项（MCP 6、Incident 29、Agent 3）0 失败/错误/跳过；14 个选定类均有本轮 XML。归档 [manifest](../../incident/target/safety-closeout/run-742defc2-0468-4e47-b99d-34cb1fe77d8a/manifest.json) 含 XML、三个证明目录、三份 Maven 日志及 SHA256 清单。原始日志 `incident/target/safety-closeout-regression.log`。仅使用 Testcontainers 临时 PostgreSQL 与本机独立 MCP，未连接用户开发数据库或付费服务；无需改生产协议、前端或 Schema。没有重跑前端、浏览器、完整 Bootstrap 或默认全仓门禁。

## 统一查阅字段与证据缺失

本轮归档 manifest 包含 runId、场景/任务、命令、起止时间、环境和替身边界、选定 XML 的测试结果、证明目录及哈希。逐场景 summary 保存执行/回执/Timeline/Outbox 身份、状态和次数；B11 另保存原 envelope 与各次发送内容。纯数据库竞争场景把真实应用次数标为不适用，不能补造一个零或一作为执行证明。

历史 B06/B09 的原 manifest、HTTP/通道故障计数及应用记录按原路径查看；历史其他 B01～B12 并非全部具有同格式独立 manifest。缺失的逐场景完整运行清单为 TBD，后续在 LAB-000 补齐，不能由本索引反推已生成。B11 真实 Broker 联合链路、跨主机部署、生产吞吐和外部日志不在本轮已验证范围。

## 2026-10-05 验收收尾：本轮统一清单

实际联合回归 54 项（MCP 6、Incident 38、Agent 3、Bootstrap 7），0 失败/错误/跳过，Maven 退出 0，14m29s，10:34:56 +08:00 结束。新增证据最小检查先跑 5 项和完整普通 Worker 1 项，均通过。首次最小检查在 Docker 创建数据库前报 5 个环境错误；`docker desktop start` 启动引擎后重跑，失败日志/XML 保留于 `incident/target/catalogue-first-environment-failure/`，不算通过。

[根 manifest](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/manifest.json) 包含实际命令、起止时间、环境、dirty 工作区说明、源码哈希、18 份本轮 XML、15 个证明目录及制品哈希。运行使用 Java 21.0.12+8-LTS、编译 release 17、Docker Engine 29.4.3 和临时数据库/Redis/RustFS；独立主应用/MCP JVM，不连接用户开发数据库、不调用付费服务。未重新运行前端、浏览器或默认全仓测试。

| 清单 | 实际证明与边界 |
| --- | --- |
| [B01](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B01.json) | 完整 Worker 批准、重复与重启；普通链路两个不同执行各一次应用；独立 MCP 第一执行的阶段快照 |
| [B02](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B02.json) | 拒绝/权限失败关闭、审批/授权/票据到期后无新应用；部分截止通过 fixture SQL 调整，不冒充自然等待 |
| [B03](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B03.json) | 消费先于关闭仍成功，关闭先于消费为 EXPIRED；批准/关闭领域竞争 |
| [B04](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B04.json) | 真实 MCP 普通/受保护入口及注册表负例；拒绝前后零应用 |
| [B05](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B05.json) | 真实入口绑定篡改与已消费票据重放；在线消费字段负例；副作用不重复 |
| [B06](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B06.json) | 真正杀死 Worker JVM 后重启、安全替票、一次应用；数据库双顺序；原详细证明也归档 |
| [B07](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B07.json) | 消费后真正杀死 MCP JVM，无新应用；SQL 提前截止后 UNKNOWN。主侧为 Incident 服务/测试 HTTP 桥，不是完整 Bootstrap |
| [B08](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B08.json) | 实际 MCP/数据库锁超时：正面回滚证据 FAILED，证据不足 UNKNOWN；冻结执行限时 100ms |
| [B09](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B09.json) | 完整 Worker 回调/响应/双丢失，各执行一次应用；真实自然截止与对账不可用条件、迟到/重复/冲突；另有回执/清扫锁竞争 |
| [B10](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B10.json) | 真实 MCP 只读已应用证据；数据库人工结论/回滚/未知/冲突快照。人工身份和部分证据为替身，不声明真实人员 HTTP 人工结论验收 |
| [B11](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B11.json) | 真实 Relay/数据库发送及标记失败恢复；两事件各三次尝试，原结论保持；固定 Producer，无实际 Broker |
| [B12](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/B12.json) | 重启查询不重执行；本机双向 HTTPS；逐实际会话/票据/服务凭据扫描，进程日志和快照命中 0 |

[A 核心可靠性清单](../../incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/scenarios/A-reliability.json) 关联本轮 100 路幂等/同键聚合、事务失败回滚、五次重试/人工重放与 Outbox 恢复的真实 PostgreSQL 断言和 XML；不是 Alert 完整 JVM 崩溃录像，也不是 Spec A 全套门禁重跑。

复现命令见 [quickstart 第 9 节](quickstart.md#9-统一清单复现与当前范围收尾)。[归档脚本](scripts/collect-evidence.ps1) 只接受本轮通过且无跳过的选定报告，要求七个 Bootstrap、一个独立 MCP、四个对账证明齐全，并拒绝缺失场景。失败日志及过期 XML 两项守卫验证通过。

此前历史同格式清单缺失没有被补造；本轮通过重新运行建立了完整 B01～B12 统一索引与事实制品。LAB-000 的当前目录/证据定义、ACT-003 的本地安全模拟验收门槛满足，可以收尾。已批准 Spec 默认固定 Producer，不要求 Broker；未把真实 Broker、更多未来攻击变体、跨主机、外部日志或生产指标改成已完成。后续实验室 LAB-001 与真实扩展另行推进。
