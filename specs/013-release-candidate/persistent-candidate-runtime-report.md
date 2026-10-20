# 固定修正候选持久运行与重启验收

2026-10-18，Spec M阶段12，T043～T047。用户批准“继续下一步”，范围为固定070候选的独立本机持久实例业务演练。阶段11已通过的构建和All复用，本阶段没有重跑Maven、npm构建或All，也未修改业务代码。

## 结论与身份

真实可执行JAR经新实例完成告警聚合、诊断引用、拒绝零应用、批准唯一一次应用、同选择意图重放和Stop/Start留存。完整117份前端文件通过源码/实例/HTTP字节核对；独立只读SQL比较重启前后业务快照一致。最终已正常Stop，5个命名卷保留；`closeout-proof.json`实际返回`PERSISTENT_CANDIDATE_CLOSEOUT_VERIFIED`，原库、冻结候选、旧配置和资源保护核对通过。

| 项目 | 固定值 |
| --- | --- |
| 本轮runId | `cea55ab3-c06a-4db0-8416-411f6d0485da` |
| 源码候选 | `070c4b76-70db-48c0-b7d0-b701a527f9e3`，1895文件 |
| 解包源码根 | `bootstrap/target/full-check/390adb80/s/` |
| 既有All | `353b453f-56c7-451e-a54a-67057342a19c`，仅作已验证制品来源 |
| 新Name | `candidate-run-cea55ab3` |
| 新instanceId | `3754886c428d40fcb7e8eec19fdcf921` |
| Compose project | `incidentpilot-local-3754886c428d40fcb7e8eec19fdcf921` |
| 本轮证据根 | `bootstrap/target/persistent-candidate/cea55ab3/`，本机忽略目录 |
| 原库分支/HEAD | `feature/incident-platform` / `67624931f20812922568d3641a095adb291263ba` |

候选四文件仍冻结：

| 文件 | SHA256 |
| --- | --- |
| `source.zip` | `541EA4659F510C9B2465E14A21D6CF185F116107EC2EEF81CFAD50BBBCF7CE87` |
| `source-manifest.json` | `D7B7181B1B8DD95CEF330F1BD104005152B4529018C798BBAC8D06172A2EA99E` |
| `candidate.json` | `7A11A9A6E6A50824945E3F2BEF843C4782277DFE8D864E6AC10F6D0CB92EA3D1` |
| `SHA256SUMS` | `5DB44911BB21A5F7D840DAB9FBFA6287ACF01DD9E0F087185FFFF6B139B0212F` |

本轮说明在原工作区追加，未回填冻结ZIP。旧011候选1890文件和旧报告继续保留各自时点。

## 起点与构建制品复用

`setup.ps1`先逐项核对完整1895源码和阶段11的127份构建目录项，记录原1902源码文件、Git状态、原16容器/89卷、8候选文件和630份旧私有配置/先前验收文件哈希。没有同名实例，候选Init返回`reused=false`。

新实例使用独立随机端口、密码和4个依赖容器。初始化时，13个主业务链路表均空、只有本机生成的local-admin，模拟应用0，目标PAUSED/version0。只使用本机安全模拟配置，不接开发/生产数据库或付费模型。

本轮没有调用CLI Build。`publish.ps1`持有同Name生命周期锁，调用冻结候选原有`Publish-DemoArtifacts`，从已验收构建目录复制两份JAR及117份dist；119项路径、字节数、SHA逐一比较，原函数生成`artifacts.json`，没有手工伪造完成标记。`artifact-publication.json`明确`rebuilt=false`并绑定原All、完整manifest及构建目录摘要：

- 构建目录SHA256：`7F7A96B71EFF2CD7846372F6C9516F024040EFC87367F1473E546271B388CBE2`。
- app JAR：`C75C046E7F6D76BBEB01FD8127135858D807E2626602CC4248A8598CF5056EEC`。
- MCP JAR：`EC15FDD72D5938414B2AE07CB3EC338D58E5C4A95522A92CC2DEBA80A88658E5`。

