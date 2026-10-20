# 源码候选无GitBackend验收记录

日期：2026-10-19。阶段16为**FAIL**；阶段17两种聚焦均27/27，新候选Backend及独立可靠性/评测审计均**PASS**。综合保护收尾以本轮`closeout-proof.json`与`completion.json`为准。对应 [规格](spec.md)、[计划](plan.md) 和 [任务](tasks.md)。两轮候选及运行分别保留，不回填失败。

## 验收目的及阶段16原始范围

上一轮All `CheckOnly`只确认工具前提，本轮实际运行固定源码包的Backend，以新测试报告检验告警幂等、审批单次模拟处置和故障恢复。结果必须绑定具体候选、实际源码与本次runId，不能用旧070的All或已通过的Frontend替代。

阶段16保持原分支`feature/incident-platform`及HEAD `67624931f20812922568d3641a095adb291263ba`；HEAD仅为历史基线，新增实现仍未提交。该阶段不改业务/门禁代码、不重建或回填候选、不重跑Frontend或完整All、不启动旧持久实例，不执行提交、Tag、推送、合并或部署。真实FAIL暴露的测试报告问题在阶段17另作最小修复、新候选和独立运行，范围与身份见后文。

## 阶段16原始输入与身份

| 项目 | 本轮实际输入 |
| --- | --- |
| 候选 | `ebf476ea-f17f-4a15-a8d4-365bcb8e1ad1`，完整1901源码文件 |
| 既有解包根 | `bootstrap/target/gitless-delivery/a83506bb/attempt2/s/` |
| 本轮证据根 | `bootstrap/target/candidate-backend/67942981/` |
| 门禁runId | `9624289c-394e-475e-ac75-143ea0a08fd0` |
| 受检子集 | 1363文件，`sourceDigest=DA82D327EF4C718C7A7CC924440FB826D0FF1EE3667B3AD61506314B3D5A9133` |
| 来源格式 | `schemaVersion=2`、`head=null`；`sourceCandidate`八项身份字段与完整清单独立核对 |
| 原库基线 | 1908条源码/说明路径；840项旧证据及私有文件仅保存摘要，不输出秘密 |

候选四文件保持冻结，起点及结束Verify和完整实际源码核对均通过：

| 文件 | SHA256 |
| --- | --- |
| source.zip | `68190063E2854629F04B3D98A6DFCD637A621B5D6CECB6A4C2C45FA68ADED681` |
| source-manifest.json | `AA809DEF5AC9A7EABF0A0F5736A68B7355A80887D0F5125BA8706115A3833503` |
| candidate.json | `EF631B1C34D34FEBFDF7D8A807677166FE8C02CB068AC3E19F83D3AA5E31C1BA` |
| SHA256SUMS | `136AA50E8A09FC178FBC93655E296CF2E79B602E4A9369D0754D25AEED1D1DE5` |

## 阶段16实际执行

在实际子进程中将Git发现上限设为解包根的父目录，Git发现退出128；移除Git工具所在PATH后`Get-Command git`不可用，没有初始化Git。调用冻结解包树的原脚本，未使用`CheckOnly`：

```powershell
# 仓库根目录中实际执行的本轮控制脚本；内部解析固定候选、隔离Git后调用原门禁。
./bootstrap/target/candidate-backend/67942981/run-backend.ps1
```

唯一Backend阶段执行原Maven Wrapper的`test`，携带本轮`lab.runId`、`DELIVERY-BACKEND`、`sourceDigest`和新reports/proof目录，不使用`skipTests`。门禁关闭`RAG_LIVE_TESTS`并移除该子进程的`YDC_API_KEY`，使用本机隔离测试资源，不替换成共享或远程数据库。宿主Windows，Java21.0.12、Maven3.9.11、Linux Docker Engine29.4.3；沿用本机Maven缓存和已有镜像，Maven命令未加`-o`，不称无缓存、异机或Linux宿主复现。

## 阶段16失败结果与归因

门禁和唯一Backend阶段均退出1，`FAIL/GATE_FAILED`，阶段原因`COMMAND_FAILED`。本轮实际162份XML、644项：622通过、20受控外部跳过、0断言失败、2错误。两错误均来自`RunbookOfflineEvaluationTest.actualLexicalRecallFiltersAndByteIntegrity`的v1/v2参数例：`RunbookEvaluationReport.write`强制启动Git，在无Git工具条件下抛出`IOException`。这是报告生成器的隐含依赖，不能据此判定检索断言通过；Maven在rag停止，agent/bootstrap未执行，诊断/审批/运行恢复必需类缺失。没有完整可靠性runtime证明或400样本结论，不运行只接受PASS的审计器来伪造通过。

同类只读排查还发现`DiagnosisEvaluationReport`存在相同Git依赖，必须在新候选中一并修复后再运行，不能仅修首个异常后盲目重跑。沿用已确认的来源方案：门禁内严格验证`lab.runId/scenarioId/sourceDigest`并记录它们，`gitHead/gitDirty`显式为空；普通非门禁仍读取真实Git，不静默降级或跳过业务测试。不改数据集、检索/抽取规则、阈值、业务代码或原冻结候选。

