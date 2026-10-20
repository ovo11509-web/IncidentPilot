# v0.1.0 源码候选

> 2026-10-19阶段20：[正式整合前审查](../../specs/013-release-candidate/integration-review-report.md)已将当前完整源码、538项待审、安全历史附件及c168/d98既有验收分列对应。本轮未生成新候选，c168仍1907文件且不回填后补说明；当前整合源码1908路径包含本轮新增报告。净化历史尚未含新增实现，承载仓库/分支仍TBD，未提交/发布。以下候选记录保留原时点。

> 2026-10-19阶段19最新收尾：Windows中央`.cmd`路径修复，真实RED/GREEN 176项合同、npm及后补49项候选合同通过；新候选`c1687735`（1907文件）真实无GitAll `d98d8861`已PASS/0，同run五阶段退出0，201 XML、952项/931通过/21受控外部跳过/0失败错误，独立可靠性/评测审计通过。完整来源/1906旧输入及资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED及旧Backend PASS保持；T076失败终止、SC-019未满足，新成功由T081支撑，82/82仅任务关闭。详见[本轮报告](../../specs/013-release-candidate/cmd-dispatch-fix-report.md)。后补说明不回填冻结ZIP；异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段18All阻断：固定b667新解包、真实无GitAll `7d1c8f40` 为BLOCKED/2，PRECONDITION_FAILED，0测试阶段/0XML；Windows带引号裸npm.cmd定位自身模块失败，完整路径探测成功。未改根门禁或候选，完整All/前端/专项审计仍未通过；后续最小执行器修复与新候选复验待确认。原Backend及旧Frontend、旧FAIL保持身份，1871项保护和资源事实及最终收尾见[本轮报告](../../specs/013-release-candidate/gitless-all-report.md)。异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段16/17后端续跑：原ebf/`9624289c`真实FAIL及162XML保留。两类评测reporter的隐含Git依赖已修复，新增22项来源校验，普通Git/真实无Git聚焦分别27/27；新候选`b66759af-6cfb-4cb6-8db5-65276702f443`（1905文件）的真实无GitBackend `fdbac063` 已PASS/0：201新XML、952项/931通过/21受控外部跳过/0失败错误，14必需类零跳过。额度恢复后未重跑Backend，实际补做45规则、HTTP幂等、进程/数据库恢复、400样本及16项评测外置产物的独立审计并PASS。完整来源/1200旧输入、Git、既有16容器/94卷和说明以本轮综合proof/completion绑定；Spec M73/73限已定义本地任务。详见 [本轮报告](../../specs/013-release-candidate/candidate-backend-report.md)。前端204文件与ebf字节相同，未新跑Frontend/完整All、异机或发布；旧TBD、安全历史整合及正式Git授权仍开放，冻结候选不含后补最终说明。以下摘要保留原时点。

> 2026-10-19补跑最新状态：用户启动Docker后，继续在固定ebf候选`ebf476ea-f17f-4a15-a8d4-365bcb8e1ad1`的原1901文件解包树运行真实无GitAll CheckOnly，`f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`为CHECKED/0、测试阶段0、schema2、`head=null`，八项来源身份与旧完成记录严格一致。Windows宿主上的Linux Docker Engine29.4.3已实际可用；T062关闭，阶段15为7/7、Spec M64/64。新证据根`bootstrap/target/gitless-preflight-followup/f4721f5c/`，见 [补跑记录](../../specs/013-release-candidate/gitless-delivery-report.md)。

> 本次只补前提检查，不是新候选、新干净复现或Linux宿主验收，未重跑Backend/完整All/Frontend或异机链路。没有源码改动，当前仍1908条源码/说明路径、532项待审；ebf仍早于后补报告与说明，070的完整All/业务证据不转属ebf，旧ac712 BLOCKED/2及a835完成记录继续保留。旧TBD、安全历史整合和正式发布权限不变。以下均为补跑前各阶段原记录，不回填冻结候选。

