# 任务：v0.1.0本地交付候选

**输入**：`specs/013-release-candidate/` 的spec、plan、research、data-model及contracts。

## 阶段1：准备

- [x] T001 核对现场、版本与许可/私有配置边界，写入 `specs/013-release-candidate/research.md`（FR-011）。
- [x] T002 完成规格、计划、契约和一致性核对，使用 `.specify/feature.json` 选择013且不切分支（FR-011）。

## 阶段2：基础约束

- [x] T003 在 `scripts/new-release-candidate.ps1` 定义允许路径/禁入规则及规范化、链接、碰撞、敏感内容检查，失败不输出敏感值（FR-004/006）。

## 阶段3：用户故事1——统一版本（P1）

**独立验收**：9POM/前端版本一致，旧JAR并存与当前缺失反例通过。

- [x] T004 [P] [US1] 在 `scripts/test-local-demo.ps1` 增加当前版本精确选择、旧包不得回退、非法版本和父版本不一致契约（FR-002/009）。
- [x] T005 [US1] 将根 `pom.xml` 与8个模块POM项目/父版本统一0.1.0，保留依赖/坐标/署名（FR-001）。
- [x] T006 [US1] 在 `scripts/local-demo.ps1` 按根版本与模块artifactId精确定位JAR，运行契约及离线validate（FR-002）。

## 阶段4：用户故事2——安全完整候选（P1）

**独立验收**：有效未提交源码完整入包；危险路径/链接/篡改/遗漏/额外成员被拒绝；许可/历史来源可核对，历史明确暂扣且禁止bundle，不能将源码就绪视为完整历史交付。

- [x] T007 [US2] 在 `scripts/test-release-candidate.ps1` 编写Create/Verify正反例，覆盖 `contracts/candidate.md` 最小契约（FR-009）。
- [x] T008 [US2] 在 `scripts/new-release-candidate.ps1` 实现当前tracked+untracked源码收集、复制前后摘要和不可覆盖Create，输出ZIP/manifest/candidate/SHA256SUMS（FR-003/004/005）。
- [x] T009 [US2] 在 `scripts/new-release-candidate.ps1` 实现独立Verify，包括成员安全、逐文件内容、四文件限制、来源保留与历史暂扣状态，拒绝额外bundle（FR-006/007）。
- [x] T010 [P] [US2] 在 `NOTICE` 与 `docs/releases/configuration-boundary.md` 保留归属，说明模板/私有配置、历史潜在凭据导致暂扣且原历史保留、完整历史交付待单独方案与后续初始化；历史验收摘要仅显式白名单与来源（FR-007/008）。
- [x] T011 [US2] 运行 `scripts/test-release-candidate.ps1`，修复真实契约失败并记录实际结果（FR-009）。

## 阶段5：用户故事3——候选可构建（P2）

**独立验收**：当前版本package及固定候选解包离线package有真实结果、准确产物与缓存边界。

- [x] T012 [US3] 在当前树运行离线package核对bootstrap/mcp-server0.1.0准确JAR，在 `specs/013-release-candidate/implementation-report.md` 记录结果（FR-010）。
- [x] T013 [US3] 源码/说明稳定后生成新候选并Verify，按清单完整解包至新隔离目录离线package，核对产物及源码/制品哈希，在 `specs/013-release-candidate/implementation-report.md` 记录（FR-005/010）。

## 阶段6：收尾

