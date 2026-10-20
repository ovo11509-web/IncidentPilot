# 实施记录：事件复盘与处置手册中心

日期：2026-10-11。实际分支 `feature/incident-platform`，保留既有修改/未跟踪文件；未提交、推送或部署。用户批准先执行推荐两项，Spec J 顺序完成 US1→US2。

## 实施结果

- 事件详情新增复盘入口，报告分页读取真实告警、诊断详情、动作与时间线，显示状态、历史引用、审批/回执与未解决事项。
- 明确跨请求时间范围、非事务快照；异常身份/游标/序号、截断、失败或首尾检测变化禁止导出。导出只选定字段并转义 Markdown，不导出任意 JSON/载荷/票据/凭据。
- Runbook 侧栏入口与 B 主题页面，五类打包模拟资料的搜索、范围、版本/有效期、作者、章节/行号/摘要及原文。报告和诊断引用带精确版本/摘要，缺失不回退，保存原文不被当前目录替代。
- Java GET 位于 rag，以管理员校验和 Map 投影复用 Corpus。无新增依赖、Schema、编辑/审批/执行流程。
- 运行核对发现旧 `.admin-layout .ui-input` 覆盖白底，新输入框浅字；提升 Incident 范围控件选择器优先级修复。实际背景 rgb(16,29,51)、文字 rgb(237,244,255)。

## 实际检查

1. `node tests/incident-view.check.mjs`（frontend）：退出0。新增采集分页、重复/不前进游标、首尾变化、跨事件、采集中取消、数量上限/最终页溢出、导出白名单/转义、UNKNOWN/失效快照、实际打包语料与版本不回退检查。实现前首次退出1，缺少新 helper；此记录只证明缺失实现，不称行为红测。
2. `node node_modules/typescript/bin/tsc --noEmit -p tsconfig.app.json`：退出0。
3. `npm run lint`：最终退出0。初次捕获常量循环及无用正则转义，已修复。
4. `npm run build`：最终退出0，1m8s。保留原大型 chunk 提示，本轮未改变打包架构。
5. 根目录 `./mvnw.cmd -o -Dspotless.apply.skip=true -pl rag -am -Dtest=RunbookCatalogControllerTest,RunbookCorpusTest,RunbookRetrievalTest -Dsurefire.failIfNoSpecifiedTests=false test`：BUILD SUCCESS；新目录契约3项、语料2项、检索1项，共6项，0失败/错误/跳过。仅核对本轮三类新 XML，没有把其他旧XML计入。首次新测试因 rag 的 subclass MockMaker 无法 mock final/static 失败，按现有模式在这些测试显式使用 INLINE 修复，不改变全模块配置。
6. `git diff --check`：通过；source manifest 及本轮输出位于 ignored target/.output。

前端最终日志 `frontend/.output/spec-j-{checks,tsc,lint,build}-final.log`；Java日志 `rag/target/spec-j-java.log`，首轮失败记录 `rag/target/runbook-catalog-red.log`。

## 本地运行与浏览器

启动前 Docker Linux engine 不可用；启动现有 Docker Desktop 后实测 linux。使用既有 IncidentOperationsUiFixture、现有测试 classpath 与本轮编译的 rag/classes，四个临时依赖、真实 Bootstrap/MCP JVM 和 Vite，不接开发数据库或付费模型。没有新增常驻部署。

本轮证据目录 `incident/target/ui-evaluation/spec-i-2026-10-11T02-18-49-912583900Z/`：名称来自既有夹具，内部 `spec-j-*` 为本轮新增证据。Vite 50749、主应用 50272 是本轮临时端口，不作为固定交付配置。

