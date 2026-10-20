# 本地交付门禁与部署准备

> 2026-10-19阶段19最新收尾：Windows中央`.cmd`路径修复，真实RED/GREEN 176项合同、npm及后补49项候选合同通过；新候选`c1687735`（1907文件）真实无GitAll `d98d8861`已PASS/0，同run五阶段退出0，201 XML、952项/931通过/21受控外部跳过/0失败错误，独立可靠性/评测审计通过。完整来源/1906旧输入及资源执行末检保持，最终保护以本轮综合proof/completion为准。旧b667/7d1c BLOCKED及旧Backend PASS保持；T076失败终止、SC-019未满足，新成功由T081支撑，82/82仅任务关闭。详见[本轮报告](../../specs/013-release-candidate/cmd-dispatch-fix-report.md)。后补说明不回填冻结ZIP；异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段18All阻断：固定b667新解包、真实无GitAll `7d1c8f40` 为BLOCKED/2，PRECONDITION_FAILED，0测试阶段/0XML；Windows带引号裸npm.cmd定位自身模块失败，完整路径探测成功。未改根门禁或候选，完整All/前端/专项审计仍未通过；后续最小执行器修复与新候选复验待确认。原Backend及旧Frontend、旧FAIL保持身份，1871项保护和资源事实及最终收尾见[本轮报告](../../specs/013-release-candidate/gitless-all-report.md)。异机/平台/旧TBD、安全历史整合与正式发布仍开放，无提交/Tag/推送/部署。以下文字保持原时点。

> 2026-10-19阶段16/17后端续跑：原ebf/`9624289c`真实FAIL及162XML保留。两类评测reporter的隐含Git依赖已修复，新增22项来源校验，普通Git/真实无Git聚焦分别27/27；新候选`b66759af-6cfb-4cb6-8db5-65276702f443`（1905文件）的真实无GitBackend `fdbac063` 已PASS/0：201新XML、952项/931通过/21受控外部跳过/0失败错误，14必需类零跳过。额度恢复后未重跑Backend，实际补做45规则、HTTP幂等、进程/数据库恢复、400样本及16项评测外置产物的独立审计并PASS。完整来源/1200旧输入、Git、既有16容器/94卷和说明以本轮综合proof/completion绑定；Spec M73/73限已定义本地任务。详见 [本轮报告](../../specs/013-release-candidate/candidate-backend-report.md)。前端204文件与ebf字节相同，未新跑Frontend/完整All、异机或发布；旧TBD、安全历史整合及正式Git授权仍开放，冻结候选不含后补最终说明。以下摘要保留原时点。

IncidentPilot 衍生自 nageoffer/ragent；保留上游历史、Apache-2.0许可和署名。本页描述本项目新增的交付门禁，不能作为生产部署、真实模型或外部基础设施验收证明。

## 固定入口

从任意目录以脚本实际路径运行，以下示例在仓库根目录：

```powershell
pwsh -NoProfile -File scripts/test-check-delivery.ps1
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate All -CheckOnly
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Smoke
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Backend
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Frontend
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate All
```

Smoke只执行诊断黄金路径；Backend执行完整Maven reactor；Frontend执行npm ci、Incident确定性检查、lint、build；All顺序执行Backend再Frontend，任何阶段失败均保留最终失败。Frontend不要求Docker。CheckOnly不启动业务或容器，也不安装前端依赖；CHECKED不等于运行通过。

## 无Git源码包入口

新门禁支持显式候选来源。将经过可信渠道核对摘要的四文件候选保存在解包源码目录外，例如同级`candidate/`，在新的空目录解包`source.zip`后，从源码根运行：

```powershell
pwsh -NoProfile -File scripts/check-delivery.ps1 -CandidatePath ../candidate -Gate All -CheckOnly
pwsh -NoProfile -File scripts/check-delivery.ps1 -CandidatePath ../candidate -Gate Frontend
# 前提满足后才按所需范围运行；以下是命令用法，不表示本轮已执行完整All。
pwsh -NoProfile -File scripts/check-delivery.ps1 -CandidatePath ../candidate -Gate All
```

候选模式不要求Git工具，先复用候选Verify，再比较实际源码的完整文件集合、大小和SHA256。只允许八个模块的`target/`、`frontend/node_modules/`、`frontend/dist/`、页面检查的`frontend/.output/incident/`和根`.git`作为生成目录；其他额外文件（包括`.env.local`、未知根文件、`.output`内其他文件和源码里的同名生成目录）必须拒绝，链接/重解析点也拒绝。开始和结束均核验候选四摘要和完整源码，CheckOnly也做结束核验。请使用新解包目录，不把开发机私有配置复制进源码。

