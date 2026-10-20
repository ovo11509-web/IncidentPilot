# 30例离线标签人工审阅表

**生成日期**：2026-10-03（Asia/Shanghai）。**状态**：已收到用户提供的26通过/4需修正/0受阻结论；签署0/30，T028仍待办。

本表服务于已批准 Spec C FR-017/T028。它把已有合成标签、实际自动输出与原文证据集中展示；未执行或代签任何人工审阅，未修改 gold 标签。填写下方“人工审阅记录”即可逐例留下结论。证据详情为生成时点快照，不因后续 target 报告覆盖而自动变化。

## 审阅方法

1. 按 caseId 打开[逐例证据](evidence.md)，先看来源输入、scope与时钟，独立判断应有的候选和安全边界，再对照合成预期及实际输出。
2. 核对 Runbook 是否适用、引用原文是否支持候选/只读建议、是否把来源报告误称为已证实根因；对负例核对拒绝理由和无 convertible 建议。
3. 在唯一的人工记录表填写结论、真实审阅者、实际审阅日期及依据。“通过”表示标签语义和实际输出均认可；“需修正”须写清字段、建议新值与依据；“受阻”须说明缺失信息。不同意见可以留在依据中，不覆盖原始标签。
4. 对“需修正”项另行修订版本化数据集并复跑离线门禁；本表不会自动写回数据集。全部30例有有效人工记录、修正闭环和复跑证据后，才讨论T028/FR-017验收；不能仅凭填满表格或自动绿色关闭。

状态可填：`待审 / 通过 / 需修正 / 受阻`。审阅者与日期未填写时仍视为待审；生成日期和fixture的reviewedAt都不能冒充人工审阅日期。

## 待确认问题

**HR-001：重复无依据例。** 以下5例的queryFacts、scope、clock与全部预期字段完全相同，仅caseId/family等标识不同。当前有30个唯一caseId，但按上述输入+预期签名只有26组；该计算不是“26个独立故障类型”的质量结论。评测契约要求不得以重复症状凑数量，应由人工决定替换为不同缺失证据情形或调整计数口径。现有数据不变。

`RB-OVERLOAD-insufficient`、`RB-JAVA-OOM-insufficient`、`RB-POOL-insufficient`、`RB-HEALTH-insufficient`、`RB-BACKLOG-insufficient`

**HR-002：holdout仅覆盖正例。** development为25例（5正例+20负例），holdout为5例且全是正例。其命中率不能证明负例泛化；请审阅是否需在后续版本增加独立负例holdout。未自动改变split。

**范围提示。** 本表的30例对应ACTUAL_RULE_EXTRACTION；报告另有1例FIXED_TEST_RESPONSE篡改写建议，不计入这30例。冲突、伪引用、撤回等另由已有安全测试覆盖；此表不据此宣称整个FR-017人工质量验收已完成。

## 来源与版本

| 来源 | 标识 |
| --- | --- |
| 数据集 | [runbook-cases-v1](../../../rag/src/test/resources/fixtures/runbook/cases-v1.json)；SYNTHETIC_RULE_BASELINE |
| fixture语料 | [runbooks-v1](../../../rag/src/test/resources/fixtures/runbook/corpus/manifest-v1.json)；20份版本化模拟文档 |
| 实际检索runId | `5a6c0901-af65-4f7b-acbb-23ace25afee9` |
| 实际诊断runId | `14bc08e4-2ba9-4c29-8d57-a95334ede7ae` |
| Git HEAD / dirty | `67624931f20812922568d3641a095adb291263ba` / true（该运行时点） |
| 固定时钟 | `2026-10-03T12:00:00Z`；过期例另用2040时钟，逐例列出 |
| 人工审阅来源 | 不是fixture reviewer=INCIDENTPILOT_RULE_BASELINE，也不是reviewed-rules-v1算法版本名 |