- 实际管理员登录→侧栏处置手册→五类目录；搜索 OOM 选择 Java OOM，范围/正文正确。
- 实际事件 `2109106420082278400`：两签名告警、后续本地诊断 `2109107945869410304`、显式 R2 提案 `2109108100987355136` 保持 PENDING_APPROVAL；报告展示2告警/1诊断/1动作/6条Timeline。没有批准或执行模拟写操作。
- 原无诊断事件的报告实际浏览器下载 Markdown，下载文件检查不含临时密码或测试 secret-value，包含真实两告警与非事务快照声明。后续真实数据由同一采集 helper 从主应用和 Vite代理分别读取成功，生成 `spec-j-runtime-review.md`；无假 API 响应。
- 浏览器采集曾出现 INVALID_ENVELOPE，后续返回主事件时另出现 TRANSPORT_UNKNOWN；手动重新采集恢复，完整刷新也有成功记录。同期同一采集 helper 从真实主应用与 Vite代理读取均成功。没有取得足以确定浏览器临时错误根因的网络证据，不将推测写为事实；此项保留待集中排查。
- Enter 展开历史 C1，引文仍为持久化原文；Enter 跳转 RB-HEALTH 1.0.0 和正确文档摘要。错误版本 missing 显示不匹配，没有回退。
- 既有 Incident100的 UNKNOWN 显示夹具在报告中继续“结果未知，不得自动重试”；这是显示验证，不是制造/对账真实 UNKNOWN 的新验收。
- 真实 HTTP Runbook：匿名401、普通用户403、管理员5类资料；摘要与完整目录查询正常。
- Report 1440/390宽下 documentWidth 等于 viewport，pageWidth约1125/352；Runbook约1301/288，未产生文档横向溢出。调整尺寸后鼠标自动化曾误点其他侧栏，恢复实际尺寸后用键盘验证成功；不能以虚拟尺寸 DOM 检查替代全部人工视觉/滚轮核对。原尺寸已恢复。

## 保留限制

- 页面/数据/主要交互已验证；截图 API 多次返回 Unable to capture screenshot，未保存或冒充完整运行截图。一次窗口截图没有形成最终页面证据；完整人工视觉与鼠标滚轮仍待补充，原 Spec I T027 不自动关闭。
- 报告不是事务快照，不保存/签署/归档，不生成确认根因。客户端上限不改变既有 Action 无分页/N+1查询成本，尚未验证大事件负载。
- 首版为本地模拟 Runbook，只读；没有在线编辑、反向全局引用索引、演练 GUI、真实模型/生产语料、常驻部署或生产验证。本轮未重跑整个后端/审批黄金路径，因为没有修改这些协议。
# 2026-10-12：Spec K续跑补充证据

同一持久演示真实报告再次读取2告警/1诊断/2动作/14Timeline，显示拒绝与成功动作目标SIM_SERVICE/demo/READY和APPLIED回执；浏览器再次成功下载实际Markdown。真实HTTP helper的阶段计数/Abort禁止导出通过；浏览器采集约0.55秒完成，本次取消点击没有形成可验证取消后状态。保留此前偶发采集故障根因未定、截图间歇失败与Spec I完整视觉边界。Spec K已减少Action/诊断共同事实重复读取，但未移除所有逐项查询、未做大事件性能实测。详见 [Spec K实施记录](../011-incident-quality-delivery/implementation-report.md)。

## 2026-10-12：浏览器取消补充验收

此前快速请求未能观察取消后的页面状态，本次通过ignored `frontend/.output/ui-acceptance-latency.mjs` 对同一隔离主应用的真实Incident GET响应增加1500ms延迟；状态、响应头和正文来自实际后端，不构造假业务响应，不延迟或重做审批写入。

浏览器在采集中点击取消，实际提示“已取消采集，请重新采集。”，下载Markdown按钮disabled，未生成或显示迟到报告。证据为 `incident/target/ui-evaluation/spec-i-2026-10-12T11-01-04-897616300Z/review-cancel.png` 和同目录 `browser-acceptance.json`。代理及临时夹具已按自有STOP标记关闭。此次关闭的是浏览器取消观察缺口；此前偶发浏览器错误根因未定、报告非事务快照和大事件性能未测仍保留。Spec I完整旧配置与四视口视觉验收已独立完成，见其实施记录。

同日恢复原持久演示的自有进程，数据库/四卷/业务事实保留，READY后管理员原会话直接读取既有事件。进一步实际查看1440×900与390×844的复盘和Runbook页面，documentWidth均等于viewport；窄屏原生滚轮到手册完整正文与复盘Timeline，长ID/原文自动换行，固定侧栏和操作按钮可达。原事件仍为2告警/1诊断/2动作/14Timeline，此次没有新的业务写入。新增运行截图 `review-1440.png`、`review-390.png`、`review-390-content.png`、`runbooks-1440.png`、`runbooks-390.png`、`runbooks-390-content.png` 位于同一证据目录，已查看；临时视口已恢复并保留工作台。历史截图受限说明不再代表当前缺少运行图。
