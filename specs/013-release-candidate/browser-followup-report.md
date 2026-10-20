# 冷态取证与真实浏览器复核记录

> 后续：[存储日志卷修复](storage-volume-fix-report.md) 在新受控容器确认旧模板匿名`/logs`机制，新实例显式命名卷及三轮依赖启停已验证。旧`e98d…`卷的历史唯一归属仍TBD，本报告保留原发现与失败，不把修复结果回填到旧候选。

2026-10-17。用户继续下一步并在本机完成登录后，执行阶段9。**服务进程首次静态读取、热态对照和指定浏览器只读链路通过；原首轮静态异常仍未复现、根因TBD。收尾另发现新增匿名卷，不能宣布交付全绿。** 本轮未改业务源码、冻结候选或Git历史，未提交、Tag、推送或部署。

## 输入与实际边界

- 本轮run：`6048ec61-2155-4a28-af26-c9394dddb023`；证据位于`bootstrap/target/candidate-followup/6048ec61-2155-4a28-af26-c9394dddb023/`，下文辅助文件名均相对此目录。
- 复用[上轮复现](reproduction-report.md)的run `c7dd6bac-3f32-4ac3-bd05-b05d97ccd931`、固定源码候选`01166dd3-0a4c-4def-83dd-688e6c15e8bd`和同一保留数据实例`candidate-proof`，instanceId为`7c657a7a0f1040f1914f2fd7b4192946`。没有初始化新库、重做诊断或审批。
- 原库起始1899文件及Git身份、16个既有容器、80个卷另存基线。沿用Windows、Java21、Node20及本机缓存边界，不代表第二机器、Linux、Node22、Java17或托管CI。
- 冷态仅指从停止状态启动新服务进程后、浏览器进入前的首次静态读取；没有清除操作系统页缓存或证明机器/浏览器冷启动。本轮未另存启动时逐PID快照，启动READY、执行顺序及停止检查分层记录，不能冒称额外PID时间证据。

## 静态读取结果

冻结辅助探针`cold-static.mjs`的SHA256为`AF3B5C891BA746875816524B8471B23DEE0210975A0707D05F13AD508BAB6C1B`，参考旧`read-proof.json`摘要为`36DCD6564F63FB1F00794CA11CC938B40AFDC00C0E44A608E0699108F8637708`。保留原30,000ms阈值、路径/大小/哈希断言和`arrayBuffer`读取方式；增加步骤前后、资源序号、状态、耗时与白名单错误类型，不输出URL、响应正文或凭据。

实际命令均从仓库根调用本轮证据目录中的辅助脚本：

```powershell
node --check bootstrap/target/candidate-followup/6048ec61-2155-4a28-af26-c9394dddb023/cold-static.mjs
node bootstrap/target/candidate-followup/6048ec61-2155-4a28-af26-c9394dddb023/cold-static.mjs --self-test
pwsh -NoProfile -File bootstrap/target/candidate-followup/6048ec61-2155-4a28-af26-c9394dddb023/lifecycle.ps1 -Command Start
node bootstrap/target/candidate-followup/6048ec61-2155-4a28-af26-c9394dddb023/cold-static.mjs cold
node bootstrap/target/candidate-followup/6048ec61-2155-4a28-af26-c9394dddb023/cold-static.mjs warm
```

| 检查 | 实际结果 |
| --- | --- |
| 语法/无网络自检 | 退出0；哈希、路径、输出安全检查通过，自检网络0/写文件0 |
| 服务进程首次读取 | 115/115 GET通过，含登录HTML及114个JS/CSS，全部200且大小/哈希匹配；2条入口引用匹配 |
| 热态同序对照 | 同样115/115、200及大小/哈希一致 |
| 分阶段证据 | 每轮946行，473组BEFORE/AFTER完整配对；含URL解析、本地入口及资源头/体读取 |

两轮探针总耗时分别约2217ms和2119ms，只是本机此次采集耗时，不作为吞吐、延迟分位或性能提升指标。旧首轮`HTTP_STATIC/VALIDATION_FAILED`原文件未覆盖；本轮结果均标记`TBD_NOT_REPRODUCED_NOT_DECLARED_FIXED`。辅助生命周期封装首试因点入库脚本覆盖`$Command`而仅执行Status，实际未启动；保存请求动作并断言返回状态后才完成Start，不将它记作产品启动失败。

## 浏览器实际复核

使用内置浏览器真实页面，保留其563×506视口；用户从私有配置自行输入密码并登录，工具未读取或填入密码。以下结论来自可见页面、交互、DOM及截图，不借用旧HTTP结果冒充浏览器交互。

