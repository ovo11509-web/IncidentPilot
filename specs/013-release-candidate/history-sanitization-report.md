# 历史净化副本实施与核验记录

2026-10-17。用户在阅读 [历史处置方案](history-handoff-review.md) 并了解目的后回复“ok”，批准在新隔离副本进行定向净化及取得固定工具。原始历史保留，新增实现提交、Tag、push、远程历史改写及部署不在授权内。

## 结果与交接位置

本轮runId为`464961d7-8325-4932-a882-9f6443b1af31`，所有工作位于`bootstrap/target/history-sanitization/464961d7-8325-4932-a882-9f6443b1af31/`，下文路径均相对此目录。

| 内容 | 位置与状态 |
| --- | --- |
| 原始历史 | 保留在原仓库，分支`feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba` |
| 独立历史附件 | `delivery/`，仅`history.bundle`、`commit-map`、`history.json`、`SHA256SUMS`四文件 |
| 全新恢复核验库 | `restored.git/`，只含白名单分支；不作为附件整体分发 |
| 完整源码待审副本 | `candidate-review/`，1890文件精确等于固定源码清单；新增实现未暂存、未提交 |
| 净化历史tip | `437e275d8f4443c4b1b86b05321de1496594a987`，属于经过说明的衍生历史，不能冒称原SHA |
| 固定源码候选 | 原`01166dd3-0a4c-4def-83dd-688e6c15e8bd`四文件保持不变，仍为`SOURCE_VERIFIED_HISTORY_WITHHELD`；附件独立核验，不改写旧元数据 |

**history.bundle仅包含净化后的既有历史，不包含尚未提交的IncidentPilot新增实现。** 完整源码继续由固定ZIP提供，待审副本将两者对齐；仅clone历史包无法取得全部新增功能。原始库、`filter.git/`、工具、夹具、原始基线和整个run目录不能整体当成分发包。

`history.bundle`为24,640,234字节，SHA256为`54DA6647B84941A116C4C796841D7ACFE74B4E2A8C9818EB79441E5FACEFD48B`；`commit-map`为66,055字节，SHA256为`375EB231EEB67FBB7D62526008926D5CE8E54F97C7C72DFA6D71538BB74ECA4D`。其他附件摘要以`delivery/SHA256SUMS`为准，源清单/ZIP摘要与原候选一致。

## 变换范围

- 只在新单分支副本内，对此前审查的2种疑似值作精确字节替换，43处匹配分布于16个历史blob；值只在内存使用，没有写入替换清单、命令行、源码或日志。README重复字符占位保留。
- 从衍生历史移除`.claude/settings.local.json`；原历史有435个提交快照包含此路径。未批量删除其他IDE历史。
- 保留全部805个提交及4个merge、父顺序、作者/提交者及时间时区、编码、完整消息字节；明确移除30个旧签名。没有重新签署，不把旧签名作为衍生历史认证。
- 原历史与固定源码候选保留；在`candidate-review/`内额外删除候选已排除的5个IDE文件，逐文件写入固定ZIP原字节，不进行git add或新增实现commit。

## 工具与执行隔离

