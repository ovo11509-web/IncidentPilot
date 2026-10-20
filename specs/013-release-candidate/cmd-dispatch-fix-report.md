# Windows命令定位修复及新候选完整All验收

2026-10-19用户确认继续[上一轮阻断报告](gitless-all-report.md)提出的最小方案：修复中央`.cmd`命令定位，补真实回归，以新候选执行完整All。原固定b667、原7d1c BLOCKED及此前Backend通过结果保持各自身份，不改写历史或冻结包。

## 授权与基线

- 证据根`bootstrap/target/cmd-dispatch-fix/9dd05c94/`，验收ID`9dd05c94-e89d-450e-8988-3f2458675d6d`。
- 分支`feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba`、暂存区空，开始时1912源码/说明路径；1906项旧证据/私有输入已逐文件核对并固定，包含旧失败、阻断、通过及候选四文件。
- 修复前两个脚本保存到本轮`before/`。仅[中央门禁](../../scripts/check-delivery.ps1)和[门禁合同](../../scripts/test-check-delivery.ps1)改变执行代码；业务Java、前端、数据集、阈值、受控跳过和全局配置不改。

## 最小修复与真实回归

中央Windows`.cmd`分支保留原可执行输入/参数的元字符拒绝；以`Get-Command -CommandType Application`解析实际完整路径，再对解析结果检查相同危险字符，交给原`cmd.exe /d /s /c`引用逻辑。非`.cmd`分支、退出码、超时、日志及输出隔离不改。

新增真实短批处理位于带空格的PATH目录，从不同工作目录用裸名称及完整路径调用：通过`%~dp0`读取同目录payload，严格核对两行输出、含空格参数及退出7。另核对九类危险参数、危险原始名称和解析后含`&`/`%`路径均被拒绝、未启动；PATH在finally恢复。测试置于任何执行器mock之前。

| 证据 | 本轮结果 |
| --- | --- |
| RED | 旧门禁、新回归实际退出1，命中`CONTRACT_FAILED:CMD_bare_LOCATION_ARGUMENT_EXIT` |
| GREEN | 修复后同一测试脚本实际退出0，`CONTRACT_PASS 176` |
| 测试来源 | RED/GREEN测试脚本SHA相同，`3679F52F597CA4E4B7F269A60C7E2DD5C6D2B3D85D927A2F48E15A631126EBDF` |
| 门禁修复前/后SHA | `BD72EDD85AA6833EBFF77AA0D5DC4930C157A85006EC3053E9FE35FE3CE577B5` / `B91863D17547804C7CC3C230C3C1A040DF60B1CCC6890893C6F9A8857AC18C99` |
| 实际npm | 用修复后的真实执行器从frontend工作目录调用裸`npm.cmd --version`，Node20.19.5/npm10.8.2、退出0，npm离线，仅子进程环境 |

另按计划追加执行49项候选合同，实际UTC `2026-10-19T10:50:17.7518920Z`至`10:50:21.4817910Z`、退出0。该检查发生在新候选封存及All启动之后，候选工具脚本未变更，文件摘要与候选另行核对；不把它写成启动前检查。记录为本轮`candidate-contracts.json/log`。

176为本轮合同检查数，不与RED执行或旧159项相加；这不是业务测试总数。真实npm检查不连接模型或业务服务，单项成功不替代完整All。

## 新候选及完整All

新候选ID：`c1687735-1ad9-4881-a620-81702d39d1ab`，1907文件；新门禁runId：`d98d8861-aa83-4168-82f5-c84e2b62022b`。176项门禁合同与真实npm通过后才冻结新候选、Verify四文件，在本轮新空`s/`完整解包；不复制旧target、node_modules或构建结果。源码起止完整清单与门禁受检子集分别绑定。

执行候选原`check-delivery.ps1 -Gate All -CandidatePath <本轮新候选目录>`，实际子进程隔离父Git并移除Git工具，不用CheckOnly或跳过测试。真实All最终`PASS/0/VALIDATED`，同run的Backend、Install、IncidentView、Lint、Build五阶段均退出0；后端201 XML、952项/931通过/21受控外部跳过/0失败错误。运行UTC `2026-10-19T10:46:36.4752184+00:00`至`2026-10-19T11:34:21.8420337+00:00`；数量来自本轮报告，不沿用旧测试总数。

新候选四摘要：source.zip `03CBF4F53B9035206DE3E0425EC60F9C2529D79E187F850BBE3C2FDD98D8E4CD`；source-manifest.json `98DA19D67975E94AD77630CFEC662D61DFF75F3793E1F7720AAAC688A6EEC77F`；candidate.json `32D7AA0B5A32B05C61C949680659C6C01AC83FF07D8F9B4058EB73F38FE58CB6`；SHA256SUMS `AACB6463C57F7E4BE22F47668BFBA97CAF635D3FF7618A5039296B70E93B7F7B`。

1365受检文件摘要`74BEB5839C44BC6B15477DE5400CF740F638800FA423FBC0D26238CA833531D4`。与原b667比较新增2份报告、修改15路径（2个门禁脚本及13份说明）、删除0；前端204文件逐字节相同，但前端通过仍须本轮实际执行支撑。冻结ZIP含的是封存时说明，不包含这些后补执行状态和最终收尾文字。

