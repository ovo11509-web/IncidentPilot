# 实施计划：v0.1.0 本地交付候选

**分支**：保持 `feature/incident-platform` | **日期**：2026-10-17 | **规格**：[spec.md](spec.md)

**输入**：用户批准第1步，见 `specs/013-release-candidate/spec.md`。

**冷态与浏览器补充**：用户在本机隔离主链通过后要求继续下一步。对保留的candidate-proof做一次新进程首次静态读取及一次同序热读，保持原超时/范围/哈希规则并按调用阶段安全记录异常；不清系统缓存，首轮TBD不因后续PASS关闭。浏览器通过界面只读复核既有事件/诊断/审批/回执/复盘/Runbook和退出保护，不新增业务操作。认证凭据由用户本机输入；等待时继续匿名/静态工作，所有证据写新目录，结束停止并保留卷，不改产品/原候选或扩大平台承诺。

**固定候选复现补充**：历史附件核验后，用户要求继续下一步。以冻结1890文件候选在`bootstrap/target/candidate-reproduction/<runId>/source/`新解包，新实例初始化、顺序构建、真实HTTP黄金路径及Stop/Start保留；使用候选原脚本和新Compose project/卷/随机私有密码。Maven/npm缓存及本机镜像是显式前提，不称第二机器或全空白环境。仅本轮实例可写，结束停止并保留卷；源码/原库/原四文件候选保持，证据只输出白名单事实，完整业务/平台结论按实际结果分别报告。

**后续授权补充**：用户了解历史处置目的后回复“ok”，批准 [历史移交方案](history-handoff-review.md) 中的独立副本净化和隔离工具取得。仅向新的`bootstrap/target/history-sanitization/<runId>/`取得固定官方工具；替换2种历史疑似值、删除历史Claude私有配置，保留并核验805提交/4个merge及来源映射。原源码候选四文件不变；本阶段不创建包含新增实现的提交/Tag，不推送或部署。原计划的源码候选约束继续生效，本附件中的许可只覆盖隔离衍生历史处理。

## 摘要

统一9POM到0.1.0，local-demo按根POM和模块artifactId精确选择JAR；增加Create/Verify文件交付管线，固定source.zip、source-manifest.json、candidate.json、SHA256SUMS四文件。历史扫描发现潜在凭据，默认禁止history.bundle；原历史保留且元数据明确暂扣。输入覆盖当前未提交实现，但拒绝私有配置、运行数据、链接和危险路径。实际执行安全契约、当前版本package和候选解包离线package。本轮不改业务逻辑、Schema或界面，不声称新Backend/All、完整历史交付或独立环境业务链路通过。

## 技术背景

**语言/版本**：PowerShell7、Git、Maven Wrapper、Java21；现有Node20前端元数据。

**主要依赖**：复用.NET压缩/哈希与已有工具，不新增依赖或服务。

**存储**：忽略目录 `bootstrap/target/release-candidates/<UUID>/`，新目录不覆盖。

**测试**：PowerShell正反例契约、离线validate/package、ZIP及manifest全部成员核验。

**目标平台**：本轮Windows/Java21已缓存依赖；Linux/Java17/Node22/远程CI未验证。

**项目类型**：多模块Java后端和React前端的本地交付工具。

**性能目标**：无吞吐指标，完整性不得采样核对。

**约束**：不切分支，不提交/Tag/推送/部署，不操作常驻演示，不联网或调用付费服务；target/dist/node_modules/.git/私有配置/volumes/rawlogs不进源码包。

**规模/范围**：9POM、既有local-demo脚本/契约、新增候选脚本/契约及必要中文文档。

## 宪章检查

| 原则 | 研究前 | 设计后依据 |
| --- | --- | --- |
| 署名可追溯 | 通过 | 保留LICENSE/上游声明和原仓库历史，候选保存HEAD来源并暂扣潜在含密历史 |
| 持久化正确性 | 通过 | 不改业务/Schema，不操作现有数据库 |
| 人工控制副作用 | 通过 | 无服务/MCP/远程写，Git只读 |
| Spec控制范围 | 通过 | 仅用户批准第1步，完整业务复现与平台验证为后续 |
| 有证据后结论 | 通过 | 正反例及真实构建记录，不套用历史PASS |

## 项目结构

### 当前功能的文档

`specs/013-release-candidate/` 内为spec、plan、research、data-model、contracts/candidate、quickstart、tasks、checklists/requirements、analysis及实施后implementation-report。

### 源代码（仓库根目录）

- `pom.xml` 与 `{framework,infra-ai,system,rag,agent,incident,bootstrap,mcp-server}/pom.xml`。
- `scripts/local-demo.ps1`、`scripts/test-local-demo.ps1`。
- `scripts/new-release-candidate.ps1`、`scripts/test-release-candidate.ps1`。
- `frontend/package.json`、`frontend/package-lock.json` 维持已有0.1.0。
- `docs/releases/`、`docs/development/` 交付和测试说明。

**结构决策**：启动仍由local-demo拥有；候选管线只读源码和生成文件，不建部署平台。历史验收摘要仅显式白名单。候选为 `SOURCE_CANDIDATE_READY`，history为 `WITHHELD_POTENTIAL_HISTORICAL_SECRETS`；Verify成功为 `SOURCE_VERIFIED_HISTORY_WITHHELD`，并拒绝额外bundle。完整历史交付需单独安全方案，不能以源码构建通过关闭该交付边界。

## 实施阶段

1. 固定现场和本Spec；并行完成版本/JAR定位与候选管线。
2. 运行负向契约、离线validate及当前树package；更新中文说明。
3. 源码/文档稳定后新建候选，Verify，再在独立新目录解包离线package；记录本机缓存复用边界。
4. 核对摘要、许可、历史归属及结果，关闭任务；不提交不发布。