候选摘要为`schemaVersion=2`、`head=null`，`sourceCandidate`保存候选ID、manifest/metadata/ZIP/SHA256SUMS摘要、文件数及历史`baselineHead`；它与受检子集`sourceDigest`分别标识。历史基线不代表新增实现已提交，完整性哈希也不代表签名或可信发布认证。未提供`-CandidatePath`时保持Git的schema1入口，要求源码根本身就是Git根，不能借父仓库身份。

此能力仅存在于新增修正候选，不能给冻结070或旧接手ZIP回填通过结论。旧包没有该参数，须保持其原限制及负向证明；新结果与适用候选在阶段15报告单列。

`test-check-delivery.ps1`包含真实Git模式回归，须在开发Git工作树运行。无Git源码接手者直接使用上面的候选门禁入口；开发者合同执行器不是源码包的启动前提。

2026-10-19本机验证：159门禁合同/49候选合同通过，新验证候选ebf476ea的无Git前端预检与四阶段PASS；All预检BLOCKED/2，因为本机Docker引擎不可用。首候选的生成目录遗漏FAIL保留，修正后使用新候选重验。实际身份与开放T062见 [本轮报告](../../specs/013-release-candidate/gitless-delivery-report.md)，不能称为新Backend/完整All或异机通过。

后续补跑：用户启动本机Docker Linux Engine29.4.3后，同一ebf候选/既有完整解包树的无GitAll CheckOnly `f1a08bdc-ff60-4f3e-8367-fe0c4ec297be`为CHECKED/0、0阶段，1901完整来源及1363受检子集摘要保持，T062关闭（阶段15为7/7、Spec M64/64）。上段的BLOCKED是旧记录，本轮未重新执行Frontend/Backend/完整All，不把CHECKED称为All PASS；最新证据见同一本轮报告。

退出0为PASS或CHECKED，1为执行/报告/摘要失败，2为前置受阻。中断的RUNNING/INCOMPLETE或缺少最终记录不得视为通过。完整后端90分钟、前端组20分钟为保护超时，不是实测性能承诺。当前入口要求pwsh7、后端Java21/keytool（POM编译release17）及Wrapper Maven3.9.11、前端兼容锁文件的Node20以上/npm；CI定义选Node22。实际平台与运行结果见 [Spec H quickstart](../../specs/008-local-delivery-gates/quickstart.md)。Java17执行兼容性、Node22、Linux和托管CI只有实际运行后才能另行确认。

## 临时编排及数据所有权

复用 `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java` 的四个Testcontainers依赖，不新增常驻Compose或应用镜像：

| 资源 | 用途与初始化 | 生命周期 |
| --- | --- | --- |
| pgvector/pgvector:pg16 | 临时主库；完整schema_pg.sql，再由夹具创建受限ragent_app及必要模拟用户/告警来源/映射 | 测试拥有，结束关闭 |
| postgres:16-alpine | 临时MCP模拟库；bit-schema.sql及demo模拟目标 | 测试拥有，结束关闭 |
| redis:latest | 临时会话/协调依赖；随机本轮密码 | 测试拥有，结束关闭 |
| rustfs/rustfs:1.0.0-alpha.72 | 临时S3兼容对象存储 | 测试拥有，结束关闭 |
| 主应用/MCP JVM | 用测试classpath启动真实Bootstrap/MCP，随机端口及内部认证；主应用拥有Incident事实，MCP只改模拟资源 | 夹具finally只关闭自身进程 |

依赖先就绪并应用隔离Schema，再启动MCP及主应用；实际启动参数、普通应用角色、票据在线消费及回执认证由既有夹具维护。黄金路径包含两告警聚合、诊断、拒绝零应用、批准一次应用、重启及事实核验。固定Runbook和检索不代表真实模型质量；Relay固定Producer不代表真实Broker。完整后端还有其他隔离数据库/网络夹具，不能把四容器说成全仓唯一资源集合。

JVM使用回环地址；Testcontainers宿主端口映射未证明仅回环可见，应在可信本机环境运行。Redis等浮动标签可能漂移，实际镜像ID记录属于本轮重现依据。主库全量初始化包含当前Incident表；模拟库只保存模拟应用记录，不拥有Incident审批、票据或Timeline。