实际控制命令为本轮`run-all.ps1`，门禁开始UTC `2026-10-19T10:46:36.4752184Z`。六项工具实际为PowerShell7.6.5、Java21.0.12、Maven3.9.11、Docker29.4.3、Node20.19.5、npm10.8.2；前提检查中的原npm自定位阻断在此新run未发生。

Node20.19.5/npm10.8.2仅在本次进程PATH置前，npm离线复用已有缓存，Maven沿用缓存但不强制`-o`。Windows宿主、Java21和本机Linux Docker Engine；不是Linux宿主、异机或全空白依赖环境。

## 独立审计及边界

额度恢复时，本轮All已在中断期间自行完成，磁盘有完整`PASS/0`的receipt、source-after、resources-after和execution-proof；原工具会话52562亦实际返回0，没有尚在运行的Java测试进程。续跑没有重复门禁或聚焦测试，实际继续执行本轮独立审计、最终说明及保护收尾，恢复记录另存`resume-record.json`。

本次恢复后实际执行`audit-reliability.ps1`，得到`RELIABILITY_AUDIT_PASS`，`reliability-audit.json`为`PASS/VERIFIED/COMPLETE`，SHA256为`D957C26D2623D29AA81276D31BD5A9036A45E54064C6E5E8F31D21094B7DAC32`。以下事实均绑定新All `d98d8861`及其新来源，未借旧Backend证明替代。

| 审计内容 | 本轮事实与界限 |
| --- | --- |
| 必需检查与跳过 | 14个必需类实际执行、零意外跳过；21项跳过仅属原13类手动外部门关闭目录，不代表这些外部服务已通过 |
| 固定规则 | 15案例×3顺序=45组；114输入，99处理、15个声明失败成员符合fixture，未完成0；声明失败成员不是JUnit错误 |
| HTTP幂等 | 40次请求均202，1次创建、39次重复，持久身份唯一；另1个冲突被拒绝 |
| 进程/数据库恢复 | 新PID处理已受理1条、重投0；自有数据库实际暂停/恢复，故障期503，积压恢复1条、重投0，重试到期时间由fixture控制 |
| 本机基线 | 四组400个独立样本，失败/未受理/未完成0；不由此推导生产SLO或容量上限，完成延迟包含100ms轮询误差 |
| 固定离线评测 | 四组评测的来源、既有阈值/分母及16个模块外置产物逐项核对；模拟模型、非盲评或真实模型质量结论 |
| 审批和重启 | Bootstrap六份XML共17项全通过，其中黄金/安全/观测/隔离启动四类13项。真实断言待审批/拒绝0应用、批准1应用、同意图重放/主进程重启仍1；新summary记录应用1、回执1、模型0 |
| Trace | 新origin绑定d98，68 spans、唯一执行、两主进程实例、三类跨进程父子关系，Q01～Q05通过；不证明生产OTLP后端 |

选定14项Bootstrap XML、Golden及Trace文件与本轮artifacts摘要一致。Trace固定计数proof内容可与旧运行相同，不能仅凭其相同哈希证明新运行；本轮origin和三项lab字段另行核验。黄金链路属于真实测试classpath JVM、HTTP和临时PostgreSQL/MCP安全模拟，不是本轮发布JAR或真实模型验收。

本轮独立审计实际为`PASS/VERIFIED/COMPLETE`，与`d98d8861-aa83-4168-82f5-c84e2b62022b`、`c1687735-1ad9-4881-a620-81702d39d1ab`和`74BEB5839C44BC6B15477DE5400CF740F638800FA423FBC0D26238CA833531D4`一致，核对新XML/必需目标/受控跳过、可靠性proof及两类评测外置产物。前端四阶段真实完成；完整来源和1906项旧输入与资源执行末检保持，最终保护和本轮说明摘要以综合`closeout-proof.json`/`completion.json`为准。原始日志/XML/proof及私有配置保留本机，公开仅safe摘要。

本轮不新增发布JAR/持久业务/浏览器演练，不验证真实模型、Broker或生产SLO。异机/承诺平台、托管CI、旧两项TBD、安全历史整合及正式Git发布仍分别开放。无提交、Tag、推送、合并或部署。旧T076只能按原阻断事实收尾，不能称旧b667完整审计通过；新成功验收另由阶段19新身份支撑。

### 阶段19实际执行补记（2026-10-19）

上文TBD、进行中和未完成表述属于创建时状态。实际新候选`c1687735-1ad9-4881-a620-81702d39d1ab`（1907文件）、真实无GitAll `d98d8861-aa83-4168-82f5-c84e2b62022b`为PASS/0，Backend、Install、IncidentView、Lint、Build五阶段退出0；201 XML、952项/931通过/21受控外部跳过/0失败错误。受检1365文件摘要`74BEB5839C44BC6B15477DE5400CF740F638800FA423FBC0D26238CA833531D4`，独立可靠性/评测审计PASS/VERIFIED/COMPLETE；完整来源与1906项旧输入和资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED、fdbac063 Backend PASS及其他旧FAIL/PASS保持；T076仅以原0阶段/0XML的阻断核查失败终止，SC-019仍未满足，新候选完整成功审计由T081承接。82/82表示任务已关闭，包含失败终止，并非82项验收成功。后补说明不回填冻结ZIP；异机/承诺平台/托管CI、旧TBD、安全历史整合和正式Git发布继续开放。