## 复杂度跟踪

无宪章违规，不引入依赖、平台或远程服务。

## 2026-10-17续跑：存储日志卷修复

用户继续已列明的T033。受控新容器Mounts证实固定RustFS镜像在原模板下给`/logs`分配匿名卷；该机制与旧匿名卷的历史具体归属分别记录，不能倒推旧归属。此修复采用现有命名卷模式：保留`storage-data:/data`，新增带同一instance标签的`storage-logs:/logs`，新实例共5个命名卷；现有归属检查和Stop保留语义直接复用，不新增bind目录或自动迁移逻辑。

顺序为受控机制取证→最小模板/聚焦合同→新Name初始化→三轮Compose依赖启停与Mounts/全局卷集合/日志标记留存→新候选Create/Verify及解包核对→证据与原资源保护收尾。旧候选、旧1890源码及旧私有实例完全不改，所有旧卷及本轮取证卷保留。只验证依赖生命周期，不借用旧JAR或将其写成新完整JVM链路、新Backend/All、第二机器或生产验收。日志卷数稳定不代表日志容量有界。

## 2026-10-18续跑：固定修正候选完整构建与All

用户批准推荐第1项。输入固定`070c4b76-70db-48c0-b7d0-b701a527f9e3`的1895成员，在全新`bootstrap/target/full-check/390adb80/s/`解包；不共享旧target、dist、node_modules、reports或proof。保留原库、两份旧候选和已有私有实例/容器/卷基线。

先运行候选门禁合同和All CheckOnly，再直接执行local-demo内部的离线Maven package命令，生成真实0.1.0可执行JAR；业务测试跳过仅属于打包步骤。然后原样调用候选`check-delivery.ps1 -Gate All`，其Frontend阶段负责首次npm安装、确定性检查、lint及production dist构建，避免再单独重复前端构建。All的Maven原命令没有强制离线，npm使用离线缓存，报告分别声明；不改冻结脚本或借环境参数绕过安全断言。

源目录位于原库ignored子目录且无.git，因此All记录的HEAD是外层来源基线，不代表新增实现已提交。All sourceDigest仅覆盖其受检子集，另以完整1895成员前后摘要绑定候选、构建、All runId、新XML、14必需目标及Spec L新运行证明。阶段失败/受控跳过/资源差异分别保留，不拼接旧报告或全局清理。此轮不另起持久演示、不做浏览器、第二机器或生产验收，不授权Git发布。

## 2026-10-18续跑：修正候选新持久实例

用户在阶段11完成后批准继续已推荐的持久业务演练。阶段12沿用原方案，不改业务/Schema/前端，也不重跑已通过的package/All。输入为070候选1895源码及阶段11新All `353b453f-56c7-451e-a54a-67057342a19c`绑定的2 JAR、117 dist；目录摘要先核对，再由冻结local-demo的`Publish-DemoArtifacts`发布到全新`candidate-run-cea55ab3`。库调用自行持有同Name排他锁，校验owner/空目标/无运行进程；明确记录“复用已验证构建”，不声称新CLI Build。

本轮证据位于`bootstrap/target/persistent-candidate/cea55ab3/`。先保存原1902文件/Git、16容器/89卷、630份私有配置及既有证据基线；新Init只允许新增4个自有依赖与5命名卷。以候选正常Start/Verify/Stop入口和真实web代理完成一次业务主链；所有写操作先checkpoint，不盲重试不确定请求。原有HTTP/引用核验器仅适配新身份和目录，不放宽超时或哈希规则。

Stop/Start比较5卷及全局卷集合、RustFS的`/data`/`/logs`实际Mounts、三进程新身份、账户/事件/诊断/审批/执行/回执/Timeline/Outbox稳定事实、凭据及完整制品。最后Stop仅本轮实例且保留5卷，核对全局集合为旧集合加本轮5卷，没有额外匿名卷。公开说明只记去值事实；不操作旧实例，不将本轮成功关闭旧静态异常根因/匿名卷历史归属TBD，不自动新增浏览器、第二机器、Linux或生产结论。

## 2026-10-18续跑：接手材料与文件级演练

用户暂无第二机器，范围收敛为准备。固定070四文件复制到新的`bootstrap/target/second-machine-handoff/f9318643/kit/candidate/`，不重新生成源码候选。中文指南从本目录`second-machine-handoff.md`复制为kit根README，附白名单provenance和逐文件SHA256SUMS，再导出独立ZIP及外层摘要。本阶段不携带历史附件或任何本机私有/生成配置，接手者自行Init。

从ZIP在本轮新目录解包，逐项比较包内集合和完整1895源码，运行候选原Verify；手册中的PowerShell代码只做语法/固定输入检查，不发送告警或审批。用仅限子进程的Git发现上限模拟没有父仓库的输入，原CheckOnly若因HEAD前置条件失败则保留真实失败，确认无工具预检/测试阶段；不改冻结门禁、git init或提交。本机文件演练不能升级为异机通过。

最后对原1903文件、原候选与上一轮完整证据46项、Git身份做前后核对；仅许可Spec/交付上下文及一份新指南变更。本轮状态`PREPARED_AWAITING_SECOND_MACHINE`，不创建或启动容器、卷、Java/Node服务，不重复已通过构建/All。新增通用验收器或修正无Git门禁需后续明确范围，本阶段只记录真实接手限制。

## 2026-10-19续跑：发布前整理

