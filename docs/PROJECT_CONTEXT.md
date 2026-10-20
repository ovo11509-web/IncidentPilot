# IncidentPilot 项目上下文

## 2026-10-20简历展示入口整理

源码整合提交`e721591`已推送至`ovo11509-web/IncidentPilot`的`codex/release-v0.1.0`分支，原`main`保留。用户随后批准重写项目首页、补充真实截图、修正必要旧说明，并提交推送及调整默认分支、About与Topics；不包括Tag、Release、合并上游或部署。

本轮首页以IncidentPilot的事件响应链路、个人新增与上游复用边界、运行入口和可追溯验证记录为主体。两张图片来自2026-10-11/12的本地持久安全模拟页面，保留原始图片字节并标明历史时点，不能当作当前提交的新运行记录。README、NOTICE及本节修正“新增实现尚未提交”等旧时点口径；下文原阶段记录继续按各自执行时点理解。

本轮只修改展示文档与图片，不改变业务代码、Schema、依赖或测试门槛；不新增Backend/All、应用启动、异机或生产验收结论。GitHub入口设置在文档推送后按批准范围执行，最终结果以分支日志与实际仓库配置为准，旧固定源码包不回填本轮说明。私有展示收尾记录ID为`e12b714c-1b69-4f7f-b116-66e5a85eb3ee`，不作为公开文件链接。


## 2026-10-20阶段21格式范围补记

提交前Git格式门禁发现46处问题，本阶段实际范围补充为7份必要承载/业务说明更新及34文件格式等价修复；TODO两类交集1份，合计40文件相对阶段20不同，源码路径仍1908。格式修复本身涉及13份Java的14处纯空白行空格及21份Markdown的硬换行/EOF：Java执行逻辑未改，Markdown格式转换去除position后的AST相同，逐文件before/after与独立复核一致。

13份Java的原始字节已经变化，不能继续宣称当前完整字节或受检摘要与旧c168/d98完全一致。旧All仍只归属原候选/run/raw摘要；本轮没有新跑All、编译、JAR、浏览器或服务。新增证据是逐字节差异、Java空白与Markdown AST等价检查及实际Git格式检查，最终工作区/暂存树检查以外置回执的真实结果为准。格式证据沿用外置记录`498a7289-80dc-4a8b-916b-3c97229e1c23`中的format-review、format-parent-check和format-applied，不创建跨库文件链接；以下2026-10-19“仅7说明/原字节一致”口径仅为补充前时点，本补记及修订FR/SC为本阶段最新验收范围。

## 阶段21提交前冻结说明（2026-10-19）

用户已选择独立净化衍生历史承载本地正式整合，分支为`codex/release-v0.1.0`，并授权检查通过后创建一个本地整合提交；该授权不包括push、Tag、托管CI触发、发布或部署。承载选择已明确，下方阶段20的carrier TBD保留为原时点记录。

本轮以净化历史tip `437e275d8f4443c4b1b86b05321de1496594a987`为父历史输入，按阶段20最终清单对齐1908条完整源码路径，排除原6条开发者配置；仅在此独立承载库更新7份必要说明/Spec，业务、门禁、数据集、许可及上游署名保持。原开发库及其1914路径、538项待审保留。

本节是提交前冻结的输入与授权说明，实际本地提交结果、commit/tree SHA及最终源码映射以外置整合回执和本库Git日志为准，不在提交后为回填SHA再修改本节。外置记录ID为`498a7289-80dc-4a8b-916b-3c97229e1c23`，逻辑证据路径为`bootstrap/target/release-integration/498a7289/`，由原开发库保存，不随源码携带，也不是本库的可点击文件入口。

c168/d98 All及阶段19独立审计仍是既有来源；本轮不新跑All、JAR、浏览器、异机或生产验收，后补说明不回填冻结候选。阶段21范围见[规格](../specs/013-release-candidate/spec.md)及[整合计划](releases/v0.1.0-integration-plan.md)。以下各阶段仅保存原时点事实。

> 2026-10-19阶段20：已完成正式整合前逐文件审查准备，详见[本轮记录](../specs/013-release-candidate/integration-review-report.md)。完整工作区/拟整合源码分别1914/1908路径，待审538项（53修改/485未跟踪，最终以本轮清单为准）；安全历史附件的摘要、bundle/恢复库与805映射复核通过。c168/d98既有All与当前执行源码一致，后补说明单列，本轮没有新测试/构建或候选。承载仓库/分支仍TBD，未复制源码、切换、暂存、提交或发布；异机/平台/托管CI、旧TBD继续开放。下文保留各阶段原时点。

> 2026-10-19阶段19最新收尾：Windows中央`.cmd`路径修复，真实RED/GREEN 176项合同、npm及后补49项候选合同通过；新候选`c1687735`（1907文件）真实无GitAll `d98d8861`已PASS/0，同run五阶段退出0，201 XML、952项/931通过/21受控外部跳过/0失败错误，独立可靠性/评测审计通过。完整来源/1906旧输入及资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED及旧Backend PASS保持；T076失败终止、SC-019未满足，新成功由T081支撑，82/82仅任务关闭。详见[本轮报告](../specs/013-release-candidate/cmd-dispatch-fix-report.md)。后补说明不回填冻结ZIP；异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段18：固定`b66759af`新解包树的真实无GitAll已尝试，run `7d1c8f40-df23-496f-8fac-485ea0706377`为`BLOCKED/2/PRECONDITION_FAILED`，测试阶段0、XML0。Windows门禁以带引号的裸`npm.cmd`经`cmd.exe`启动时，npm错误地在源码工作目录定位自身模块；同环境完整路径调用成功。未改固定候选或根门禁；完整All/前端及专项审计仍未通过，等待确认最小修复和新候选复验。证据根`bootstrap/target/gitless-all/a392f45a/`；详见[本轮报告](../specs/013-release-candidate/gitless-all-report.md)。旧FAIL、Backend PASS、异机/平台/旧TBD/安全历史整合与正式发布边界保留。以下摘要保持原时点。

