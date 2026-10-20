# Spec M实施记录

> 2026-10-19阶段19最新收尾：Windows中央`.cmd`路径修复，真实RED/GREEN 176项合同、npm及后补49项候选合同通过；新候选`c1687735`（1907文件）真实无GitAll `d98d8861`已PASS/0，同run五阶段退出0，201 XML、952项/931通过/21受控外部跳过/0失败错误，独立可靠性/评测审计通过。完整来源/1906旧输入及资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED及旧Backend PASS保持；T076失败终止、SC-019未满足，新成功由T081支撑，82/82仅任务关闭。详见[本轮报告](cmd-dispatch-fix-report.md)。后补说明不回填冻结ZIP；异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段18All阻断：固定b667新解包、真实无GitAll `7d1c8f40` 为BLOCKED/2，PRECONDITION_FAILED，0测试阶段/0XML；Windows带引号裸npm.cmd定位自身模块失败，完整路径探测成功。未改根门禁或候选，完整All/前端/专项审计仍未通过；后续最小执行器修复与新候选复验待确认。原Backend及旧Frontend、旧FAIL保持身份，1871项保护和资源事实及最终收尾见[本轮报告](gitless-all-report.md)。异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段16/17后端续跑：原ebf/`9624289c`真实FAIL及162XML保留。两类评测reporter的隐含Git依赖已修复，新增22项来源校验，普通Git/真实无Git聚焦分别27/27；新候选`b66759af-6cfb-4cb6-8db5-65276702f443`（1905文件）的真实无GitBackend `fdbac063` 已PASS/0：201新XML、952项/931通过/21受控外部跳过/0失败错误，14必需类零跳过。额度恢复后未重跑Backend，实际补做45规则、HTTP幂等、进程/数据库恢复、400样本及16项评测外置产物的独立审计并PASS。完整来源/1200旧输入、Git、既有16容器/94卷和说明以本轮综合proof/completion绑定；Spec M73/73限已定义本地任务。详见 [本轮报告](candidate-backend-report.md)。前端204文件与ebf字节相同，未新跑Frontend/完整All、异机或发布；旧TBD、安全历史整合及正式Git授权仍开放，冻结候选不含后补最终说明。以下摘要保留原时点。

> 2026-10-19阶段14：用户暂无第二机器，要求先执行后续动作，完成 [发布准备入口](../../docs/releases/v0.1.0-release-preparation.md)、[整合草案](../../docs/releases/v0.1.0-integration-plan.md) 与 [本轮核对记录](release-preparation-report.md)。初始53 tracked修改+475逐文件untracked=528，新增3说明后531项完整分5组，9POM/前端版本和原候选非说明差异0已核对；文档稳定后以最终清单/完成摘要绑定内容。状态仅`RELEASE_PREPARATION_ONLY_NOT_RELEASED`。不改业务/门禁、不重跑构建/All/服务，不提交发布；异机、无Git来源前提、历史安全整合与旧TBD保留，冻结输入和先前证据不回填。

> 2026-10-18阶段13：用户确认暂无第二机器，已整理 [接手执行手册](second-machine-handoff.md) 及`bootstrap/target/second-machine-handoff/f9318643/IncidentPilot-v0.1.0-handoff-f9318643.zip`，24,166,854字节、7成员、SHA256 `89162815A8CC06EB8D0CA71DA12DDCD2EEF01A33543D21F6DABD92E9A1FE7BB5`。原四文件候选字节保持，外层新解包比对通过；9段示例语法/公开固定HMAC校验通过、HTTP0。状态仅`PREPARED_AWAITING_SECOND_MACHINE`，不继承旧All或业务结果作为异机通过；本机无父Git预检及保护收尾见本轮证据，不启动服务/构建/测试。首轮中文路径基线拒用、辅助超长命令行失败及后续修正分别保留。

阶段13实际负向预检：同一固定source.zip新提取1895文件、候选原Verify退出0、前后源码哈希及精确集合保持。仅子进程设置Git发现上限后，discovery退出128，原样All CheckOnly run `bce6744d-5ef7-46fb-a31d-ec8c0d376f6e` 实际退出1、`FAIL/GATE_FAILED`，head/sourceDigest为空、工具版本/阶段均0，仅有run/summary。结果是`NEGATIVE_PREFLIGHT_VERIFIED`，不能写成CHECKED或All/异机PASS。首轮辅助器将两个Git Application路径拼成一个可执行文件名，Win32 2/进程未启动，失败和诊断保留；独立continuation显式选择有效应用，只补未完成Git/CheckOnly及最终完整性，不重复提取/Verify。完成摘要和13份文档哈希由本轮`final-check-proof.json`、`completion.json`绑定；未改冻结脚本或伪造Git历史。