用户要求在暂无第二机器时继续后续动作，执行阶段14材料准备。证据根为`bootstrap/target/release-preparation/9d956285/`，先冻结原1904文件、Git及77项旧候选/先前证据输入。逐文件读取NUL/UTF-8 Git状态，区分53项tracked修改和475项初始untracked，构建精确路径/状态/长度/内容SHA及互斥审阅分组，纳入本轮新增说明后再固定最终清单。直接复用原候选库的路径和敏感模式校验，不输出命中值、不扩大为历史秘密认证。

编写`docs/releases/v0.1.0-release-preparation.md`、`v0.1.0-integration-plan.md`及本目录`release-preparation-report.md`，分别提供草案版本说明/证据、审阅与整合/发布回退操作草案、本轮真实检查结果。共享POM/Schema/装配和跨Spec依赖作为原子边界说明；建议先审阅分组，获授权后再选择实际提交粒度，不预先承诺每组独立编译。

本轮不重新生成070或接手包，不重跑构建、All或业务/浏览器，不修无Git门禁，不调用旧实例或Docker。核对链接、版本、许可证、逐文件边界、原Git/77输入及候选非说明源码差异后保存完成摘要；异机/无Git门禁/安全历史整合及旧TBD继续公开，不执行Git写入或部署。

## 2026-10-19续跑：显式候选来源的无Git门禁

本阶段落实已推荐且由用户批准的门禁修复，历史阶段约束按各自时点保留。只改当前交付门禁、必要合同和中文说明，不改业务、Schema、前端行为或既有候选；保持当前工作树，不新增依赖，不提交、Tag、推送、合并或部署。证据根为`bootstrap/target/gitless-delivery/a83506bb/`，开始固定原1907路径、Git和80项旧证据输入。

### 来源选择与完整源码边界

`scripts/check-delivery.ps1`增加可选`-CandidatePath`。未提供时走原Git分支，保留两参数内部调用的默认兼容、Git工具要求、固定门禁命令及`schemaVersion=1`摘要。显式提供时复用`new-release-candidate.ps1 -Library`的`Test-ReleaseCandidate`，读取候选元数据及manifest后逐文件比较门禁所在源码根；库加载的参数绑定不得覆盖门禁参数。此分支不调用Git，也不将Git列为必要工具，不提供任意命令或可变输出根。

实际完整树只剪枝`framework/target/`、`infra-ai/target/`、`system/target/`、`incident/target/`、`agent/target/`、`rag/target/`、`mcp-server/target/`、`bootstrap/target/`、`frontend/node_modules/`、`frontend/dist/`、`frontend/.output/incident/`和根`.git`。只对这些已知生成根停止递归，避免把正常依赖链接当源码；`.output`仅对既有测试配置声明的`incident/`精确输出剪枝，不能扩展为整个`.output/`。其他条目一律参与比较，尤其不能按打包白名单忽略`.env.local`、未知根文件或源码内部同名生成目录。源码根、被核验文件及祖先均检查链接/重解析点；规范相对路径、大小写一致、无碰撞，文件集合、字节长度和SHA256必须完全对应manifest。

开始保存四文件SHA256和来源身份，同时继续生成原门禁`source.json/sourceDigest`以兼容测试证明；完整清单身份与该受检子集摘要分开。工具探测和所选阶段结束后重新校验完整候选、四文件原摘要与源码集合/内容，`CheckOnly`也必须走结束核验；通过结束Verify但与最初候选不同仍失败。来源不合格须在工具探测和构建前失败，不能通过父Git回退或虚填HEAD继续。

### 安全摘要与失败语义

候选模式`run.head=null`，安全摘要使用`schemaVersion=2`，只构造固定`sourceCandidate`字段：`kind=SOURCE_CANDIDATE`、`candidateId`、`manifestSha256`、`metadataSha256`、`zipSha256`、`checksumsSha256`、`baselineHead`、`fileCount`。UUID、SHA256、历史基线和正整数计数逐项校验；`baselineHead`仅指候选历史基线。默认Git模式不加候选字段，保持原schema1。

沿用固定状态/原因码、工具预检受阻退出2、其他失败退出1、真实成功退出0及finally恢复环境。白名单不得复制本机路径、命令、异常、原始配置或自由文本；安全摘要构造失败不留下上传目标、无raw回退。CI原调用与只上传`safe/summary.json`的约束不变，本阶段不宣称托管CI已运行。

### 测试与实施顺序

先补正反合同，再实现最小共享边界，运行当前门禁合同及候选Verify合同。覆盖真实有效候选与完整解包树、坏候选、缺失/新增/隐藏配置/大小写差异/链接/内容变化、候选四文件与源码的结束漂移、Git工具移除、来源白名单和默认调用兼容；不得用全部替身绕过候选校验。新合同必须对旧缺陷有实际拒绝或失败证明。

源码和前置说明稳定后生成新的四文件候选，Verify并在新的空目录完整解包。仅对子进程移除Git工具可发现路径、清除Git环境并限制父Git发现，独立记录发现失败和工具不可用；从该源码运行`-CandidatePath`模式的真实All `CheckOnly`及Frontend四阶段。保留本机Windows/Java21/Node20与Maven/npm缓存前提；CheckOnly中的Wrapper/Java/Docker信息探测不代表Backend测试或容器业务通过。Frontend安装、页面检查、lint、构建的命令与退出分别记录，源码/候选结束核验和safe来源独立复核。

最后编写`gitless-delivery-report.md`，同步项目/交付入口，核对Git、旧候选、接手ZIP、旧证据与已有私有/运行资源的保护结果，再依据真实证据关闭T058～T064。新候选固定后不回填自身ID或测试结果进包，报告中的候选与后续说明变化分别标注。此轮不新跑Backend/完整All，不启动持久实例；第二机器、Linux/Node22/Java17、旧两项TBD、安全历史整合与正式发布继续待执行。

### 本阶段宪章复核

