# Spec K 实施记录

状态：2026-10-12本轮已批准范围收尾，27/27任务完成。以下阶段性记录保留原时点，当前结论以最后的收尾核对为准；单列未验证平台与视觉边界，不等同整项目生产交付。

## 基线与授权

- 实际分支 `feature/incident-platform`，HEAD `67624931f20812922568d3641a095adb291263ba`；已有修改/未跟踪文件保留。
- 用户明确批准六项审查建议；本地代码/运行验证，不含提交、推送、远程部署或付费调用。
- 内建规格质量检查10/10。实际运行setup-plan/setup-tasks/check-prerequisites，中文覆盖模板生效。
- 一致性分析无阻塞冲突；15项功能需求语义覆盖26项任务，任务ID无重复。FR缩写范围的纯字符串扫描会误报缺失，覆盖按实际任务语义核对。

## 需求覆盖

| 需求 | 任务 |
| --- | --- |
| FR-001/002 | T004/005/008 |
| FR-003 | T006/007/008 |
| FR-004/005/006 | T009/010/025 |
| FR-007 | T009/011/025 |
| FR-008/009/010/011 | T012～018/027 |
| FR-012/013 | T019～021/023/025 |
| FR-014 | T022/023/025 |
| FR-015 | T008/016～018/023～026 |

## 实际检查

### 额度中断后的现场恢复（2026-10-11）

实际Git分支/HEAD和最近提交未变，已有工作区修改与未跟踪文件全部保留。任务清单20/27已勾选；最后保存的Maven日志为查询11项通过及筛选9项通过，未发现最终相关回归日志、继续运行的Java/Maven或新的端到端演练proof。前端最后登录页修改已保存，现有dist尚需最终Build更新。恢复从最终回归及T016～018/023～026续跑，不重做已通过红/绿测，不清理无关进程、容器或数据库。

### 本轮已取得的检查

