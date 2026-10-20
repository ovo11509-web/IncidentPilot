# 数据模型

没有新增持久化模型或状态转换。

- Review：incident、alerts、diagnoses、actions、timeline、startedAt/finishedAt、issues。页面只读，issues 非空禁导出。
- 上限：1000告警、200诊断、5000时间线、200动作。分页游标必须前进、ID不重复、时间线序号安全且递增；诊断/动作所属事件必须匹配。
- Catalog：observedAt、corpusVersion/corpusDigest、documents。
- Document：runbookId/version/currentVersion/title/owner/author/sourceType/status/reviewedAt/validUntil/documentDigest、services/environments/resourceTypes、content、sections(sectionId/lineStart/lineEnd/quote/chunkDigest)。不返回任意 manifest 字段或路径。
- 手册状态：撤回→历史可读；版本不当前→历史可读；未到审阅时间/过期→不适用；当前有效仅说明目录元数据，不承诺事件范围匹配。
