# 固定修正候选完整构建与All验收

2026-10-18，用户批准阶段11（T038～T042），额度中断后继续原范围。输入为存储日志卷修正后的固定源码候选，不重新生成或覆盖候选，不运行新的持久演示，不提交、Tag、推送或部署。

结论：阶段11的固定候选构建、完整All及独立证明/完整性/资源保护复核均完成。以下保留首次受阻、中断和辅助核验错误，不把本轮通过扩成新持久业务演练、异机或正式发布通过。

## 输入与恢复断点

- 候选：`070c4b76-70db-48c0-b7d0-b701a527f9e3`，完整1895文件。
- 完整清单SHA256：`D7B7181B1B8DD95CEF330F1BD104005152B4529018C798BBAC8D06172A2EA99E`。
- 源码ZIP SHA256：`541EA4659F510C9B2465E14A21D6CF185F116107EC2EEF81CFAD50BBBCF7CE87`。
- 本轮证据根：`bootstrap/target/full-check/390adb80/`；全新解包根为其下`s/`，初始没有target、dist或node_modules。1895文件逐项大小/哈希一致。
- 原分支`feature/incident-platform`、基线HEAD `67624931f20812922568d3641a095adb291263ba`；新增实现未提交。ZIP没有`.git`，嵌套解包运行时Git读取外层原库，HEAD只表示来源基线，不表示新增实现提交。
- 中断前已完成87项门禁合同、All CheckOnly与后端打包。All `6e584be7-d29d-43c2-bc03-beab8149f228`中断后仅37份部分XML，进程已退出、没有最终safe摘要，原run仍为RUNNING；不得计为通过或拼接进新run。
- 恢复时Docker Engine已停止。启动本机Desktop并等待Engine就绪后，原16容器/89卷及其状态与基线相同；1895文件及两份JAR摘要也未变，故不重跑已完成构建，仅以新UUID补跑All。

## 已完成的预检与打包

固定候选命令（在解包根执行）：

```powershell
pwsh -NoProfile -File scripts/test-check-delivery.ps1
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate All -CheckOnly
./mvnw.cmd -o '-Dmaven.repo.local=<本机既有缓存>' '-Dspotless.apply.skip=true' '-Dmaven.test.skip=true' -pl bootstrap,mcp-server -am package
```

合同实际`CONTRACT_PASS 87`、退出0；CheckOnly `fc81ec57-e27a-49dd-bde4-1e35906b085d`为CHECKED。实测工具为pwsh7.6.5、Java21.0.12、Maven3.9.11、Docker29.4.3、Node20.19.5、npm10.8.2。CHECKED不算All通过。

离线package父工程及八模块共9条reactor成功，日志`BUILD SUCCESS`，约3分54秒。此步骤明确跳过业务测试，不借用原树target。两份JAR按内嵌`pom.properties`的Maven坐标、版本、Boot launcher及启动类存在性核对；本轮不启动这些JAR，不能将结构核对写成新持久实例运行验收。

| 产物（相对解包根） | 字节 | SHA256 |
| --- | ---: | --- |
| bootstrap/target/bootstrap-0.1.0.jar | 224251704 | C75C046E7F6D76BBEB01FD8127135858D807E2626602CC4248A8598CF5056EEC |
| mcp-server/target/mcp-server-0.1.0.jar | 40081318 | EC15FDD72D5938414B2AE07CB3EC338D58E5C4A95522A92CC2DEBA80A88658E5 |

## All与独立证据复核

最终All `353b453f-56c7-451e-a54a-67057342a19c`为PASS/VALIDATED，原门禁及辅助封装均退出0。门禁记录时段为2026-10-18 11:28:59～12:11:52（Asia/Shanghai），随后完成制品索引并退出；时长属于本次验收运行，不是产品响应时间。

