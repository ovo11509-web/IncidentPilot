# 实施记录：B 风格值班工作台

用户于2026-10-10明确要求“进行实施”，批准现有规格、计划与任务范围。

基线：分支 `feature/incident-platform`，HEAD `67624931f20812922568d3641a095adb291263ba`，暂存区为空。变更前状态和文件哈希保存在忽略的 `incident/target/workbench-20261010/`。已有修改和未跟踪文件保留；不提交、推送或部署。

## 验证记录

Docker Desktop 首次引擎检查失败，随后实际检查确认 Linux 引擎 29.4.3 可用。Node20.19.5、Java21.0.12、MavenWrapper3.9.11。

- 实现前运行新测试，因缺少新增Mapper/Service/Controller编译失败；该记录只证明缺失能力，不是行为测试通过。
- 新接口及既有查询回归：10项全部通过，失败0、错误0、跳过0。命令：`./mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentWorkbenchContractTest,IncidentWorkbenchQueryIntegrationTest,IncidentQueryContractTest,ActionQueryContractTest,IncidentOperationsQueryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`。真实PostgreSQL16覆盖全部动作/事件状态、计数、列表限额/排序、桶边界、非UTC时区、重复身份约束、白名单和两个连接间已提交写入下的一致快照。日志 `incident/target/workbench-20261010/tests-final.log`。
- 前端 `node tests/incident-view.check.mjs`、`npm run lint`、`npm run build` 均通过。已有大包警告保留；不声称容量/性能达标。
- 最终前端源码再次执行helper检查、全量 `tsc --noEmit -p tsconfig.app.json`、ESLint与production build全部通过；日志以frontend-*-final.log保存。新增快照并发测试进一步改为直接调用实际QueryService、用代理Mapper在首次数据库读后提交另一连接写入，四项真实SQL检查再次通过（tests-snapshot-final.log），而非只验证测试自建事务模板。
- 隔离UI应用第一次启动超过原测试夹具HTTP就绪期限。当时编译、测试及构建同时执行；这是现场观察，资源竞争是可能原因而非已证实结论。现已串行重新启动，浏览器验收待完成。

## 本轮实际应用与浏览器功能核对

串行重启成功。隔离栈包括临时主PostgreSQL、模拟PostgreSQL、Redis、RustFS、真实Bootstrap/MCP双JVM和Vite。主应用端口59430，预览端口62532；随机测试凭据仅存在忽略的本地target文件，无日志/文档/截图传播。夹具约两小时自动清理，不是常驻交付启动方式。

证据目录：`incident/target/ui-evaluation/spec-i-2026-10-10T15-31-00-805682200Z/`。

- 首页初始四卡为活跃25、待审批1、执行中0、UNKNOWN1，与独立数据库查询一致。响应24桶、关注最多8、待审批1；图表接收26条实际保存告警。管理员登录默认首页、普通用户登录/chat且直接访问/admin/workbench回/chat；真实HTTP匿名401/普通用户403记录在security-gates.json。
- 待审批深链聚焦 `action-2108943395102654464`，完整冻结目标/参数/摘要可读。选择UNKNOWN动作后摘要明确未知，动作区域无写操作按钮。
- 浏览器明确拒绝 `2108943395102654464` 后持久化APPROVAL_REJECTED；server-evidence-1.json显示simulationApplications=0、目标PAUSED。
- 真实告警Incident `2108943390577000448` 发起只读诊断 `2108945205125185536`，显式选择R2生成 `2108945389188022272`，再独立人工批准。server-evidence-2.json显示simulationApplications=1、目标READY，Action=SUCCEEDED，实际票据CONSUMED、回执SUCCESS/SIMULATION_APPLIED、applied=true。无重复应用；Incident仍独立保持OPEN。
- 夹具提交第三条实际告警后旧诊断stale=true，R2转换按钮禁用，关联流程显示“诊断证据已失效或待复核”。历史成功执行保留。拒绝/批准/失效的实际DOM分别保存rejection-dom.txt、approval-dom.txt、stale-dom.txt。
- 1440×900、1280×720、768×1024、390×844的稳定DOM测量：documentWidth均等于视口宽度；桌面四卡、窄屏两卡，390时图表实际宽268px。侧栏和主区overflowY=auto，表格overflowX=auto；导航到末项产生独立侧栏scrollTop约328px。responsive-dom.json记录稳定测量，不以折叠动画中间尺寸作为结论。
- 旧系统设置、知识库列表/新建弹窗（打开后取消）和AI运行概览实际导航回归；系统设置内容仍是原浅色文字/内容主题。
- 390×844事件详情另测documentWidth=390，长绑定摘要wordBreak=break-all，9个证据details可达。键盘Tab从刷新按钮进入流程所选动作，实际焦点轮廓为67d6ff实线约2px。已恢复默认视口并保留管理员首页预览标签页。