> 2026-10-19阶段15：新验证输入`ebf476ea-f17f-4a15-a8d4-365bcb8e1ad1`含1901文件，显式`-CandidatePath`复用四文件Verify并核验实际完整树，候选模式无需Git工具、`head=null`且safe摘要为schema2；默认Git模式保持schema1。159门禁/49候选合同及真实无GitFrontend预检CHECKED、四阶段PASS/0已有记录。All CheckOnly `ac712f69-fea2-4ea0-9401-f842b88ce386`为BLOCKED/2，本机Docker Linux管道不可用，T062仍开放，未取得新Backend/完整All或异机结论。见 [本轮记录](../../specs/013-release-candidate/gitless-delivery-report.md)。

> 新ebf候选冻结早于本轮最终报告和说明，仅代表实际验证的输入快照；不能把这些后补材料说成已在包内，也不继承070的完整All或持久业务结论。首253候选Frontend四阶段退出0但末尾完整树FAIL保留；只补`frontend/.output/incident/`精确生成根，不忽略整个`.output/`。070及原7成员接手ZIP保持原字节和旧门禁，完整All/真实JAR业务证据仍绑定070。旧531待审清单是阶段14时点，新增本轮唯一报告后为532项（53修改、479逐文件未跟踪）；这份清单与新候选manifest不是同一集合。以下段落保留原时点，安全历史整合、正式Git发布、异机及两个旧TBD未关闭。

> 2026-10-19阶段14：暂无第二机器时先完成 [发布前准备](v0.1.0-release-preparation.md) 和 [整合草案](v0.1.0-integration-plan.md)。当前工作区待审清单包含候选冻结后的说明，不能与070完整manifest或HEAD提交混为一谈；本轮不重新生成/回填候选，不修改无Git门禁，不新增All/异机或正式发布结论。

> 2026-10-18阶段13：用户暂无第二机器，已准备固定070的 [接手手册](../../specs/013-release-candidate/second-machine-handoff.md) 和7成员可携带ZIP，状态仅`PREPARED_AWAITING_SECOND_MACHINE`。文件级校验不代替异机运行，既有四文件候选及历史暂扣元数据不变。原All门禁在无Git解包根无法满足HEAD前提，手册明确其与本机演示Build/Verify的区别；后续适配另行处理。

> 2026-10-18阶段12：[新持久实例实际JAR主链与重启](../../specs/013-release-candidate/persistent-candidate-runtime-report.md) 已对同一070候选复用阶段11制品完成：空库起点、拒绝0/批准唯一1次、完整117静态HTTP及5命名卷留存通过，最终停止保留数据。本阶段没有重新构建/重跑All或修改冻结四文件；第二机器/承诺平台、正式Git发布及旧两项TBD仍未闭合。以下阶段记录保留原时点。

> 2026-10-18最新补充：[固定修正候选完整构建与All](../../specs/013-release-candidate/full-candidate-acceptance-report.md) 已对`070c4b76-70db-48c0-b7d0-b701a527f9e3`的1895文件重新解包构建并运行新All，后端930项/909通过/21受控跳过/0失败错误，前端四阶段通过。JAR与test-classpath集成分别说明；本轮未启动新持久业务演练，第二机器/跨平台未验。四文件及WITHHELD身份冻结，本轮说明不回填ZIP。

> 2026-10-17阶段10补充：[存储日志卷修复](../../specs/013-release-candidate/storage-volume-fix-report.md) 已形成新的修正源码候选；新实例5命名卷及三轮依赖留存通过。最终身份见该轮独立`final-candidate.json`，旧`01166dd3`四文件和1890源码不变；该阶段不继承旧完整业务链路或构建通过结论，后续阶段11另有实测。下文继续保留旧冻结时点。

> 2026-10-17后续：[固定候选本机隔离复现](../../specs/013-release-candidate/reproduction-report.md) 的HTTP主链/停启留存，以及[冷态/热态静态读取与指定浏览器只读链路](../../specs/013-release-candidate/browser-followup-report.md)已有实际结果。原首轮静态异常仍TBD；新发现匿名卷新增1，归属与日志挂载待核验，不称交付全绿。第二机器/跨平台未完成；原四文件及其WITHHELD状态不回填，下面保留源码冻结时点说明。

2026-10-17，首个正式版本号仍为`v0.1.0`，当前只是本地源码候选，未提交、Tag、推送或发布。根及八个模块Maven版本、前端package/lock均为`0.1.0`，上游坐标、依赖和许可证保持。