- [x] T014 更新 `docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、`docs/development/testing.md` 与 `docs/releases/`，区分源码就绪/历史暂扣与第1步整体未闭合、本轮构建、历史Spec L与后续独立链路/平台/Git发布（FR-008/011）。
- [x] T015 核对 `specs/013-release-candidate/tasks.md` 与实施报告、Git状态和实际证据一致，关闭真实完成项，无提交/推送/部署（FR-011）。

## 依赖与并行

T001→T002后实施；T003→T007/T008/T009→T011。US1的T004～T006可与US2候选实现并行，T010为独立文档。US1及US2均通过后执行T012；T014说明稳定后T013固定候选；最后T015核对。MVP为US1，但本轮目标包含全部三故事。所有任务具有ID/复选框/准确路径，故事任务均标US。

## 阶段7：另行批准的历史净化副本

用户在阅读 [历史移交方案](history-handoff-review.md) 并了解目的后回复“ok”，批准仅在新的隔离目录执行该方案。原15项源码任务和固定四文件候选不变；本阶段产生衍生历史对象，不授权提交新增实现、Tag、推送、远程改写或部署。工作目录为`bootstrap/target/history-sanitization/<runId>/`。

- [x] T016 冻结原库/候选基线，取得固定官方工具及摘要，仅安装到隔离tools目录。
- [x] T017 以合成Git夹具验证精确替换、路径移除、消息/编码/父顺序和签名处理，再在单分支独立副本执行过滤。
- [x] T018 全量核对805项commit-map、4个merge、元数据/消息及每个提交的树；记录签名和SHA实测变更数量。
- [x] T019 从白名单分支重新导出history.bundle，在新仓库恢复并fsck，检查全部对象、引用、原blob及窄规则，无原始对象误带出。
- [x] T020 在独立待审源码副本按固定manifest精确对齐1890文件，缺失/额外/哈希差异为0，保留未提交状态。
- [x] T021 核对原库和冻结四文件未改变，生成去值交接记录，更新本目录实施附件及项目上下文。

本阶段顺序T016→T017→T018→T019→T020→T021。任何验证失败均停止可分发标记，修复后从新的隔离输入重新验证，不修改原库或扩大净化范围。

## 阶段8：用户批准继续的固定候选本机隔离复现

用户在历史处理完成后要求“继续下一步”，执行此前已列明的固定候选业务复现。本轮使用新解包目录、新Name/Compose project/卷/随机私有密码，不复制旧target、node_modules、dist或私有配置；复用本机Maven/npm缓存与已缓存镜像，不能声称第二台机器、无缓存环境、Linux或生产验收。所有副作用限新实例。

- [x] T022 核对原库与固定候选，完整解包1890成员到新目录并确认无生成物/私有配置，记录原库基线。
- [x] T023 在新源码根以`candidate-proof`初始化新实例，记录工具/镜像/资源归属和空数据起点，不操作既有资源。
- [x] T024 通过候选自带Build从源码构建真实0.1.0 JAR及production dist；记录缓存前提、制品完整摘要及源码前后哈希。
- [x] T025 启动新实例，经真实静态服务/HTTP代理登录、读取身份，提交2条签名告警并核对同Incident和诊断引用事实。
- [x] T026 实际拒绝后核对零应用；另一个Action实际批准并核对唯一应用及回执，重复后仍只有一次。
- [x] T027 对本轮实例Stop/Start，比较卷/凭据/制品、进程身份及Incident/诊断/审批/回执/模拟应用事实保留。
- [x] T028 留存去值验收记录和前提/限制，安全停止本轮进程与容器并保留卷，核对原库、冻结候选及既有资源未受变动。

顺序T022→T023→T024→T025→T026→T027→T028；错误保留并修正真实原因，不借用旧制品或旧库令结果通过，不重新计算此前Spec L测试总数。浏览器交互、第二机器/跨平台及新的完整Backend/All不自动由本轮HTTP链路验收获得通过结论。

## 阶段9：用户批准继续的冷态取证与浏览器只读复核

用户继续下一步，复用上轮停止并保留数据的candidate-proof，现有诊断/动作均只读。冷态仅指新服务进程启动后首次读取，不清操作系统缓存、不创建新数据库或业务记录。首轮采集失败的TBD保留。

- [x] T029 新建本轮证据目录和原库/资源基线；保持30s阈值、路径/哈希规则，新增分阶段安全取证探针及无网络自检，旧结果不可覆盖。
- [x] T030 对同一保留实例执行一次进程冷启动后115项首次静态读取与一次同序热态对照，记录每阶段失败类型/状态/耗时与摘要，不能将后续成功写成首轮根因已修复。
- [x] T031 真实浏览器查看登录、工作台、既有事件/诊断/拒绝与成功Action/回执、复盘/同版本手册及退出保护；仅登录/退出允许认证写入，不发新诊断或审批请求，浏览器私有凭据由用户本机输入。
- [x] T032 留存截图及事实记录，核对既有业务事实、原库/候选与旧资源保持，停止本轮进程/容器并保留卷；明确本机、外部Star装饰请求及未验证平台边界。原80卷保留但新增匿名卷1，严格卷集合检查FAIL和归属TBD单列，不声明资源无新增；见 [本轮记录](browser-followup-report.md)。

顺序T029→T030→T031→T032，缺少浏览器登录时继续不依赖登录的静态/匿名复核，不绕过认证或将HTTP接口结果冒称浏览器交互验收。

## 阶段10：用户批准继续的存储日志卷修复

- [x] T033 对受控新存储容器Mounts定向取证，确认原模板自动生成匿名`/logs`的机制；新取证不能独立证明旧匿名卷的历史具体归属，该TBD另行保留。
- [x] T034 在 `resources/local-demo/compose.yaml` 显式映射带同instance标签的`storage-logs:/logs`，在 `scripts/test-local-demo.ps1` 增加实际Compose挂载/归属合同，复用现有Stop语义（FR-012）。
- [x] T035 对新Name执行至少三轮依赖启停，核对全部Mounts、日志标记、空业务事实及全局卷集合稳定；记录Init允许的5个命名卷，不运行旧JAR或改旧实例（FR-013）。
- [x] T036 从稳定源码生成新的四文件候选，Verify并在新目录完整解包核对；记录候选身份和验证范围，旧四文件及1890副本保持（FR-005/006）。预演已通过，文档收尾后最终身份另存本轮`final-candidate.json`，不将自身ID回填清单。
- [x] T037 更新交付/项目说明和实施报告，停止本轮自有依赖并保留全部卷，核对原库/Git/旧候选/旧私有配置与既有容器保护，保留首轮静态异常及旧匿名卷历史归属TBD（SC-007）。实际检查17个预期文件修改/1份新报告、原1890源码/4候选文件/9私有文件/16容器/81旧卷保持，103相对链接有效；见 [修复记录](storage-volume-fix-report.md) 与本轮独立收尾JSON。

顺序T033→T034→T035→T036→T037。此轮仅修复本机依赖资源生命周期；日志容量/清理策略、旧实例迁移、完整应用构建/业务链路、跨平台和生产不在范围内。

## 阶段11：用户批准的固定修正候选完整构建与All

2026-10-18，用户批准推荐第1项。冻结候选及解包源码不改；新增记录只写原仓库说明或本轮ignored证据目录，不把HEAD误作包含新增实现的提交。

- [x] T038 核对固定070候选四摘要并全新解包1895成员，保存原库/Git、既有16容器/89卷及私有配置基线，确认无旧生成物。
- [x] T039 在新解包根运行候选门禁合同与All CheckOnly，记录工具、缓存前提和安全环境，不把CHECKED当PASS。87合同通过，CheckOnly `fc81ec57-e27a-49dd-bde4-1e35906b085d` 为CHECKED；Java21.0.12/Maven3.9.11/Node20.19.5。
- [x] T040 直接执行候选local-demo内部离线Maven package命令，父工程及八模块成功，两份准确0.1.0可执行JAR及摘要已核对；业务测试仅在package步骤跳过，未初始化持久演示或启动新JAR。辅助manifest字段/跨行统计误判保留，按内嵌Maven坐标、Boot入口和九条reactor行复核既有产物，未重复构建。
- [x] T041 原样运行候选All `353b453f-56c7-451e-a54a-67057342a19c`，五阶段退出0；199新XML/930项（909通过、21受控跳过、0失败错误）、14必需类零跳过、13外部门目录，1363受检源/563制品及safe白名单独立核对通过。45规则组合、真实HTTP/恢复及400样本另行复核通过，dist117文件；完整1895manifest绑定在T042收尾。
- [x] T042 完整1895新源码/1890旧源码、8候选文件/16私有文件、两JAR摘要及127构建产物目录均核对；563门禁制品/safe、Git分支/HEAD/index/refs/worktrees、原16容器/89卷保持，额外0。原库仅10份说明变更/1份新报告，删除0、业务源码未改；失败/中断/限制及中文交付/测试上下文已同步，见 [本轮报告](full-candidate-acceptance-report.md)。不提交或发布。

顺序T038→T039→T040→T041→T042。完整构建和All与第二步新持久实例业务演练、浏览器及异机复现分别验收；旧TBD不会由后续成功自动关闭。

## 阶段12：用户批准的修正候选新持久业务演练

2026-10-18，固定070候选及阶段11构建复用，原All不重跑。本轮只可操作新`candidate-run-cea55ab3`，证据根`bootstrap/target/persistent-candidate/cea55ab3/`。

- [x] T043 核对1895冻结源码、127项既有构建及候选身份，保存原1902文件/Git、16容器/89卷及630份私有/先前证据文件基线；需求清单14/14通过，无已有同名实例。
- [x] T044 候选Init返回reused=false，新instance `3754886c428d40fcb7e8eec19fdcf921`；空库/唯一local-admin/模拟0应用PAUSED、4依赖5命名卷全部Mounts及标签通过，全局89→94且额外匿名卷0；同Name锁内用原函数发布2 JAR/117 dist并逐项绑定，rebuilt=false。
- [x] T045 正常Start为READY，经真实web代理完成2告警→1事件、1诊断及5引用/1文档摘要、拒绝0应用/批准唯一1应用和同意图重放；独立SQL核对2动作/1执行/1回执、14连续Timeline/6 Outbox（relay关闭，PENDING）；117 dist及117次完整静态HTTP字节核对通过，包含favicon和mjs worker，checkpoint保持。
- [x] T046 正常Verify/Stop/Start/Verify通过，三服务新owned进程、5命名卷/全局94卷/Mounts、9私有文件及完整制品保持；重启后HTTP与独立SQL快照比较通过，唯一应用仍1，14时间线/6 Outbox保持。CIM核对两java进程为-jar发布产物，非test classpath，静态代理身份匹配。
- [x] T047 正常Stop退出0，保留5卷；closeout逐项核对两候选/1895新与1890旧源码、630旧保护文件、127源制品/119实例制品/9私有配置无变化，原16容器/89卷及Git身份保持，全局94卷、额外容器/卷0。原库仅12说明修改+1新报告、删除0，中文上下文/发布与交付文档及最终摘要另行绑定；见 [本轮报告](persistent-candidate-runtime-report.md)，未提交发布。

顺序T043→T044→T045→T046→T047；本轮不重做已通过All，不新增浏览器或跨平台结论，旧两项TBD不回填关闭。

## 阶段13：暂无第二机器时的接手准备

2026-10-18用户确认暂无第二台机器。本阶段只准备材料并验证本机文件/命令边界，外部执行不是本阶段已完成项。固定证据根`bootstrap/target/second-machine-handoff/f9318643/`；不调用旧实例生命周期、不构建或重跑All。

- [x] T048 固定070身份及原1903文件/Git、两候选和阶段12完整证据46项；首轮Git中文路径转义导致57项基线摘要缺失，原记录保留并明确拒用，UTF-8/NUL读取后1903项有效摘要重新核对。
- [x] T049 编写 [接手手册](second-machine-handoff.md)，完整说明校验/空目录解包、依赖/缓存、新Name生命周期、HMAC及UI审批/SQL/停启证据；列无Git门禁和Linux执行位限制，所有异机项待执行。9段PowerShell语法0错误、公开固定HMAC向量与Node独立计算一致、HTTP请求0；首轮辅助EncodedCommand超出Windows命令行上限（44,224字符/ENAMETOOLONG），保留记录并改用本轮脚本文件核验，指南未因此变更。
- [x] T050 原样四文件候选+指南+白名单来源摘要组成7成员接手ZIP，24,166,854字节，SHA256为`89162815A8CC06EB8D0CA71DA12DDCD2EEF01A33543D21F6DABD92E9A1FE7BB5`；内外层SHA及全新解包精确集合/字节通过，不含私有/历史/缓存/运行数据，不修改冻结候选。
- [x] T051 外层新ZIP解包精确集合/摘要通过，内层source.zip与原输入同字节，独立全新提取1895源码前后哈希/集合及原Verify退出0。隔离父Git后discovery退出128，原All CheckOnly `bce6744d-5ef7-46fb-a31d-ec8c0d376f6e` 退出1/FAIL/GATE_FAILED，head/sourceDigest为空，工具/阶段0，仅run/summary；没有构建/测试/服务或Git初始化。首轮辅助器多Git路径拼接失败（Win32 2）保留，独立续跑只补未完成步骤；结果为负向前提验证，不是All通过。
- [x] T052 中文上下文/发布索引/测试交付说明已同步材料准备、失败与实际限制；用`final-check.ps1`逐项核对原1903→1904路径、13份说明边界/无删除、46旧保护文件/两候选/前证据及Git身份，绑定包/指南/负向预检与最终文档摘要。最终状态仅`PREPARED_AWAITING_SECOND_MACHINE`，本阶段5项为准备完成，异机执行/完整All/提交/发布未发生，保护结果见本轮`final-check-proof.json`及`completion.json`。

顺序T048→T049→T050→T051→T052。本阶段准备完成不等于异机验收；以后有接手机器时，按手册收集新的环境/构建/业务/SQL/重启事实。

## 阶段14：暂缓异机后的发布前整理

2026-10-19用户暂无第二机器，要求先执行后续动作。本阶段仅发布准备，不提交/Tag/推送/合并/部署，不修改业务或门禁，不重做已经通过的构建/All；证据根`bootstrap/target/release-preparation/9d956285/`。

- [x] T053 核对分支/HEAD/状态/最近提交，冻结原1904源码文件和77项旧候选/前证据输入；确认暂存为空、初始53修改+475逐文件未跟踪=528待审，保持已有工作。
- [x] T054 草稿531项（53修改/478逐文件未跟踪）及互斥G01～G05=38/285/29/12/167核对通过，现有路径/敏感模式无命中；正式清单在说明稳定后以-Final重新固定，不暂存或提交，不把审阅组当独立提交。
- [x] T055 完成 [发布准备入口](../../docs/releases/v0.1.0-release-preparation.md) 与 [整合草案](../../docs/releases/v0.1.0-integration-plan.md)，未发布状态、贡献/证据矩阵、原/净化历史承载TBD、授权顺序及本机失败回退边界已交叉核对，不冒称生产回滚。
- [x] T056 9POM/前端package+lock均0.1.0、LICENSE/NOTICE保持；相对070非说明差异0，12说明差异和12额外路径分别核对。纠正索引中阶段11/12过时表述，历史报告保持；材料相对链接/宿主机路径另由收尾检查绑定，无新All或服务。
- [x] T057 本轮报告、项目上下文/TODO/发布索引和任务已同步；以最终清单及closeout核对原1904→1907仅新增3说明、8旧说明可改/删除0、77保护输入和Git身份/暂存保持，保存完成摘要。状态仅`RELEASE_PREPARATION_ONLY_NOT_RELEASED`，异机及所有原阻断保留，无Git写入或发布。

顺序T053→T054/T055→T056→T057；材料准备任务完成不代表实际提交或发布。最终清单必须在本轮说明稳定后固定，不借旧源码哈希为当前工作区背书。

## 阶段15：用户批准的无Git源码候选门禁

2026-10-19用户要求继续已推荐下一项，修复源码包没有`.git`时门禁无法执行的问题。仅修改当前脚本/合同和说明，以新候选、新解包目录及新证据验收；旧070、接手ZIP、阶段13负向证明和历史All保持，不提交、Tag、推送、合并、部署或操作旧实例。证据根`bootstrap/target/gitless-delivery/a83506bb/`。

**US4目标与独立验收**：显式选择有效候选后，在无父Git和无Git工具的子进程完成真实All `CheckOnly`及Frontend四阶段，安全摘要绑定完整候选和实际源码；正反合同证明篡改/漂移失败关闭，默认Git调用兼容。不执行新的Backend/完整All或持久业务。

- [x] T058 核对原1907源码路径、Git身份与80旧证据保护项并固定基线；在本目录`spec.md`、`plan.md`和`tasks.md`补充US4、FR-020～024、SC-012～014及验收顺序，不改历史阶段或checklist。
- [x] T059 [US4] 在 `scripts/test-check-delivery.ps1` 补充有效候选、无Git工具、坏候选、完整树缺失/新增/隐藏配置/大小写/链接/内容变化、起止候选与源码漂移、安全白名单及原Git调用兼容合同，保留真实校验边界而非全替身通过（FR-020～024、SC-013）。
- [x] T060 [US4] 在 `scripts/check-delivery.ps1` 实现显式`-CandidatePath`、既有Verify复用、仅已知生成根剪枝的完整树核对、起止四摘要及源码身份比较、CheckOnly结束复核、候选无Git工具要求和schema2白名单，默认Git模式schema1/固定命令/退出语义保持（FR-020～023）。
- [x] T061 [US4] 运行 `scripts/test-check-delivery.ps1` 与 `scripts/test-release-candidate.ps1`，核对新增正反例和旧合同实际结果，修复适用失败并将命令/退出/计数记录到本轮证据目录（FR-024、SC-013）。
- [x] T062 [US4] 从稳定源码生成新ID四文件候选并独立Verify，在本轮新空目录完整解包；隔离父Git并移除Git工具后运行真实All `CheckOnly`，记录来源/工具/退出及完整候选与源码起止一致（FR-020～024、SC-012）。此前ebf已解包；用户启动Docker后的补跑`f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`为CHECKED/0、0阶段，复用原解包树且完整来源保持，不称新干净环境复现。
- [x] T063 [US4] 从同一新解包根执行真实无GitFrontend安装、页面检查、lint、构建四阶段，独立核对`safe/summary.json`的schema2来源及四文件/完整源码结束摘要；在 `scripts/check-delivery.ps1` 与 `scripts/test-check-delivery.ps1` 仅补充既有配置声明的`frontend/.output/incident/`精确生成根并验证`.output`其他新增成员仍拒绝，以另一个新ID候选重新验收，不将本机缓存复用说成异机或新完整All（FR-021/024、SC-012/014）。
- [x] T064 在本目录 `gitless-delivery-report.md` 及 `docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、`docs/development/testing.md`、交付/发布入口同步真实结果、候选身份和旧接手包适用边界；逐项核对原库/Git/80保护输入及已有私有/运行资源，按证据关闭任务，保留异机/平台/旧TBD/安全历史整合与正式发布未完成事实（SC-014）。