保留上游署名、原Git历史和许可证，不触及持久化业务边界或MCP权限；范围仅FR-020～024，正反合同与真实无Git运行分别作为结论依据。无新增宪章违规或待定设计，不以本机成功扩大平台、业务或发布声明。

### 首轮实际运行后的精确输出修正

首候选`25318b5b-0868-44d2-b786-16065f820158`保持冻结。Frontend `837f10df-1c1b-4ca4-b0ac-0334ee44db31`的Install、IncidentView、Lint、Build均退出0，但最终完整树核对拒绝新生成的`.output/incident`，门禁退出1/`FAIL/GATE_FAILED`，不能称Frontend门禁通过。源码中的`frontend/tests/incident-view.check.mjs`调用`tests/tsconfig.incident.json`，后者已明确`outDir=../.output/incident`；据此只将`frontend/.output/incident/`加入精确生成根。为它补充合法输出和`.output`其他成员仍拒绝的合同，重跑适用合同后生成新ID候选并新解包重验，不修改首候选或回填首轮结果。

首候选All `CheckOnly`运行`df9174b9-dfea-48d5-b12d-bb14afe03fa4`已越过无Git来源边界，但Docker前提未满足，实际退出2/`BLOCKED/PRECONDITION_FAILED`。T062保留未完成；新候选完整树和Frontend最终门禁尚待重新核验，T063也保留未完成。不得在规格或收尾材料中预写新候选通过或把阶段退出0替代门禁最终状态。

## 阶段16：固定ebf候选真实Backend与独立复核

2026-10-19用户批准在已完成的同源前提检查之后执行真实Backend。输入固定为`ebf476ea-f17f-4a15-a8d4-365bcb8e1ad1`四文件候选及`bootstrap/target/gitless-delivery/a83506bb/attempt2/s/`；阶段15的禁Backend范围保留为历史边界，本阶段以FR-025/026单独扩展验证，不修改候选或实现。本轮证据根`bootstrap/target/candidate-backend/67942981/`，规格/计划/任务只追加此阶段。

### 开始边界与实际执行

先固定原1908条源码/说明路径、Git分支/HEAD/index/refs/worktrees和840项旧证据/私有文件，以及Docker容器/卷集合；逐项Verify候选四文件与完整1901源码。读取用户已启动的本机Linux Docker Engine，记录Windows宿主、实际Java/Maven/Docker工具版本及现有缓存前提；容器引擎为Linux不等于验证Linux宿主。基线保存在`baseline.json`、`source-before.json`和`protected-before.json`，准备记录不预写Backend结论。

从原解包树调用原`check-delivery.ps1 -CandidatePath <固定候选目录> -Gate Backend`，不带`CheckOnly`。仅对子进程清理Git环境、限制父Git发现并移除Git工具可发现路径，保留实际发现失败/工具不可用的证据；继承既有门禁安全环境、固定Maven参数、独立runId/sourceDigest和输出目录。允许既有Testcontainers/跨进程测试创建隔离本机临时资源，不调用旧持久演示生命周期、不连接开发或远程业务库、不启用付费模型/外部联调。前提或执行失败保留首轮记录，先核对实际结果，再决定是否需要单独修复范围，不能覆盖冻结候选或盲目重跑。

### 新报告与证明的独立验收

实际退出后独立读取该run的`run.json`、`stages.json`、`reports/`、`proof/`、`source.json`、`safe/summary.json`及`artifacts.json`，核对唯一Backend阶段和真实执行命令。新XML禁止DTD，核对时间与`lab.runId`、重复/空报告、用例与套件计数、候选原必需目标和受控跳过目录；将通过、失败、错误、跳过和实际目标数量分列，不把历史总数当预期。

逐份复核本轮proof中的runId、scenarioId、sourceDigest、结果状态与实际样本分母/断言事实，交叉核对相关测试报告，区分真实PostgreSQL/HTTP/进程恢复与固定输入、模拟模型或测试classpath边界。受检子集摘要与完整1901清单身份分别验证；四文件与实际源码结束一致、schema2/head=null及固定八字段来源、safe白名单和制品逐文件摘要均须独立核对。任一验收缺口如实记录，不以门禁退出0单独代替全部专项结论。

### 收尾与宪章复核

在本目录新增`candidate-backend-report.md`，同步项目上下文/TODO、测试与交付/发布入口；说明ebf新Backend与此前Frontend/All CheckOnly、070历史All的不同身份，数量仅取本轮真实报告。本轮原库只允许必要中文说明变化，保护冻结候选/源码、旧证据和私有/运行资源及Git身份，不执行提交、Tag、推送、合并或部署。检查测试自有临时进程/容器状态及全局资源集合，使用既有测试的自有清理语义，不全局清理旧容器、卷或数据。

本阶段为既有测试执行及证据核对，不新增架构、依赖、业务接口或Schema；上游署名/许可证、人工审批与持久化边界保持。第二机器、Linux宿主/Node22/Java17、托管CI、真实模型/生产、旧两项TBD、安全历史整合和正式发布继续独立待验证或授权；不重跑Frontend或完整All，不把本轮测试结果转写成生产可靠率。

### 阶段16实际失败（原边界保留）

真实Backend `9624289c-394e-475e-ac75-143ea0a08fd0`已退出1/`FAIL/GATE_FAILED`，162份XML为部分报告；`RunbookOfflineEvaluationTest`两个参数例在写报告时因`RunbookEvaluationReport.git`无法启动Git进程而报错，后续agent/bootstrap未执行。旧840保护文件、固定ebf及原源码起止一致，既有16容器/94卷保持。T066完成的是执行，T067完成的是失败归因及部分报告核对，不能标记完整Backend或Spec L专项验收通过；T068的文档/最终收尾仍待完成。