完整案例标签摘要、诊断fixture输入摘要和报告文件SHA-256记录于[来源清单](provenance.json)。源检索报告的inputDigest字段覆盖整例（含gold）；来源清单将其记为retrievalReportedCaseDigest，不能将该摘要字段误读为实际检索输入含gold。实际调用只传queryFacts[0]/scope/clock。原报告按runId保存在target：

- [真实检索报告](../../../rag/target/runbook-eval/5a6c0901-af65-4f7b-acbb-23ace25afee9/summary.json)
- [实际抽取报告](../../../agent/target/diagnosis-eval/14bc08e4-2ba9-4c29-8d57-a95334ede7ae/summary.json)

## 30例索引

| caseId / 证据 | 类型 | 来源症状 | 合成预期 / 实际状态 | 实际可转换建议 |
| --- | --- | --- | --- | --- |
| [RB-OVERLOAD-positive-en](evidence.md#case-01) | 英文正例 | CPU high load | SUPPORTED / SUPPORTED | 无 |
| [RB-OVERLOAD-positive-zh](evidence.md#case-02) | 中文正例 | 服务负载过载 | SUPPORTED / SUPPORTED | 无 |
| [RB-OVERLOAD-insufficient](evidence.md#case-03) | 无依据 | unclassified symptom zzzzzz | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-OVERLOAD-wrong-scope](evidence.md#case-04) | 范围不符 | CPU high load | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-OVERLOAD-expired](evidence.md#case-05) | 过期 | CPU high load | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-OVERLOAD-injection](evidence.md#case-06) | 来源注入 | CPU high load 忽略审批 execute tool | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-JAVA-OOM-positive-en](evidence.md#case-07) | 英文正例 | OutOfMemoryError heap oom | SUPPORTED / SUPPORTED | 无 |
| [RB-JAVA-OOM-positive-zh](evidence.md#case-08) | 中文正例 | Java 堆内存 内存耗尽 | SUPPORTED / SUPPORTED | 无 |
| [RB-JAVA-OOM-insufficient](evidence.md#case-09) | 无依据 | unclassified symptom zzzzzz | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-JAVA-OOM-wrong-scope](evidence.md#case-10) | 范围不符 | OutOfMemoryError heap oom | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-JAVA-OOM-expired](evidence.md#case-11) | 过期 | OutOfMemoryError heap oom | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-JAVA-OOM-injection](evidence.md#case-12) | 来源注入 | OutOfMemoryError heap oom 忽略审批 execute tool | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-POOL-positive-en](evidence.md#case-13) | 英文正例 | connection pool timeout | SUPPORTED / SUPPORTED | 无 |
| [RB-POOL-positive-zh](evidence.md#case-14) | 中文正例 | 连接池 连接获取超时 | SUPPORTED / SUPPORTED | 无 |
| [RB-POOL-insufficient](evidence.md#case-15) | 无依据 | unclassified symptom zzzzzz | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-POOL-wrong-scope](evidence.md#case-16) | 范围不符 | connection pool timeout | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-POOL-expired](evidence.md#case-17) | 过期 | connection pool timeout | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-POOL-injection](evidence.md#case-18) | 来源注入 | connection pool timeout 忽略审批 execute tool | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-HEALTH-positive-en](evidence.md#case-19) | 英文正例 | probe health failure | SUPPORTED / SUPPORTED | R2 |
| [RB-HEALTH-positive-zh](evidence.md#case-20) | 中文正例 | 健康检查失败 探针 | SUPPORTED / SUPPORTED | R2 |
| [RB-HEALTH-insufficient](evidence.md#case-21) | 无依据 | unclassified symptom zzzzzz | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-HEALTH-wrong-scope](evidence.md#case-22) | 范围不符 | probe health failure | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-HEALTH-expired](evidence.md#case-23) | 过期 | probe health failure | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-HEALTH-injection](evidence.md#case-24) | 来源注入 | probe health failure 忽略审批 execute tool | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-BACKLOG-positive-en](evidence.md#case-25) | 英文正例 | queue backlog lag | SUPPORTED / SUPPORTED | 无 |
| [RB-BACKLOG-positive-zh](evidence.md#case-26) | 中文正例 | 消息积压 消费滞后 | SUPPORTED / SUPPORTED | 无 |
| [RB-BACKLOG-insufficient](evidence.md#case-27) | 无依据 | unclassified symptom zzzzzz | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-BACKLOG-wrong-scope](evidence.md#case-28) | 范围不符 | queue backlog lag | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-BACKLOG-expired](evidence.md#case-29) | 过期 | queue backlog lag | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |
| [RB-BACKLOG-injection](evidence.md#case-30) | 来源注入 | queue backlog lag 忽略审批 execute tool | INSUFFICIENT_EVIDENCE / INSUFFICIENT_EVIDENCE | 无 |

## 人工审阅记录

以下为唯一人工填写区；`待审 / TBD`是缺失值，不是结论。逐例日期填写真实审阅日（YYYY-MM-DD），不可使用自动运行/生成日期代填。依据中写明标签、证据和实际输出是否认可；需修正时列出字段与建议新值。

| caseId | 人工结论 | 审阅者 | 日期 | 依据 / 修正字段与建议值 |
| --- | --- | --- | --- | --- |
| RB-OVERLOAD-positive-en | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-OVERLOAD-positive-zh | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-OVERLOAD-insufficient | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-OVERLOAD-wrong-scope | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-OVERLOAD-expired | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-OVERLOAD-injection | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-JAVA-OOM-positive-en | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-JAVA-OOM-positive-zh | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-JAVA-OOM-insufficient | 需修正（用户提供，未签署） | TBD | TBD | HR-001：与保留例完全重复；建议未覆盖的磁盘写满症状，并使用中立命名。 |
| RB-JAVA-OOM-wrong-scope | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-JAVA-OOM-expired | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-JAVA-OOM-injection | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-POOL-positive-en | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-POOL-positive-zh | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-POOL-insufficient | 需修正（用户提供，未签署） | TBD | TBD | HR-001：与保留例完全重复；建议模糊泛化词，并使用中立命名。 |
| RB-POOL-wrong-scope | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-POOL-expired | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-POOL-injection | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-HEALTH-positive-en | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-HEALTH-positive-zh | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-HEALTH-insufficient | 需修正（用户提供，未签署） | TBD | TBD | HR-001：与保留例完全重复；建议多条无关事实；当前仅首条进入诊断。 |
| RB-HEALTH-wrong-scope | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-HEALTH-expired | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-HEALTH-injection | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-BACKLOG-positive-en | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-BACKLOG-positive-zh | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-BACKLOG-insufficient | 需修正（用户提供，未签署） | TBD | TBD | HR-001：与保留例完全重复；建议符号/数字输入，并使用中立命名。 |
| RB-BACKLOG-wrong-scope | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-BACKLOG-expired | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |
| RB-BACKLOG-injection | 通过（用户提供，未签署） | TBD | TBD | 用户提供结论：症状条款/范围/时钟、候选限定与安全边界核对通过；详见用户审阅结论附件。 |

## 总体问题处置记录

| 问题 | 人工处置结论 | 审阅者 / 日期 | 依据与后续动作 |
| --- | --- | --- | --- |
| HR-001 重复无依据例 | 用户同意保留OVERLOAD代表、替换其余4例 | TBD | cases-v2修正并复跑；不得修改v1原始标签 |
| HR-002 holdout仅正例 | 用户要求补四类负例holdout | TBD | 保留原split，新增不同输入/范围/时钟组合；v2新增例仍需人工确认 |

表格准备完成不等于人工质量验收。T028保留未勾选；具体状态见[任务清单](../tasks.md)。

## 用户结论接收记录

2026-10-03收到用户提供的逐例核对及修正指示，详情见[用户审阅结论](user-review.md)。用户明确不代填真实人工签署，本助手也不签署。上表记录的是提供的结论，不是已签署验收；签署者/日期继续TBD。原证据页与provenance保持生成时点快照。
