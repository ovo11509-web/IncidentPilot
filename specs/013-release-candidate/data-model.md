# 数据模型：候选文件

无业务Schema变化，全部为本地只增文件。

| 实体 | 关键字段/约束 | 关系 |
| --- | --- | --- |
| Candidate | schemaVersion、candidateId、projectVersion、createdAt、baselineHead、branch、upstream、dirty事实、制品摘要、status=SOURCE_CANDIDATE_READY；无机器路径/凭据 | 绑定manifest/ZIP及历史暂扣事实 |
| SourceManifest | 稳定排序files，每项规范相对path、size、sha256；不分大小写无冲突 | 精确覆盖ZIP源码成员 |
| HistoryStatus | status=WITHHELD_POTENTIAL_HISTORICAL_SECRETS、historyPreservedInOriginalRepository=true、baselineHead；不携带疑似秘密内容 | 原仓库保留历史，候选禁止history.bundle |
| SHA256SUMS | 制品名和SHA256，无自摘要循环 | 绑定ZIP/manifest/meta |
| EvidenceSummary | 显式允许字段、historical标记、原runId/sourceDigest/范围/计数 | 不自动归属新candidate |
| BuildRecord | 工具版本、命令、exitCode、candidate摘要、产物版本及缓存边界 | 存于候选外，不能反改冻结内容 |

默认目录 `bootstrap/target/release-candidates/<UUID>/`，只允许source.zip/source-manifest.json/candidate.json/SHA256SUMS四文件。状态为“未创建→暂存中→SOURCE_CANDIDATE_READY→SOURCE_VERIFIED_HISTORY_WITHHELD”；源码校验通过不改变历史暂扣状态。失败不能保留误导的成功标记；已有目录不可覆盖。manifest不把自身列为源码；ZIP不能夹带生成制品或历史包。路径先规范化/允许范围/越界/碰撞检查，再复制或核验。Verify只读且独立于当前工作树。