## 对比度与剩余验证边界

范围内实际主题颜色计算：正文edf4ff/0e1930=15.82:1，辅文a5b5cc/15253d=7.39:1，链接81dfff/0e1930=11.61:1，警告ffd878/372c18=9.99:1。输入边界5a7a9b/101d33=3.77:1，outline按钮边界使用同色，焦点67d6ff/13213a=9.68:1；状态保留文字，装饰pointer-events:none，reduced-motion关闭动画。

收尾核对：新代码仅在已批准范围，未增加依赖/Schema/审批写重试。变更前文件哈希审计无缺失文件，原其他修改保留；git diff --check通过，暂存区为空，分支/HEAD未改变，LICENSE与上游归属保留。未提交、推送、部署或调用付费模型。任务28/29完成，T027的运行截图及人工滚轮视觉复核未关闭。

浏览器截图API及其公开替代截图入口均返回无法捕获截图，实际滚轮输入也未产生可证明的滚动变化。已完成上述真实DOM/角色/业务回归，但没有运行截图，也未确认人工鼠标滚轮手感及完整视觉裁切。T027保持未完成；概念图不作为本轮运行截图。当前可直接打开本机预览复核。没有验证Java17运行、Linux、Node22、托管CI、真实模型或生产部署，不声称性能/生产验收完成。
# 2026-10-12：Spec K续跑补充证据

此前截图/滚轮受限记录保留原时点。Spec K已用真实持久演示复核侧栏滚轮0→768→0、四视口文档无横向溢出及真实拒绝零/批准一次模拟应用；最终图标收缩问题已修正，390视口稳定侧栏54px、普通图标16px/Bot18px。当前截图采集仍间歇失败，过渡帧不作为稳定布局截图。原T027仍保留完整视觉及旧配置环境回归边界，不能用只开放Incident的演示环境替代旧知识/AI功能验收。详见 [Spec K实施记录](../011-incident-quality-delivery/implementation-report.md)。

续跑最终通过公开getScreenshot取得并查看默认尺寸工作台和稳定390窄屏图，保存在ignored `frontend/.output/spec-k-final-workbench.png`、`spec-k-final-390.png`。原任务剩余边界现在为完整旧配置环境视觉回归，不再笼统声称所有运行截图/滚轮证据缺失。

## 2026-10-12：T027最终页面验收

用户批准下一步页面验收与完整交付门禁。复用现有 `IncidentOperationsUiFixture` 启动独立临时四依赖、真实Bootstrap/MCP与Vite；此次未重做已通过的审批业务写入，未调用模型。证据目录为 `incident/target/ui-evaluation/spec-i-2026-10-12T11-01-04-897616300Z/`，安全页面事实记录在 `browser-acceptance.json`。

- 普通用户通过实际登录表单进入 `/chat`，直接访问 `/admin/workbench` 回到 `/chat`，退出回到 `/login`；管理员实际登录进入 `/admin/workbench`。没有用管理员会话冒充普通角色验收。
- 规定的1440×900、1280×720、768×1024、390×844均实际截图并查看；documentWidth分别等于1440/1280/768/390。桌面侧栏236px，768下64px、390下54px；收起图标普通16px、Bot18px，桌面四卡、窄屏两卡。截图为 `workbench-1440.png`、`workbench-1280.png`、`workbench-768.png`、`workbench-390.png`。
- 原生滚轮令侧栏scrollTop从0到554.24再回0（clientHeight545、scrollHeight1100）；品牌与底部固定。`sidebar-wheel-bottom.png`记录末项可见状态。键盘Enter从侧栏事件中心进入 `/admin/incidents`，临时视口随后恢复。
- 旧系统设置真实配置/表格、知识库真实空列表及新建弹窗打开后取消、AI运行概览真实空统计均截图并查看，内容可读且保留旧浅色主题。截图为 `settings.png`、`knowledge-dialog.png`、`ai-overview.png`；未创建知识或运行入库。
- 此次夹具数据库的模拟目标仍PAUSED、应用数0；UNKNOWN是显示夹具。拒绝零/批准一次应用的业务证据仍来自上文2026-10-10明确记录的运行，以及Spec K的COMPLETE/严格重启证明，不将它们写成本次视觉夹具的新审批操作。

独立只读复核确认原T027缺口已补齐，任务29/29完成。此前截图失败记录保留原时点，不作为当前未完成状态。临时UI夹具及延迟代理由自身停止标记正常关闭；既有持久演示资源和数据库保留。此次未改生产源码、提交、推送或部署；Linux/Node22/Java17、托管CI、真实模型与生产边界继续保留。