## 环境与证据边界

入口强制RAG_LIVE_TESTS=false、临时移除YDC_API_KEY，并在finally恢复原值。拒绝非空不受控JVM/Maven参数、远程Docker覆盖、Testcontainers复用/禁用Ryuk及未审查配置；不打印原值。只接受Testcontainers自动保存的本地Npipe/Unix/Desktop provider缓存行，其他用户属性仍拒绝。当前Docker context还必须是本机npipe/unix端点与Linux Engine；不提供任意输出根或命令透传。

每次创建 `bootstrap/target/delivery-gates/<UUID>/`：source.json记录受控源码路径/哈希，run.json/stages.json/summary.json记录来源、命令、时间、退出和本轮计数。原始stdout/stderr在raw，XML在reports，Java临时日志/证明在proof。带delivery.gates的测试helper只允许delivery target目录并按场景保留独立证明；原LAB路径继续可用。本轮源码结束复核，运行期间变化则失败。

报告只接受新目录、新时间与本轮runId，XML禁止DTD/外部实体，重复类、计数冲突、空/旧报告、必需类缺失或跳过均失败。Backend当前核对14个必需类（Spec L新增规则/真实HTTP可靠性两类）及13个手工类的完整名称/允许数量；跳过逐类列明，不能计为通过。新增跳过不会自动获准。历史H/K记录的12类保留原时点，当前目录以脚本为准。完整目录见 [门禁契约](../../specs/008-local-delivery-gates/contracts/gates.md)。Frontend不代替浏览器验收。

只有safe/summary.json可发布：固定状态/原因码、时间、版本、runId、HEAD、源码摘要、阶段退出/计数及受控跳过目录。它重新构造白名单，不复制原始对象。raw、XML properties/system-out/失败正文、配置、classpath、TLS/账号临时文件及整个target目录均不得上传。摘要生成失败时不回退raw。这里的敏感fixture检查不是全仓或生产秘密扫描认证。

CI定义位于 `.github/workflows/incidentpilot-ci.yml`：Ubuntu24.04/pwsh，Backend与Frontend分别调用同一入口，contents:read、checkout不持久化凭据，官方Actions完整SHA；无业务Secrets、services重复栈、部署或镜像发布。只有pull_request/workflow_dispatch；本轮不提交、推送或触发。定义通过静态审查不代表托管执行。

## 排错与清理

前置受阻时先核对本机工具、Docker context/Linux Engine及被拒绝配置；Docker Desktop窗口存在不证明Engine可用。不改连开发数据库、不用共享资源降级。阶段失败在本轮raw及XML结构查找，保留旧目录，修复后新UUID重跑。命令退出0但目标未实际运行仍失败。

长阶段运行时，当前入口在子命令退出后才保存该阶段stdout/stderr；缺少 `raw/Backend.log` 本身不能证明测试卡住或失败。终端会话失联后先核对自有门禁/Maven/Surefire进程和新报告写入，再判断是否仍在执行。若进程已退出但 `run.json` 仍RUNNING、没有最终阶段和safe摘要，应按未完成证据保留目录，新UUID补跑，不修改旧记录为PASS或拼接部分XML冒充完整验收。

资源正常结束由Testcontainers/夹具关闭。取消/超时可能留下本轮资源；应按本轮容器ID、Testcontainers session标签和自有JVM核查，未来单独批准后仅处理已确认归属的残留。不得全局docker prune、stop所有容器、kill全部Java或清理开发数据库。原始证据只留本地，保留期限与安全销毁策略TBD。

## 本机持久演示与生产准备

2026-10-18用户暂无第二机器，现提供 [同候选接手手册](../../specs/013-release-candidate/second-machine-handoff.md) 及7成员可携带ZIP，只含冻结源码候选四文件、手册和白名单来源摘要。阶段13状态为`PREPARED_AWAITING_SECOND_MACHINE`，未重复构建、启动或异机验收。原All/CheckOnly依赖Git HEAD，源码包无Git时受阻；该限制不靠假提交绕过，后续来源标识适配另列范围，不能把材料准备叫完整交付通过。