顺序T058→T059→T060→T061→T062→T063→T064。T059与T060均以已确定来源契约为基础，首轮合同失败保留且实施前后分列；文档收尾和检查材料可独立准备，但新候选必须在实际输入稳定后固定。默认Git兼容、无Git前提检查、Frontend真实执行与历史Backend/All分别统计，不借旧绿灯补齐本阶段未执行范围。

**首轮未完成状态**：候选`25318b5b-0868-44d2-b786-16065f820158`的Frontend `837f10df-1c1b-4ca4-b0ac-0334ee44db31`四阶段退出0，但末尾完整树核对因既有`frontend/tests/tsconfig.incident.json`生成的`.output/incident`而最终退出1/`FAIL/GATE_FAILED`；只补精确生成根，原失败/候选保持，新候选尚待重验，T063不勾选。All前提检查`df9174b9-dfea-48d5-b12d-bb14afe03fa4`退出2/`BLOCKED/PRECONDITION_FAILED`，Docker前提未满足，T062继续不勾选；不以已完成合同或四阶段退出0替代这两项最终验收。

阶段15实际收尾：159门禁合同、49候选合同通过；新验证候选ebf476ea真实无GitFrontend CheckOnly为CHECKED，Frontend四阶段与源码结束核验PASS。All CheckOnly ac712f69-fea2-4ea0-9401-f842b88ce386因Docker引擎不可用BLOCKED/2，因此T062保持未完成（6/7，累计63/64），待本机依赖恢复再实际补跑。首253候选FAIL及四阶段0的原记录保留，不以修正后的新成功覆盖旧失败；详见 [本轮报告](gitless-delivery-report.md)。