固定工具为[官方git-filter-repo v2.47.0](https://github.com/newren/git-filter-repo/releases/tag/v2.47.0)，上游提交`6f79afc8c90c592a3052e6cc53c2ca8907515bca`。本机工具文件SHA256为`67447413E273FC76809289111748870B6F6072F08B17EFE94863A92D810B7D94`，标签地址与固定提交地址返回的工具字节一致；工具、手册和许可证只存于本轮`tools/`，未全局安装。来源与各文件摘要见`tool-receipt.json`。

参数显式为`--invert-paths --path .claude/settings.local.json --preserve-commit-hashes --preserve-commit-encoding --prune-empty never --prune-degenerate never --replace-refs delete-no-add --no-fetch`，只使用两参数blob回调修改内容；没有姓名、邮箱、消息或mailmap变换。保留消息内旧SHA文本不等于保留提交本身SHA。

使用`clone --no-local --single-branch --no-tags`建立新副本，禁止硬链接/alternates；仅分发`refs/heads/feature/incident-platform`。没有使用`--refs`部分过滤、debug/dry-run原始流、`bundle --all`或复制原`.git`。执行期间Git只允许file协议；本地`fetch history.bundle`是恢复到新库，没有网络fetch/push。原库没有运行过滤、GC、删refs或重打包。

Git全局/系统配置对执行子进程隔离，autocrlf关闭、hooks关闭；原库所有权读取只通过子进程`safe.directory`明确单路径允许，没有改全局配置。一次性执行器最终摘要为`2EF1A7C2259DF49DA8563BFB16BB3E14D98EA987F4A67BD747BAE28687BB0E49`，绑定夹具结果的记录为`executor-receipt-v2.json`；这些辅助脚本不是已交付的通用发布工具。

## 实测验收

| 检查 | 实际结果 |
| --- | --- |
| 合成夹具 | 4提交/1merge，2种替换、路径删除、非UTF-8消息编码、消息内旧SHA、父顺序与1个模拟签名均符合预期 |
| 提交映射 | 805→805双射，零映射0；SHA变化804、保留1 |
| 拓扑与元数据 | 4个merge保留；805个提交的父顺序、作者/时间、提交者/时间、编码、消息及其他非签名元数据差异0 |
| 每次提交的文件树 | 390,029项文件快照检查，除指定替换/Claude路径删除外差异0；模式/路径/内容均核对 |
| 原疑似值及blob | 恢复制品的全部对象中，2种原值出现0、16个原命中blob出现0 |
| 对象与refs | 17,833对象=白名单ref可达闭包，额外对象0；其中805commit/11,596tree/5,432blob，只有1个白名单ref |
| 内容规则 | 全部blob及commit/tag文本扫描，未解释模式匹配0；只保留2次已核实README占位匹配 |
| 历史完整性 | 全新目录恢复、bundle verify、`git fsck --full --strict`通过；无alternates/reflog/原始refs |
| 固定源码对齐 | 1890文件，缺失0/额外0/大小差异0/哈希差异0；暂存0、新增实现commit 0 |
| 原候选校验 | Verify退出0，仍为`SOURCE_VERIFIED_HISTORY_WITHHELD`，原ZIP及manifest摘要保持 |
| 独立只读交叉核对 | 另一核验过程直接读取提交原始对象、bundle、恢复库及1890文件，映射/元数据/闭包/源码结果一致 |

执行命令为本轮`run.py fixture`、`create`、`verify`、`align`，最终均退出0；原候选另由`pwsh -NoProfile -File scripts/new-release-candidate.ps1 -Command Verify -CandidatePath bootstrap/target/release-candidates/01166dd3-0a4c-4def-83dd-688e6c15e8bd`核对。每个目标要求新目录，不应在旧目录盲目重复有副作用阶段。

原库保护和附件收尾检查单独记录在`original-preservation.json`及`closeout-verification.json`，不把脚本里未经执行的断言算作通过。

收尾实测通过：原始1897个tracked/untracked文件基线逐项核对，除本轮说明性文档外无变化、无删除；分支、HEAD、全部refs、index、reflog及工作树清单保持。原候选四文件摘要全部保持，历史附件四文件摘要自检通过；文档相对链接无缺失、无本机绝对路径，窄秘密规则检查通过。原15项源码任务及后续6项历史任务分别收尾，不将后续通过反写进旧源码候选。

## 实际失败与修复

1. 默认网络环境无法取得工具，API返回403，只读ls-remote遇到DNS失败；改由官方发行页固定完整提交号，再从官方raw地址取得并比对标签字节，未改下载版本或省略摘要。
2. 首轮合成夹具暴露Git参数中的bytes误转为文本，尚未过滤真实历史；修正后使用新夹具目录通过。
3. 真实create首次在读取原库ref时遇Git所有权保护，目标尚未创建；仅在本轮子进程加入原库单路径允许，重新通过夹具后执行成功。没有修改原库权限或全局配置。

## 结论与剩余事项

已识别历史疑似值和私有工具路径的本地衍生历史处理通过，得到可核对的独立附件；扫描只覆盖明确模式及已识别精确值，不能称全面秘密认证，也没有验证凭据有效性或撤销凭据。其他上游/远程副本未处理。

原始历史仍在原库；完整源码与衍生历史尚未形成包含新增实现的正式提交/Tag，因此不能声称正式版本已发布或整个第1步全部交付闭合。下一项有价值的验收仍是固定候选在独立环境完成初始化、构建、登录、告警聚合、诊断、拒绝零应用、批准一次应用及重启保留。本轮不重复业务测试、Maven构建或演示，不声称新的Backend/All、跨平台、真实模型、生产或托管CI通过。