本阶段原“不修改代码、不生成候选”约束不回写。以下阶段17在已授权的无Git验收范围内单独处理必要兼容缺陷，新的代码和运行不能覆盖原候选、失败或既有解包树。

## 阶段17：两类评测报告来源兼容与新候选Backend

证据根`bootstrap/target/gitless-backend-fix/aa898811/`，runId `aa898811-88aa-4639-b24e-365bf1994b07`。先固定原1909源码/说明路径、Git及1200项旧保护输入，包括840旧文件、6794控制记录和9624全部失败制品。已知兼容修复仅涉及`rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookEvaluationReport.java`、`agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvaluationReport.java`及各自新增的JUnit测试类；不改门禁、产品源码、依赖、Schema、数据集、检索/诊断阈值或skip目录。

### 来源分支与测试范围

沿用两个模块各自现有模式，不引入共享生产抽象。`delivery.gates=true`时校验`lab.runId`为规范UUID、`lab.scenarioId`严格为`DELIVERY-SMOKE`或`DELIVERY-BACKEND`、`lab.sourceDigest`符合现有门禁摘要格式；非法或缺失时明确拒绝，不访问Git、不填默认身份。有效时把门禁来源写入评测manifest，与原评测随机UUID分开；保留原runType、数据/语料摘要、配置、评测结果和输出布局，`gitHead`、`gitDirty`显式null，不借候选`baselineHead`。非门禁分支继续读取真实Git HEAD/dirty，原错误仍失败，不根据偶然存在的lab参数静默切换来源。

先在`RunbookEvaluationReportTest.java`和`DiagnosisEvaluationReportTest.java`覆盖有效来源、缺失/非法UUID与场景/摘要、门禁无Git以及普通Git兼容；仅在测试局部保存并恢复相关系统属性。随后分别运行原`RunbookOfflineEvaluationTest`和`DiagnosisOfflineEvaluationTest`的含Git与真实无Git聚焦测试，记录实际命令、来源及结果，不使用替身结果冒称真实评测。Git模式须实际查询当前Git；无Git子进程清除Git环境、限制父Git发现并移除Git工具路径，沿用已验证的门禁三字段。所有结果只写本阶段新证据或生成目录，不修改旧候选或旧失败树。

### 新候选与真实Backend

代码及前置说明稳定后用既有Create/Verify生成新ID候选，完整清单和文件数取本轮实际值；在新空目录解包，不复用ebf旧target或原失败树。记录四文件摘要、完整源码与受检子集，再执行候选原`check-delivery.ps1 -CandidatePath <新候选目录> -Gate Backend`，不带`CheckOnly`，不执行Frontend或完整All。适用工具/缓存、本机Windows与本地Docker边界继续披露；只允许既有测试创建和清理自有临时资源，失败留新记录，不改输入凑通过。

### 独立审计与收尾

依据新run实际XML重新统计通过/失败/错误/受控跳过及必需目标，不固定旧199/930或其他历史总数。核对同一runId/sourceDigest/scenarioId的Spec L规则、HTTP/进程/数据库恢复及固定样本证明，保持原夹具和计时边界；评测来源修复不自动证明这些后续目标已执行。

两个报告写入器的原输出分别位于模块`target/runbook-evaluation/`、`target/runbook-eval/<评测UUID>/`和`target/diagnosis-evaluation/`、`target/diagnosis-eval/<评测UUID>/`，不在门禁run目录中，须单独收集本轮新报告及manifest/summary/cases并计算摘要。逐项核对评测自身UUID、门禁三字段、显式null的Git字段、数据/语料/配置摘要、原阈值及实际分母，不把历史模块target或仅XML通过当新来源证明。

对新候选四文件、完整解包源码、受检子集、schema2安全摘要和制品做独立前后检查，按新基线核对Git、旧1200保护输入及既有私有/运行资源。更新`candidate-backend-report.md`和项目/测试/交付说明，分列阶段16失败、聚焦修复与新候选Backend；不预填候选身份或把后补报告回填ZIP。上游许可、旧TBD、异机/承诺平台、安全历史整合及正式发布权限保持。该范围为既有验收路径的必要测试工具修复，无新产品或架构方案，修复与验证均有实际证据后才能关闭任务。

## 阶段18：固定b667候选的真实无Git完整All

本阶段承接用户已批准的下一步，仅执行和审计现有门禁，不涉及新产品、架构或候选生成。固定输入为`bootstrap/target/release-candidates/b66759af-6cfb-4cb6-8db5-65276702f443/`四文件及1905项源码清单；本阶段证据根为`bootstrap/target/gitless-all/a392f45a/`，验收记录ID为`a392f45a-8dde-4647-a184-8b8c3f03b5c9`，使用其新空目录`s/`完整解包，门禁另生成独立runId，不沿用阶段17的target或报告。

### 输入保护与真实执行

先核对当前分支/HEAD、完整Git状态/内容、暂存/refs/worktrees和当前源码路径，保存旧候选、失败与成功证据、私有配置以及Docker容器/卷集合的保护清单。Verify固定b667四文件并记录原摘要，在新空目录完整解包，开始核对所有清单成员的路径、长度和内容SHA256。完整候选身份与门禁受检源码子集分别保存，不把历史baselineHead填入实现head。

仅在实际执行子进程中清理Git环境、阻断父Git发现并移除Git工具路径，保存发现失败和Git工具不可用的事实；运行冻结候选原`check-delivery.ps1 -CandidatePath <固定b667候选目录> -Gate All`，不使用`CheckOnly`、`skipTests`或替身阶段。按原门禁顺序执行Backend与Install、IncidentView、Lint、Build，记录实际工具、命令、时间和退出。沿用门禁的隔离本机测试与安全环境，禁用真实付费模型/外部联调；不调用旧持久实例生命周期，不改冻结源码或当前业务/门禁，不生成新候选。