> 2026-10-19阶段16/17后端续跑：原ebf/`9624289c`真实FAIL及162XML保留。两类评测reporter的隐含Git依赖已修复，新增22项来源校验，普通Git/真实无Git聚焦分别27/27；新候选`b66759af-6cfb-4cb6-8db5-65276702f443`（1905文件）的真实无GitBackend `fdbac063` 已PASS/0：201新XML、952项/931通过/21受控外部跳过/0失败错误，14必需类零跳过。额度恢复后未重跑Backend，实际补做45规则、HTTP幂等、进程/数据库恢复、400样本及16项评测外置产物的独立审计并PASS。完整来源/1200旧输入、Git、既有16容器/94卷和说明以本轮综合proof/completion绑定；Spec M73/73限已定义本地任务。详见 [本轮报告](../specs/013-release-candidate/candidate-backend-report.md)。前端204文件与ebf字节相同，未新跑Frontend/完整All、异机或发布；旧TBD、安全历史整合及正式Git授权仍开放，冻结候选不含后补最终说明。以下摘要保留原时点。

> 2026-10-19补跑最新状态：用户启动Docker后，在原ebf候选1901文件的既有解包树完成真实无GitAll CheckOnly `f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`，CHECKED/0、测试阶段0、schema2、`head=null`；候选身份与旧完成记录的八项身份字段严格匹配。Linux Docker Engine 29.4.3实际可用，宿主仍为Windows；T062关闭，阶段15为7/7、Spec M累计64/64。证据根`bootstrap/target/gitless-preflight-followup/f4721f5c/`，详见 [补跑记录](../specs/013-release-candidate/gitless-delivery-report.md)。本轮仅补前提检查，未新跑Backend/完整All/Frontend或异机验收，未改源码、未生成新候选或发布；当前1908条源码/说明路径、532项待审不变。旧ac712的BLOCKED/2及原a835完成记录作为历史输入保留，下面各阶段摘要按原时点理解。

> 2026-10-19阶段15最新状态：无Git源码包的来源入口已修复，显式`-CandidatePath`核验完整候选/实际源码并生成schema2安全摘要，默认Git模式保持schema1。159项门禁合同和49项候选合同通过；新验证候选`ebf476ea-f17f-4a15-a8d4-365bcb8e1ad1`（1901文件）在无父Git/无Git工具下完成Frontend CheckOnly及四阶段PASS。All CheckOnly因本机Docker Linux引擎管道不可用返回BLOCKED/2，T062仍开放；阶段15为6/7、Spec M累计63/64。未执行新的Backend/完整All、持久业务或异机验收，未提交/Tag/推送/部署。详见 [本轮记录](../specs/013-release-candidate/gitless-delivery-report.md)。下一段及后续历史记录保留原时点。

> 最后核对：2026-10-19，基于分支 `feature/incident-platform` 的当前工作区。Spec A～L及Spec M已有本地构建/All/持久业务证据保持原身份。用户暂无第二机器，要求先推进后续动作，阶段14现整理v0.1.0发布草案、完整待审清单和整合/回退操作草案，状态仅`RELEASE_PREPARATION_ONLY_NOT_RELEASED`。异机、无Git门禁、正式安全历史整合与Git发布仍开放，两个旧TBD保留；本轮未改业务或门禁、未执行新构建/All或服务。
> 本文记录项目级长期事实。代码、配置和可执行检查发生变化后，应同步核对本文。

## 项目目标

IncidentPilot 是 `nageoffer/ragent` 的衍生项目，目标是建设一个以 Java 后端为主的
AI 运维事件响应平台，服务于 SRE、运维人员和值班开发者。

项目定位为 **面向生产设计的作品项目**：实现可部署、可测试、可审计、可复现的核心链路，
用真实代码和运行证据展示工程能力，但不把未经真实生产验证的结果宣称为“生产级”。

目标主流程为：

1. 接收外部告警并进行幂等处理与关联聚合。
2. 将相关告警组织为可跟踪的 Incident。
3. 使用 Runbook 知识库和 RAG 为 Incident 提供有依据的诊断建议。
4. 对有副作用的处置动作执行服务端强制的人工审批。
5. 通过 MCP 执行安全的演示或模拟处置。
6. 使用只追加的事件时间线和审计记录保存全过程证据。

项目采用质量门而不是固定工时作为完成标准。时间估算用于排期，不得以赶进度为由省略
幂等约束、输入校验、审批控制、审计记录、异常处理和必要测试。

## 项目来源与归属

本地交付入口及贡献边界已整理至 [2026-10-12候选说明](releases/local-candidate-2026-10-12.md) 与 [当前源码候选](releases/source-candidate-v0.1.0.md)。首次正式版本号已确认为`v0.1.0`，Maven/前端构建版本现统一`0.1.0`，尚未提交或发布；不能仅检出当前HEAD获得未提交实现。源码ZIP保存工作区内容，完整Git历史因潜在凭据未随包移交，原仓库历史不改写。