> 2026-10-18阶段12：[固定修正候选持久运行与重启验收](persistent-candidate-runtime-report.md) 已复用阶段11制品，从新空库/私有配置启动真实JAR，完成2告警→1事件、诊断引用、拒绝0/批准1次及重启留存；完整117份静态HTTP通过。最终Stop保留5卷，原库/候选/630旧保护文件/16旧容器及89旧卷核对通过，全局94卷且额外0；本阶段未重跑构建/All、新浏览器或异机验收，旧两项TBD保留。以下记录保留各自时点。

> 2026-10-18阶段11：[固定修正候选完整构建与All](full-candidate-acceptance-report.md) 记录新070候选的实际结果：两份0.1.0 JAR、新All及可靠性证明复核通过。中断和辅助脚本失败保留，完整来源/制品/资源收尾以该报告为准；本轮不启动新持久业务演练，不回填旧冻结包。

> 2026-10-17阶段10历史时点：[存储日志卷修复](storage-volume-fix-report.md) 已确认匿名`/logs`机制并显式映射第五命名卷；30合同/代理2项、49候选合同和三轮新实例依赖留存通过。该阶段不证明新Backend/All或完整应用构建，后续阶段11单独记录；旧匿名卷历史具体归属及首轮静态异常TBD不回填。

> 2026-10-17阶段9历史时点：[冷态取证与真实浏览器复核](browser-followup-report.md) 完成首次/热态各115项静态读取、指定真实页面只读链路和业务留存；当时T033尚开放，后续机制核验见阶段10，旧卷历史具体归属仍TBD。阶段9四项完成不代表全部交付问题关闭；原冻结候选不回填。

> 后续复现：[固定候选本机隔离HTTP主链及停启留存](reproduction-report.md) 已有新运行结果，缓存前提明确；首轮辅助静态采集失败原因仍TBD，不将本轮写成全部验收无失败或第二机器/跨平台通过。

> 后续补充：用户另行批准的隔离历史处理及6项追加任务已完成，实际结果见 [历史净化记录](history-sanitization-report.md)。原15项源码阶段记录和冻结候选不改写；历史附件独立核验，新增实现正式提交/Tag、独立业务复现及发布仍未完成。

2026-10-17。用户批准继续既定第1步：整理v0.1.0交付候选。保持`feature/incident-platform`和基线HEAD`67624931f20812922568d3641a095adb291263ba`，不提交、Tag、推送或部署，不操作现有演示实例。

## 已实现及验证

- 根及八模块POM项目/parent版本统一`0.1.0`，前端package/lock原已为0.1.0。依赖、坐标、LICENSE和原有dirty内容保留；新增NOTICE与配置边界说明。
- local-demo按根版本和模块artifactId精确选JAR。聚焦合同新增7项，最终`LOCAL_DEMO_CONTRACT_PASS 26`、Node代理2/2通过、0跳过；覆盖旧包并存、当前包缺失、非法版本及父版本不一致。
- 候选工具收录有效tracked/untracked源码，使用NUL/UTF8路径，包含中文文件名；拒绝私有/生成路径、链接及危险内容，复制后复核源文件和精确路径集合。Verify只读四文件候选，拒绝遍历/碰撞/篡改/缺失/额外成员及历史附件。最终`CANDIDATE_CONTRACT_PASS 49`、退出0，包括真实POM/lock解析；未跳过负向安全检查。
- 当前白名单覆盖既有H门禁1363个受检源文件，遗漏0。实际候选文件数随本轮文档稳定后固定；不能用Git status目录条目数充当源码文件数。
- 当前树离线Maven validate九模块通过；离线package九模块通过、退出0，约3分47秒。bootstrap/mcp-server准确0.1.0 JAR已生成，业务测试明确跳过。命令为`./mvnw.cmd -o -Dmaven.repo.local=<本机既有缓存> -Dspotless.apply.skip=true -Dmaven.test.skip=true -pl bootstrap,mcp-server -am package`。

## 真实问题与修正

默认沙箱用户缓存为空，首轮离线validate缺BOM，保留失败日志；随后显式复用原用户已有Maven缓存成功，不下载依赖或复制私有settings。CIM进程归属合同首次因沙箱访问受阻，同一自有fixture在允许读取进程元数据条件下完整通过；未改弱化断言。