本轮允许为取得同一run的整体验收证据而重新执行Backend，不能把这次重复运行称为新增独立测试用例。遇到失败或受阻先保留原身份与已产生证据，再据实际原因处理；不盲目覆盖、重跑或用前端/Backend历史成功填补。

### 独立审计与收尾

真实All退出后，独立核对新run的`run.json`、`stages.json`、`reports/`、`proof/`、`source.json`、`safe/summary.json`和`artifacts.json`。XML禁止DTD，运行三字段、时间、重复/空报告、实际用例/套件计数、候选必需目标和受控跳过按本轮输入核对；Backend及前端四阶段必须同属该run，最终PASS不能仅由某一阶段退出0推出。

独立审计本轮可靠性proof和两类评测模块外置manifest/summary/cases及报告，记录摘要并核对门禁三字段、评测自身UUID、Git字段null、实际样本分母/断言及既有阈值。保留真实数据库/HTTP/跨进程、固定样本、模拟模型及test classpath的不同证明边界。四文件、完整1905源码、受检子集、schema2来源、safe白名单及制品集合分别核对，不能仅凭门禁总退出码关闭专项验收。

在本目录新增`gitless-all-report.md`，同步项目上下文/TODO与测试、交付和发布入口；最终收尾绑定新run、保护输入、文档摘要及Git/资源状态。任务仅按真实结果勾选，原ebf/9624 FAIL、b667/fdbac063 Backend PASS、旧Frontend及所有旧完成记录保持原身份和字节。后补说明不回填固定ZIP。

本阶段只证明固定包在当前Windows/Java21/Node20及现有本机Maven/npm缓存条件下执行完整All；Docker Linux引擎不等于Linux宿主，亦非异机或全新依赖环境。持久JAR业务、真实模型/生产、第二机器、承诺平台/托管CI、旧TBD、安全历史整合与正式Git发布均保持独立边界；无新增权限、Schema、依赖或宪章违规。

## 阶段19：Windows命令入口最小修复与新候选真实All

用户在阶段18真实阻断及修复方案说明后明确授权继续。本阶段单独扩展允许当前`scripts/check-delivery.ps1`和`scripts/test-check-delivery.ps1`的必要修复及回归，不变更业务、Schema、阶段清单/门槛、数据集、受控skip、依赖或全局Node/npm配置。证据根`bootstrap/target/cmd-dispatch-fix/9dd05c94/`，验收记录ID`9dd05c94-e89d-450e-8988-3f2458675d6d`；新候选及All runId先为TBD，实际创建后再记录。原固定b667及其源码、7d1c BLOCKED、fdbac063 Backend PASS和旧失败/完成记录不可覆盖。

### 保护基线与中央执行器

先固定原1912条源码/说明路径及1906项旧保护输入的集合与内容摘要、Git分支/HEAD/暂存/refs/worktrees和既有Docker资源。只在本阶段新根保存控制记录，不清理旧文件或旧实例。阶段18的裸`npm.cmd`失败、完整路径成功的实际定位是修复依据，不能通过复制npm、伪造shim或变更工具全局配置掩盖问题。

在中央`Invoke-DeliveryCommand`的Windows`.cmd`分支，将调用的可执行名称解析为真实完整工具路径，再沿用固定`cmd.exe /d /s /c`引用方式。解析后的实际路径和原参数都保留既有元字符拒绝，无法定位时失败关闭；不引入Shell字符串插值或宽泛的执行绕过，不改变其他可执行文件分支、日志重定向、真实退出码和超时处理。复用当前PowerShell和.NET能力，不增加通用执行框架或新依赖。

### 真实跨目录回归与适用合同

在`scripts/test-check-delivery.ps1`增加真实Windows命令文件回归：工具与工作目录不同，工具目录和工作目录包含空格，经PATH查找调用并核对脚本自身目录、含空格参数及原退出码。增加解析路径/参数的适用危险输入拒绝检查；核对已有非Windows/非`.cmd`、超时及失败传播等合同保持。另从实际源码工作目录通过中央执行器调用当前安装的`npm.cmd --version`，记录真实工具路径、输出与退出，不把替身检查当作该实际回归。仅在本次子进程选择既有Node20工具路径，不修改全局环境或配置。

运行完整`scripts/test-check-delivery.ps1`及适用`scripts/test-release-candidate.ps1`，保留首轮失败和每次实际命令、退出与计数；先解决适用失败，再封存新候选。原合同数量不作为本轮硬编码预期，新增回归不改变正式门禁覆盖和跳过规则。

### 新候选、新解包和完整All

修复与前置说明稳定后，使用既有Create/Verify从当前源码生成不可覆盖的新ID四文件候选，记录实际文件清单、四摘要与完整来源；向本阶段全新`s/`完整解包，禁止复制旧target、node_modules或产物。新候选ID、受检子集摘要及完整文件数以实际结果填写，不沿用b667身份或其1365摘要。

仅在实际执行子进程清理Git环境、阻断父Git发现并移除Git工具，保存发现失败及工具不可用证据；从新树执行候选原`scripts/check-delivery.ps1 -CandidatePath <新候选目录> -Gate All`，不带CheckOnly或跳过测试。按同一新run真实执行Backend和Install、IncidentView、Lint、Build，记录工具、缓存、命令、时间与退出；沿用本机隔离测试安全环境，不操作旧持久实例或付费/远程资源。失败/受阻保留原始记录，先诊断后决定必要后续，不改冻结候选凑通过。

### 独立审计、任务衔接与收尾