2026-10-18阶段12对同一固定070候选复用已核验制品，新私有实例正常Start后完成告警→诊断→拒绝0/批准唯一1次、完整117静态HTTP、独立SQL及Stop/Start留存。两Java进程为实际JAR，5命名卷精确映射，最终停止保留数据；本轮未重复构建/All，未新增浏览器/异机或生产结论。来源与收尾见 [新持久实例记录](../../specs/013-release-candidate/persistent-candidate-runtime-report.md)，后文旧记录保留各自时点。

临时验收、常驻开发和真实部署是不同用途。`resources/docker/` 为上游可选服务示例；lightweight历史README的“生产环境”表述不能证明当前平台生产就绪，保留原文而在本页明确范围。

Spec K新增本机持久化安全演示入口，运行真实JAR和production dist，提供Init/Build/Start/Status/Verify/Stop；使用独立Compose资源和持久卷，只开放Incident安全模拟所需功能。使用方式见 [本机演示说明](local-demo.md)，实际启动及重启验收状态以 [Spec K实施记录](../../specs/011-incident-quality-delivery/implementation-report.md) 为准。此入口补充Spec H临时门禁，不把常驻本机运行等同生产部署。

2026-10-12核对：实际初始化、构建、同库告警到拒绝/批准安全模拟、Stop-Start数据保留及前端制品更新后事实保留已通过。额度中断后Docker/自有进程停止，恢复同实例Start并只读核对成功；启动入口不会替用户重新创建业务演练。正式版本整理、干净Linux/Node22/Java17和托管CI仍未验收。

同日后续验收：Spec I角色/旧配置/四视口截图/原生滚轮完成，Spec J取消及报告/手册截图已补齐。Spec L新增前的All门禁 `f989e36a-9e11-4f92-a719-2149762870f1` PASS：Backend197份XML、884项/863通过/21受控跳过/0失败/0错误；Install/IncidentView/Lint/Build全部exit0。来源结束复核、安全摘要和554项本地制品索引独立核对通过。详情见 [Spec K最终记录](../../specs/011-incident-quality-delivery/implementation-report.md)；结果限Windows/Java21/Node20，不把21项外部探针跳过或已中断run算作通过。

2026-10-12 Spec L历史Backend `1d0cd914-3565-4e70-9f73-e98fe4db02b9` PASS：199XML、930项/909通过/21受控跳过/0失败错误，1363源文件及557归档制品一致性通过。独立固定规则、真实HTTP幂等及进程/数据库故障恢复、四组400样本基线完成，首轮端口竞态FAIL保留并修复复跑。前端未改，该阶段历史四阶段记录不合称新的All。详细事实、计时边界及复现入口见 [Spec L实施记录](../../specs/012-incident-reliability-acceptance/implementation-report.md)。

2026-10-18固定修正候选070重新构建并执行新All `353b453f-56c7-451e-a54a-67057342a19c`：199新XML/930项、909通过/21受控跳过/0失败错误，前端四阶段退出0；45规则组合、真实HTTP/进程/数据库恢复及400正式样本均有本轮独立证明复核。sourceDigest仅为受检子集，完整1895manifest、JAR/dist及资源保护另行绑定，详见 [阶段11记录](../../specs/013-release-candidate/full-candidate-acceptance-report.md)。本机缓存、Windows/Java21/Node20及test-classpath边界保留，不代替新JAR持久演练、异机或生产。

新环境全量Schema与初始化数据由数据库所有者审查，不能再重复应用历史增量；既有环境按已实际采用版本审查增量及备份/回滚。当前清单有 `resources/database/upgrades/v2.0.0/`，数据库README仍称v1.0/v1.1.0，两者发布版本口径不一致，正式发布版本及迁移矩阵TBD。此次不改历史SQL、不执行开发/生产升级；验收夹具有自己的隔离初始化流程。

真实部署前仍需独立确认凭据外置与轮换、普通应用/迁移角色权限、备份恢复、TLS及跨主机服务认证、网络/端口/存储隔离、资源限制、健康/就绪检查、升级及回滚、持续运维与依赖镜像固定。人工审批、幂等键、只追加Timeline和UNKNOWN不自动重试协议必须保持。本Spec H未交付常驻Compose；后续Spec K的本机演示另记。应用Dockerfile/Kubernetes、真实Broker/模型/Milvus/OTLP、跨主机及生产部署仍未交付或验收。

本地artifacts.json为本轮文件完整性索引，记录相对路径和SHA256，不包含正文，也不属于CI可发布白名单。检查结果不能由索引存在本身推断；要同时核对run状态、阶段退出、报告/来源与完整性。