## 生成与校验

在仓库根目录执行：

```powershell
pwsh -NoProfile -File scripts/test-release-candidate.ps1
pwsh -NoProfile -File scripts/new-release-candidate.ps1 -Command Create
pwsh -NoProfile -File scripts/new-release-candidate.ps1 -Command Verify -CandidatePath bootstrap/target/release-candidates/<candidateId>
```

Create每次使用新的UUID目录，不覆盖旧候选。结果为`SOURCE_VERIFIED_HISTORY_WITHHELD`，候选仅有四文件：

| 文件 | 用途 |
| --- | --- |
| source.zip | 当前有效tracked修改与untracked实现的原始字节快照 |
| source-manifest.json | 每个源码成员的相对路径、字节数、SHA256 |
| candidate.json | 版本、候选ID、基线HEAD/分支、上游与历史暂扣状态 |
| SHA256SUMS | 上述三文件的摘要 |

Verify独立核对四文件、身份、清单、ZIP成员及内容，拒绝额外/缺失/重复/大小写冲突、越界路径和链接。它不要求原源码树或Git仓库；保留独立的pwsh/Git工具仅供后续恢复与开发。摘要检测意外变化，不是签名或来源真实性认证。

源码包括构建脚本、锁文件、Schema、规格、模板、NOTICE、LICENSE和资源；排除IDE设置、私有账号配置、target、依赖缓存、数据库数据和原始日志。生成期间源码变化会拒绝成功，不将历史HEAD误当成当前完整实现。

## 历史分发阻断

旧版上游配置的16个Git blob出现两种无法证明失效的疑似密钥。本轮未调用其值、未联网验证，也没有改写/删除历史。当前源码窄规则扫描无相同凭据规则命中，不代表全面秘密扫描认证。

因此候选不包含`.git`或`history.bundle`，元数据为`SOURCE_CANDIDATE_READY`及`WITHHELD_POTENTIAL_HISTORICAL_SECRETS`，`historyPreservedInOriginalRepository=true`。完整Git历史仍在原仓库，但尚未安全移交；不能把源码候选称为完整历史交付、已发布版本或全新验收通过。不要自行把原`.git`追加到候选中。

归属与配置解释见 [NOTICE](../../NOTICE) 和 [配置边界](configuration-boundary.md)。后续历史处理需单独确认方案和权限，不通过重写历史掩盖上游来源。

后续全refs只读盘点与可执行处置边界已整理为 [历史移交审查](../../specs/013-release-candidate/history-handoff-review.md)。目前只是方案，未净化或改写。固定候选`01166dd3-0a4c-4def-83dd-688e6c15e8bd`保持1890文件及原摘要；该后续审查附件不在旧快照内，不回填旧包或旧验证记录。

2026-10-17后续用户授权并已完成 [独立副本净化与核验](../../specs/013-release-candidate/history-sanitization-report.md)，补充上述原时点状态：提供独立`history.bundle`及映射，保留805提交/4merge和声明的作者/消息信息，SHA变化804、旧签名移除30。原源码候选四文件和WITHHELD元数据不改；历史附件另有身份和摘要，不包含未提交新增实现，不能仅clone历史包获取完整功能。完整1890源码待审副本已精确对齐，正式整合提交/Tag及独立业务复现仍未完成。

## 解包与后续复现

先Verify，再将source.zip解压至新的空目录，禁止覆盖已有项目或数据库目录。当前步骤仅验证该快照在本机现有缓存下离线构建后端；Maven Wrapper本身、依赖与镜像的缓存前提必须单列，源码包不携带缓存或私有settings。

实际命令与结果见 [Spec M实施记录](../../specs/013-release-candidate/implementation-report.md)。Build跳过业务测试不等于新的Backend/All通过；Spec L保留原runId/sourceDigest的历史归属。完整独立环境初始化、登录、审批、重启留存与跨平台复现仍属于下一步，不能以本机解包编译代替。

后续运行按 [本地演示说明](../development/local-demo.md)，新环境自行Init生成账号和凭据。常驻演示现有JAR、配置、数据均不由本轮候选自动升级；既有数据库迁移矩阵仍未确定。