规则proof只保存案例身份及聚合计数，逐告警anchor由同源测试断言和新XML支撑，不声称独立SQL重查了每个anchor。可靠性runtime中的零模拟应用与审批正向黄金路径分别验收。仅重算已保存逐样本的HTTP接收百分位，观察完成时延包含100ms轮询误差，不能称生产吞吐上限或SLO。

## 阶段16保护结果

本次控制器已完成结束核对：完整1901来源/候选四文件、840保护输入保持；既有16容器的选定状态及94卷名称集合前后相同。`receipt.json`、`source-after.json`、`resources-after.json`和`execution-proof.json`保留真实FAIL。Git/最终说明和全部制品的综合收尾随阶段17另存，不运行原先只接受PASS的`closeout.ps1`，也不生成阶段16成功completion。原始日志和完整XML/proof保持本机私有，安全摘要仅按白名单输出；不声称全部raw日志已完成秘密扫描。

阶段17通过后也只代表修正候选的本机隔离Backend验收。新的完整All、实际发布JAR持久业务、第二机器、Linux宿主/Node22/Java17、托管CI、真实模型/Broker及生产仍不能据此判为通过。旧两项TBD、安全历史整合与正式Git发布继续独立处理。上游`nageoffer/ragent`署名、Apache-2.0许可证及历史边界保留。

## 阶段17修复与聚焦回归

只修改 [Runbook测试报告器](../../rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookEvaluationReport.java) 和 [Diagnosis测试报告器](../../agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvaluationReport.java)，分别增加 [来源合同测试](../../rag/src/test/java/com/nageoffer/ai/ragent/rag/runbook/RunbookEvaluationReportTest.java) 与 [Diagnosis来源合同测试](../../agent/src/test/java/com/nageoffer/ai/ragent/agent/diagnosis/DiagnosisEvaluationReportTest.java)。门禁中使用现有lab三字段并校验规范UUID、固定场景和64位大写摘要；无效来源报错，不启用Git兜底。非门禁保留真实Git行为。数据集、检索/抽取规则、阈值、受控跳过及所有业务源码不改。

为保护开发树旧输出，以交付白名单复制1905文件到本轮新`focused-source/`。普通Git兼容测试明确使用开发父仓库的真实Git信息，不能当作候选独立Git证明；之后同副本限制父Git发现至退出128并移除Git工具，使用门禁身份完成真实评测。每次新XML和独立UUID目录都已分别留存。

| 聚焦 | runId | 实际结果 |
| --- | --- | --- |
| 普通Git兼容 | `cf73b276-e8d5-4cb0-857e-b9edf49f1380` | 4XML，27/27通过，失败/错误/跳过均0；四组评测，`sourceKind=GIT`，真实HEAD及dirty=true |
| 无Git | `ce6bb092-910a-4dbe-8402-49b642d43965` | 4XML，27/27通过，失败/错误/跳过均0；四组评测，`sourceKind=DELIVERY_GATE`，`gitHead/gitDirty=null` |

上述27项包括22项新增来源校验及5项原评测/门槛测试，不把两次重复执行相加成独立案例数。两次受检子集均1365文件、摘要`2263934E0E57656CB6B1BB851391D56C5B0CE60E9F1DC597C36E44D841FCB6BA`；`focused-proof.json`已独立核对run/scenario/source、XML分母、评测来源和文件哈希。

聚焦辅助器的输出根检查和`-D`大小写问题都在测试启动前暴露，原空目录记录/usage失败日志保留；修正后另用`focused-git-attempt2`，不覆盖首轮。只读复核器首轮UTC/本地时间比较误报STALE，统一DateTimeOffset后通过，测试原文件未改。

## 阶段17新候选与真实Backend

证据根`bootstrap/target/gitless-backend-fix/aa898811/`，基线1909源码/说明路径、1200旧保护输入，包含原840项、阶段16控制记录及9624全部失败制品。以下新候选是在聚焦通过后从稳定源码生成并Verify，全新解包到本轮`s/`，没有复制旧构建或聚焦target。

| 项目 | 新身份 |
| --- | --- |
| 候选 | `b66759af-6cfb-4cb6-8db5-65276702f443`，1905文件 |
| source.zip | `18C919F1A26E29E4F3FB5461A26FA346F80AD92546EBDB0A9981E3F565D668D9` |
| source-manifest.json | `66F98BCA81BC35C435814DB6B1AE1FDF982925A1729829F68AC6F9A2160C7E61` |
| candidate.json | `235B21BA8B56DB9F60D9DE09DDCAB7957B8E7CD893A1AB4073A116D068431AA3` |
| SHA256SUMS | `D4A1D5B42AD164962B29F90EA38EB3EFCF36852DE639F3F32F7585BC83907548` |
| Backend runId | `fdbac063-5fac-464d-8472-e2795e8b29c7` |
| 受检子集 | 与聚焦相同的1365文件及`2263934E…FCB6BA`摘要，完整清单另行核对 |

