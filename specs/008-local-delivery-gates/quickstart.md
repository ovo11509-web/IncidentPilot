# DEP-001 验证指南与本轮记录

入口已实施，以下是固定复现命令；实际命令、失败与通过证据见下方本轮记录。

## 前置与运行

pwsh7、JDK21（编译17）、后端Docker Linux Engine，前端Node22/npm锁文件。Windows已有旧门禁证据；Linux/托管CI未实测。新入口拒绝不受控JVM/Docker连接注入，不提供开发JDBC或业务key。

```powershell
pwsh -NoProfile -File scripts/test-check-delivery.ps1
pwsh -NoProfile -File scripts/check-delivery.ps1 -CheckOnly -Gate All
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Smoke
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate All
```

契约检查以fixture/假子进程覆盖拒绝、恢复及结果聚合，不启动服务；Smoke实际启动临时依赖与双JVM，精确黄金路径一项通过。All须完整后端与前端都通过；Smoke不能替代All。门槛见 [契约](contracts/gates.md)。

## 记录与CI审查

新 `bootstrap/target/delivery-gates/<UUID>/` 记录来源/命令/时间/版本/退出/统计，只接收本轮XML。失败保留，raw仅本地；CI只safe摘要。正常结束核对本轮资源退出，不全局清理。审查workflow触发、权限、官方Action SHA、同一入口、安全artifact与超时；静态检查不证明托管运行。未获授权不提交/推送/触发。

## 文档与未验证范围

部署准备说明分临时验收、常驻开发和真实部署；核对依赖/Schema初始化升级、凭据/角色、回调认证、审批/幂等、端口/清理。不开生产Compose/Kubernetes/真实Broker/模型/Milvus/OTLP或开发库。

Spec G 2026-10-05全仓864项（843通过、21跳过）为历史记录，见 [实施报告](../007-rag-test-isolation/implementation-report.md)，不能用于验收新入口。最初设计阶段只有静态检查；后续实施及运行见下方新记录和implementation-report.md，不能把设计或历史结果当成本轮通过。

## 2026-10-06 新入口实际执行

本轮用户完整方案批准，analyze无阻塞；使用pwsh7.6.5、Java21.0.12/release17、Wrapper Maven3.9.11、Docker Linux Engine29.4.3，前端Node20.19.5/npm10.8.2。CI定义Node22未实测；Linux/托管CI未运行。

| 实际命令 | runId/来源 | 结果与退出 |
| --- | --- | --- |
| `pwsh -NoProfile -File scripts/test-check-delivery.ps1`（首个入口缺失负例） | 入口实现前，无业务启动 | ENTRY_MISSING，1 |
| 同上（第一轮完整契约） | fixture/自有短子进程 | 71项通过，0；不当作业务通过 |
| 同上（补安全失效/CI负例） | `contracts-final.log`，82项 | 全部通过，0；摘要失败退出1且无safe上传目标 |
| `pwsh -NoProfile -File scripts/check-delivery.ps1 -CheckOnly -Gate Smoke` | d7d8853a-27a6-4f28-8519-1944ae94e2b5 | BLOCKED；初期组合宿主命令未独立捕获门禁子进程退出，不能据此声称已核对退出2；本地provider缓存未获识别 |
| 同上 | 07c9ae92-78dd-4bee-9220-3fa0e9b786d0 | BLOCKED；宿主PowerShell会话退出1，未独立记录门禁子进程退出；Docker探测受阻，非成功，原目录保留 |
| `pwsh -NoProfile -File scripts/check-delivery.ps1 -CheckOnly -Gate All` | a936aead-cd6a-47bc-b870-95a644ed6a1d | CHECKED，0；无业务启动 |
| `pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Smoke` | d405e73a-5a62-408a-9527-f169e77d1b25 | PASS，1通过/0失败/0错误/0跳过，0 |
| `pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Frontend` | b9201aeb-4afe-4d1d-a7bd-014ede3d8392 | PASS，Install/IncidentView/Lint/Build实际退出均0，最终0 |

Smoke于14:22:18～14:27:06（Asia/Shanghai），Frontend于14:19:53～14:24:54。Engine受阻期间先执行不依赖Docker的Frontend，Engine就绪后执行Smoke；二者未共用可变编排资源。两轮相同受控sourceDigest为 `D58BAA599BE3B968F372D51029A19DE1383187046AE4E484759896FD5089CCC1`；新XML只来自对应reports/ragent-bootstrap。Smoke必需方法alertsDiagnosisRejectionApprovalExactlyOnceAndRestart实际运行；临时四容器及自有JVM正常结束，containers-after-smoke.json为[]。