2026-10-19后续补跑：用户已启动Docker，同一固定ebf候选及原attempt2/s在无父Git/无Git工具下执行新All CheckOnly，run `f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`实际CHECKED/0、0阶段、schema2/head=null，完整1901来源起止保持。T062现关闭，阶段15为7/7、Spec M累计64/64；上段为先前时点，原BLOCKED记录和完成证明不回填。证据根`bootstrap/target/gitless-preflight-followup/f4721f5c/`，本轮未新跑Backend/完整All/Frontend、未异机或发布。

## 阶段16：用户批准的固定ebf候选真实Backend验收

2026-10-19用户批准在同源无Git前提检查之后执行真实Backend。保持固定ebf四文件及原`bootstrap/target/gitless-delivery/a83506bb/attempt2/s/`，不生成候选、不改冻结源码、不重跑Frontend或完整All；新证据根`bootstrap/target/candidate-backend/67942981/`。阶段15未执行Backend的历史边界不改写。

**US5目标与独立验收**：在无父Git/无Git工具的实际子进程运行候选原Backend门禁（无`CheckOnly`），用本轮新XML、proof、完整来源、安全摘要及制品逐项核对真实结果；计数按本轮报告，不沿用历史总数。保护原库/Git、旧候选/证据和私有/运行资源，不把本机测试扩写成新完整All、持久业务、异机或生产结论。

