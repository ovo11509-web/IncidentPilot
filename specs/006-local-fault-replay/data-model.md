# 实验归档数据模型（已批准并实施）

全部为本地测试制品，无数据库迁移；保留原证明格式并引用。

## ScenarioDefinition

schemaVersion=1、id、version、title、faultPoint、module、testSelector、expectedTests、requiredProofs、evidenceLevel、expectations、limitations。固定六项，选择器来自受控清单，不能当作任意命令执行；具体映射见 [入口契约](contracts/local-runner.md)。

## LabRun

schemaVersion=1、runId(UUID)、selectedScenarios、startedAt、finishedAt、status、commands（结构化参数及exitCode）、environment（版本/本地隔离说明）、source（branch/head/dirty/相关文件sha256）、scenarioResults、artifactHashes。

状态：RUNNING → PASS | FAIL | BLOCKED | INTERRUPTED。未结束目录保持 RUNNING，验证不能通过。All只有全部选定场景PASS时PASS；有实际断言/证明失败时FAIL优先，否则环境受阻为BLOCKED，可捕获人为中断为INTERRUPTED。历史完成记录不可覆写。

## ScenarioResult

runId、scenarioId/version、status、reasonCode、tests/failures/errors/skipped、精确caseNames、proofPaths、子场景列表、预期及实际安全事实、稳定ID、traceSummary、secretScanSummary、limitations。应用次数按executionId分组；回执通道存在三个逻辑执行，不能只核对总数。

## Provenance与完整性

入口传本轮runId/scenarioId与仓库内outputRoot；测试写proof-origin.json，绑定本轮标记、测试名和证明列表。XML位于本轮专属目录，来源核验同时要求精确caseNames、证明标记与源码摘要一致。运行前后相关源码摘要改变则拒绝通过。

SHA256SUMS.json列出归档其余文件的相对路径及SHA256（不包括自身，避免自引用）；验证必须拒绝清单未列出的额外制品、缺失文件、越界路径与摘要不符。摘要证明完整性，不是数字签名，不能声称抗恶意同时改写清单的认证；运行来源契约还需独立校验。

## 安全投影

只接受现有安全字段、状态、计数、固定reasonCode及UUID/业务ID；不加入票据、密码、连接串、目标内容、正文或异常文本。新增报告/证明加入测试现有实际秘密扫描；原始 Maven 失败日志仅作本地诊断，明确不冒充逐实际凭据扫描合格的公开报告。