证据位于 `bootstrap/target/delivery-gates/<runId>/`。契约fixture在contracts子目录，与实际runId分开；前期fixture不作运行证据。保留基线、镜像ID、资源/CI静态检查及契约日志位于 `bootstrap/target/dep001-implementation/e5a7c477-70f1-455a-a02e-59b0bec0d38a/`。本轮固定四个镜像ID已实际读取；Redis latest等漂移风险仍存在。没有全局清理或替代开发库。

Smoke/Frontend后新增本地artifacts.json哈希索引，并补摘要失败与CI权限/上传负例，再运行82项契约通过；两个早期成功目录不回写源码或补造新索引。后续完整Backend在最终脚本/测试/前端说明来源上执行，共同finalize路径和摘要完整性由该轮验证，不用旧864项充当通过。


危险环境的实际负例：临时JAVA_TOOL_OPTIONS=无秘密的delivery-negative-fixture，运行CheckOnly Smoke，finally恢复。runId `81038c3f-3843-4cd2-a2c3-24ff1f83bac5`，明确捕获GATE_CHILD_EXIT:2，BLOCKED，未启动Java/容器；原值没有输出。早期宿主会话与子进程退出的记录不补造；独立契约和本次负例证明入口退出约定。

以下保留两次完整Backend失败记录；实际固定命令从各轮run/stages读取，不使用历史门禁计数。

完整Backend首次 `bc6d2820-84cd-4536-b43a-e634b3779650` 未完成：实际部分XML发现YouCom两类使用JUnit @DisplayName，testcase.classname为中文显示名，原解析器错误要求其等于testsuite.name。只读核对真实报告及源码注解后，仅按runId核对并终止本轮自有cmd/Maven进程树（PID21928），无全局操作；阶段退出1、最终FAIL，66个部分XML共218项/0失败/0错误/1跳过只是部分记录，不能当全仓通过。结束后容器列表[]。

修正只以规范testsuite.name校验类身份；testcase.classname须非空但允许合法显示名，计数/DTD/目标/runId等约束不放宽。加入中文DisplayName正例及空classname负例。第一次补测的假All曾因NTFS采样比高精度UtcNow早4.5毫秒而失败REPORT_STALE；只为mock XML明确本轮fixture时间，生产旧报告检查继续严格拒绝，不改写任何真实XML。

DisplayName/空classname契约合计84项退出0（contracts-final84.log），包括摘要失效不发布、CI权限/上传负例与假All首次失败保留。修正后真实完整Backend重跑：runId `910a60e1-47bf-46cc-9a9c-2f00eb730d69`，sourceDigest `F7E4A1252C9E45E20D3FA6F70787FC26205652363B0ED472C999616A9D758660`。重跑前容器列表[]，该轮仍失败，详情如下；前轮部分XML不复制到新目录。



第二轮完整Backend `910a60e1-47bf-46cc-9a9c-2f00eb730d69` 实际FAIL/子进程退出1：90份部分XML、261项/0失败/3错误/1跳过，Maven在Incident停止，后续RAG/Agent/Bootstrap未执行，不能计全仓通过。共享EvidenceManifestWriter还要求scenario/sourceDigest和fault-lab路径，原入口只传runId/proofRoot，三项恢复测试写证明时出错。补固定DELIVERY场景及本轮源码属性、同样受限的delivery路径，87项契约通过（contracts-final87.log）。接着先做真实三个恢复测试的最小兼容检查，不修改旧XML或静默禁用证明。

最小真实兼容检查runId `01947443-a053-4d69-b900-3cc6050d133c`：`./mvnw.cmd -Dspotless.apply.skip=true -pl incident -am -Dtest=AlertRecoveryTest,OutboxRecoveryTest -Dsurefire.failIfNoSpecifiedTests=false -Dlab.reportRoot=<本轮>/reports -Dlab.proofRoot=<本轮>/proof -Dlab.runId=<UUID> -Dlab.scenarioId=DELIVERY-BACKEND -Dlab.sourceDigest=<本轮SHA256> -Ddelivery.gates=true test`。实际路径/参数已完整存start.json；本页不写当前机器绝对路径。3项/0失败/0错误/0跳过，Maven退出0，2份新XML及proof-origin三方法、runId/DELIVERY-BACKEND/sourceDigest均核对，sourceDigest D9FC783F358E7595172452A91281ED49F6A0074E50993737E788E25F7608D3F6。