- 上游仓库：`nageoffer/ragent`。
- 当前项目保留上游 Git 历史和 Apache-2.0 许可证。
- RAG、Agent、模型路由、知识入库、MCP 桥接和现有管理界面主要来自上游。
- Incident 领域的模块、告警接入、聚合、状态机、Timeline、Outbox、恢复和运行状态属于本项目新增代码；
  Spec B 的审批、一次性票据和安全模拟处置，以及 Spec C 的模拟 Runbook 检索与诊断证据链也是本项目新增代码；Spec D的Incident页面、专用客户端和安全查询投影属于本项目新增贡献。
- Spec E的主链关联/短时Trace、Spec F本地故障实验入口、Spec G确定性测试治理和Spec H交付门禁/安全CI摘要也属于新增贡献；不将上游RAG/Agent框架算为自行从零实现。

## 当前已实现功能

- 2026-10-19补齐T062：同一ebf验证输入在既有1901文件解包根、无父Git/无Git工具条件下完成真实All前提检查，runId `f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`为CHECKED/0，Java21.0.12/Maven3.9.11/Docker29.4.3/Node20.19.5前提可用，未进入任何测试阶段。阶段15为7/7、Spec M64/64，仅关闭该Spec本地已定义任务；这不是新干净环境复现、Linux宿主验证或新完整All。未重新执行Frontend，继续引用其原63e4 PASS；ebf仍早于后补说明，070的完整All/真实JAR业务证据不转归ebf。旧TBD、异机、安全历史整合与正式发布授权继续保留，见 [补跑记录](../specs/013-release-candidate/gitless-delivery-report.md)。以下旧结果保留原时点。

- 2026-10-19阶段15完成无Git候选来源修复及Frontend实测：新候选ebf的完整1901文件、四文件摘要和受检子集分别绑定，`head=null`、`baselineHead`仅作历史基线。Frontend预检`5fb0e086-e82e-4f70-b2c1-4b225754617e`为CHECKED，真实四阶段`63e4bec8-7de7-4ea7-aaa9-242a05f69ec4`为PASS/0；All预检`ac712f69-fea2-4ea0-9401-f842b88ce386`因Docker前提失败为BLOCKED/2。首253候选四阶段0但完整树末检FAIL的记录保留，仅按既有测试配置补充`frontend/.output/incident/`，不放宽整个`.output/`。新候选冻结早于最终报告/说明，不继承070的完整All或业务通过；旧531清单仅为阶段14快照，新增本轮报告后为532项待审（53修改、479逐文件未跟踪）。原Git与旧候选保持，异机、安全历史整合、正式发布及两个旧TBD仍开放，见 [本轮记录](../specs/013-release-candidate/gitless-delivery-report.md)。以下历史段落保持原时点。

- 2026-10-19阶段14将异机之后可先做的发布材料集中为 [v0.1.0发布准备](releases/v0.1.0-release-preparation.md) 与 [整合草案](releases/v0.1.0-integration-plan.md)，以逐文件Git状态/内容SHA记录待审范围，复用候选现有路径和敏感模式规则；发布仍未执行。本轮只新增说明和本机核对记录，分阶段历史证据、不支持的平台及无Git门禁限制继续明确，见 [阶段14记录](../specs/013-release-candidate/release-preparation-report.md)。以下记录保留原时点。

- 2026-10-18阶段13按用户暂无第二机器的实际条件，仅准备固定070四文件、中文手册及白名单来源摘要的可携带ZIP。7成员新解包字节一致、9段PowerShell语法/公开固定签名、1895源码与原Verify通过；实际隔离父Git后CheckOnly退出1/FAIL/GATE_FAILED，未进入工具或测试阶段。详细流程与所有异机待执行项见 [接手手册](../specs/013-release-candidate/second-machine-handoff.md)。本轮不启动服务、不重新构建或执行完整All，不传私有配置、缓存或历史；负向预检只能证明交接限制，不能用来声称第二机器通过。以下记录保留原时点。

- 2026-10-18阶段12复用阶段11已验证制品，新`candidate-run-cea55ab3`从空库完成2告警→1Incident、诊断5引用、拒绝零应用/批准唯一1应用、同选择意图重放及Stop/Start。独立SQL核对14连续Timeline/6 Outbox（relay关闭、PENDING），重启快照一致；117 dist/117次HTTP字节一致，两Java进程确为`-jar`。最终实例停止、5卷保留；本轮未重跑All或新增浏览器/异机结论，见 [持久运行与重启记录](../specs/013-release-candidate/persistent-candidate-runtime-report.md)。以下记录保留原时点。

- 2026-10-18固定`070c4b76-70db-48c0-b7d0-b701a527f9e3`从全新解包根完成87门禁合同、预检、两份0.1.0 JAR和新All `353b453f-56c7-451e-a54a-67057342a19c`。199新XML/14必需类零跳过，前端四阶段退出0；45规则组合、40次HTTP幂等、进程/数据库恢复及400正式样本均有本轮独立复核。中断37XML不计通过，JAR未在本轮持久启动，缓存/平台/来源与收尾事实见 [固定候选完整验收](../specs/013-release-candidate/full-candidate-acceptance-report.md)。以下记录保留原时点。

- 2026-10-17继续T033，受控新容器Mounts确认固定RustFS镜像未显式映射的`/logs`会生成匿名卷。Compose增加带instance标签的`storage-logs:/logs`；聚焦30合同/代理2项、候选合同49项及新Name三轮依赖重建通过，全局卷集合89恒定、日志标记留存、业务空库不变。旧匿名卷历史归属继续TBD，旧4卷实例不迁移；详见 [修复记录](../specs/013-release-candidate/storage-volume-fix-report.md)。以下记录保留各自时点，不能回填为新候选完整业务验收。

