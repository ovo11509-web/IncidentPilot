# 固定b667候选真实无Git完整All验收

2026-10-19，用户批准[上一阶段报告](candidate-backend-report.md)后的完整All。目的是使同一固定交付物具有一个新run的Backend和前端四阶段证据，不拼接不同候选或历史结果。

## 输入、授权与保护

- 固定候选`b66759af-6cfb-4cb6-8db5-65276702f443`，完整1905源码文件；未创建新候选或回填后补说明。
- 证据根`bootstrap/target/gitless-all/a392f45a/`，验收ID`a392f45a-8dde-4647-a184-8b8c3f03b5c9`，使用全新`s/`解包树；未复制旧target、node_modules或构建产物。
- 原库基线1911条源码/说明路径，分支`feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba`，暂存区空；1871项旧证据/私有输入及固定候选四文件纳入保护。
- 完整候选Verify及新树Get-DeliveryCandidate通过；1365受检文件摘要`2263934E0E57656CB6B1BB851391D56C5B0CE60E9F1DC597C36E44D841FCB6BA`与此前Backend相同，完整源码身份另外核对。
- 本轮进程清理Git环境、限制父Git发现（实际退出128）并移除Git工具；禁用真实提供商联调、清除服务覆盖环境。不修改用户全局配置、既有数据库或服务。
- Node20.19.5/npm10.8.2使用本机已有工具目录，仅在本次子进程PATH置前；npm离线并复用缓存。Maven使用既有缓存但不强制`-o`。Windows宿主、Java21、本机Linux Docker Engine，不称异机或全空白依赖环境。

## 实际执行结果

实际控制命令：

```powershell
./bootstrap/target/gitless-all/a392f45a/run-all.ps1
```

调用新解包树的原`check-delivery.ps1 -Gate All -CandidatePath <固定b667候选目录>`，未使用CheckOnly或跳过测试。原门禁原定Backend、Install、IncidentView、Lint、Build五阶段；本轮在工具前提检查阻断，五阶段均未开始。

| 项目 | 本轮事实 |
| --- | --- |
| 门禁runId | `7d1c8f40-df23-496f-8fac-485ea0706377` |
| 结果 | `BLOCKED / 2 / PRECONDITION_FAILED` |
| 时间UTC | `2026-10-19T09:48:22.4883534Z`至`09:48:54.6595861Z` |
| 测试阶段/XML | 0/0，不填写旧952项作为本轮测试 |
| 来源 | schema2、`head=null`、候选八项身份及1365受检摘要一致 |
| 资源与保护 | 控制器末检1871项保持、完整1905源码及四文件保持、既有16容器/94卷选定状态与名称集合保持 |

`raw/`实际留下Java、Maven、Docker上下文、Docker Engine、Node及npm的六份工具日志；npm退出非零，整份toolVersions未赋值，不能把空对象解读为已完成版本验收。独立工具探测得到Node20.19.5/npm10.8.2；真实门禁日志已记录Java21.0.12/Maven3.9.11/Docker29.4.3。

`controller-receipt.json`另外记录外层PowerShell工具会话退出1；门禁实际退出2见独立控制器`receipt.json`，两层退出码分别保留。`npm-launch-probe.json`为下述三种只读定位调用的实际记录，不作为门禁阶段或测试通过。

## 已复现的根因与建议修复

[门禁中央命令执行器](../../scripts/check-delivery.ps1)的Windows`.cmd`分支把裸`npm.cmd`再次引用后交给`cmd.exe /d /s /c`。在源码工作目录下，npm启动脚本的`%~dp0`指向该工作目录，导致其查找不存在的`node_modules/npm/bin/npm-prefix.js`和`npm-cli.js`，而非Node安装目录下的真实模块。

在同一源码工作目录及同一Node20优先PATH下进行了只读定位：

| 调用方式 | 实际结果 |
| --- | --- |
| 与门禁相同的带引号裸`npm.cmd --version` | 退出1，`MODULE_NOT_FOUND` |
| 带引号完整路径`npm.cmd --version` | 退出0，`10.8.2` |
| 未再次引用的裸`npm.cmd --version` | 退出0，`10.8.2` |

PATH目录使用正斜杠或Windows反斜杠时，首种失败均存在。工具本身已安装；这是门禁调用方式缺陷，不能通过复制npm到源码树、修改全局配置或伪造shim掩盖。

建议的最小变更：在同一中央执行器的Windows`.cmd`分支将可执行名称解析为实际完整路径，再对路径和参数保留原元字符检查及固定引用方式；补一个从不同工作目录、PATH含空格位置调用`.cmd`并核对脚本自身目录和参数的真实回归。之后冻结新候选、新解包、新run执行完整All。该变更尚未实施，原b667及本轮BLOCKED不能改写为通过。

## 收尾与尚未完成

本轮`receipt.json`、`execution-proof.json`、前后完整来源及资源记录已生成，保留全部原始工具日志、safe摘要和制品清单。最终保护收尾以本轮`closeout-proof.json`和`completion.json`为准，成功核对保护不等于All通过。

本轮收尾状态为`GITLESS_ALL_BLOCKED`、保护核验为`BLOCKED_CLOSEOUT_VERIFIED`；`gateAttempted=true`，`allPassed/testsExecuted/newFullAllExecuted/newFrontendExecuted=false`。完整1905源码与四摘要、1365受检清单、旧1871项、原Git身份/暂存及既有Docker选定状态共同绑定，说明链接和摘要另行核对。准备过的PASS收尾脚本作为`closeout-pass-draft.ps1`保留，未运行，不存在用于本轮的可靠性PASS审计。

完整Backend、前端四阶段、可靠性/评测新运行审计均未执行。阶段18的SC-019仍未满足；T075仅可勾选为尝试已结束并保留真实阻断，T076完整运行审计保持开放。旧ebf/9624 FAIL、b667/fdbac063 Backend PASS及旧Frontend继续保持各自身份。

本轮不新增发布JAR构建或持久业务/浏览器演练，不验证真实模型/Broker、生产可靠性或未见样本泛化。异机/承诺平台、托管CI、原两项TBD、安全历史整合及正式Git发布继续独立开放。无提交、Tag、推送、合并或部署；原始日志/XML/proof和私有配置只留本机，公开仅使用门禁白名单safe摘要。