正常Start为READY。CIM在重启后核对两Java进程实际以`java -jar`启动对应发布产物，另一个为候选Node静态代理；均核对PID、创建时刻和自有配置标识，无test classpath运行。记录只存命令行摘要，不输出私有参数。

## 实际业务断言

真实HTTP经过本实例静态服务/代理；登录读取管理员身份，未登录的保护接口返回401。告警Webhook使用独立HMAC身份，不携带登录Authorization。每个有副作用POST先写发送检查点；传输结果不明确时停止，不盲目重试。

| 断言 | 实测结果 |
| --- | --- |
| 两条独立签名告警 | 均PROCESSED，聚合为同一Incident，alertCount=2 |
| 诊断 | 1个COMPLETED/SUPPORTED、非过时结果；R2可转待审批动作 |
| 拒绝路径 | 人员显式REJECT后APPROVAL_REJECTED；无执行，模拟应用仍0、目标PAUSED |
| 批准路径 | 新动作显式APPROVE后SUCCEEDED；1执行、1票据、1个SUCCESS/APPLIED回执 |
| 模拟资源 | 唯一应用记录与executionKey一致；PAUSED→READY，version0→1 |
| 相同选择意图重放 | 返回同一已批准Action，应用仍1；本轮未重复批准或直接重放MCP执行 |
| Incident终态 | OPEN，未把处置成功自动写为事件关闭 |

可核对业务ID：Incident `2111707906880569344`，Diagnosis `2111707911313948672`，拒绝Action `2111707913226551296`，批准Action `2111707917966114816`，回执 `2111707925180968960`。

`db-check.mjs`另行以REPEATABLE READ READ ONLY事务读取数据库，核对归属及以下计数与关联，而非只依据HTTP成功：

| 数据 | 数量/不变量 |
| --- | --- |
| Alert / Incident | 2 / 1，关联相同Incident |
| Diagnosis / Result / Action Link | 1 / 1 / 2 |
| Action / Approval | 2 / 2，拒绝与批准路径分别保留 |
| Execution / Ticket / Receipt | 1 / 1 / 1，executionKey关联一致 |
| Timeline / Alert审计 | 14 / 2，时间线序号连续1～14、businessKey唯一、事件类型分布符合主链 |
| Outbox | 6，eventId/businessKey匹配相应Timeline |
| 模拟应用 | 1，与批准执行和目标状态一致 |

本机配置明确关闭Outbox relay，6条均PENDING；这不证明Broker投递。敏感payload/参数仅在核验内存中计算摘要，不存原始凭据、票据秘密或payload。稳定快照SHA256为`b6abdc09bfc5bff821db2750a9f1de021b4e4f3ebee17c22fdb6777adb960322`。

## 引用与完整静态读取

5条引用的原文、行范围、chunkDigest逐一与固定候选源文件一致，1份文档documentDigest一致，相关引用链接通过。117份源dist与实例dist路径、长度和SHA均相同。

HTTP覆盖完整117文件：`/login`返回index字节，另外116份文件逐项按路径读取并比SHA，包含`favicon.svg`和PDF worker `.mjs`，没有仅按JS/CSS后缀过滤。两条入口引用存在。此次证明文件交付字节完整，不包含新浏览器渲染或PDF功能验收；不反推旧首轮静态失败根因。

## 停启与留存

通过候选正常Verify→Stop→Start→Verify，四条命令退出0。三个服务均为新自有进程；账户、5手册、Incident、诊断、审批及回执投影前后一致，分页已完整读取。重启后再执行只读HTTP及独立SQL比较，稳定数据库快照相同、模拟应用仍1。

两JAR、117 dist和9份私有文件（凭据、制品标记、app/MCP/web配置、Compose/env、初始化及owner）哈希保持。五个命名卷是`main-data`、`simulation-data`、`redis-data`、`storage-data`、`storage-logs`，均有本instance标签；实际Mounts精确匹配，RustFS的`/data`、`/logs`都使用显式命名卷。重启前/停止中/重启后的全局94卷集合完全一致，没有新增匿名卷。

最后再次正常Stop退出0、`dataRetained=true`，不删除数据卷。停止后的地址不作为正在运行的预览入口。

## 执行记录与保护收尾