目录 `bootstrap/target/delivery-gates/narrow/01947443-a053-4d69-b900-3cc6050d133c/`，不伪称它是完整Backend或CI安全摘要。RAG关闭/key移除并finally恢复，资源结束后[]。最小成功后启动最终完整Backend；该次真实执行才用于T016和DEP-001收尾。

最终Frontend命令 `pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Frontend`，runId `42f23a1f-a861-4849-a674-1e0753b5836d`，PASS且明确GATE_CHILD_EXIT:0；Install/IncidentView/Lint/Build均退出0。与最终Backend相同D9FC...源码摘要/1339文件；safe字段白名单、四阶段及本地artifacts全文件哈希已独立核对。此次重新执行因共享索引和最终来源变化，早期D58BAA...目录保留，不改写。仍不代表Node22、Linux或浏览器验收。

## 最终完整门禁（本轮新证据）

| 实际命令 | runId | 状态、子进程退出及统计 |
| --- | --- | --- |
| `pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Backend` | 5576b210-ed88-4ef9-b841-e0a755d8f627 | PASS，0；190份新XML，864项：843通过、0失败、0错误、21跳过 |
| `pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Frontend` | 42f23a1f-a861-4849-a674-1e0753b5836d | PASS，0；Install/IncidentView/Lint/Build均0 |
| `pwsh -NoProfile -File scripts/test-check-delivery.ps1` | contracts-final87.log | 87项契约通过，0；fixture及自有短子进程，不当作业务 |

Backend14:54:20～15:25:46、Frontend14:59:01～15:03:44（Asia/Shanghai）。两轮sourceDigest均为 `D9FC783F358E7595172452A91281ED49F6A0074E50993737E788E25F7608D3F6`，1339受控文件运行前后未变。Backend实际Maven命令为 `mvnw.cmd -Dspotless.apply.skip=true -Dlab.reportRoot=<本轮>/reports -Dlab.proofRoot=<本轮>/proof -Dlab.runId=<本轮UUID> -Dlab.scenarioId=DELIVERY-BACKEND -Dlab.sourceDigest=<本轮SHA256> -Ddelivery.gates=true test`；Linux分支mvnw未执行。绝对参数仅本地stages.json保存，仓库文档保持相对路径。

12个必需类均存在且零跳过；Bootstrap四类分别7/1/1/4项通过，OBS四份Trace证明实际生成。13个手工类的21项跳过均为MANUAL_EXTERNAL_GATE_CLOSED，完整名单如下，跳过不计通过：

| 完整类名 | 跳过项数 |
| --- | --- |
| com.nageoffer.ai.ragent.agent.admin.AgentDashboardReaderTest | 1 |
| com.nageoffer.ai.ragent.mcp.executor.common.YouComSearchMcpExecutorLiveTest | 1 |
| com.nageoffer.ai.ragent.core.parser.mineru.MinerUPdfUploadFlowTest | 1 |
| com.nageoffer.ai.ragent.index.InvoiceIndexDocumentTests | 3 |
| com.nageoffer.ai.ragent.rag.core.retrieval.channel.WebSearchChannelLiveTest | 1 |
| com.nageoffer.ai.ragent.rag.embedding.SiliconFlowEmbeddingServiceTests | 1 |
| com.nageoffer.ai.ragent.rag.Intent.IntentTreeServiceTests | 2 |
| com.nageoffer.ai.ragent.rag.Intent.SimpleIntentClassifierTests | 2 |
| com.nageoffer.ai.ragent.rag.Intent.VectorTreeIntentClassifierTests | 4 |
| com.nageoffer.ai.ragent.rag.rewrite.QueryRewriteTests | 2 |
| com.nageoffer.ai.ragent.RagentCoreApplicationTests | 1 |
| com.nageoffer.ai.ragent.service.ConversationMessageServiceTests | 1 |
| com.nageoffer.ai.ragent.vector.MilvusCollectionTests | 1 |

独立核对本轮XML runId/计数、安全摘要字段白名单、来源和全部本地索引哈希：Backend534制品/五份proof-origin，Frontend11制品。最终容器列表[]，匹配本轮UUID的Java进程0；无全局清理。复核记录final-evidence-validation.json、containers-after-final-backend.json及final-preservation.json存前述保留基线目录。分支/HEAD/空暂存保持，1745份哈希基线文件无缺失、18个预期文件变更，AGENTS/许可证/需求清单未改。57份中文名既有受跟踪资料初始未入哈希，初始status及当前diff确认未改，边界见实施报告；真正新增五份文件。

全部20项完成后只收尾文档，未再次全仓执行。Linux、Node22、Java17运行、托管CI、浏览器和真实部署未验证；本地门槛满足不授权发布或生产操作。
