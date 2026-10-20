# 契约：Runbook、快照与可复核证据

**版本**：runbook-evidence-v1（2026-10-03 已批准）；以下样例均为 IncidentPilot 自建模拟资料。

## 元数据与摘要

manifest：`corpusVersion, schemaVersion, corpusDigest, publishedAt, runbooks[]`。每版本包含：

`runbookId, version, title, owner, author, sourceType=INCIDENTPILOT_SYNTHETIC, sourcePath, services[], environments[], resourceTypes[], symptomTerms[], reviewedAt, validUntil, status=PUBLISHED|WITHDRAWN, documentDigest, sections[]`。

首版仅精确匹配范围，不隐含通配符；同一 runbookId 在相同范围一次只选 manifest 明确的 currentVersion，不按版本字符串大小猜“最新”。sourcePath 必须是打包语料内相对路径，禁止绝对路径、`..`、URL 和符号链接逃逸。

规范文本：解码 UTF-8，去开头 BOM，将 CRLF/CR 变 LF，保留所有其余空白，全文末尾恰好一个 LF；摘要为规范 UTF-8 字节 SHA-256 小写 hex。章节有显式唯一 sectionId，范围为规范全文 1-based 闭区间行号；片段是对应整行与结尾 LF。documentDigest 和 chunkDigest 分别针对全文/片段。

引用只保存片段时无法独立重算全文摘要，故每次诊断按 Runbook 版本去重保存 `documentSnapshots[{runbookId,version,normalizedContent,documentDigest,metadataSnapshot}]`；引用再保存位置/摘录/chunkDigest。总原文证据默认 ≤256 KiB，超限失败关闭或转不足，不悄悄丢掉文档。历史文档与片段都能独立核对。

## 五类具体模拟样例

所有文件已建立于 `rag/src/main/resources/runbooks/<runbookId>/1.0.0.md`，service=incident-demo、environment=demo、resourceType=sim_service；Alert 的 resource.type 遵循 Spec A 小写规范，Action 的 targetType 仍为 B 的 SIM_SERVICE，不混用两种字段。以上身份是演示约定，不能用于真实服务处置。

| ID | 标题/症状词 | 必要来源事实 | 候选解释和只读检查 | 可转换建议 |
| --- | --- | --- | --- | --- |
| RB-OVERLOAD | 资源过载；cpu/high/load/负载 | 来源报告资源利用率或负载告警 | 可能资源压力；需指标窗口、进程与流量观测 | 无，禁止直接扩容 |
| RB-JAVA-OOM | Java OOM；OutOfMemoryError/heap/oom | 来源报告 OOM 症状 | 可能内存耗尽；需 GC、堆与 OOM 类型，不能证明泄漏 | 无，禁止直接重启 |
| RB-POOL | 连接池耗尽；pool/timeout/connection | 来源报告连接获取超时 | 可能池资源耗尽；需活跃/等待/上限与慢查询 | 无，禁止直接扩池 |
| RB-HEALTH | 健康检查失败；probe/health/探针 | 来源报告演示探针失败、服务/范围完整 | 候选健康状态异常；建议补充只读探针结果 | 满足 rb-health-sim 前提时可建议模拟 READY |
| RB-BACKLOG | 消息积压；lag/backlog/queue | 来源报告 backlog 告警 | 可能消费滞后；需生产/消费速率及错误观测 | 无，禁止清队列 |

每份正文有 `symptoms`、`evidence-required`、`checks-readonly`、`limitations`；仅 RB-HEALTH 额外有 `simulation-recovery`。首批语料发布日期/审阅时间使用明确 fixture 值，评测固定时钟，不靠当前墙钟决定是否过期。

RB-HEALTH 的 `simulation-recovery` 示例正文：

> 仅用于 incident-demo/demo 的隔离模拟服务。来源报告探针失败且映射到已登记模拟目标时，可由人员建议将模拟状态设置为 READY。该建议必须经服务端人工审批；不能证明真实探针恢复或真实故障已修复。

该条款的发布清单冻结 `ruleId=rb-health-sim-v1`、必须事实字段、模拟工具/version/desiredState； targetId 由服务端固定映射并与 B 白名单交集裁决，不能取模型输出或任意告警 resourceId。

## IncidentSnapshot

字段：`snapshotVersion=1, incidentId, incidentVersion, lifecycleState, aggregationRuleVersion, capturedAt, timelineWatermark, correlationId, scope, alerts[], facts[], truncated, totalAlertCount, includedAlertCount, factsDigest, snapshotDigest`。

alerts 按 `(receivedAt, alertId)` 排序，只含该 incidentId 的已关联 PROCESSED Alert。白名单：alertId/sourceId/ruleId/title/severity/service/environment/resourceType/resourceId/occurredAt/receivedAt/安全 summary；labels 仅审阅清单允许的键。缺字段显式 missing，不复制完整 payload_json。

首版安全 summary 不提供，labels 审阅白名单为空：两者均省略，原始载荷不进入检索或诊断。
事实仅提取 ruleId/title，不能当作指标或日志；指令性来源、矛盾描述及多类症状均禁止 convertible。

事实 ID 使用 `alert:<alertId>:<fieldName>`；value 是经长度/控制字符限制的原值，origin=`ALERT_SOURCE_REPORT`，含 sourceField 和 observed/source 时间。来源描述即使含“已修复”也不能改变权威状态。不存在指标/日志时不得制造测量值。

factsDigest：规范 JSON 的 scope、主状态、aggregationRuleVersion、排序后的已关联 Alert 投影、truncated/计数；不含 capturedAt、Timeline 水位和诊断事实。snapshotDigest 包含完整快照。canonical JSON 使用递归键排序、无额外空白、UTF-8；保留数组显式排序、数值表示按 v1 固定，禁止 NaN/Infinity、重复键或未知字段。

## RetrievalEvidence / DiagnosisResult

每条 evidence：`citationId, runbookId, runbookVersion, sectionId, lineStart, lineEnd, quote, documentDigest, chunkDigest, rank, score, scope, metadataSnapshot`。citationId 只在本次诊断内唯一，身份由 Runbook/version/section/hash 决定，不复用聊天文档编号。

result：`contractVersion, generatorVersion, ruleVersion, promptVersion (首版 NONE), query, retrievalConfigVersion, corpusVersion, documentSnapshots, evidence, claims[], recommendations[], missingFacts[], limitations[], status, reasonCode`。

claim：`claimId, ruleId, statement, factIds[], citationIds[], prerequisites[], uncertainty`；recommendation：`recommendationId, type=READ_ONLY_CHECK|SIMULATION_PROPOSAL, ruleId, statement, factIds[], citationIds[], prerequisites[], risk, convertible, binding?, recommendationDigest`。

通过校验要求：

1. 所有 ID 来自冻结快照和本次实际检索集合，原文/位置/摘要可复算，范围和版本有效。
2. 前提由审阅规则逐字段满足；矛盾、缺失、截断禁止 convertible，不按“最高分足够”整批放行。
3. 候选 statement 是允许的条款抽取/模板表述；未知 ruleId 或任意自由文本处置输出非法。
4. simulation binding 固定为既有 B 参数，不包括执行许可、票据或“已批准”。
5. 推荐只读检查也不能暗含写工具；首版展示检查内容，不自动执行 MCP。

原文 hash 一致不等于逻辑支持；首版的支持性来自人工审阅规则+明确前提，后续模型语义评测需另行设计。