- 2026-10-17后续完成同一candidate-proof新服务进程首次/热态各115项静态读取和真实浏览器只读复核，包括工作台、既有诊断/两个Action/回执、复盘、同版本手册、侧栏滚动及退出保护；SQL业务计数保持，实例停止且4命名卷保留。原16容器/80卷保留，但新增匿名卷1，日志挂载来源推断尚待独立确认；首轮静态异常继续TBD。详见 [冷态与浏览器记录](../specs/013-release-candidate/browser-followup-report.md)，以下为此前时点记录。

- 2026-10-17用户继续下一步，固定源码候选从新目录/新卷构建真实0.1.0 JAR与dist，经真实web代理完成2告警→1Incident、诊断、拒绝0应用、批准1应用、同意图重放及Stop/Start保留。5条引用/117前端文件/115项HTTP静态复核通过；首次辅助静态采集FAIL根因TBD，后续3轮只读复查成功，保留未解决边界。新实例最终停止且保留4卷，原资源不动；见 [本机隔离复现记录](../specs/013-release-candidate/reproduction-report.md)。限Windows/Java21/Node20及既有缓存，不称第二机器或完整交付全绿。

- 2026-10-17用户了解历史处置目的后回复“ok”，批准隔离副本净化。实际805提交映射、4个merge及390,029文件快照核验通过，SHA变化804、旧签名移除30；历史包全新恢复的17,833对象中原疑似值/原命中blob为0。独立待审源码精确等于冻结1890文件，新增实现保持未提交。见 [实施与边界](../specs/013-release-candidate/history-sanitization-report.md)。这补充以下只读审查时点，原源码候选仍不带历史，附件单独标识为衍生历史；原库不改写。

- 2026-10-17后续只读审查覆盖本地全部refs可达对象及提交文本，未新增疑似值规则命中；184个提交快照含既有疑似凭据，内容替换预计连带804个提交SHA，30个提交含签名。已形成 [历史移交审查与待授权方案](../specs/013-release-candidate/history-handoff-review.md)，尚未执行净化；原库与固定源码候选保留，不能声称历史已经安全交付。

- 2026-10-17用户批准继续第1步，由 [Spec M](../specs/013-release-candidate/spec.md) 管理版本/源码候选。9POM统一、演示JAR精确定位及聚焦合同已通过；当前树离线package已成功，构建跳过业务测试。候选提供源码ZIP、逐文件清单、候选身份和SHA256SUMS，排除私有/生成内容；历史WITHHELD而非冒称完整移交。真实状态见 [实施记录](../specs/013-release-candidate/implementation-report.md)。

以下Spec L结果对应2026-10-12版本改动前的源码，不作为本轮新的Backend/All通过记录。

- 2026-10-12用户批准 [Spec L可靠性专项验收](../specs/012-incident-reliability-acceptance/spec.md)，15/15任务完成：15个独立固定预期×3处理顺序、真实HTTP幂等/冲突、受理后真实进程重发现、自有数据库暂停及既受理积压恢复、四组400样本本机基线通过。修复测试夹具端口竞态，保留首轮FAIL；最终Backend `1d0cd914-3565-4e70-9f73-e98fe4db02b9` PASS，199XML/930项/909通过/21受控跳过/0失败错误，1363源码及557制品哈希核对通过。业务源码和Schema未改，现有演示未操作；测量限制及逐项事实见 [实施记录](../specs/012-incident-reliability-acceptance/implementation-report.md)。

以下能力已能从当前代码、Controller、Schema 或配置中确认：

- Spec A～H共266项任务在已批准的本地范围完成。SPC-001及M0已按代码、Schema、实际报告和签署核对收尾；总览、逐项要求映射及剩余边界见 [本地完成度核对](development/local-acceptance-review.md)。这不是生产就绪或托管CI通过结论。
- 六类固定故障实验及只读归档Verify已经由Spec F实现；Spec G补齐实际服务/客户端的确定性检查并保留外部门；Spec H提供固定Smoke/Backend/Frontend/All和CheckOnly、CI定义及部署准备说明。Spec K新增独立本机持久演示栈，真实JAR/dist已完成两告警、诊断、拒绝零应用、批准一次应用和重启保留事实验证；生产部署未交付。见 [Spec K实施记录](../specs/011-incident-quality-delivery/implementation-report.md)。
- 2026-10-12 Spec L新增检查前的All门禁 `f989e36a-9e11-4f92-a719-2149762870f1` PASS：197份新XML、884项/863通过/21受控跳过/0失败/0错误，Install/IncidentView/Lint/Build均退出0，来源结束复核与安全摘要通过。限Windows、Java21、Node20；独立中断run不计通过，此历史All不包含Spec L。事实及源码摘要见 [后续验收记录](../specs/011-incident-quality-delivery/implementation-report.md)。
- Spec E 统一 Incident HTTP 尝试 requestId，以 incidentId 汇总聚合后操作；保留各 Alert correlationId 和历史事实。新增固定字段安全日志、默认关闭的 OTel 短时 Trace、认证 MCP/内部回调传播及显式工作线程 Context/MDC 恢复。该主链观测属于 IncidentPilot 新增贡献；本地运行证据及全仓门禁状态见 `specs/005-incident-correlation-trace/implementation-report.md`，不代表追踪后端或生产验收。

- 本地五类模拟 Runbook、范围/版本过滤、稳定引用原文与摘要；冻结 Incident 来源事实，
  按审阅规则返回候选和只读建议，拒绝越权生成。历史诊断独立于聊天消息读取。