- Wrapper必要修补（T027）：普通缓存目录Target=null，原入口报空数组且未进入测试。修补后 `./mvnw.cmd -v` 退出0，实际Maven3.9.11/JDK21.0.12。初始26任务增加此必要任务，共27项。
- 新增查询红测实际执行4项、4失败、0错误/跳过：get/list复现旧Action与新Approval混合，多动作Timeline读2次、多诊断事实捕获2次。此前Wrapper、依赖缓存及编译环境受阻没有计为行为红测。修复后聚焦11项0失败/错误/跳过、BUILD SUCCESS，包含真实双连接交错、Timeline预算与诊断共同观察；日志分别为本机ignored的 `incident/target/spec-k-query-red.log` 与 `spec-k-query-green.log`。
- 前端 `node frontend/tests/incident-view.check.mjs`、TypeScript `--noEmit`、`npm run lint`、`npm run build`、frontend diff检查实际通过。新目标/超时/阶段/取消和请求分类检查包含真实Response/fetch边界；请求定位只接受受控原因码及UUID，不复制自由响应字段。
- 最终Vite构建5615 modules；主入口352.91 kB/gzip116.13 kB，工作台340.19/102.33、详情18.90/6.53、复盘17.64/6.46、列表5.58/2.12、手册6.10/2.46 kB。与Spec J记录的旧主包3979.36/1225.50 kB相比，路由拆分减少主入口体积；不能据此声称页面总下载量或实测加载速度。旧知识图谱/表格等大chunk警告仍存在。
- 本地Init已创建独立四容器/持久卷、全量主Schema和随机本地账号；MCP模拟目标初始PAUSED。目录仅本机当前用户可访问，不输出凭据；真实JAR启动和重启验证仍待完成。
- 本地脚本最终10项契约及Node真实HTTP代理1项通过，含真实子进程排他锁冲突；重复操作返回LIFECYCLE_BUSY；HTTP范围过滤核对规范化servletPath并拒绝编码/dot等绕过，登录/退出仅POST。重复Init实际通过，不重新应用Schema或重置凭据。Java装配测试及打包仍待执行。
- 筛选真实SQL红测3项、3失败、0错误/跳过，复现未知q被忽略、输入上限未生效和分页后混入不匹配服务；修复后第二轮聚焦9项0失败/错误/跳过。覆盖字面%/_、NFC、同成员组合、未关联告警排除、无成员ID搜索、摘要归属/秘密清洗及分页。
- 普通角色浏览器回归需要真实账号；自动审批最初拒绝新增账号，用户随后明确批准。在本轮自有主库创建local-reader（role=user），私有reader.json保存随机凭据，不扩大权限、不输出凭据；前次误用数据库名称的INSERT没有执行，改用实际Compose数据库incidentpilot后成功。
- 恢复后实际完成最后相关回归29项，0失败/错误/跳过，BUILD SUCCESS；覆盖新增诊断超时/逐项适用性、筛选、审批竞争、回执、UNKNOWN收敛、告警到模拟处置、工作台及诊断API/持久化/快照。日志为ignored `incident/target/spec-k-final-regression.log`，未把此前通过集合全部重复跑。
- 恢复后的子进程环境覆盖关闭检查单独通过；脚本契约现共11项（旧10项及Node代理已通过，未为恢复重复运行）。
- `LocalDemoConfigurationTest` 实际执行2项，0失败/错误/跳过，验证HTTP范围及Broker拒绝装配；真实Build开始，未使用测试classpath启动。
- 真实Build退出0，已生成首轮JAR与最后登录文案更新后的production dist；审阅发现Compose环境覆盖及Stop配置漂移阻挡，按原契约修补。关于Listener缺失的初步推断后来被真实JAR启动否定，见下文纠正；前端制品保留，后续仅定向Java验证及重新打包。
- 首轮装配最小Spring上下文报缺少DelegatingTransactionListener（1 error）；该context没有覆盖真实组件包扫描，不能计为主应用缺Bean的行为红测。临时增加local-demo Bean后局部3项虽绿，真实JAR反而重复注册：上游 `@RocketMQTransactionListener` 元注解已有 `@Component`。已移除冗余Bean，改测实际producer包扫描、恰好一个Listener和两个checker，最终结果另记。生命周期阶段性17项、Node真实HTTP代理2项通过，覆盖Compose变量清空/恢复、重复Init检测配置漂移、app配置漂移后可停止自有真实子进程、Compose自身漂移仍拒绝停止及留卷。普通受限环境的CIM失败单列，授权升级后的完整检查退出0。
- 修补后local-demo装配最终3项0失败/错误/跳过，包含现有两类知识入库checker的真实Spring上下文。最后前端Build 1m29s，主入口352.33 kB/gzip116.11 kB、工作台339.40/102.33 kB；前文数字保留为此前构建记录，以本段作为最终发布制品记录。
- Java首轮重新打包及制品复制均退出0；首次Start在本机依赖/native命令阶段报LOCAL_COMMAND_FAILED。后续修复真正删除Compose环境键、宿主机端口未发布和properties映射键冒号未转义；MCP真实JAR已启动，主应用暴露上述Listener重复注册，目前正定向重测及打包。数据库、密码、reader及四个持久卷保留，尚未运行告警演练。依赖分阶段诊断保留在本机私有日志，不将已构建等同运行验收。
- 对Git非ignored文件进行本轮生成秘密字面核对：1866个文件、10项私有随机字段，0命中；只保存计数与路径级结果，没有输出凭据。这是本地已生成秘密的泄漏核对，不等同生产全类秘密认证。
- 最终装配测试已改为真实producer包扫描，3项0失败/错误/跳过（20:25:43）；恰好一个上游Listener、两个现有checker且拒绝Producer事务副作用。Java再次打包BUILD SUCCESS（20:26:47）；前端无需重建。生命周期最新19项及Node2项通过，含原生环境无键与JDK properties键读取，日志为ignored `bootstrap/target/local-demo-contract.log`。
- 真实本地启动、完整演练、重启及浏览器结果待核对；不能用Spec J或旧XML替代。

## 本机实际运行与重启结果（2026-10-11）