新All结束后独立核对该run的run/stages/source/safe/artifacts、新XML三字段、时间、计数、必需目标与受控跳过，并审计可靠性proof、两类评测模块外置manifest/summary/cases及报告的身份、摘要、样本分母和既有断言。前端四阶段与Backend必须同属新run；完整源码与四文件起止、受检子集、schema2安全白名单和制品集合分别核对，不以总退出0或历史952项代替专项验收。

阶段18 T076的原固定b667成功审计无可执行阶段可供核对，只能保留阻断身份、0阶段/0XML和未执行项并以失败收尾终止，不能变成旧SC-019通过；新候选完整成功审计由本阶段T081承接。当前先保持旧T076未勾选，最终依据真实阻断核查明确其终止含义，不重复运行旧失败入口。

在本目录新增`cmd-dispatch-fix-report.md`，同步项目上下文/TODO和测试、交付、发布入口；按实际证据关闭T078～T082。综合proof/completion核对原Git、1906项旧输入、既有资源及最终说明，后补报告不回填冻结ZIP。本阶段仍限Windows/Java21/Node20及既有Maven/npm缓存、隔离临时测试与安全模拟；Linux Docker Engine不等于Linux宿主，异机/未验平台/托管CI、生产/真实模型、旧TBD、安全历史整合和Git发布继续开放。上游署名/许可证、人工审批及持久化边界保持，无新产品或架构决策及宪章违规。

2026-10-19阶段19执行身份补充：新候选c1687735-1ad9-4881-a620-81702d39d1ab已Verify，1907完整文件；本轮1365受检文件摘要74BEB5839C44BC6B15477DE5400CF740F638800FA423FBC0D26238CA833531D4。真实无GitAll d98d8861-aa83-4168-82f5-c84e2b62022b已通过六项工具前提并处于RUNNING。176门禁合同及实际npm检查在封存前通过；49项候选合同在All启动后追加核对并通过，候选工具未修改且来源摘要另行绑定，不冒称启动前执行。后补说明不回填冻结ZIP，最终结果待本轮审计。

### 阶段19实际执行补记（2026-10-19）

上文TBD、进行中和未完成表述属于创建时状态。实际新候选`c1687735-1ad9-4881-a620-81702d39d1ab`（1907文件）、真实无GitAll `d98d8861-aa83-4168-82f5-c84e2b62022b`为PASS/0，Backend、Install、IncidentView、Lint、Build五阶段退出0；201 XML、952项/931通过/21受控外部跳过/0失败错误。受检1365文件摘要`74BEB5839C44BC6B15477DE5400CF740F638800FA423FBC0D26238CA833531D4`，独立可靠性/评测审计PASS/VERIFIED/COMPLETE；完整来源与1906项旧输入和资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED、fdbac063 Backend PASS及其他旧FAIL/PASS保持；T076仅以原0阶段/0XML的阻断核查失败终止，SC-019仍未满足，新候选完整成功审计由T081承接。82/82表示任务已关闭，包含失败终止，并非82项验收成功。后补说明不回填冻结ZIP；异机/承诺平台/托管CI、旧TBD、安全历史整合和正式Git发布继续开放。

## 阶段20：正式整合前输入审查与清单更新

本阶段仅为用户已授权的只读审查及说明整理，无新业务、行为或架构设计。验收记录ID`68e1acf2-a2a3-45d0-9669-b86483cb3aee`，证据根`bootstrap/target/integration-review/68e1acf2/`。基线为1913条完整源码/说明路径、537项逐文件待审（53修改/484未跟踪）、原分支`feature/incident-platform`和HEAD `67624931f20812922568d3641a095adb291263ba`、暂存空；不因整理材料而修改这些既有实现。

### 固定输入与分工核对

使用完整NUL/UTF8路径枚举、逐文件长度/SHA256和只读Git身份检查保存开始基线；保留原工作区及全部有效修改/未跟踪文件。核对完整路径而非折叠目录计数或仅比较HEAD，最终说明稳定后重新生成清单并比较开始/结束变化；仅允许本阶段必要说明和Spec变化，发现其他漂移先报告，不覆盖或清理。

历史附件核对与当前源码/候选比较可由不同执行者并行只读进行，结果汇入同一证据根，不重复扫描或运行历史副本。历史侧核对现有附件和声明tip `437e275d8f4443c4b1b86b05321de1496594a987`、原历史保留、来源映射及净化声明的实际适用边界；当前新增实现尚未整合，旧1890文件待审副本不升级为当前版本。可推荐净化衍生历史承载，但目标仓库/分支仍为TBD，本轮不复制源码、不checkout或切换工作树、不写入任何Git对象、索引、refs或配置。

源码侧读取固定c168四文件及d98既有All/独立审计/完成来源，逐项比较完整当前工作区与1907成员候选；新增、修改、删除及候选原排除项分别分类，区分源码受检子集、完整候选和后补说明。历史PASS仅属原候选/run/sourceDigest，不把本轮清单复核说成新All、构建、持久JAR业务或异机结论，不生成新候选或回填旧ZIP。

### 逐文件清单与整合草案

复用阶段14的互斥G01～G05审阅组及现有候选路径/敏感模式规则，为本轮实际待审路径保存Git状态、相对路径、长度、SHA256和唯一审阅组，不复制源码正文或私有值。重新核对每组与总集合精确一致，数量取最终实际枚举，不套用旧531项或固定开始537项；跨模块共享POM、Schema、配置、前端/后端接口和生命周期入口的共同依赖继续说明，不机械拆分为独立提交。