- 健康模拟建议可由人员显式转换为待审批 Action，并持久记录诊断来源；默认没有目标映射，
  诊断不会自动批准或调用 MCP。真实模型与生产 Runbook 未接入；操作界面已由 Spec D 完成。
- Spec C v2 34例由fOggy于2026-10-04确认并签署，HR-001/002处置与剩余安全契约覆盖已核对，T028关闭、36/36任务完成。质量结论限本地模拟语料、关键词检索和受约束抽取规则；公开设计holdout不证明未知分布泛化。

- 用户登录、退出、个人信息、用户管理和密码修改。
- 业务变更日志查询。
- 知识库、文档、切片、入库任务和可编排入库 Pipeline 管理。
- 向量检索、可选关键词检索、可选图谱检索、问题改写、Rerank 和引用输出。
- RAG 流式聊天、会话管理、反馈、推荐问题、检索 Trace 和评测入口。
- AgentScope ReAct Agent、Agent 配置、Prompt、技能、会话记忆、工具追踪和任务取消。
- MCP 工具发现与调用，以及写工具执行前的现有人工确认交互。
- 独立 MCP Server，包含电商、企业办公、日期、天气和联网搜索等演示工具。
- React 管理后台和聊天页面。
- Spec A 的 HMAC Webhook、持久化幂等接收、Incident 聚合与状态机、只追加 Timeline/Alert 审计、
  可恢复 Worker、人工重放、Transactional Outbox、人员运行状态和隔离 PostgreSQL 测试；
  当前门禁用固定/替身发布器验证 at-least-once与故障恢复。历史Broker生产者确认/持久化偏移及跨JVM记录见changelog，不是本次新的Broker联合验收。尚无 `incident-domain-events` 业务消费者，也未验证生产运行。
- Spec B 的 Action、Approval、Execution、一次性 Ticket、结构化回执、UNKNOWN 对账及人工结论；
  普通 MCP 入口停用旧写工具，专用入口仅可改隔离演示库。已通过隔离数据库和本地跨 JVM 定向测试，
  同库两告警聚合→拒绝/批准→一次模拟应用的联合链路已通过。B01～B12当前目录于2026-10-05收尾；B10部分身份/证明、B11 Producer及部分时间推进仍为声明的替身/fixture，不声称所有部署变体均已验证。2026-10-06 H新入口完整Backend通过864项（843通过、21手工跳过），零失败/错误；外部探针的跳过不算验收，详见Spec B证据索引及Spec H quickstart。

## 当前范围外与尚未验证能力

- 六类首版之外的故障目录和规模实验；当前Spec A/F已有本地检查与实验入口。
- 真实生产 Runbook/模型的质量评测；本地关键词检索与抽取规则自动门禁已有运行记录。
- 真实跨主机MCP处置与生产权限/凭据治理；当前安全模拟威胁模型、B01～B12及恢复目录已完成。
- Linux/Node22/Java17运行兼容性、托管CI实际运行、常驻部署与备份/升级/回滚；CI定义和部署准备说明不能代替这些验收。

以上未验证或扩展项不由本地M0收尾自动获得授权；非平凡扩展须先明确需求并走Brainstorming/Spec Kit。

## 技术栈

### 后端

- Java编译基线/release为17；当前实际隔离验收使用Java21.0.12，Java17运行兼容性未实测。
- Spring Boot 4.1.0，多模块 Maven 工程，仓库内 Maven Wrapper 版本为 3.9.11。
- Spring MVC、Jakarta Validation、MyBatis-Plus。
- Sa-Token 认证，Redis/Redisson 用于会话、协调和并发控制。
- PostgreSQL；默认向量存储可使用 pgvector。
- RocketMQ 用于已启用的异步流程。
- AgentScope Java、MCP Java SDK、OpenTelemetry；Langfuse 为可选追踪后端。
- S3 兼容对象存储或阿里云 OSS；Milvus、Elasticsearch 和 LightRAG 为可选后端。

### 前端

- React 18、TypeScript 5、Vite 5。
- React Router、Zustand、Axios、Tailwind CSS、Radix UI。
- Recharts 和 AntV G6 用于统计与图谱展示。

### 工程与规格

- GitHub Spec Kit：`.specify/` 与 `.agents/skills/speckit-*`。
- 后端测试：JUnit/Spring Boot Test；具体命令见 `docs/development/testing.md`。
- 前端检查：ESLint 与 Vite production build。

## 关键目录

| 路径 | 作用 |
| --- | --- |
| `bootstrap/` | 主 Spring Boot 应用装配与运行配置，默认端口 9090 |
| `framework/` | Web、数据库、MQ、幂等、并发控制、追踪等公共能力 |
| `infra-ai/` | 模型、Embedding、Rerank 和供应商适配 |
| `system/` | 登录、用户、权限上下文、示例问题和业务审计 |
| `rag/` | 知识管理、入库、检索、RAG 会话、Trace 和评测 |
| `agent/` | ReAct 编排、技能、记忆、MCP 工具桥接、确认与追踪 |
| `mcp-server/` | 独立 MCP 演示服务，默认仅绑定本机 9099 |
| `incident/` | Spec A/B/C 的告警、Incident、审批/执行、诊断事实、Timeline/Outbox 和恢复 |
| `frontend/` | React/Vite Web 控制台 |
| `resources/database/` | 主 PostgreSQL 全量 Schema、初始化数据和增量升级脚本 |
| `resources/docker/` | PostgreSQL 周边组件及可选服务的容器示例 |
| `docs/` | 项目上下文、架构、决策、任务、笔记及开发文档 |
| `.specify/` | Spec Kit 模板、治理和工作流元数据 |
| `.agents/skills/` | 仓库本地 Spec Kit 技能 |

