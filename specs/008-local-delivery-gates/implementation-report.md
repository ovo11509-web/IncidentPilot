# DEP-001实施与作者走读

2026-10-06用户明确批准完整Spec H，先正式speckit-analyze，17/17 FR/SC有任务覆盖、20任务均映射，无CRITICAL或其他阻塞；清单16/16保持只读。全部20项任务已实施并完成本地验收，DEP-001在本Spec范围收尾；Linux、托管CI及部署未运行。

## 实现范围与调用路径

新增scripts/check-delivery.ps1、scripts/test-check-delivery.ps1、最小CI定义及交付/部署准备说明。Java改动仅位于既有BootstrapIsolatedStartupTest及EvidenceManifestWriter两处测试helper：delivery.gates选定受控target/delivery-gates，Bootstrap按场景保留独立proof；原fault-lab路径继续受限。没有生产代码、Schema、POM、运行配置或新依赖变更。

作者走读对应实际代码：

1. Invoke-Delivery保存两项原环境，分配UUID并检查输出祖先无链接/越界；受控source.json覆盖未提交源码，HEAD只是另一个来源字段。
2. Get-DeliveryTools核对pwsh/Java/keytool/Wrapper/Docker及适用的Node/npm；Test-DeliverySafety拒绝不受控注入。Get-DeliveryCommands只构造固定四范围，传递固定DELIVERY-SMOKE/BACKEND及本轮lab.sourceDigest，无任意命令/输出根参数。CheckOnly不进入执行循环。
3. Invoke-DeliveryCommand启动自有子进程，stdout/stderr只保存raw；Windows按固定cmd参数调用Wrapper/npm.cmd，Linux使用原mvnw/npm。超时只终止该子进程树，未保证容器或强制取消的全部清理。
4. 阶段记录实际时间/退出；失败保留，不由后续成功覆盖。Read-DeliveryReports核对独立XML的时间、runId、DTD禁用、节点/计数与完整类名；Smoke精确1项、Backend12类必需且13类手工目录精确跳过。未执行或无适用计数不填0冒充通过。
5. 结束后重新核对实际源码；finally恢复环境。Get-DeliverySafe重新构造固定白名单，不能把Exception、XML正文、环境或临时文件复制进去；摘要失效非零且无raw回退。本地artifacts.json记录局部证据相对路径及SHA256，不是可发布制品。

CI使用相同入口、Ubuntu24.04/pwsh、Java21和Node22、contents:read、checkout凭据不持久化、固定官方SHA；无业务Secrets、远程部署或镜像发布。Backend90/Frontend20分钟仅保护超时。发布目录精确safe/summary.json，其他target制品不上传。官方SHA核实、YAML解析和权限/上传负例见research。

## 取舍与实质前提差异

复用现有数据库角色、模拟目标、真正的双JVM断言和资源关闭，避免另一套常驻初始化流程。原测试helper仅允许fault-lab，与批准的delivery目录存在实际冲突，已明确报告并只扩展测试侧白名单，没有取消路径限制或扩大业务权限。

本机实际Node20.19.5/npm10.8.2通过当前锁文件和前端门禁；CI声明Node22未经运行。Java执行21.0.12、编译release17不证明Java17运行兼容性。工具版本和平台分列，不把设计版本写成实测版本。全后端受Docker启动影响时先执行无Docker前端，后续才Smoke及Backend；运行期间没有编辑受控源码。

## 本轮真实验证

