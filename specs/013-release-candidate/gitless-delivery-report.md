# 无Git源码候选门禁修复与本机验证

## 2026-10-19 Docker启动后的T062补跑

最新状态：`T062_ALL_PREFLIGHT_CHECKED`。用户启动Docker后，同一固定候选的真实无GitAll前提检查返回`CHECKED/0`，T062关闭；阶段15为7/7，Spec M累计64/64。这只关闭无Git门禁的前提检查缺口，不代表新的Backend/完整All、业务或异机验收。

输入仍为`ebf476ea-f17f-4a15-a8d4-365bcb8e1ad1`的1901文件，以及上轮已完整核对的`attempt2/s/`。本轮复用原解包树，未重新解包、构建或修改源码/候选；不称新的干净环境复现。原源码四文件身份和1363项sourceDigest保持，摘要仍为`DA82D327EF4C718C7A7CC924440FB826D0FF1EE3667B3AD61506314B3D5A9133`。

证据根：`bootstrap/target/gitless-preflight-followup/f4721f5c/`。子进程将Git发现上限设置为实际源码父目录，绝对Git探针退出128，再从PATH移除Git工具；没有git init/commit。实际执行冻结源码中的`check-delivery.ps1 -Gate All -CheckOnly -CandidatePath <固定候选目录>`，新run为`f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`，原因`PRECONDITIONS_CHECKED`、0 stages、schema2、head=null。

| 工具 | 本轮预检实际版本 |
| --- | --- |
| PowerShell | 7.6.5 |
| Java | 21.0.12，另检查keytool存在 |
| Maven Wrapper | 3.9.11 |
| Docker Linux Engine | 29.4.3，本机npipe端点 |
| Node / npm | 20.19.5 / 10.8.2 |

宿主仍为Windows，Docker的Linux引擎可用不等于Linux宿主交付通过。预检前后完整1901源码/候选四摘要一致，827个去重保护文件逐项哈希保持；新门禁10项artifacts和safe/source摘要由本轮收尾独立核对。原`a83506bb`完成证明、旧BLOCKED/FAIL和六次旧run原样保留，既有容器/卷以**用户启动Docker之后**采集的16容器/94卷为本轮基线，本次预检窗口集合及所选容器状态保持；不与上轮UNAVAILABLE比较，也不改变上轮记录。

本轮仅更新当前状态文档与T062标记，原1908源码路径和532项待审状态保持，代码/版本/许可/Git身份不变，无提交、Tag、推送、合并或部署。最终核对存本轮`closeout-proof.json`和`completion.json`；旧完成证明不重写。下一步的新Backend/完整All、异机/承诺平台、安全历史整合及正式发布仍分别待执行，两个旧TBD不自动关闭。

以下保留上轮记录及其当时的阻断状态。

日期：2026-10-19。状态：`GITLESS_FIX_VERIFIED_ALL_PREFLIGHT_BLOCKED`。真实无Git前端预检与四阶段门禁通过，All预检因Docker引擎不可用受阻；T062未完成，阶段15为6/7，累计63/64。未提交、Tag、推送、合并或部署。

## 断点与修复范围

阶段13已经证明：旧070源码包不含`.git`，原门禁在HEAD读取处失败，尚未进入工具或测试。用户批准继续下一项后，本轮在当前`feature/incident-platform`工作树修复该入口，保留旧候选、接手ZIP、负向证明和全部既有修改。

- [门禁](../../scripts/check-delivery.ps1)新增显式`-CandidatePath`，先复用四文件Verify，再核对实际完整源码集合、大小写、长度和SHA256；不要求Git，不借父仓库身份。
- 开始、结束均比较候选四摘要及完整源码，CheckOnly也执行结束核验。仅剪枝八模块target、frontend/node_modules、frontend/dist、frontend/.output/incident及根.git；未知文件和隐藏配置仍拒绝。
- 候选摘要schema2/head=null，固定sourceCandidate白名单；baselineHead只表示历史基线。默认Git模式保持schema1并要求实际源码根为Git根。
- [合同](../../scripts/test-check-delivery.ps1)增加真实候选/完整树正反、漂移、工具前拒绝、父Git拒绝、摘要数组/类型注入及精确生成目录检查。开发者合同包含真实Git回归，须在开发Git树执行；源码包使用候选门禁入口。

未改业务、Schema、前端实现或构建命令；未启动持久演示、操作旧实例或运行新Backend/完整All。

## 本轮来源

证据根：`bootstrap/target/gitless-delivery/a83506bb/`。开始固定1907条源码路径、80项旧候选/证据输入，另以只读方式固定650项既有私有配置与记录摘要；原HEAD为`67624931f20812922568d3641a095adb291263ba`，不是包含新增实现的提交。

新验证候选为`ebf476ea-f17f-4a15-a8d4-365bcb8e1ad1`，1901文件，状态`SOURCE_VERIFIED_HISTORY_WITHHELD`。目录为`bootstrap/target/release-candidates/ebf476ea-f17f-4a15-a8d4-365bcb8e1ad1/`。

| 文件 | SHA256 |
| --- | --- |
| source.zip | `68190063E2854629F04B3D98A6DFCD637A621B5D6CECB6A4C2C45FA68ADED681` |
| source-manifest.json | `AA809DEF5AC9A7EABF0A0F5736A68B7355A80887D0F5125BA8706115A3833503` |
| candidate.json | `EF631B1C34D34FEBFDF7D8A807677166FE8C02CB068AC3E19F83D3AA5E31C1BA` |
| SHA256SUMS | `136AA50E8A09FC178FBC93655E296CF2E79B602E4A9369D0754D25AEED1D1DE5` |