- [x] T065 固定原1908条源码/说明路径、Git身份/暂存、840项旧证据/私有文件及现有Docker资源；Verify固定ebf完整1901源码与四文件身份，在 `bootstrap/target/candidate-backend/67942981/baseline.json`、`source-before.json`、`protected-before.json` 保存基线，并在本目录 `spec.md`、`plan.md`、`tasks.md` 追加FR-025/026、SC-015/016与US5范围，不改历史阶段或checklist。
- [x] T066 [US5] 从原 `bootstrap/target/gitless-delivery/a83506bb/attempt2/s/` 执行 `scripts/check-delivery.ps1 -CandidatePath <固定ebf候选目录> -Gate Backend`，不带`CheckOnly`；仅对子进程隔离父Git并移除Git工具，记录实际工具、命令、退出、新runId和安全环境，本轮控制记录写 `bootstrap/target/candidate-backend/67942981/`，保留任何失败/受阻，不运行Frontend或完整All（FR-025、SC-015）。实际run `9624289c-394e-475e-ac75-143ea0a08fd0`已退出1/FAIL，勾选仅表示真实执行结束，不代表验收通过。
- [x] T067 [US5] 在 `bootstrap/target/candidate-backend/67942981/` 完成本轮失败归因与162份已产出XML的部分报告/计数核对：`RunbookOfflineEvaluationTest`两个参数例因 `rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookEvaluationReport.java` 强制启动Git进程报错，agent/bootstrap后续目标未执行。原计划完整XML/必需目标、Spec L proof、样本断言及制品全面验收未通过、未执行部分不补造；勾选仅关闭失败核查，余项由阶段17新候选重新验证（FR-026、SC-015）。
- [x] T068 在本目录 `candidate-backend-report.md` 及 `docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、测试/交付/发布入口同步真实结果、失败或受阻与适用边界；以本轮证据目录收尾记录核对原库仅必要说明变化、Git/旧840保护输入及既有Docker/私有资源保持、测试临时资源状态，保留异机/平台/旧TBD/安全历史整合与正式发布未完成事实，按证据关闭任务，不提交或发布（SC-016）。

顺序T065→T066→T067→T068。只追加本轮说明，不回填ebf冻结源码、旧完成记录或阶段15结果；若Backend失败，保留独立失败身份并先诊断，不用旧All或已通过Frontend代替。

阶段16已实际得到FAIL而非完整Backend通过。840旧保护输入、固定ebf及原源码起止保持，既有16容器/94卷保持；162XML只能证明已执行部分，T067不关闭FR-026/SC-015成功门槛。阶段16原禁代码/禁新候选约束保留为历史执行范围，T068仍待父层说明与收尾。

## 阶段17：两类评测报告的无Git来源兼容修复

延续US5及已授权无Git可靠验收，修复真实FAIL暴露的测试报告来源缺陷；不增加产品设计或架构。证据根`bootstrap/target/gitless-backend-fix/aa898811/`，runId `aa898811-88aa-4639-b24e-365bf1994b07`。只修改两个测试reporter及两个JUnit类，普通Git行为保持，不修改门禁、业务检索/诊断、阈值、数据集或受控跳过；新候选、新树和新运行独立验收，旧ebf/9624/失败树/历史结果保持。

**独立验收**：两个reporter的门禁三字段严格校验、无Git来源及非门禁真实Git兼容均有正反检查和聚焦实跑；新候选完整树的真实Backend、评测新产物、Spec L证明及保护收尾按实际结果分别核对，不预设新身份/文件数/XML或测试总数。

- [x] T069 核对并固定本阶段原1909源码/说明路径、Git、1200旧保护项及既有私有/运行资源基线，在 `bootstrap/target/gitless-backend-fix/aa898811/` 保存新控制记录，并在本目录 `spec.md`、`plan.md`、`tasks.md` 追加FR-027/028、SC-017/018和阶段16真实FAIL处置，不改旧执行范围、输入或checklist。
- [x] T070 [US5] 在 `rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookEvaluationReport.java` 与 `agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvaluationReport.java` 实现仅门禁分支使用严格校验的lab三字段、评测UUID与门禁来源分列及`gitHead/gitDirty=null`，保留普通Git查询与失败语义；在同目录分别新增 `RunbookEvaluationReportTest.java`、`DiagnosisEvaluationReportTest.java` 覆盖有效/缺失/非法UUID、场景和摘要、来源选择及属性恢复，不改产品或评测门槛（FR-027、SC-017）。
- [x] T071 [US5] 对上述两个JUnit类及原 `rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookOfflineEvaluationTest.java`、`agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisOfflineEvaluationTest.java` 执行含Git与真实隔离父Git/移除Git工具的聚焦测试，在 `bootstrap/target/gitless-backend-fix/aa898811/` 记录实际命令、退出、来源和评测新产物；核对非门禁真实Git兼容，非法门禁来源不回退Git，不以替身替代实际评测（FR-027/028、SC-017）。
- [x] T072 [US5] 从稳定修正源码用 `scripts/new-release-candidate.ps1` 创建新ID四文件候选并Verify，在本阶段新空目录完整解包后，通过候选 `scripts/check-delivery.ps1 -CandidatePath <新候选目录> -Gate Backend` 执行真实无GitBackend；记录新runId、完整清单/四摘要/受检子集、工具和缓存前提，不带CheckOnly、不重跑Frontend或完整All、不覆盖原ebf/失败树，不预设本轮身份或数量（FR-028、SC-018）。
- [x] T073 在 `bootstrap/target/gitless-backend-fix/aa898811/` 独立审计本轮新XML/必需目标/受控跳过、Spec L proof、两类评测新manifest/summary/cases与模块外置生成报告、完整来源/safe/制品及Git/1200旧输入/资源保护；在本目录 `candidate-backend-report.md` 及项目/测试/交付说明分列阶段16失败与新候选实际结果，按证据关闭任务，保留异机/平台/旧TBD/安全历史整合及正式发布边界，不将部分结果或旧All转归新输入（FR-028、SC-018）。

顺序T069→T070→T071→T072→T073。T070两模块的reporter/JUnit可由不同执行者按相同来源契约并行实施；T071分别记录聚焦来源和实际结果，全部适用检查满足后才固定新候选。阶段16T068可独立整理原失败文档，但不得因此覆盖9624失败或声称阶段17尚未发生的Backend通过。

2026-10-19额度恢复收尾：新候选b66759af的Backend fdbac063已自行完成PASS/0（201XML，952项/931通过/21受控跳过/0失败错误）。续跑只补独立可靠性/评测审计与综合保护收尾，未重跑Backend。T068关闭的是原9624真实FAIL及保护记录，不改SC-015原失败；T072/073依据新候选新证据关闭，阶段17为5/5、Spec M73/73仅限当前本地任务。异机/承诺平台、完整All、Git整合发布和原TBD继续独立开放。

## 阶段18：固定b667候选真实无Git完整All

2026-10-19用户批准下一步，在新的空解包目录执行同一固定`b66759af-6cfb-4cb6-8db5-65276702f443`候选的真实All，不使用CheckOnly，不重建候选或修改业务/门禁。证据根为`bootstrap/target/gitless-all/a392f45a/`，验收记录ID为`a392f45a-8dde-4647-a184-8b8c3f03b5c9`，新解包目录为该根`s/`，门禁另有新runId。阶段17Backend及阶段15旧候选Frontend记录不拼接为本轮整体验收，旧FAIL与PASS均保持原身份。

**US6目标与独立验收**：同一新run真实完成Backend及Frontend四阶段，完整来源、XML/专项证明、前端命令、安全摘要、制品与保护检查独立核对；成功必须最终PASS/0且所有适用检查满足，数量由新结果获得。本机缓存、Windows及隔离安全模拟边界公开，异机/承诺平台和正式发布继续独立开放。

- [x] T074 [US6] 固定本轮原库/Git/源码及旧候选、失败与成功证据、私有配置和Docker资源基线，保存到 `bootstrap/target/gitless-all/a392f45a/` 本轮证据根；Verify `bootstrap/target/release-candidates/b66759af-6cfb-4cb6-8db5-65276702f443/` 四文件，向新空目录`s/`完整解包并核对1905源码成员与四摘要，在本目录 `spec.md`、`plan.md`、`tasks.md` 追加FR-029/030、SC-019/020及阶段18范围，不回填冻结候选（FR-029、SC-019/020）。
- [x] T075 [US6] 从新解包目录运行冻结候选 `scripts/check-delivery.ps1 -CandidatePath <固定b667候选目录> -Gate All` 的真实无父Git/无Git工具子进程，不带CheckOnly或跳过测试；在本轮新证据根记录独立runId、工具/环境/缓存、完整来源及Backend、Install、IncidentView、Lint、Build实际命令/时间/退出，保留所有失败或受阻，不覆盖旧树或重建候选（FR-029、SC-019）。 实际本轮`7d1c8f40`在npm前提检查返回`BLOCKED/2`，0阶段/0XML；勾选仅表示尝试已终止且阻断证据已保留，不表示五阶段执行或SC-019通过。
- [x] T076 [US6] 核对原固定b667的All `7d1c8f40`为BLOCKED/2、0阶段/0XML，核查原run/source/safe/制品与来源、保护及未执行范围；原定新XML、可靠性/评测和前端完整成功审计因未执行而以失败终止，SC-019仍未满足。勾选仅表示原阻断核查结束，新候选完整成功审计由T081接续，不把旧Backend/Frontend/All补为本轮成功（FR-030、SC-019）。
- [x] T077 [US6] 在本目录 `gitless-all-report.md`、`docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、测试/交付/发布入口同步同一候选真实All的实际结果与限制，以本轮综合proof/completion绑定Git、旧保护输入、私有/运行资源、测试自有资源状态和最终说明；按实际证据收尾任务，保留旧FAIL/PASS、异机/承诺平台/旧TBD/安全历史整合及Git发布未完成事实，不提交、Tag、推送、合并或部署（FR-030、SC-020）。