| 阶段 | 本轮结果 |
| --- | --- |
| Backend | 199份新XML；930项，909通过、21受控跳过、0失败、0错误；14必需类零跳过 |
| Install | `npm ci --no-audit --no-fund`，退出0 |
| IncidentView | `node tests/incident-view.check.mjs`，退出0 |
| Lint | `npm run lint`，退出0 |
| Build | `npm run build`，退出0；生成117个dist文件 |

21项跳过来自固定13类手工外部门目录，不计通过；逐类数量以本轮safe摘要为准。此处930/199来自新run实际报告重新统计，不借用旧Spec L记录。

- 门禁sourceDigest：`843F07D559ECF2D62AC2BF40F341399761E1E9C5C32A1CB120D3747FABB7733C`。
- safe摘要SHA256：`B4726A7A6C6021921B10F63B6BC3A4A5D9219DA2D21D59AE680ABDD6977F46AC`。
- 本机安全摘要：本轮证据根下`s/bootstrap/target/delivery-gates/353b453f-56c7-451e-a54a-67057342a19c/safe/summary.json`。

```powershell
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate All
```

调用冻结脚本原命令：Backend全仓`test`，Frontend依次为npm ci、IncidentView、lint、production build。All没有强制Maven离线；npm请求离线缓存。本机既有Wrapper、Maven/npm依赖缓存和镜像均为前提，不称第二机器或完全无缓存验收，也不声称整轮零出站。

规则/HTTP/进程/数据库证明的独立复核已退出0并生成`reliability-audit.json`，来源均绑定新runId/sourceDigest，不复制旧结果：

| 验收对象 | 本轮事实 |
| --- | --- |
| 聚合规则 | 15个独立固定预期×3处理顺序=45组，与fixture逐项一致；114个输入中99个PROCESSED、15个按用例预期进入FAILED，未完成0。预期FAILED成员与测试失败、样本丢失分开统计 |
| HTTP幂等 | 10客户端工作线程、40次同身份请求均202；新建1、重复39、唯一落库1；另1次冲突被拒绝 |
| 进程恢复 | 受理后终止自有进程，前后PID不同；原记录处理1、重新提交0 |
| 数据库故障 | 自有库暂停/恢复均核对；故障请求HTTP_503，不假定失败响应等于回滚；恢复后唯一记录1，既受理积压恢复1、积压补提交0。积压到期时间由测试夹具控制 |
| 固定样本 | hot/multi×历史0/500四组，每组100；400个唯一正式样本逐项202且processed，失败0、未受理或未完成0；各组10客户端工作线程、实际峰值10 |
| 样本指标 | 接收延迟百分位按100个原始样本重新计算；完成延迟分母各100，观察包含100ms轮询误差，不冒称能复算未保存的逐样本处理延迟 |
| 模拟副作用 | 该可靠性运行专项的模拟应用0；不能扩成整个All无模拟应用，其他黄金路径另有经审批的一次模拟处置 |

证据层分别为`REAL_PG_SERVICE_CALL_SQL_TIME_FIXTURE`与`REAL_HTTP_PG_TEST_CLASSPATH_JVM`。这证明声明的本机固定案例与恢复事实，不证明生产SLO、真实Broker/模型或新可执行JAR的持久运行。完整XML、artifacts及safe白名单独立复核结果在收尾段记录。门禁sourceDigest覆盖源码子集，完整1895文件另按固定manifest核对，不把两者混同。

## 本轮失败、中断与辅助脚本修正