以下辅助文件均只在上述忽略目录，命令从仓库根执行；它们是本轮固定输入的取证器，不是通用交付入口。

| 实际命令/操作 | 结果 |
| --- | --- |
| `pwsh -NoProfile -File <证据根>/setup.ps1` | BASELINE_VERIFIED，退出0 |
| `pwsh -NoProfile -File <证据根>/lifecycle.ps1 -TaskAction Init` | INITIALIZED/reused=false，退出0 |
| `node <证据根>/db-check.mjs -baseline` | EMPTY_DATABASE_VERIFIED，退出0 |
| `pwsh -NoProfile -File <证据根>/publish.ps1` | VERIFIED_ARTIFACTS_PUBLISHED，退出0，未重建 |
| `pwsh -NoProfile -File <证据根>/check-initialized.ps1` | NEW_PERSISTENT_INSTANCE_VERIFIED，退出0 |
| `pwsh -NoProfile -File <证据根>/lifecycle.ps1 -TaskAction Start` | READY，退出0 |
| `node <证据根>/runtime-check.mjs` | RUNTIME_VERIFIED，退出0 |
| `node <证据根>/read-proof.mjs` | READ_PROOF_VERIFIED，退出0，完整117 HTTP |
| `node <证据根>/db-check.mjs -capture` | DB_FACTS_CAPTURED，退出0 |
| `pwsh -NoProfile -File <证据根>/restart-check.ps1` | STOP_START_VERIFIED，退出0 |
| `node <证据根>/runtime-check.mjs -verify` | RESTART_VERIFIED，退出0 |
| `node <证据根>/db-check.mjs -compare` | DB_RESTART_VERIFIED，退出0 |
| `pwsh -NoProfile -File <证据根>/check-processes.ps1` | PACKAGED_PROCESSES_VERIFIED，退出0 |
| `pwsh -NoProfile -File <证据根>/lifecycle.ps1 -TaskAction Stop` | STOPPED/dataRetained=true，退出0 |
| `pwsh -NoProfile -File <证据根>/closeout.ps1` | PERSISTENT_CANDIDATE_CLOSEOUT_VERIFIED，退出0 |

保护收尾实际结果：1895新源码、1890旧源码、8候选文件、630份旧私有/先前验收文件、127份源构建产物、119份实例制品及9份本轮私有文件摘要全部保持。原16容器的ID/名称/运行状态/启动时刻未变，原89卷保留，全局卷集合精确为89+本轮5=94；额外容器/卷均0，本轮服务进程/容器均0。

原库分支、HEAD、index、refs及worktrees未变，删除0；仅12份既有说明变更与1份新报告，1902→1903源码路径，业务代码未改。实施范围在本目录spec/plan/tasks追加阶段12；项目上下文、TODO、NOTES、发布索引和开发/交付说明同步最新证据，历史报告未改。

最终使用`pwsh -NoProfile -File <证据根>/final-record.ps1`核对T001～T047全完成、13份文档相对链接及宿主机路径边界，保存`docs-proof.json`、本轮证据`artifacts.json`和绑定三摘要的`completion.json`。辅助文件只留本机；原始记录不作为可直接公开发布的材料。

## 边界与下一步

- 本阶段为Windows/Java21/Node20、本机既有缓存/镜像下的独立新配置和新数据库实例。完整源码与上一阶段制品关联可核验，不称第二机器、无缓存或Linux宿主机复现。
- 本轮没有新的All/930项测试数据；930项及受控跳过属于 [阶段11](full-candidate-acceptance-report.md)。这里新增的是实际JAR持久业务与资源留存证据，不是性能基准或生产可靠率。
- 模型/检索限本机模拟Runbook规则，不证明真实模型泛化；Broker、跨主机认证、生产备份/迁移/升级/回滚和持续运维未验收。
- 旧首轮静态异常根因、旧匿名卷历史具体归属继续TBD。新命名挂载与本轮无额外卷结论不能关闭旧历史问题。
- 原上游历史、Apache-2.0与署名保留；新增实现仍未正式提交/Tag/推送/发布。后续优先做接手机器或明确承诺平台的独立复现，范围先明确；Git与部署另需授权。
