# 版本与交付记录

2026-10-19最新入口：[正式整合前最终审查](../../specs/013-release-candidate/integration-review-report.md)与[整合范围](v0.1.0-integration-plan.md)。538项逐文件待审及安全历史/现有验收来源已核对；仅审查准备，承载库/分支待决定，未提交或发布。下文各候选与历史运行保持原身份。

> 2026-10-19阶段19最新收尾：Windows中央`.cmd`路径修复，真实RED/GREEN 176项合同、npm及后补49项候选合同通过；新候选`c1687735`（1907文件）真实无GitAll `d98d8861`已PASS/0，同run五阶段退出0，201 XML、952项/931通过/21受控外部跳过/0失败错误，独立可靠性/评测审计通过。完整来源/1906旧输入及资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED及旧Backend PASS保持；T076失败终止、SC-019未满足，新成功由T081支撑，82/82仅任务关闭。详见[本轮报告](../../specs/013-release-candidate/cmd-dispatch-fix-report.md)。后补说明不回填冻结ZIP；异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段18All阻断：固定b667新解包、真实无GitAll `7d1c8f40` 为BLOCKED/2，PRECONDITION_FAILED，0测试阶段/0XML；Windows带引号裸npm.cmd定位自身模块失败，完整路径探测成功。未改根门禁或候选，完整All/前端/专项审计仍未通过；后续最小执行器修复与新候选复验待确认。原Backend及旧Frontend、旧FAIL保持身份，1871项保护和资源事实及最终收尾见[本轮报告](../../specs/013-release-candidate/gitless-all-report.md)。异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段16/17后端续跑：原ebf/`9624289c`真实FAIL及162XML保留。两类评测reporter的隐含Git依赖已修复，新增22项来源校验，普通Git/真实无Git聚焦分别27/27；新候选`b66759af-6cfb-4cb6-8db5-65276702f443`（1905文件）的真实无GitBackend `fdbac063` 已PASS/0：201新XML、952项/931通过/21受控外部跳过/0失败错误，14必需类零跳过。额度恢复后未重跑Backend，实际补做45规则、HTTP幂等、进程/数据库恢复、400样本及16项评测外置产物的独立审计并PASS。完整来源/1200旧输入、Git、既有16容器/94卷和说明以本轮综合proof/completion绑定；Spec M73/73限已定义本地任务。详见 [本轮报告](../../specs/013-release-candidate/candidate-backend-report.md)。前端204文件与ebf字节相同，未新跑Frontend/完整All、异机或发布；旧TBD、安全历史整合及正式Git授权仍开放，冻结候选不含后补最终说明。以下摘要保留原时点。

## IncidentPilot 本地交付候选

2026-10-19补跑最新结果：用户启动Docker后，同一ebf候选的真实无GitAll前提检查已CHECKED/0，T062关闭，阶段15为7/7、Spec M64/64。仍使用原1901文件解包树，宿主为Windows、可用的Linux Docker Engine为29.4.3；没有新的Backend/完整All/Frontend、异机或发布。532项待审、1908条源码/说明路径不变，未新增候选或源码改动；ebf早于后补说明，完整All/业务证据仍归070。以下受阻记录保留补跑前时点。

2026-10-19阶段15最新结果：新ebf候选1901文件的无Git来源及Frontend已验证，All前提检查因本机Docker受阻，T062保持开放；未发布。完整All/持久业务证据仍归属旧070输入，原070接手包仍包含旧门禁。下表各阶段记录保留各自身份，不能将新修复或后补说明回填旧候选。