- 真实JAR和production dist启动成功，主应用/MCP/Web均为自有进程，监听回环地址；独立四容器和卷保留。标准Verify读取管理员42、五类Runbook；普通用户43的工作台/目录/事件接口均403，匿名401。
- 实际演练runId `dcdda143-a3a2-4b00-ba0d-7d0e1d619388`：两条HMAC告警聚合到Incident `2109261944274817024`，OPEN/version2/alertCount2；诊断 `2109261945621188608` 为SUPPORTED/VALID。拒绝动作 `2109261947093389312` 无Execution、零应用；另建批准动作 `2109261951677763584` SUCCEEDED，回执 `2109261957684617216` SUCCESS/APPLIED，模拟目标demo从PAUSED到READY，应用记录总计及该Execution匹配均1。Timeline14条；处置成功不自动意味着事件解决。
- 验证脚本首次把人员Authorization混入HMAC Webhook，服务端按协议401拒绝；精确身份只读核对确认没有告警或副作用后，仅修正验证脚本并按已知拒绝的一次性恢复条件续跑。没有重试未知写结果，没有改弱服务端鉴权。原失败、恢复记录和最终COMPLETE证明均保留。
- Verify-before→Stop→Start→Verify-after及严格比较成功，账户、同一Incident/诊断/拒绝与批准动作/回执保持不变，四卷保留，三进程重新创建；Java和前端制品哈希未变。私有证据：`bootstrap/target/local-demo/default/restart-before.json`、`restart-after.json`、`restart-proof.json`。
- 真实GET复盘采集得到2告警/1诊断/2动作/14Timeline，阶段计数、冻结目标和回执投影通过；在告警阶段触发真实Abort后抛AbortError且不导出。安全Markdown与证明存放ignored `frontend/.output/spec-k-runtime-review.md`、`spec-k-runtime-review-proof.json`。它不是事务快照或报告签署。
- 实际管理员浏览器核对工作台真实统计、关键词/服务/环境/HIGH组合筛选、无匹配与清空恢复、详情拒绝/成功目标和复盘引用；四视口1440/1280/768/390没有文档横向溢出。侧栏滚轮从0到768再回0，品牌和底部区域固定。截图/DOM曾暴露收起图标被原生滚动条与左右padding挤至宽0：仅IncidentPilot范围将collapsed item左右padding改2px/gap0、图标flex-shrink0，保留Bot光学校正。
- 最终CSS修复后自检、lint、一次production build通过（5615 modules、1m21s）；入口JS352904字节/gzip116111，CSS262022/39514，工作台340186/102327，列表5576/2119，详情18903/6531，复盘17635/6463，手册6100/2459。旧上游大chunk警告保留；这些是制品体积而非总下载量或加载性能测试。
- 最后Stop→复制新dist→Start→Verify严格证明 `UI_REFRESH_VERIFIED`（2026-10-11 20:55），frontendChanged/javaUnchanged/factsUnchanged均true。`ui-refresh-before/after/proof.json`独立保存，不覆盖前次restart/start证明。

## 再次额度中断后的恢复（2026-10-12）

分支、HEAD、最近提交及49个已跟踪修改保持；当前任务清单23/27，T018/024/025/026未勾选，属于运行结果尚未回填与最后页面复核。上次最终dist复制及重启已经完成，不重新构建Java或重复播种。此次开始时Docker引擎和三个演示进程均未运行，登录只读检查返回TRANSPORT_UNKNOWN；这不是对已保存成功证明的否定。启动已安装Docker Desktop并恢复同一实例，保留数据库/卷。生成秘密字面核对1866个Git非ignored文件、10个私有随机字段、0命中；凭据不输出。

## 最后收尾核对（2026-10-12）

- 同一实例Start退出0、READY，三个自有进程及四依赖运行。只读 `spec-k-runtime-check.mjs -verify` 再次RESTART_VERIFIED，同一Incident/诊断/批准动作、matchingApplications=1；`spec-k-review-check.mjs` 再次REAL_REQUEST_REVIEW_VERIFIED，2/1/2/14条及cancelExportBlocked=true。没有新增业务写入。
- 原local-admin浏览器会话仍有效，实际工作台统计1活跃/0待审批/0执行/0未知，原事件详情及报告再次加载；报告展示拒绝与成功动作各自SIM_SERVICE/demo/READY及APPLIED回执。浏览器点击下载成功，实际文件 `IncidentPilot-incident-2109261944274817024.md` 已读核对，采集阶段可见。
- 最终四视口1440/1280/768/390的documentWidth等于viewport；390收起稳定侧栏54px，普通图标16×16、Bot18×18且flex-shrink=0。临时viewport已reset。截图采集仍有间歇失败；首次390截图捕获展开→收起过渡中间态，不作为稳定布局验收截图。先前视觉观察与当前稳定DOM分别记录，未冒充完整最终截图。
- 随后公开 `getScreenshot()` 取得默认尺寸工作台及稳定390窄屏最终截图，已实际查看：四/两列统计布局、54px收起侧栏正常图标、主表容器内横向查看，无文档溢出。ignored证据 `frontend/.output/spec-k-final-workbench.png`、`spec-k-final-390.png`；稳定图覆盖过渡帧，临时viewport再次reset，工作台保留供用户查看。截图工具的间歇失败不等同页面失败。
- 浏览器本轮取消点击时请求约0.55秒完成，未取得可靠取消后页面状态；真实HTTP采集helper中Abort和禁止导出、前端确定性取消检查已通过。普通角色实际API403与已有路由守卫检查通过，本轮不把管理员会话当作普通角色完整浏览器回归。Spec I完整视觉/旧配置回归独立保留。
- 最终相关后端回归29项、装配3项、生命周期19项、Node代理2项均0失败/错误/跳过；前端自检/TypeScript/lint/build有本轮通过记录。集合存在覆盖，不把各次计数相加宣称独立用例总数；此次未重跑已冻结Java检查或全仓门禁。
- 文档、任务、查询/前端/运行契约已按当前结果交叉回填；`git diff --check`通过，暂存为空，分支/HEAD/历史未变。Apache-2.0与上游署名保留，生成秘密扫描0命中。未commit/push/merge/远程部署/付费调用，也未清理无关文件、容器或数据库。