1. 合同子进程实际已通过，但辅助脚本最初识别错结果标记；保留`precheck-attempt1.json`，按候选真实`CONTRACT_PASS`识别并复用原成功输出，未重跑合同。
2. Maven打包成功后，辅助核验误要求项目未配置的`Implementation-Version`；reactor正则也会跨行多算。保留`package-attempt1.json`，改用内嵌Maven元数据/Boot入口和精确单行reactor核对，复用既有JAR，未改产品或重构建。
3. All `f60a4990-7f54-4309-89ab-b329e20fccc7`因沙箱读取本机Docker配置被拒而BLOCKED；警告混入endpoint检查，业务阶段未开始。保留原目录，所需本机权限运行不修改安全规则。
4. 第二次All在额度中断后缺终态，按未完成保留；恢复后新run不复用其部分报告。
5. 恢复时只修改ignored辅助脚本：保留ISO时间字符串以避免pwsh7.6自动日期转换丢精度/时区歧义；按实际`gateRunId`及状态/候选/摘要绑定审计；前端没有counts字段时不触发StrictMode属性错误；收尾全部断言后才写产物目录。旧辅助版本已留存，固定候选源码未改。
6. 独立XML审计首轮`AUDIT_REPORT_STRUCTURE_INVALID`源于PowerShell XML适配器把`name`属性映射到`.Name`，不是报告根节点错误。实际根节点均为`testsuite`；保留`gate-audit-attempt1.json`，改用`LocalName`判节点种类，仍用`GetAttribute('name')`判类身份，重读同批产物，不重跑All。
7. 收尾脚本点入local-demo库后，辅助循环变量`name`继承了演示参数`Name`的校验，读取`candidate.json`时误触发参数验证。保留`closeout-attempt1.json`和失败辅助版本，改用任务专属变量名，不改产品库或重跑构建/测试；失败时尚未写入产物目录。

## 收尾与未验证范围

独立完整性审计`gate-audit.json`已PASS：run/summary/safe一致，1363受检源码重新计算摘要一致；199新XML的时间/runId、结构、逐项计数、14必需类零跳过及13类受控跳过均核对；563制品的路径集合与摘要全部一致，artifacts清单按设计不含自身，目录共564文件；safe白名单重新构造一致。

最终`closeout-proof.json`为CLOSEOUT_VERIFIED，与同一All runId/sourceDigest及完整manifest摘要绑定：

| 保护/交付对象 | 实际核对 |
| --- | --- |
| 完整源码 | 新1895文件逐项大小/哈希/路径类型一致；旧1890源码逐项一致，差异0 |
| 候选与私有配置 | 新旧两组候选共8文件的精确集合/摘要保持；两旧实例共16份私有文件摘要保持，只取哈希不输出密码 |
| 构建产物 | 两份可执行JAR在All后摘要未变；10份Maven归档（含两份.original）与117个dist文件共127项，形成build-artifacts.json |
| 构建目录摘要 | `7F7A96B71EFF2CD7846372F6C9516F024040EFC87367F1473E546271B388CBE2` |
| 原库变更范围 | 起点1901文件，结束1902；10份既有说明/计划/任务变更、1份新报告、删除0，业务源码没有本轮新修改 |
| Git | 分支/HEAD/暂存区/全部refs/工作树列表均与基线一致；新增实现仍未提交 |
| Docker | 原16容器ID/名称/运行状态/StartedAt及89卷集合保持；额外容器/卷0，没有手动清理资源 |

最终修改清单为`plan.md`、`tasks.md`、`implementation-report.md`、本报告，以及项目上下文/任务/笔记、版本索引/候选说明、交付/测试说明；共11份说明文件，均在原库。最终文档哈希与相对链接检查另存本轮`docs-proof.json`。没有回填冻结ZIP、初始化新持久实例或启动打包JAR。

旧首轮静态采集异常根因和旧`e98d…`匿名卷历史具体归属继续TBD。已证实的匿名`/logs`机制修复不等于旧卷历史唯一归属已确定。旧1890候选、私有实例及衍生历史保留；源码候选WITHHELD元数据不回填。

本轮完整构建/All与新持久实例业务演练、浏览器、第二机器/跨平台分别验收。Linux/Node22/Java17、托管CI、真实模型/Broker和生产均不能由本轮结果获得通过结论。新增实现仍待经授权整合提交、Tag及发布。本轮原库新文档不回填冻结ZIP。

原始日志、XML/proof、配置、classpath及整个target只留本机；可公开的门禁证据仅`safe/summary.json`白名单，本文只列去值聚合事实。逐项本机路径及最终摘要以本轮独立JSON记录为准。