- 入口缺失负例退出1，不启动服务；XML解析修正后第一轮71项契约通过。
- 最终87项契约退出0，含自有短子进程退出7与超时、四范围/仅检查、失败与finally恢复、空/旧/重复/目标缺失/跳过/计数冲突/DTD拒绝、敏感fixture白名单、摘要失效及CI五类负例。fixture不当作业务或托管通过。
- 实际CheckOnly All为CHECKED/退出0。无秘密JVM注入负例81038c3f-3843-4cd2-a2c3-24ff1f83bac5明确捕获子进程退出2、BLOCKED、无业务启动。
- 新Smoke d405e73a-5a62-408a-9527-f169e77d1b25：1通过、0失败/错误/跳过，退出0；模拟Action两项、回执一份、一个Execution应用一次。正常结束后容器列表[]。
- 新Frontend b9201aeb-4afe-4d1d-a7bd-014ede3d8392：Install/IncidentView/Lint/Build均退出0，最终PASS；不能代替浏览器。
- 早期Backend两次FAIL及部分报告保留（见下方失败段）；最终Backend5576b210-ed88-4ef9-b841-e0a755d8f627 PASS/子进程退出0，本轮190份XML共864项：843通过、0失败、0错误、21跳过。12必需类均执行零跳过，13手工类的21项跳过逐类核对。计数来自新入口的新XML，不引用历史Spec G。

实际命令、时间、来源摘要及证据相对目录见 [quickstart](quickstart.md)。原始诊断仅本地；摘要敏感fixture通过不代表全仓或生产秘密认证。

## 真实失败与修复

- 最初入口不存在：契约返回ENTRY_MISSING/1，确认检查能阻止假通过，再实施入口。
- PowerShell XML节点Name被name属性遮蔽，合法fixture误报REPORT_STRUCTURE；用LocalName和GetAttribute修复后负例/正例通过。未更改XML来掩盖错误。
- 初版将任何用户Testcontainers properties都拒绝，实际文件只含自动缓存Npipe provider；改为固定本地Npipe/Unix/Desktop类名行白名单，其他配置仍拒绝，未修改全局文件。
- Docker Desktop进程存在但Linux Engine管道缺失，WSL实例未启动；保留受阻目录，不改连开发库。尝试本机启动后延迟恢复到linux29.4.3，再实际通过Smoke。服务启动权限不可用没有被写成已解决的权限问题。
- 初期宿主PowerShell退出与门禁子进程退出未分别捕获；不补造两次历史子进程码，随后独立实际负例立即读取LASTEXITCODE=2。未来复现须在每条命令之后独立捕获退出码。

## 作者自检

1. 为什么Maven退出0或历史864项不能证明新入口通过？空报告、必需目标跳过和意外新增跳过在哪一步被拒绝？
2. HEAD、sourceDigest、runId分别证明什么？为什么未提交源码、fixture和同名旧XML不能相互替代？
3. safe摘要怎样排除XML/Exception/环境/临时凭据？如果摘要失败或运行中断，为什么不能回退raw或宣布PASS？

## 未验证范围与授权

Linux入口分支、Node22、托管CI及部署未实际运行。浏览器本轮未复验；Java17运行、真实模型/Runbook质量、真实Milvus/Broker/OTLP、跨主机、生产认证及常驻栈均未验收。镜像浮动标签和强制取消资源残留仍有边界，部署/迁移矩阵见 [部署准备](../../docs/development/delivery.md) 中TBD。

IncidentPilot门禁和安全模拟新增贡献与上游Ragent能力区分，Apache-2.0/署名/历史保留。未提交、推送、合并、部署、触发远程CI、调用付费服务、操作真实基础设施/开发数据库或全局容器清理。1745份哈希基线文件全部存在，仅18个预期文件在基线上继续变更；原需求清单、AGENTS及LICENSE哈希未变，分支/HEAD/空暂存保持。最终保留及证据核对存final-preservation.json和final-evidence-validation.json。

初始Git列表对中文名使用引号转义，57份既有受跟踪资料未进入初始哈希清单；以初始status和当前diff分别确认这些文件未修改且仍存在，不回填或假造历史哈希。最终使用未转义路径清点，真正新增文件只有入口、契约脚本、CI、交付说明和本报告五份；这项保留检查边界单独记入final-preservation.json。

## 完整入口发现的报告适配失败