顺序T074→T075→T076→T077。保护清单及审计材料可提前只读准备；T075必须使用新解包目录且T076只能核对该新run的实际结果。四项均未执行完成前不得预勾选；本阶段完成不等于异机、生产或正式发布通过。

2026-10-19阶段18阻断收尾：T074基线/解包完成；T075仅真实尝试终止（7d1c8f40，BLOCKED/2，0阶段/0XML），SC-019未满足。T076完整结果审计未能开始，继续开放；T077仅整理阻断原因和保护记录，综合状态为GITLESS_ALL_BLOCKED，不是All通过。Spec M当前76/77、阶段18为3/4；最小Windows命令执行器修复和新候选复验待用户确认，未修改根门禁或固定b667。异机/平台/旧TBD/安全历史整合与正式发布保持独立边界。

## 阶段19：用户批准的Windows命令入口修复与新候选完整All

2026-10-19用户对阶段18阻断后的最小修复与新候选复验明确回复“继续”。证据根`bootstrap/target/cmd-dispatch-fix/9dd05c94/`，验收记录ID`9dd05c94-e89d-450e-8988-3f2458675d6d`，新候选与新All runId为TBD；不预设文件/XML/测试数量或成功。原b667及旧树、7d1c BLOCKED、fdbac063 Backend PASS、旧FAIL与完成记录保持，不提交、Tag、推送、合并或部署。

**US7目标与独立验收**：修正Windows命令文件在不同工作目录的真实工具路径定位，保留元字符检查与退出/超时语义；真实跨目录及含空格路径、已安装npm与适用旧合同通过后，封存新候选，在全新`s/`无父Git/无Git工具环境真实执行All，并以同一run的新证据完成独立审计及保护收尾。

