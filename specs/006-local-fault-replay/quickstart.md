# 本地故障实验复现指南

2026-10-05：Spec F 已批准并完成本地验收，18/18任务。详细调用路径、失败修复、作者自检与限制见 [实施报告](implementation-report.md)。清单审批标记保持只读；实际授权与analyze 17/17覆盖记录见 [计划](plan.md)。

## 前置条件

沿用 [仓库测试说明](../../docs/development/testing.md)：本地JDK/Maven Wrapper、可用Docker及临时容器镜像。此次实际使用Java 21、编译release 17；测试强制关闭RAG_LIVE_TESTS，使用临时PostgreSQL及自有隔离JVM，不连接开发数据库。不得提供付费模型或真实基础设施凭据；环境不满足返回受阻。

## 操作

在仓库根目录使用PowerShell 7或Windows PowerShell 5.1：

```powershell
.\specs\006-local-fault-replay\scripts\run-lab.ps1 -List
.\specs\006-local-fault-replay\scripts\run-lab.ps1 -Scenario ALERT-REPLAY
.\specs\006-local-fault-replay\scripts\run-lab.ps1 -Scenario WORKER-RECOVERY
.\specs\006-local-fault-replay\scripts\run-lab.ps1 -All
# 将 <runId> 替换为本轮输出；只读，不启动服务
.\specs\006-local-fault-replay\scripts\run-lab.ps1 -Verify bootstrap/target/fault-lab/<runId>
# 脚本契约检查；Archive用于真实归档副本负例，不修改原件
.\specs\006-local-fault-replay\scripts\test-run-lab.ps1 -Archive bootstrap/target/fault-lab/<runId>
```

参数互斥，场景为固定六类；退出0表示通过，1表示实验/核验失败，2表示参数或环境受阻，3表示可捕获中断。强制终止留下RUNNING也不能通过Verify。每次执行生成独立UUID目录，仓库锁禁止重叠运行。

## 本轮实际命令与结果

- `run-lab.ps1 -All`：2fba02a7-4681-409b-9030-10212de0b51f，2026-10-05 22:08:33～22:19:47 +08:00；六场景7项，零失败/错误/跳过，退出0。249个实际Span，双JVM证明扫描25个实际秘密、零命中；1155项源码摘要、107项制品摘要。`-Verify`退出0。
- `test-run-lab.ps1 -Archive bootstrap/target/fault-lab/2fba02a7-4681-409b-9030-10212de0b51f`：PowerShell 7与Windows PowerShell 5.1各30项PASS、退出0；归档副本的删除、篡改、来源错配、额外文件、失败/跳过/未完成、缺少回执/Trace、目录/manifest错配均拒绝，原件保留。
- 再次 `run-lab.ps1 -Scenario ALERT-REPLAY`：9a2619ae-5b12-4249-a45d-26e21adc1b1f，2项通过、退出0，Verify退出0；新目录未覆盖统一归档。
- 默认配置兼容性回归（未启用LAB profile），2026-10-05 22:29:51结束：下述命令5项、零失败/错误/跳过，BUILD SUCCESS、退出0；报告回到默认模块target/surefire-reports。

```powershell
$env:RAG_LIVE_TESTS='false'
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=AlertRecoveryTest,OutboxRecoveryTest,IncidentEvidenceCatalogTest,IncidentObservabilityIsolatedTest#actualInvalidAndStaleDiagnosisRequestsCannotExecuteAndKeepAttemptIdentity' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

两版本契约结果目录分别为 `bootstrap/target/fault-lab/contract-f7b257b9-91be-49a2-96bf-088723825d5b/` 与 `contract-d3731f0c-28b4-4ea6-81c8-5fa49916d022/`。这些fixture不是Java或真实Docker故障证据。统一归档生成后仅修正test-run-lab.ps1的模拟Docker退出码恢复；统一归档保留当时源码摘要，复跑ALERT归档包含最终脚本。运行入口、场景清单、Java与POM无后续变化。

## 阅读报告与验收边界

从 `bootstrap/target/fault-lab/<runId>/report.md` 查看状态、限制、注入点、实际事实与来源，再查result/proof及Trace。回执场景三个execution各一次应用，不能用总Span数推断执行数。Outbox是实际数据库/Relay加固定Producer，没有启动真实Broker。

原始Maven日志仅用于本地排错，未承诺逐实际凭据扫描，不应当作可公开的安全报告。新证明沿用实际秘密扫描，公开报告不输出凭据、正文或异常文本。SHA256是完整性检查，不是数字签名；不声称防御同时改写全部证据的攻击者。

首次路径契约错误和Worker Trace判定错误的FAIL归档原样保留，见实施报告。未重新运行历史840项全仓Java门禁；本轮不包含生产、跨主机、性能、真实模型/工具/Broker或OTLP后端验收。