候选交叉审查补齐：加密私钥头、credentials前缀/运行配置/证书排除；复制期间同一未跟踪目录新增文件必须重新枚举清单，不仅看porcelain目录状态。package-lock包含空字符串包键，版本解析改用Hashtable并加入真实POM/锁文件合同。未输出任何秘密值。

## 历史分发阻断

只读扫描HEAD可达17870对象、5449 blobs、67000659字节。README的一种重复字符占位另列；旧application.yaml有16个blob、43次匹配，涉及两种非占位疑似密钥。未联网调用，无法判断有效性。历史Claude配置另有上游用户路径。窄规则检查不是全面秘密认证。

因此不生成或分发history.bundle，不改写或删除原历史。四文件源码候选使用`SOURCE_CANDIDATE_READY`、`history=WITHHELD_POTENTIAL_HISTORICAL_SECRETS`、`historyPreservedInOriginalRepository=true`，保存基线HEAD/分支和上游归属；Verify为`SOURCE_VERIFIED_HISTORY_WITHHELD`。这只关闭源码快照部分，完整Git历史移交与原第1步整体交付仍未闭合，需后续独立安全处理。

## 候选冻结与解包构建

预演候选`e5f67a88-3d7a-45d5-a28c-cf82d8ed68bf`已真实生成并Verify，共1890文件，manifest摘要`4B005FF92A285ED5D7DDF2B62E5C437F879FB5354971EB707193BE903B5BEF10`。解包到新空目录后1890文件逐项哈希一致、0遗漏/变化；直接调用解包脚本Verify成功，不依赖原工作树。

预演解包源码离线package于09:17:41 +08:00结束：九模块BUILD SUCCESS、退出0，约2分40秒，生成两准确版本JAR；不借用原目录target，明确复用本机依赖缓存。预演后补充的修改仅候选工具校验/合同及说明，业务源码、POM及前端实现未再改变。

说明与任务核对完成后冻结新的最终四文件候选，独立Verify/解包构建及安全白名单结果由`bootstrap/target/release-candidates/<candidateId>-verification.json`记录。最终候选ID及摘要以其candidate.json、source-manifest.json、SHA256SUMS和该记录为准，避免把最终身份循环写入被哈希源码；原始日志继续只保留本机target。

核对准确版本的同时记录各次JAR摘要，不宣称不同构建的二进制字节相同；构建时间等元数据并未冻结，本轮不是位级可复现构建验收。

## 收尾核对

说明文档60个相对链接实际核对无缺失；最终再核对Git分支/HEAD/暂存区和候选完整性。源码阶段按修订后的15项任务收尾，不把历史分发阻断关闭。已有53个tracked修改、72个untracked条目包含旧成果与本轮新增；不据条目数声称文件无变化，实际以manifest/source hashes核对。没有覆盖/清理既有修改、无提交或推送，现有演示未启动、停止或升级。

## 结论边界

Spec L的930项/909通过/21受控跳过保留历史runId/sourceDigest；本轮修改了版本及交付脚本，未声称新的Backend/All。解包构建也不代表第2步独立干净环境登录、告警、诊断、审批和重启留存已通过。Linux/Node22/Java17、托管CI、真实模型/Broker、正式Git发布及生产部署仍未验证或未授权。

## 2026-10-19阶段15：无Git门禁修复

当前脚本增加显式候选来源、完整树起止核验及schema2安全摘要，默认Git保持schema1并拒绝借父Git。159门禁合同/49候选合同通过；新验证候选ebf476ea的真实无GitFrontend CheckOnly与四阶段PASS，All CheckOnly因本机Docker引擎不可用BLOCKED/2，T062继续开放。首253候选四阶段0但结束FAIL的输出目录遗漏已定向修复且原记录保留。旧070及其完整All/持久业务结论不改属本轮，异机与正式发布未完成；身份、失败、命令及收尾见 [本轮报告](gitless-delivery-report.md)。

2026-10-19后续：用户启动Docker后，同一ebf候选和原解包树的新无GitAll CheckOnly `f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`为CHECKED/0、0阶段，完整来源保持，T062关闭，阶段15为7/7、Spec M累计64/64。上段及旧a835完成证明保留原时点，不回填旧BLOCKED；本轮只补预检和文档，不重跑Frontend/Backend/完整All或新建候选，最新记录见 [本轮报告](gitless-delivery-report.md)。