- [x] T078 固定原1912条源码/说明路径、Git身份/暂存/refs/worktrees、1906项旧候选/失败/成功证据和私有输入及Docker资源基线，在`bootstrap/target/cmd-dispatch-fix/9dd05c94/`保存本轮控制记录；在本目录`spec.md`、`plan.md`和`tasks.md`追加US7、FR-031/032、SC-021/022和阶段19范围，不改旧结果或旧T076勾选（FR-032、SC-022）。
- [x] T079 [US7] 仅在`scripts/check-delivery.ps1`修复Windows`.cmd`中央执行器的真实完整路径定位，保留原路径/参数元字符拒绝、退出码、超时和其他调用分支；在`scripts/test-check-delivery.ps1`增加真实跨目录/含空格工具路径、脚本自身目录和参数/退出结果及危险输入回归，实际检查已安装`npm.cmd --version`并运行门禁及候选适用合同，在本轮证据根记录实际命令/结果，不改业务、门槛、数据集、skip、依赖或全局Node/npm配置（FR-031、SC-021）。
- [x] T080 [US7] 从稳定修正源码通过`scripts/new-release-candidate.ps1`生成新ID四文件候选并Verify，在`bootstrap/target/cmd-dispatch-fix/9dd05c94/s/`新空目录完整解包；执行候选`scripts/check-delivery.ps1 -CandidatePath <新候选目录> -Gate All`的真实无父Git/无Git工具子进程，不带CheckOnly或跳过测试，保存新run、实际五阶段命令/时间/退出、完整候选/源码及受检子集起止身份、工具/缓存边界和失败或受阻，不覆盖旧候选/旧树（FR-032、SC-022）。
- [x] T081 [US7] 在本轮新证据根独立审计新All的run/stages/reports/proof/source/safe/artifacts及两类评测外置产物，核对新XML三字段/时间/计数、必需目标/受控跳过、可靠性与评测身份/分母/既有断言、前端四阶段、完整来源起止、安全摘要和制品；承接新候选完整成功审计，不将阶段18 T076阻断核查或旧Backend/Frontend/All转成新成功（FR-032、SC-022）。
- [x] T082 在本目录`cmd-dispatch-fix-report.md`及`docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、测试/交付/发布入口同步修复、真实回归及新All实际结果；以本轮综合proof/completion核对Git、1906项旧输入、既有资源和最终说明，明确旧T076仅按原阻断事实终止且SC-019未满足，按实际证据关闭任务，保留异机/承诺平台/旧TBD/安全历史整合与Git发布边界，不回填冻结ZIP（FR-032、SC-022）。

顺序T078→T079→T080→T081→T082。两脚本的实现和相关回归必须协同，不能标为相互无依赖并行；审计材料可只读提前准备，但完整审计必须等待新run真实结束。阶段创建时五项均未勾，不得预勾；当前完成项以上方任务及实际记录为准；阶段创建时旧T076保持未勾，现按阻断核查失败终止关闭，其原固定b667完整成功目标未满足，新候选成功要求由T081接续，不宣称旧All通过。

2026-10-19阶段19执行身份补充：新候选c1687735-1ad9-4881-a620-81702d39d1ab已Verify，1907完整文件；本轮1365受检文件摘要74BEB5839C44BC6B15477DE5400CF740F638800FA423FBC0D26238CA833531D4。真实无GitAll d98d8861-aa83-4168-82f5-c84e2b62022b已通过六项工具前提并处于RUNNING。176门禁合同及实际npm检查在封存前通过；49项候选合同在All启动后追加核对并通过，候选工具未修改且来源摘要另行绑定，不冒称启动前执行。后补说明不回填冻结ZIP，最终结果待本轮审计。

### 阶段19实际执行补记（2026-10-19）

上文TBD、进行中和未完成表述属于创建时状态。实际新候选`c1687735-1ad9-4881-a620-81702d39d1ab`（1907文件）、真实无GitAll `d98d8861-aa83-4168-82f5-c84e2b62022b`为PASS/0，Backend、Install、IncidentView、Lint、Build五阶段退出0；201 XML、952项/931通过/21受控外部跳过/0失败错误。受检1365文件摘要`74BEB5839C44BC6B15477DE5400CF740F638800FA423FBC0D26238CA833531D4`，独立可靠性/评测审计PASS/VERIFIED/COMPLETE；完整来源与1906项旧输入和资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED、fdbac063 Backend PASS及其他旧FAIL/PASS保持；T076仅以原0阶段/0XML的阻断核查失败终止，SC-019仍未满足，新候选完整成功审计由T081承接。82/82表示任务已关闭，包含失败终止，并非82项验收成功。后补说明不回填冻结ZIP；异机/承诺平台/托管CI、旧TBD、安全历史整合和正式Git发布继续开放。

## 阶段20：用户批准的正式整合前最终审查

验收记录ID`68e1acf2-a2a3-45d0-9669-b86483cb3aee`，证据根`bootstrap/target/integration-review/68e1acf2/`；开始1913完整路径、537逐文件待审（53修改/484未跟踪）、原分支/HEAD和空暂存。仅只读核验并整理说明，不新增行为，不提交、复制源码到历史副本、切换分支/工作树、生成候选、回填旧ZIP或运行测试/服务。历史阶段和T076失败终止事实不改。

**US8目标与独立验收**：逐文件看清当前完整实现、净化历史附件及c168/d98既有来源之间的关系，生成实际待审集合清单和可授权的后续建议；来源/差异/排除/许可与保护独立复核，承载库/分支仍为TBD，不把审查完成说成正式整合或新测试通过。

- [x] T083 固定当前1913完整源码/说明路径、537项逐文件待审状态/长度/内容摘要及Git分支/HEAD/暂存/refs/worktrees，在`bootstrap/target/integration-review/68e1acf2/`保存基线；在本目录`spec.md`、`plan.md`、`tasks.md`追加US8、FR-033/034、SC-023/024和阶段20，不改旧任务/失败/成功事实（FR-033、SC-023/024）。
- [x] T084 [US8] 在本轮证据根汇总净化历史附件与声明tip/原历史保留的只读核验，以及当前完整工作区对固定c168四文件和d98 All既有来源的逐文件比较，分列新增/修改/删除、原排除项与后补说明，明确当前新增实现尚未正式整合、旧历史副本不等于当前源码；分别执行者可并行核对两类输入，但不复制覆盖、不运行测试/服务或生成候选（FR-033/034、SC-023）。
- [x] T085 [US8] 按本轮最终实际逐文件待审集合更新整合清单，精确保存路径/Git状态/长度/SHA256/唯一G01～G05组，沿用已有排除及可识别敏感模式规则，核对集合无遗漏/重复；更新`docs/releases/v0.1.0-integration-plan.md`和`docs/releases/v0.1.0-release-preparation.md`的当前输入、依赖、许可/贡献及证据范围，保留承载仓库/分支TBD，仅建议净化衍生历史和后续分别授权顺序，不执行Git或源码迁移（FR-034、SC-023/024）。
- [x] T086 在本轮证据根独立复核最终清单、完整来源差异、历史附件结论、说明链接及Git/旧候选/证据与私有/运行资源保护；新增本目录`integration-review-report.md`并同步`docs/PROJECT_CONTEXT.md`、`docs/TODO.md`和交付/发布入口，以综合proof/completion记录实际结果和范围，按事实关闭任务，不把旧All转归当前后补说明或称正式整合/提交/发布完成（FR-033/034、SC-024）。

顺序T083→T084→T085→T086。T084历史附件与源码候选两侧可分别并行只读核验；清单必须在说明稳定后重新枚举，T086据最终实际输入独立复核。阶段创建时四项均未勾选；本轮基线、两项只读审计、逐文件清单/说明及独立复核已完成，当前完成状态以上方标记和本轮综合proof/completion为准。旧82项为原时点已关闭，其中T076失败终止，并非全部成功。

## 阶段21：独立承载库的完整源码与提交前冻结

记录ID`498a7289-80dc-4a8b-916b-3c97229e1c23`，外置逻辑路径`bootstrap/target/release-integration/498a7289/`由原开发库保存，不随新源码携带。用户已选择独立净化衍生历史承载和`codex/release-v0.1.0`，授权检查通过后创建一个本地整合提交；不包括push、Tag、托管CI触发、发布或部署。阶段20原carrier TBD及历史任务/报告保留。

**US9目标与独立验收**：1908完整源码路径与阶段20来源对应；7份必要说明及34文件格式修复（TODO交集1，合计40差异）分列，原6开发者配置排除，许可和执行逻辑保持，提交前独立审查及最终清单冻结。任务只覆盖提交前可证明动作，实际Git收尾和commit/tree SHA另由外置回执与日志证明，不在同一提交中勾选自己的创建结果。

- [x] T087 固定独立承载库的净化父历史`437e275d8f4443c4b1b86b05321de1496594a987`、`codex/release-v0.1.0`分支、恢复来源及本地一次提交授权，核对阶段20完整1908源码清单、原库/旧证据保护及外置记录身份；在本目录`spec.md`、`plan.md`、`tasks.md`记录US9、FR-035/036、SC-025/026，不改历史结论（FR-035、SC-025）。
- [x] T088 [US9] 核对独立承载库完整1908源码的路径/长度/字节来源与阶段20对应、原6开发者配置排除；分列7份必要说明及34文件格式修复（TODO交集1、合计40差异），核对format-review/format-parent-check/format-applied逐文件before/after、13份Java的14处纯空白行与21份Markdown去position AST等价，明确Java原字节已异于旧c168/d98/raw摘要，不把格式等价写成新All或编译；其余来源字节、执行逻辑、数据集、许可/署名及原开发库保持（FR-035/036、SC-025）。
- [x] T089 [US9] 仅在`docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、`docs/releases/v0.1.0-integration-plan.md`、`docs/releases/v0.1.0-release-preparation.md`和本Spec三文件追加/置顶提交前冻结说明，明确已选承载/分支及本地一次提交授权、旧carrier TBD历史时点、外置回执与验收归属，不新建跟踪路径、不预写提交成功或SHA，不把旧All/JAR/浏览器视作本次新验收（FR-035/036、SC-025/026）。
- [x] T090 独立复核最终1908路径/字节来源、40文件的说明/格式分类及交集、相对链接、Java空白/Markdown AST等价与工作区及最终暂存树实际Git格式检查，核对许可/版本/排除和窄秘密规则、父历史/分支/授权，冻结完整清单和审阅结果于外置记录；按真实证据关闭提交前任务，旧All仅原raw身份，本轮无新All/编译/JAR/浏览器，不为回填实际SHA或任务状态在提交后再修改源树（FR-036、SC-026）。