## 当前开发阶段

2026-10-10用户选择IncidentPilot B深色科技工作台概念图并批准首阶段实施：品牌/公共导航、真实数据值班首页、既有事件页统一主题。管理员只读汇总接口、主题与真实隔离浏览器主链已实现；2026-10-12进一步补齐四个规定视口运行截图、普通角色跳转/退出、原生侧栏滚轮及完整旧配置环境回归，Spec I共29/29完成。图中样例不属于运行数据。见 [Spec I实施记录](../specs/009-incidentpilot-workbench/implementation-report.md)。本机持久启动已由Spec K实际验收；跨平台复现、正式版本与生产仍需各自后续范围及授权。

Spec A～H的已批准本地门槛完成，当前进入作者走读、可交付版本整理和未验证平台的后续规划。SPC-001与M0于2026-10-06核对收尾；工程完成与作者独立演示、跨平台/生产验收分列，依据见 [本地完成度核对](development/local-acceptance-review.md)。历史分日进展保留，当前状态以本节及TODO完成表为准：

- Fork、上游远程和 Apache-2.0 许可证仍然保留。
- Spec Kit 和仓库级 AI 开发指南已放入当前工作区，但尚未提交。
- 已建立文档驱动的上下文管理，并确认“面向生产设计的作品项目”定位。
- 已确认三项重点增强能力：告警可靠性与故障回放实验室、Runbook RAG 评测门禁、
  MCP 安全处置网关。
- 已创建 Incident 领域模块、五张核心表及 Spec A 业务接口和 Worker；Spec D事件列表和详情已复用现有管理员后台创建，本地隔离浏览器验收完成。
- Incident 主数据已确定存放在主 PostgreSQL `ragent` 中，使用现有增量 SQL 机制并保持领域隔离。
- 告警可靠处理已确定采用 PostgreSQL 持久化、数据库 Worker 异步聚合和 Transactional Outbox；
  RocketMQ 只负责提交后的跨领域事件分发，不进入告警接入和 Incident 聚合的正确性边界。
  `DEC-013` 进一步确定 Webhook 的签名时效、载荷上限、JSON 结构限制和幂等响应状态要求；
  `DEC-014` 要求所有来源 HMAC 请求同时绑定 HTTP 方法、目标路径和请求体；`DEC-015` 确认
  Alert 审计使用独立只追加事实表，而唯一 Incident 归属直接保存在 Alert 行。`DEC-016` 确认
  同键 Alert 接收和 Incident 首次封存使用持久化边界分配的单调可信业务排序时间；`DEC-017`
  和 `DEC-018` 分别归档告警聚合规则与 Incident 主状态机。
- MCP 审批与幂等执行协议已由 `DEC-011`、`DEC-012` 确定：Incident 是 Action、审批、一次性票据、
  执行回执和 Timeline 的唯一事实来源，并拥有 Action 调度与过期清扫 Worker；`agent` 只实现交互
  与出站调度适配，`mcp-server` 对白名单工具的所有调用路径强制服务身份认证和在线票据消费。
  票据消费后的执行使用独立截止时间；无回执时收敛为 `UNKNOWN`，禁止盲目自动重试，只能通过
  可信迟到回执、只读对账或带证据的人工结论收敛。该协议已进入 Spec B 业务代码，完整场景验收状态见 `docs/changelog.md`。
- M0 项目级目标由两份可独立验收且顺序推进的 Spec 承接：Spec A“可靠告警到 Incident”覆盖告警、
  聚合、状态机、Timeline 和 Outbox；Spec B“审批到安全模拟处置”覆盖 Action、人工审批、票据、
  模拟执行、结构化回执与 `UNKNOWN` 恢复。两者共享 Incident、Action、Timeline、状态名和关联标识。
- Spec A 已于 2026-09-23 建立 v1.0 基线，50 项稳定编号任务已完成；Spec B 的 v1.1 规格和计划已确认，
  64项任务及当前目录收尾；Spec C 36项含v2签署，D/E/F/G/H分别31/27/18/20/20项完成。后续操作服从当前用户授权；本地验收只使用临时数据库，不授权开发/生产库迁移。
- Spec A 的 `TASK-001`～`TASK-050` 已按对应测试和模块级回归完成；验证记录见
  `docs/changelog.md`；只追加事实的
  普通应用数据库角色依据已确认的 `DEC-021` 建立。

## 文档导航与权威顺序

1. 当前用户指令和授权边界。
2. 当前代码、配置、数据库 Schema 和可执行检查。
3. 根目录 `AGENTS.md` 和本目录中的项目文档。
4. 已批准的 Spec Kit 规格、计划和任务。
5. 历史方案、聊天记录和临时笔记。

- 系统结构：`docs/ARCHITECTURE.md`
- 已确认决策：`docs/DECISIONS.md`
- 当前工作：`docs/TODO.md`
- 排错与临时说明：`docs/NOTES.md`
- 开发环境：`docs/development/environment.md`
- 测试命令：`docs/development/testing.md`

## 已完成阶段：Incident操作界面（2026-10-04）

UI-001的Spec D位于 `specs/004-incident-operations-ui/`，用户确认设计并授权本地实施后完成31/31任务。现有/admin新增事件列表、详情、关联告警、Timeline、诊断快照/引用、显式建议选择、独立审批和执行回执。列表和关联告警采用有界游标查询；冻结参数和回执只输出服务端持久化白名单投影，无Schema变化。DEC-027按接口保留A的code="0"及B/C的"SUCCESS"，全局客户端行为保持。