首次Backend的66份部分报告218项/0失败/0错误/1跳过不能证明全仓通过。真实YouCom XML的testcase.classname使用JUnit中文DisplayName，与testsuite.name规范类名不同，原解析器会误拒绝；在JUnit源码注解与本轮XML核对后，仅停止匹配该runId的自有Maven进程树，阶段/最终均退出1、FAIL，资源随后正常退出，没有修改测试报告。修正规范身份只取testsuite.name，同时仍要求病例classname非空，并补中文显示名正例/空值负例。

追加契约第一次假All失败来自fixture的NTFS文件时间比UtcNow早约4.5毫秒；只明确mock报告的本轮时间，不改变生产严格新时间校验，也不把这次失败计为真实Java失败。失败日志contracts-displayname.log保留，重跑记录另存contracts-final84.log。

追加84项契约通过，退出0；真实Backend重跑910a60e1-47bf-46cc-9a9c-2f00eb730d69以当轮sourceDigest F7E4A1252C9E45E20D3FA6F70787FC26205652363B0ED472C999616A9D758660执行，仍发现下节的共享证明前提失败。前轮报告仅用于发现适配假设，未被新运行计数或改写，未因部分绿关闭DEP-001。


## 共享Incident证据前提的真实失败

第二轮910a60e1-47bf-46cc-9a9c-2f00eb730d69在Incident模块停止：90份本轮XML合计261项、0失败、3错误、1跳过，Maven阶段及门禁子进程均退出1；不是完整Backend结果。AlertRecovery两项与OutboxRecovery一项的业务断言运行后，labProof拒绝缺失scenario身份。实施初查只发现Bootstrap路径限制，漏核Incident共享写入器的scenario/sourceDigest/路径前提；本轮全仓暴露了这处遗漏。

补固定lab.scenarioId=DELIVERY-SMOKE/BACKEND和本轮lab.sourceDigest，EvidenceManifestWriter按delivery.gates接受相同受控delivery target目录；没有绕过身份或路径校验，不改业务/Schema/外部许可。追加三范围proof参数契约后87项通过，退出0。先独立真实运行AlertRecoveryTest/OutboxRecoveryTest三个检查，再执行完整Backend；失败目录不删除、不修改原XML。

三个真实恢复检查01947443-a053-4d69-b900-3cc6050d133c已通过：3/0失败/0错误/0跳过、退出0，proof-origin绑定本轮UUID、DELIVERY-BACKEND及源码摘要，含两类三方法。全仓检索lab.*使用点还确认OBS写入器也使用scenario/sourceDigest，同一固定属性覆盖该前提；没有跳过或禁用任何证明输出。源码摘要D9FC783F358E7595172452A91281ED49F6A0074E50993737E788E25F7608D3F6用于随后最终完整Backend，后续只编辑说明文档。


最终源码上的Frontend 42f23a1f-a861-4849-a674-1e0753b5836d已PASS、子进程退出0，四阶段实际退出均0；与最终Backend同一1339文件sourceDigest D9FC783F358E7595172452A91281ED49F6A0074E50993737E788E25F7608D3F6。白名单字段、阶段退出及本地artifacts.json全部哈希已独立复核；原始日志不上传。此次复跑用于验证早期成功后改变的共享完整性索引和最终来源，不因历史绿或文档变化机械重跑。

## 最终完整验收与保留核对

最终Backend于14:54:20～15:25:46（Asia/Shanghai）完成，Maven阶段及门禁子进程均0。独立重算190份XML的计数与本轮runId一致；Backend 534个、Frontend 11个制品SHA256全部匹配，safe字段只含固定白名单。Backend五份proof-origin均绑定本轮UUID与来源，包含Incident三恢复方法和四个OBS场景；运行前后1339源码文件一致。

正常结束后本机运行容器列表[]，匹配本轮UUID的Java进程0；未执行容器或JVM全局清理。最终Frontend14:59:01～15:03:44，实际Node20.19.5；它与Backend独立运行，不争用容器/报告目录。全部成功后仅收尾文档，不再机械全仓复跑。T016/T019/T020按这些实测证据完成，任务20/20，长期文档同步本地范围与未验证边界。