| 项目 | 实际看到及操作 |
| --- | --- |
| 工作台 | 活跃事件1；同一事件关联2告警，审批/执行/未知队列为0；点击事件链接进入详情 |
| 拒绝动作 | 下拉选择`2111335136946552832`，流程显示已拒绝、尚无执行结果 |
| 成功动作 | 选择`2111335142067798016`，显示已批准、执行成功；既有Execution `2111335143972012032`、回执`2111335149924282368`为SUCCESS/SIMULATION_APPLIED/APPLIED，`desiredState=READY · applied=true` |
| 既有诊断 | 展开`2111335134794874880`，COMPLETED/SUPPORTED/VALID；可展开冻结来源与C1引用。页面明确根因未确认、缺指标/日志/只读观测，不误称真实故障修复 |
| 复盘 | 从详情进入并等到采集完成，告警2/2、诊断1、动作2、时间线14；说明非事务快照、事件尚未解决。只检查下载按钮可用，本轮未下载Markdown |
| 同版本手册 | 点击冻结引用链接，RB-HEALTH v1.0.0；展开文档/章节摘要与symptoms原文。文档摘要`94787207f85e06ab23e4ff382de93722a1925b03672ad431c2bfd335c9607adc`、章节摘要`bbc50336ee58fd7e4328eb9443ba8ee01d295b42e5027c2976e633c7bdb54fb5`与引用一致 |
| 侧栏 | 展开后在导航内实际滚动，scrollTop从0到768，clientHeight331、scrollHeight1100；可看到系统设置，再收起侧栏 |
| 退出保护 | 用户菜单退出，显示已退出；直接再次进入已知事件地址，实际重定向`/login`，不显示事件内容 |

没有点击发起新诊断、创建提案、拒绝或批准。浏览器控制台采集到一条图表初始尺寸`width(-1)/height(-1)`警告；本轮页面正常完成渲染，未将该警告修复或当作不存在。布局含上游Star公共查询及字体资源，未测量全部出站流量，不声明浏览器零出站或完全离线。

截图及DOM原件：`workbench.jpg`、`diagnosis-citation.jpg`、`review-overview.jpg`、`review.jpg`、`runbook.jpg`、`sidebar-scroll.jpg`、`logout-guard.jpg`，以及`diagnosis.dom.txt`、`review.dom.txt`、`runbook.dom.txt`、`logout-guard.dom.txt`。其中`diagnosis-citation.jpg`实际保存的是诊断候选及缺失事实视图，引用摘要以DOM和手册截图核对。

## 业务留存与停止

实际运行`business-check.ps1`、生命周期`Verify`、`Stop`及`resource-check.ps1`，未重跑构建或完整Backend/All，因为冻结源码未变。

- SQL明确开启READ ONLY事务：告警2、事件1、诊断请求1、Action2、审批2、Execution1、Receipt1、Timeline14；模拟应用总数1、目标READY。与旧结果和浏览器ID一致，不将本轮查看算作新业务验证样本。
- Verify读到同一事件版本2、诊断及两个Action/审批、同一回执，无后续分页；登录/退出属于允许的认证状态变化。
- Stop返回STOPPED/dataRetained；后续CIM及监听端口检查显示本实例Java/Node服务进程0、监听端口0、自有容器0，4个命名卷保留。原有16容器ID、名称、running与startedAt均一致，原80卷无缺失。
- **严格卷集合相等检查首轮FAIL**：当前81卷，比基线多1个匿名卷`e98d0e0926c2e4a90993c1cb2e9609820713c017056cb25ffb80ac9e81b46a3b`，创建于07:26:43Z，与本轮Start时段一致。RustFS镜像声明`/data`和`/logs`，Compose仅显式挂载`/data`，因此`/logs`是有代码依据的可能来源；历史Docker事件无返回，停止前未另存Mounts，归属仍TBD，不能冒称独立证实。未删除该卷或清理其他资源。
- `resource-first-failure.json`保留失败；最终`resource-preservation.json`状态明确为`OWN_STOPPED_BASELINE_PRESERVED_WITH_ADDED_VOLUME_TBD`，`volumeSetUnchanged=false`。后续检查区分“原资源保留”和“新增卷”，并没有把严格检查改成全绿。

## 收尾与后续

固定候选四文件摘要与原基线一致；新source按manifest核对1890文件，缺失/大小/哈希差异0。原库分支、HEAD、index、全部refs及worktree保持，仅本轮规格/说明文件更新，最终差异另存`closeout-verification.json`。不覆盖原候选、历史附件或旧失败记录；正式整合提交/Tag仍需用户授权。

当前仍有两项开放问题：首轮静态异常根因TBD；新增匿名卷归属及重复启停的资源留存问题。建议下一步先对存储容器Mounts定向取证，确认后修正日志挂载并验证多轮启停卷数稳定，再生成新的修正候选，保留旧包及证据。该修正不回填本轮冻结快照。第二机器、承诺平台和新完整Backend/All也仍需独立记录。