实际控制命令为本轮`run-backend.ps1`，调用冻结`s/scripts/check-delivery.ps1 -Gate Backend -CandidatePath <本轮新候选目录>`，没有`CheckOnly`。实际进程中无父Git/无Git工具，唯一Maven `test`阶段及新reports/proof目录；Windows/Java21/Maven3.9.11/本机Linux Docker Engine与已有缓存前提保持。源码包不含后补最终报告，元数据仍明确历史暂扣，不能检出旧HEAD获得新增实现。

<!-- BACKEND_RESULT_BEGIN -->
状态：**PASS / 0 / VALIDATED**，唯一Backend阶段退出0。本轮201份新XML，952项：931通过、21受控跳过、0失败、0错误；14个必需类实际执行且零跳过，21跳过属于原13类手动外部门关闭目录，不称真实模型或外部服务已经验证。新增22项来源校验计入952，不与两次聚焦重复相加。

实际Backend命令窗口为UTC `2026-10-19T08:31:14.9626351Z` 至 `09:10:54.9169007Z`；门禁完成于`09:11:17.0259556Z`。这是本机执行记录，不作为性能对比。工具实测PowerShell7.6.5、Java21.0.12、Maven3.9.11、Docker29.4.3；Maven使用已有缓存且未加`-o`。

恢复额度时磁盘已有完整PASS的`receipt.json`、`resources-after.json`、`source-after.json`及`execution-proof.json`，没有尚在运行的Java测试进程，故没有重复Backend。本次续跑实际执行`audit-reliability.ps1`并得到`RELIABILITY_AUDIT_PASS`，其输出`reliability-audit.json`为`PASS/VERIFIED/COMPLETE`，SHA256为`BBBA5822BA30F76D39A06A7D6EE15A3F5A85014BDFA3BABA3EF3F8BCF9B0D6F0`。原控制器保存空列表时的辅助失败及其修正记录保留，发生在本次门禁启动之前。

| 独立核对项目 | 本轮事实与分母 |
| --- | --- |
| 固定规则 | 15固定案例×3顺序=45组；114输入中99处理、15声明失败成员符合fixture，未完成0。逐告警anchor由同源测试断言/新XML支撑，不冒称收尾重查临时数据库 |
| HTTP幂等 | 40次请求均202，1次创建、39次重复，持久身份唯一；另1个冲突被拒绝 |
| 进程恢复 | 受理后结束工作进程，新PID恢复处理1条、重投0；测试classpath跨JVM |
| 数据库故障 | 自有数据库实际暂停/恢复，故障期间503；既受理积压1条恢复、重投0，重试到期时间为受控fixture |
| 本机告警处理基线 | 四组共400个独立样本身份，失败0、未受理/未完成0；不由该样本推导生产SLO或容量上限 |
| 两类离线评测 | Runbook实际30/34例，Diagnosis实际30/34例另各1固定篡改响应；四个评测UUID与本门禁runId分列，来源三字段一致、Git字段null，16项模块外置产物另外逐文件摘要绑定 |
| 审批与重启 | 黄金/安全/观测/隔离启动共13例无失败错误跳过；真实HTTP断言待审批/拒绝0应用、批准1应用、重放/重启仍1。该正向事实不借用可靠性runtime中的0应用 |

源码完整1905清单及候选四文件起止保持，1365受检子集与聚焦相同。控制器已核对1200保护输入及既有16容器/94卷选定状态保持；最终综合收尾另重读XML、全部门禁制品、白名单safe、聚焦副本和评测外置产物，并核对Git及说明链接。新旧候选间新增4文件、修改16文件、删除0，前端204文件逐字节相同，静态一致不升级为本轮Frontend执行通过。

最终证明写入`bootstrap/target/gitless-backend-fix/aa898811/closeout-proof.json`及`completion.json`，状态仅`GITLESS_BACKEND_FIX_VERIFIED`；T068以原FAIL保护收尾，T072/073以新候选实际结果关闭，Spec M任务73/73只表示当前已定义本地任务收尾。原始日志/XML/proof及私有配置保持本机，公开仍仅使用门禁safe白名单摘要。

最终收尾首轮因辅助器遗漏门禁目录外的`input.json`与`source-before.json`两项摘要而报`FIX_AUDIT_DIGEST_UNBOUND`，当时没有写出综合proof/completion，错误记录保存在`closeout-attempt1.json`。两项真实SHA与既有审计完全一致；修正为四项关键角色逐文件绑定，保留全部摘要成员校验后重新收尾。未修改测试或审计原始产物，未重跑Backend。
<!-- BACKEND_RESULT_END -->

当前未重跑Frontend或完整All，原Frontend PASS仍绑定ebf；原070完整All及真实JAR持久业务继续保持其身份。本轮Backend的跨进程测试使用测试classpath，不等于本轮发布JAR故障恢复。异机/承诺平台、托管CI、真实模型/Broker、生产、旧两项TBD、安全历史整合及Git发布仍分别开放。没有提交、Tag、推送、合并或部署。