## 未验证边界

Linux/Node22/Java17运行、托管CI、生产、真实模型/基础设施尚未验证。此前偶发浏览器采集失败根因仍未确定，新错误定位不能自动视为已根治。

## 2026-10-12：批准的后续页面验收与全仓门禁

用户批准先执行页面剩余验收及完整当前源码交付门禁，不扩展功能或授权Git发布。独立完整UI夹具补齐Spec I四视口截图、普通角色跳转/退出、原生滚轮/键盘及旧配置视觉，T027关闭、29/29；真实GET延迟下复盘取消提示和下载禁用已取得截图。持久演示恢复同一实例后，既有报告/手册桌面与390窄屏、正文滚轮均实际截图查看，未产生新业务写入。详细事实分别见 [Spec I记录](../009-incidentpilot-workbench/implementation-report.md) 与 [Spec J记录](../010-incident-review-runbooks/implementation-report.md)。

首轮完整门禁 `3cd04b58-54e6-4c50-a067-788c8014c39c` 在Backend的agent→bootstrap阶段附近中断；进程已退出，run仍RUNNING/INCOMPLETE，无Backend退出、stages或safe摘要。已落盘192份有效XML包含868项/847通过/21跳过/0失败/0错误，仅是部分结果，不能称All通过。bootstrap仅classpath警告，无XML；同类警告在rag之后仍产生正常报告，具体中断原因未确定。旧目录保留，不补写虚假成功。

独立后台新运行 `f989e36a-9e11-4f92-a719-2149762870f1` 使用固定 `pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate All`，与中断运行相同HEAD和源码摘要 `F73360C5C7B4489540C1256AA86783307C9EAF5A24A5A80759C5D6AB8A44F674`，生产/测试源码冻结。新运行与恢复演示均仅本地隔离资源，未提交/推送/部署或运行外部探针。

### 新All运行最终结果

- `PASS / VALIDATED`，开始 `2026-10-12T11:27:08.2510585Z`，结束 `2026-10-12T12:06:21.7972255Z`（本地20:06:21）。PowerShell7.6.5、Java21.0.12、Maven3.9.11、Docker Linux Engine29.4.3、Node20.19.5/npm10.8.2，应用测试仍在Windows宿主机运行。
- Backend、Install、IncidentView、Lint、Build五阶段均exit0/VALIDATED。Backend独立核对197份新XML：884项、863通过、21跳过、0失败、0错误；全部lab.runId/源码摘要及时间匹配，没有旧报告或重复计数。12必需类全部执行；13个受控手工类准确符合21项预算，无新增跳过。跳过仍为外部门关闭，不代表真实模型、Milvus、MinerU或外部搜索已验收。
- 最终源码复核通过，来源摘要与开始时一致。`bootstrap/target/delivery-gates/f989e36a-9e11-4f92-a719-2149762870f1/safe/summary.json` 是此轮可发布白名单摘要；只保留本机，未上传。artifacts.json共554项，独立逐项核对路径、存在性及SHA256匹配，除索引自身外覆盖完整。raw/XML/私有配置不属于发布白名单。
- 前端当前源码重新npm ci、自检、lint与production build通过；既有上游大chunk提示保留，不据构建通过声称加载性能达标。此次没有新增生产代码/依赖/Schema；仅更新任务/长期文档及ignored页面验证代理、运行证明和截图。
- 15张本轮页面验收图已实际查看并生成 `screenshot-index.json`；普通角色、旧配置、四视口、滚轮及取消证据与原持久演示事实分开。恢复后的标准Verify为VERIFIED，管理员42、5类Runbook和同一事件/动作/回执保留；工作台已实际显示并保留浏览器标签页。
- 最后Git差异检查通过、暂存为空，分支/HEAD未变；1866个Git非ignored文件与10个生成秘密字段字面核对0命中。未提交/推送/合并/部署，没有清理无关文件、容器或数据库。

页面与当前Windows源码完整门禁的两个已批准后续步骤完成。作者独立走读、正式版本/配置/署名材料整理及干净Linux/Node22/Java17、托管CI/生产验收继续分列；此前偶发浏览器采集错误根因仍未确定，不以本轮成功根治声明覆盖旧事实。