2026-10-04定向Java34项、前端逻辑/tsc/lint/build通过，真实隔离Bootstrap/MCP浏览器22项最终通过，拒绝0应用、批准1应用、重复/刷新不重执行，响应丢失手动原意图重发同Action。UNKNOWN仅展示fixture，运行恢复另由Java回归覆盖；无映射/旧版本/权限失败关闭。实际命令、3个runId、截图、服务器计数及限制见quickstart和changelog。仅本地安全模拟，未提交/推送/部署，不代表生产或真实模型验收。

## B06 恢复验收补充（2026-10-04）

按既有 Spec B 协议增加真实数据库锁竞争与完整 Worker JVM 崩溃重启测试。实际签票未消费时杀死主应用，重启后原有调度器替票，保持同一个 Execution/执行键/requestId；旧票拒绝、模拟应用及回执各一次，Incident 保持 OPEN。安全快照和进程日志位于 `bootstrap/target/b06-evaluation/3fbfcb09-8099-4b0a-9bfc-f1f381bb9fc0/`，实际失败及回归结果见 `docs/changelog.md`。只增加测试和证据文档，未修改生产协议或 Schema；LAB-000 / ACT-003 的其他缺口仍开放。

## B09 双通道故障验收补充（2026-10-04）

按既有 Spec B 补充真实 Dispatcher 的回调失败、工具响应 EOF、两通道均丢失三种路径。每个 Execution 只调用工具/应用一次，回执、Timeline 和 Outbox 事实各唯一；原回执重复不新增，反向冲突返回 409、成功不覆盖。双通道丢失的 UNKNOWN 快照额外注入只读对账不可用，随后原迟到回执收敛；正常只读对账可快速依据真实应用记录收敛，不能据此要求持续 UNKNOWN。

12 项联合 Java 回归通过，含 B06 真实崩溃恢复复跑。制品 `bootstrap/target/b09-evaluation/9963cf7f-016e-4cf1-90da-1375a452a740/`、命令与失败记录见 Spec B quickstart / `docs/changelog.md`。只改既有测试和文档，不改生产协议、Schema 或前端。LAB-000 / ACT-003 的回执/截止并发、B11 联合故障和正式威胁模型仍开放。

## G03～G06 安全验收补充（2026-10-04）

后续 T056～T060 补齐回执/截止清扫真实数据库锁两种顺序、Action 两类事件的真实 Relay 发送/标记失败恢复、当前安全模拟威胁模型及 B01～B12 分层证据索引。最小 5/2 项与共享回归 38 项均通过，0 失败/错误/跳过；独立 MCP 路径复跑，生产代码、Schema、前端未改。本轮回执竞争采用 fixture、B11 Producer 为替身，不能冒充真实模拟应用或 Broker 验证。

证据归档 `incident/target/safety-closeout/run-742defc2-0468-4e47-b99d-34cb1fe77d8a/`。最新状态以 `specs/002-approval-safe-simulation/evidence-index.md` 和 `docs/security/incident-action-threat-model.md` 为准；上段开放项中 G03/B11/威胁模型由本段更新。历史全场景统一 manifest、更多攻击/部署变体仍未齐，LAB-000 / ACT-003 保留进行中，实验室、Trace、容器/CI 和旧探针隔离工作未被本轮关闭。无提交、推送或部署，不代表生产安全认证。

## 当前阶段更新：本地安全验收收尾（2026-10-05）

Spec B T061～T064 补存并实际复跑 B01～B12 统一证据。最小 5/1 项及扩大 54 项均通过，0 失败/错误/跳过；七个完整 Bootstrap/MCP 入口、独立 MCP 的阶段快照、真实 PostgreSQL 竞争/恢复和对账/人工结论契约都有本轮记录。首次 Docker 引擎未运行的 5 个环境错误单独归档，启动后重跑通过。

归档 `incident/target/b-catalogue/4cf89770-cdca-4f7d-a675-2cc33798ac49/` 含 12 个 B 场景清单、A 核心可靠性清单、18 XML、15 证明目录、环境/命令/源码与制品哈希。B10 部分身份/证据、B11 Producer 为替身，部分截止为 SQL fixture；全程本机隔离安全模拟，实际使用 Java 21、编译 release 17。生产代码、Schema、前端未改，无新增权限或付费调用。

LAB-000 的当前目录/证据定义与 ACT-003 的本地模拟验收门槛完成，Spec B 64/64；历史不完整清单未补造，而由新运行提供统一证据。真实 Broker 不在已批准核心门槛，跨主机/生产安全和更多未来变体未宣称完成。后续主要开放项是 LAB-001、OBS-001、DEP-001 与 TST-002 的旧探针隔离；本轮未启动这些功能设计或实现，也未提交、推送、合并或部署。

## 当前更新：OBS完成与LAB-001设计草案（2026-10-05）

前段开放项中的OBS-001已由Spec E本地验收更新，27/27任务完成；实际命令、840项默认Java结果及21跳过边界见 [实施报告](../specs/005-incident-correlation-trace/implementation-report.md)。该结果不代表生产或追踪后端验收。

用户要求继续下一步，已静态走读OBS稳定身份、取消与实际停止、跨重启恢复三处关键代码，并形成 [Spec F](../specs/006-local-fault-replay/spec.md) 的LAB-001待确认草案：六类既有故障、本地命令入口、事实/Trace报告与只读核验，18项实施任务尚未开始。本地命令方案目前为推荐假设，待用户确认；不增加生产服务、Schema或管理员页面。当前功能指针切至Spec F，实际Git分支/HEAD保持。长期架构与DEC不把待批准方案记录成已实现或已确认决策。