完整源码在全新`attempt2/s/`解包，与候选逐项核对。门禁另外保存1363项受检子集，sourceDigest为`DA82D327EF4C718C7A7CC924440FB826D0FF1EE3667B3AD61506314B3D5A9133`；它不能代替1901文件manifest。子进程清除既有Git环境、限制父Git发现，绝对Git探针退出128，再移除PATH内Git工具；无git init/commit。npm使用本机离线缓存。

此候选是运行验证输入快照，冻结早于本报告及最后说明更新；包内任务/说明仍是冻结时点，最终报告在当前仓库查看，不能把后补说明或新身份回填旧ZIP。完整All和持久业务已经验收的输入仍是070，不能将其结论改属本轮候选。哈希是完整性证据，不是签名或秘密扫描认证。

## 实际检查

| 检查 | 结果 | 记录 |
| --- | --- | --- |
| `pwsh -NoProfile -File scripts/test-check-delivery.ps1` | 159合同，退出0 | contract-attempt4.log；原157及更早记录保留 |
| `pwsh -NoProfile -File scripts/test-release-candidate.ps1` | 49合同，退出0 | candidate-contract.json |
| 新候选Create/Verify及全新解包 | 1901项一致 | final-candidate.json、final-source-before.json |
| 实际无GitAll CheckOnly | BLOCKED / 2，0阶段 | `ac712f69-fea2-4ea0-9401-f842b88ce386` |
| 实际无GitFrontend CheckOnly | CHECKED / 0，0阶段，起止来源一致 | `5fb0e086-e82e-4f70-b2c1-4b225754617e` |
| 实际无GitFrontend | PASS / 0，四阶段均VALIDATED/0，起止来源一致 | `63e4bec8-7de7-4ea7-aaa9-242a05f69ec4` |

实际入口为解包目录中的`check-delivery.ps1 -CandidatePath <本轮四文件目录>`，分别指定`-Gate All -CheckOnly`、`-Gate Frontend -CheckOnly`和`-Gate Frontend`。Frontend固定执行npm ci、页面检查、lint、build；它不代替浏览器或后端业务。当前PowerShell7.6.5、Node20.19.5/npm10.8.2；All预检已探测Java21/Wrapper Maven3.9.11，但Docker Desktop Linux引擎管道不存在，未启动或改变Docker。CHECKED不计PASS，BLOCKED不计通过。

## 首轮失败与更正

首候选`25318b5b-0868-44d2-b786-16065f820158`的All预检同样BLOCKED，Frontend预检CHECKED。真实Frontend `837f10df-1c1b-4ca4-b0ac-0334ee44db31`四阶段均退出0，但最终状态为FAIL/1：原精确生成目录清单漏掉页面检查的`frontend/.output/incident`。

该目录来自既有`frontend/tests/tsconfig.incident.json`的outDir。独立`first-output-proof.json`对同一原源码复现旧校验器的`CANDIDATE_SOURCE_SET_INVALID`，仅补充该精确输出根后核对成功；新增合同同时拒绝`.output/other/unexpected.txt`。首四文件、原s源码和FAIL记录保留，未修改旧候选凑通过；本轮采用新ID、新空目录重新运行。

复核还发现PowerShell数组匹配可使身份字符串校验误接受数组，现先校验字符串/整数类型再匹配格式；来源坏输入在工具前拒绝，安全摘要额外自由字段不复制。自动反例已通过，不由测试数量代替真实运行。

## 收尾与开放项

收尾脚本`closeout.ps1`独立检查原1907→1908路径仅增加本报告、允许范围内旧文件修改、无删除，原Git分支/HEAD/index/refs/worktrees与暂存保持；80＋650项保护输入、两次新候选、原/新解包树、六次门禁记录及safe/artifacts摘要分别核对。Git枚举的当前源码中，非说明内容必须与验证候选一致，6项既有不入包开发文件保持；后补说明单列到`actual-source-comparison.json`。完整解包树的核验另由候选门禁执行，不将Git清单等同于含忽略数据的整个开发目录。最终收尾以本轮`closeout-proof.json`和`completion.json`为准，不在被哈希文档中回填自身摘要。

Docker引擎不可用，无法获取本轮容器/卷集合快照，状态为UNAVAILABLE而不是0，也不冒称已核对资源集合保持。本轮没有执行容器、卷或服务的变更命令；私有文件保护与Docker集合可观测性分开报告。

辅助收尾首次因Windows目录尾分隔符产生双斜线，被路径断言以`CLOSEOUT_UNSAFE_PATH`拒绝，尚未生成完成证明。聚焦续核另遇到JSON重序列化省略时间戳末尾零，实际时刻一致但字符串比较拒绝。仅在辅助核对副本中规范化路径、同一时刻的ISO表示及已验证范围内的JSON整数类型，其他字段和原始文件均不改；失败另存`closeout-attempt1.json`和`closeout-attempt2.json`，不重新执行已通过的前端。

T062保持未完成：先由使用者启动本机Docker Desktop并确认本地Linux引擎可用，再对上述候选补跑`-Gate All -CheckOnly`，得到新的真实记录；随后再决定是否推进新的Backend/All。第二机器、Linux/Node22/Java17、托管CI、安全历史与当前实现整合、正式Git发布、旧静态异常和旧匿名卷归属TBD仍开放。旧070、接手ZIP及既有All/持久业务证据不改。