更新`docs/releases/v0.1.0-integration-plan.md`及发布准备入口，列出当前完整输入、原/净化历史差异、许可与上游贡献、原始日志/生成材料/凭据排除、既有测试来源和未验证项。配置模板例外沿用当前已确认清单，不自动扩大允许范围；可识别敏感模式无命中只能按既有规则描述，不证明无任何秘密。提出净化路径建议、目标库/分支选择及暂存/提交/Tag/推送/发布分别授权顺序，保持只读建议状态，不执行任何一项后续动作。

### 独立复核与保护收尾

对最终待审清单和完整来源差异做独立集合/状态/内容摘要复核，核对历史附件结论及所有本地相对链接、无机器绝对路径/私有值、许可/署名和实际授权边界。新增本目录`integration-review-report.md`，同步项目上下文/TODO与交付、发布入口，记录开始和最终清单身份、差异、建议、TBD与真实限制；最终proof/completion绑定本轮输入及说明摘要，核对Git与旧候选/证据及私有/运行资源保持。任一来源或保护检查失败保留失败记录，不强行标记审查成功。

本阶段不运行构建、测试、服务或Docker生命周期，不触发CI、不复制到旧历史副本、不暂存/提交/Tag/推送/合并/部署，不删除或改写原历史。只读审查不降低上游署名、人工审批和持久化原则，宪章五原则无新增冲突。阶段18原阻断、T076失败终止及SC-019未满足、阶段19原c168/d98 PASS、异机/承诺平台/托管CI、旧TBD、安全历史整合及正式发布权限分别保留。

## 阶段21：独立净化历史承载与提交前冻结

用户已明确授权选定独立承载目录和`codex/release-v0.1.0`分支，检查通过后创建一个本地整合提交；原开发库保持。此处仅补充既有交付操作范围，不新增产品、业务或工具行为。外置记录ID`498a7289-80dc-4a8b-916b-3c97229e1c23`，逻辑路径`bootstrap/target/release-integration/498a7289/`保存在原开发库，不复制私有证据进新源码，也不在正文记录机器绝对路径或不存在的跨库链接。

### 身份恢复、完整树与文档范围

以净化tip `437e275d8f4443c4b1b86b05321de1496594a987`为父历史输入，对齐阶段20清单的1908条`integrationSourceFiles`，排除原6条开发者配置；原库1914路径和538项待审及旧历史/候选/证据保持。核对已恢复源树、分支与路径/字节来源，不用旧1890文件副本或仅538个待审路径替代完整树。无需重新设计历史净化方式或业务实现。

必要承载说明只更新`docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、`docs/releases/v0.1.0-integration-plan.md`、`docs/releases/v0.1.0-release-preparation.md`和本目录`spec.md`、`plan.md`、`tasks.md`共7文件；另按独立证据处理34文件既有格式门禁问题（13 Java/21 Markdown，与TODO交集1，合计40文件差异），保持1908路径。Java仅清除14处纯空白行空格，Markdown仅转换硬换行/EOF并核对去position AST；LICENSE/NOTICE、执行逻辑、Schema、数据集和跳过规则保持。原始字节变化与旧All来源明确分列，不将等价检查视为编译或新测试。

### 提交前独立审查和外置Git回执

在说明稳定后独立核对1908完整集合和字节来源、7份说明差异/相对链接、许可/版本与既有窄秘密规则、原6排除项及原库保护。执行源码与c168/d98受检子集和来源摘要分别映射；既有All及专项审计不转写为本次重跑，也不把旧070 JAR或01166dd3浏览器结果转归此树。检查暂存树精确匹配审阅后的完整清单、父历史与分支一致，确认只有本地一次提交授权，无推送/Tag/托管CI/发布/部署。

T087～T090只覆盖提交前可证明的恢复、对齐、说明和独立审查冻结。四项按真实记录完成后，由已授权Git收尾执行一个本地整合提交并只读核对父提交、tree、commit及最终干净状态；实际SHA和结果写外置回执。该回执绑定本轮输入、阶段20来源、7份说明差异和最终完整清单，不需要在已提交文档中回填自己的提交SHA，不为更新任务或说明再创建第二次提交。若提交或收尾失败，保留失败和未完成结果，不修改旧通过记录、不自动追加提交。

本节自身为提交前冻结方案；实际Git收尾状态以外置回执和本库日志为准，不预写成功或SHA，也不以编写时状态断言提交后的当前状态。所有测试/构建、服务/Docker生命周期、模型/远程调用、新候选、异机/平台仍不在本轮执行范围。上游归属和历史声明、人工审批、持久化边界、历史FAIL/BLOCKED及T076失败终止/SC-019未满足不变，无新增架构或宪章冲突。

## 2026-10-20阶段21格式范围补记

提交前Git格式门禁发现46处问题，本阶段实际范围补充为7份必要承载/业务说明更新及34文件格式等价修复；TODO两类交集1份，合计40文件相对阶段20不同，源码路径仍1908。格式修复本身涉及13份Java的14处纯空白行空格及21份Markdown的硬换行/EOF：Java执行逻辑未改，Markdown格式转换去除position后的AST相同，逐文件before/after与独立复核一致。

13份Java的原始字节已经变化，不能继续宣称当前完整字节或受检摘要与旧c168/d98完全一致。旧All仍只归属原候选/run/raw摘要；本轮没有新跑All、编译、JAR、浏览器或服务。新增证据是逐字节差异、Java空白与Markdown AST等价检查及实际Git格式检查，最终工作区/暂存树检查以外置回执的真实结果为准。格式证据沿用外置记录`498a7289-80dc-4a8b-916b-3c97229e1c23`中的format-review、format-parent-check和format-applied，不创建跨库文件链接；以下2026-10-19“仅7说明/原字节一致”口径仅为补充前时点，本补记及修订FR/SC为本阶段最新验收范围。