顺序T087→T088→T089→T090。当前创建时四项均未勾选；旧86项为原时点已关闭，包含T076失败终止，并非全部验收成功。实际本地提交属于已明确授权的Git收尾，检查通过后至多执行一次，其结果及SHA以外置回执和本库日志为准；任务文件冻结于提交前，不要求记录自己所在提交的创建结果。push、Tag、托管CI、发布、部署和新测试/服务均不在本轮授权内。

## 2026-10-20阶段21格式范围补记

提交前Git格式门禁发现46处问题，本阶段实际范围补充为7份必要承载/业务说明更新及34文件格式等价修复；TODO两类交集1份，合计40文件相对阶段20不同，源码路径仍1908。格式修复本身涉及13份Java的14处纯空白行空格及21份Markdown的硬换行/EOF：Java执行逻辑未改，Markdown格式转换去除position后的AST相同，逐文件before/after与独立复核一致。

13份Java的原始字节已经变化，不能继续宣称当前完整字节或受检摘要与旧c168/d98完全一致。旧All仍只归属原候选/run/raw摘要；本轮没有新跑All、编译、JAR、浏览器或服务。新增证据是逐字节差异、Java空白与Markdown AST等价检查及实际Git格式检查，最终工作区/暂存树检查以外置回执的真实结果为准。格式证据沿用外置记录`498a7289-80dc-4a8b-916b-3c97229e1c23`中的format-review、format-parent-check和format-applied，不创建跨库文件链接；以下2026-10-19“仅7说明/原字节一致”口径仅为补充前时点，本补记及修订FR/SC为本阶段最新验收范围。