| 记录 | 状态 | 说明 |
| --- | --- | --- |
| [无GitAll前提检查补跑](../../specs/013-release-candidate/gitless-delivery-report.md) | CHECKED / 0，T062关闭 | f1a08bdc，证据f4721f5c；同ebf1901既有解包根，schema2/head=null、八项来源身份一致、测试阶段0；阶段15为7/7、Spec M64/64，不新增完整All、Frontend、异机或发布结论 |
| [无Git源码候选门禁修复](../../specs/013-release-candidate/gitless-delivery-report.md) | Frontend PASS；All CheckOnly BLOCKED | 159门禁/49候选合同，新ebf候选1901文件、显式完整树及schema2来源，真实无GitFrontend预检与四阶段通过；Docker Linux管道不可用，阶段15为6/7、Spec M累计63/64；首轮253末检FAIL保留，无新Backend/完整All/异机结论 |
| [v0.1.0发布前准备](v0.1.0-release-preparation.md) | RELEASE_PREPARATION_ONLY / NOT_RELEASED | 版本说明草案、逐文件待审范围、证据矩阵及整合/本机回退草案；异机、无Git门禁、历史整合与正式Git发布未关闭 |
| [第二机器接手准备](../../specs/013-release-candidate/second-machine-handoff.md) | PREPARED_AWAITING_SECOND_MACHINE | 同070候选7成员接手包、操作/取证手册及本机文件级检查；用户暂无第二机器，无Git门禁限制明确，异机项均待执行 |
| [修正候选持久运行与重启](../../specs/013-release-candidate/persistent-candidate-runtime-report.md) | 新实例真实JAR主链/5卷留存通过 | 复用阶段11制品，空库到拒绝0/批准唯一1次、完整117静态HTTP及独立SQL留存；最终停止保留数据，未新增All/异机或正式发布结论 |
| [固定修正候选完整构建与All](../../specs/013-release-candidate/full-candidate-acceptance-report.md) | 新070候选构建/All通过 | 1895文件、两份0.1.0 JAR；199XML/930项、前端四阶段及本轮可靠性证明，旧中断保留；阶段11未执行持久业务，后续结果见阶段12，异机仍待执行 |
| [存储日志卷修复](../../specs/013-release-candidate/storage-volume-fix-report.md) | 新实例命名挂载/三轮依赖留存通过 | 新候选单独验证；旧候选和旧实例保持，旧卷历史归属及首轮静态异常仍TBD，不冒称新应用全链验收 |
| [冷态与浏览器后续复核](../../specs/013-release-candidate/browser-followup-report.md) | 指定链路通过，两个TBD保留 | 首次/热态静态各115项，真实页面只读复核；旧静态异常未复现，新增匿名卷1待核验，不称交付全绿 |
| [固定候选本机隔离复现](../../specs/013-release-candidate/reproduction-report.md) | HTTP主链/停启通过，辅助首轮FAIL根因TBD | 新源码/配置/数据库实例，缓存前提明确；浏览器见后续记录，第二机器/跨平台未验收 |
| [独立历史附件核验](../../specs/013-release-candidate/history-sanitization-report.md) | 衍生历史核验通过，新增实现未提交 | 原库保持，805映射/4merge及全新恢复扫描；1890源码待审副本精确对齐，不代表正式发布 |
| [v0.1.0 源码候选](./source-candidate-v0.1.0.md) | 源码候选，历史暂扣，未发布 | 统一构建版本、完整工作区快照及校验；完整历史安全移交仍未闭合 |
| [2026-10-12 本地交付候选](./local-candidate-2026-10-12.md) | `v0.1.0` 已确认，待发布 | 接手与启动、贡献归属、验收证据及平台/迁移边界 |

Maven与前端构建版本已统一`0.1.0`；候选日期不是Git Tag，不代表正式发布。原四文件源码候选不携带历史；经授权处理已识别历史疑似值后，另有经过核验的衍生历史附件，不能分发原始历史或整个本机工作目录。

## 上游 Ragent 历史发布记录

以下保留上游发布说明，其稳定性表述不作为 IncidentPilot 的生产验收结论。

| 版本 | 发布日期 | 说明 |
| --- | --- | --- |
| [1.0.0](./v1.0.0.md) | 2026-06-10 | 首个生产可用稳定版本 |
