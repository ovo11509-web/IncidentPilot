# 本地入口契约（已批准并实施）

## 参数与输出

从仓库根目录运行 `specs/006-local-fault-replay/scripts/run-lab.ps1`：

- `-List`：读取目录并显示六项定义；不启动 Maven、Docker或服务。
- `-Scenario <ID>`：执行一个固定场景。
- `-All`：串行执行六项，失败保留并继续其余项，最终综合判定。
- `-Verify <run-directory>`：只读核验仓库内实验归档，不调用外部程序或业务服务。

四种互斥；未知/缺失参数拒绝。不提供任意命令、连接目标、清理历史、环境绕过或跳过校验选项。输出根固定为 `bootstrap/target/fault-lab/`；规范化后的路径不得越界，拒绝路径穿越和重解析点。

退出码：0=完整通过（List成功亦0）；1=测试/证明/完整性失败；2=参数错误或前置环境受阻；3=可捕获中断。无法捕获的杀进程不承诺退出码，遗留RUNNING目录验证失败。拒绝第二个同时Run；List/Verify无需运行锁。

## 六类固定映射

| ID | 模块/测试选择（包名沿用当前类） | 预计本场景检查数 | 事实与层级 |
| --- | --- | --- | --- |
| ALERT-REPLAY | incident / AlertRecoveryTest | 2 | 真实PG：接收持久性、五次重试后终止、人工重放恢复；直接服务调用与SQL时间fixture，无完整JVM杀进程 |
| OUTBOX-RETRY | incident / OutboxRecoveryTest | 1 | 真实Relay/PG：发送失败、发布标记失败、过期claim，同event/envelope恢复；固定Producer，无Broker |
| WORKER-RECOVERY | bootstrap / IncidentObservabilityIsolatedTest#actualWorkerCrashRecoversStableExecutionInNewTrace | 1 | 真实双JVM、杀死Worker、稳定Execution/调度ID、新Trace、一次应用 |
| RECEIPT-CHANNELS | bootstrap / IncidentObservabilityIsolatedTest#lostReceiptChannelsRemainUnknownUntilTrustedLateReceipt | 1 | 真实双JVM，三个通道子场景；每Execution一次应用、无再调度、UNKNOWN及可信迟到收敛 |
| GOLDEN | bootstrap / IncidentObservabilityIsolatedTest#twoAlertsDiagnosisRejectionAndApprovalHaveActualCrossProcessAncestry | 1 | 双告警聚合、诊断、拒绝零应用/新批准一次、真实跨进程Trace |
| DIAGNOSIS-SAFETY | bootstrap / IncidentObservabilityIsolatedTest#actualInvalidAndStaleDiagnosisRequestsCannotExecuteAndKeepAttemptIdentity | 1 | 实际HTTP安全拒绝、请求身份、零模拟应用 |

上述共7个选定检查，指目标模块的精确检查数，reactor依赖模块不得意外执行其他检查。稳定ID是本Spec目录ID，不替代原B/Q场景编号。报告保留原编号及限制。

## 必需归档

每运行包含 manifest.json、report.md、source-hashes.json、SHA256SUMS.json 和每场景的 result.json、精确XML、proof-origin.json及原证明引用/副本。真实执行后才可填写PASS；各场景声明的数据库证明与四个Trace场景的summary/trace-proof/实际Span文件均为必需。安全过程日志按原逐实际秘密扫描边界复制，不能随意增加内容字段。

失败/受阻仍保留可获得的证据，不要求失败运行具备成功证明；验证它时必须返回非零，不能补造。

## 信任边界

入口只改变本机测试输出与运行选择。Maven Wrapper和固定场景定义来自当前仓库，不接受用户shell片段；测试使用自己的临时资源。归档核验不相信PASS字符串，检查本轮来源、XML、事实契约及摘要；本地SHA256不构成第三方认证。