## LAB-001本地实验入口与验收（2026-10-05）

用户“执行”批准Spec F并完成LAB-001本地验收，18/18任务；前段待确认草案由本段更新。固定PowerShell入口复用六类既有故障，报告记录独立runId、源码/测试来源、数据库事实及可用Trace，Verify只读复核归档。六场景7项、两PowerShell版本各30项契约及默认配置5项兼容性回归通过；实际失败与源码快照边界见 [实施报告](../specs/006-local-fault-replay/implementation-report.md)。不新增生产服务、Schema、UI或外部追踪平台，后续DEP-001及旧探针工作保持原状态。


## TST-002分类设计（2026-10-05）

当前功能指针切至Spec G：TST-002旧RAG探针分类与隔离门禁。用户已选择按行为分类，拟补真实服务/客户端规则并保留test-only演示和外部手工探针；You.com/MinerU拟增加显式联调许可门。完整 [规格](../specs/007-rag-test-isolation/spec.md)、计划/20任务待确认，本轮只有静态核对与设计，不属于已实现能力。LAB/OBS验收结果保持，生产架构与正式DEC未变。

## TST-002实施收尾（2026-10-05）

用户授权检查遗漏并直接执行，Spec G 20/20完成，覆盖术语映射、会话查询、意图树、生产分类、Embedding本地HTTP、Milvus SDK交互，新增24项确定性检查；You.com/MinerU增加显式许可门。聚焦40通过，模块392项/19跳过，全仓864项/21跳过，零失败/错误；843项实际通过。前段设计状态由本段更新，详见 [实施报告](../specs/007-rag-test-isolation/implementation-report.md)。旧手工探针和上游署名保留，生产代码/Schema/POM/依赖/架构未变；未验证真实模型、Milvus、开发库或生产基础设施。后续DEP-001仍待设计；术语priority注释与排序方向的遗留差异为TBD。

## DEP-001设计阶段（2026-10-06）

用户要求继续并选择隔离本地编排＋CI＋说明。现有Bootstrap测试已有临时主/模拟PostgreSQL、Redis、RustFS与双JVM编排，仓库尚无CI工作流。推荐复用编排、增加跨平台PowerShell固定门禁入口及最小CI定义，产物只发布白名单摘要；不新增常驻Compose或生产服务。功能指针切Spec H，[规格/计划/20任务](../specs/008-local-delivery-gates/spec.md) 完整方案待审阅，全部未实施；历史Windows结果不证明Linux或托管CI通过。上游可选容器示例不是生产验收，部署准备说明拟明确数据/凭据/资源边界；本轮无新Java、容器、开发数据库或远程操作。

## DEP-001实施验收进行中（2026-10-06）

用户批准完整Spec H，正式analyze无阻塞后实施固定PowerShell门禁、入口/报告负例、安全摘要、最小CI定义及部署准备说明。87项契约、最小三个真实恢复检查、CheckOnly、Smoke和前端均有本轮记录；完整Backend首次DisplayName适配及后续lab证明前提失败已保留并修复，最终完整门禁仍运行中，尚不关闭DEP-001。测试侧两处helper复用受控delivery路径和原LAB身份，生产代码/Schema/POM/配置未变。当前事实和最终状态见Spec H实施报告，Linux/托管CI/部署未经实测。

## DEP-001本地验收完成（2026-10-06）

Spec H 20/20完成，前段进行中由本段更新。统一PowerShell入口、87项契约、安全摘要、最小CI定义及部署准备说明均交付；新Smoke1项、三个真实恢复检查、完整Backend本轮190份XML864项（843通过、21受控跳过，零失败/错误）及Frontend四阶段通过。Backend和最终Frontend共用1339文件来源摘要，证据/作者走读/失败修复见 [Spec H实施报告](../specs/008-local-delivery-gates/implementation-report.md)。临时资源正常退出；原dirty/未跟踪内容及分支/HEAD/空暂存保持。仅本地安全模拟范围，Linux、Node22、托管CI、浏览器与真实部署未在本轮运行；无提交、推送、合并、部署、真实基础设施/开发库或付费调用。

## 事件复盘与处置手册中心（2026-10-11）

用户批准先实施推荐两项，Spec J 增加 `/admin/incidents/:incidentId/review`：管理员从详情进入，只读采集既有分页事实、诊断历史引用、审批与回执，下载明确字段的本地 Markdown。显示开始/结束时间，复核 Incident/Action/Diagnosis 与新增 Timeline；发现变化、超限或失败禁止导出。跨请求采集不是事务快照，也没有报告签署/持久化。Action 既有接口没有服务端分页，报告前端限制不证明大事件查询成本有界。

`/admin/runbooks` 读取五类已校验本地模拟资料，提供搜索、版本/有效期、范围、章节和全文；历史引用携带 ID/版本/文档摘要精确匹配，缺失不回退，当前资料不能替代诊断冻结原文。新 GET 位于 rag 模块，不增加 incident→rag 依赖、Schema、MCP 写入或模型费用。实际检查及浏览器边界见 [Spec J 实施记录](../specs/010-incident-review-runbooks/implementation-report.md)。2026-10-12补齐复盘/手册桌面与窄屏截图、正文滚轮及浏览器取消禁导出；原Spec I完整视觉待办已按独立验收记录关闭。
