# 固定候选本机隔离复现记录

> 后续补充：[冷态取证与真实浏览器复核](browser-followup-report.md) 已完成指定只读链路，原静态异常仍TBD；新增匿名卷1的归属及日志挂载另待核验。本报告保留上轮时点及原始失败，不能把后续通过回填为首轮成功。

2026-10-17。用户在历史附件核验后要求“继续下一步”，执行固定源码的新目录、新实例业务复现。结论为：**本机隔离HTTP主链与停启留存通过；首轮补充静态采集失败原因TBD，未将全部验收宣称为无失败。** 本轮没有修改业务源码、提交、Tag、推送或部署。

## 输入与隔离范围

- 固定源码候选：`01166dd3-0a4c-4def-83dd-688e6c15e8bd`，1890文件；manifest SHA256为`4F3AC6EFF9C2933B8BAD48700202168A4EE7DF300F3BF0328F81D071119FC1F1`，ZIP SHA256为`D907650FF07A3A8DF5EABBC3B7DF298FB9097DBEC80286F695908E150A4E829C`。原四文件保持，Verify为`SOURCE_VERIFIED_HISTORY_WITHHELD`，历史附件另见 [历史处理记录](history-sanitization-report.md)。
- 复现runId：`c7dd6bac-3f32-4ac3-bd05-b05d97ccd931`；目录`bootstrap/target/candidate-reproduction/c7dd6bac-3f32-4ac3-bd05-b05d97ccd931/`，下文辅助文件路径均相对此目录。
- 从ZIP逐项验证后解包到新的`source/`；开始时没有`.git`、target、node_modules、dist或私有配置。使用候选自己的`local-demo.ps1`，没有从原开发目录复制JAR、dist、依赖目录、配置或数据库。
- 实例名`candidate-proof`，新instanceId为`7c657a7a0f1040f1914f2fd7b4192946`，独占Compose project及4个新卷。Init返回`reused=false`；用户名按入口固定为`local-admin`，密码及内部服务身份新随机生成并仅存在本轮私有目录。
- 明确复用同机已有Maven/npm缓存及4个Docker镜像；Java21.0.12、Node20.19.5、npm10.8.2、pwsh7.6.5、Docker29.4.3。Maven使用`-o`，npm设置`npm_config_offline=true`；未自动拉镜像或补下载依赖。

这是本机隔离资源复现，**不是第二台机器、无缓存机器、Linux/Node22/Java17或托管CI验收**。没有设置操作系统网络防火墙或测量全部网络流量，不作“所有进程零出站”证明。

## 实际执行与事实

按候选文档执行Init→Build→Start→实际业务请求→Verify/Stop/Start/Verify→重启后业务只读复核→Stop。所有生命周期调用明确带`-Name candidate-proof`，源码根来自本轮`source/`，没有调用旧default实例。

| 项目 | 实测事实 |
| --- | --- |
| 空白起点 | 新主库告警0、Incident 0，1个新local-admin；模拟应用0，目标PAUSED |
| 从源码构建 | Maven九模块BUILD SUCCESS，业务测试按Build约定跳过；npm锁文件安装和production build成功，得到两个准确0.1.0 executable JAR |
| 启动制品 | 本轮发布app.jar/mcp.jar与新构建字节完全一致；完整dist的117文件路径、大小、SHA256一致 |
| 就绪与身份 | MCP/主应用匿名401，静态服务200；经真实web代理登录，读取用户id42/admin；不是测试classpath或UI fixture |
| 告警聚合 | 2次真实HMAC HTTP告警受理并PROCESSED，归属同一Incident、alertCount=2 |
| 诊断 | 1次诊断COMPLETED/SUPPORTED；5条引用的quote、行范围、chunkDigest及1份文档快照的documentDigest与冻结源码一致；建议引用关联核对通过 |
| 拒绝 | 真实REJECT后为APPROVAL_REJECTED，无Execution；模拟库应用仍为0、目标仍PAUSED |
| 批准 | 另一个Action显式APPROVE后SUCCEEDED，SUCCESS/APPLIED回执关联正确；模拟库总应用1、同executionKey应用1、目标READY |
| 重复意图 | 成功后用相同sourceRequestId、原意图和originalActionId再次请求创建Action，返回同一Action；SQL核对仍只有1次应用 |
| Stop/Start保留 | 原3个进程结束、重启为新进程身份；4卷保留，凭据/制品摘要不变，账户、事件、诊断、审批和回执事实一致；重启后独立SQL再核对应用仍为1 |
| 静态HTTP | 成功复核读取/login及全部发布JS/CSS，共115项，响应状态和字节摘要与制品一致；后续独立3轮各115项一致 |
| 收尾 | 最后Stop成功，本轮进程/容器停止，4卷保留；原有16容器身份/运行状态保持，原有74卷无缺失 |

业务运行标识为`9085a80a-5d34-4783-8d8c-28974267bf10`，Incident为`2111335131301019648`、Diagnosis为`2111335134794874880`、批准Action为`2111335142067798016`、回执为`2111335149924282368`。这些只属于本轮独立模拟库，不是生产数据。

上述重复验证覆盖“相同选择意图重放返回同Action、未新增模拟应用”，不能扩大成重复执行所有MCP协议或全部并发变体均已重测。主链只是一组确定性本地黄金路径，不代表可靠率、吞吐容量或未知故障分布。

## 执行命令与辅助材料

辅助调度器`lifecycle.ps1`固定指向本轮`source/scripts/local-demo.ps1`，实际命令包括：

```powershell
pwsh -NoProfile -File <本轮目录>/lifecycle.ps1 -Command Init
pwsh -NoProfile -File <本轮目录>/lifecycle.ps1 -Command Build
pwsh -NoProfile -File <本轮目录>/lifecycle.ps1 -Command Start
node <本轮目录>/runtime-check.mjs
pwsh -NoProfile -File <本轮目录>/restart-check.ps1
node <本轮目录>/runtime-check.mjs -verify
node <本轮目录>/read-proof.mjs
pwsh -NoProfile -File <本轮目录>/lifecycle.ps1 -Command Stop
```

成功业务脚本输出`RUNTIME_VERIFIED`及`RESTART_VERIFIED`，重启脚本输出`STOP_START_VERIFIED`，补充成功复核输出`READ_PROOF_VERIFIED`，最终均退出0。辅助脚本是本轮忽略目录中的验收器，不是新增产品交付入口，不能在已有数据上盲目重播。

| 去值证据 | 内容 |
| --- | --- |
| `run.json`、`original-baseline.json` | 输入身份、全新源目录与原库保护基线；原始基线不作公开分发 |
| `empty-database-proof.json`、`initialization-proof.json` | 新资源及空数据起点 |
| `java-build-proof.json`、`artifact-proof.json` | 九模块构建及准确JAR摘要；不把跳过测试称业务回归 |
| `source-postbuild-check.json` | 1890冻结源文件核对，差异0 |
| `runtime-proof.json`、`runtime-receipt.json` | 写入前断点、真实业务身份、拒绝/批准/重复意图及SQL计数；不含登录token或密码 |
| `restart-summary.json` | 停启、凭据/制品/卷/进程及业务事实核对 |
| `read-proof-attempt1.json` | 首次补充采集FAIL，原因TBD，不覆盖或删除 |
| `read-proof.json`、`static-recheck.json` | 后续引用/静态字节核对及3轮只读重复采样 |
| `resource-preservation.json`、`closeout-verification.json` | 本轮停止、旧资源保留与原库/候选收尾核对 |

原始服务/构建日志和credentials只存于`source/bootstrap/target/local-demo/candidate-proof/`私有目录，不复制到仓库说明或公开材料。SHA256用于关联本轮输入/制品/结果，不是数字签名或来源认证。

收尾检查通过：原库1898个文件基线核对，仅本轮说明性文档变化，业务源码、Schema、分支/HEAD、index、全部refs及工作树清单保持；原候选四文件摘要保持。64个相对文档链接无缺失，9份本轮文档的窄秘密规则检查通过；原15项源码、6项历史、7项复现任务分别收尾。辅助采集TBD仍在TODO及本报告保留，不用任务复选框表示全部交付无遗留。

## 实际异常与未解决项

1. 默认工具沙箱无法读取Docker管道和私有配置；使用已授权的本机执行权限完成，不改权限或改连外部资源。
2. 首次外层Start调度捕获原生命令输出，等待被服务进程继承的管道关闭；实际源脚本已退出、生命周期锁已释放，3个进程归属及401/401/200和READY均独立核实。第一次Stop后原调度自然返回0。后续重启验收器使用隐藏子进程、文件重定向并仅等待直接子PID，真实停启核验通过；没有为此修改候选产品脚本或终止外来进程。
3. **首次补充read-proof在HTTP_STATIC阶段退出1，只有泛型VALIDATION_FAILED，无法确认根因。** 该时点诊断与文件清单检查已走过，但没有形成成功proof，不能将首轮算通过。后续只增加安全诊断粒度，没有放宽超时、范围或哈希断言；重启后复核成功，独立3轮各115项也成功。首轮根因仍为TBD/未复现，不宣称已定位、已修复或必定属于产品/网络/验收器某一方；后续需在冷启动采集中保留粒度足够的错误证据。
4. 个别外层日志统计命令存在PowerShell参数/换行计数问题，改为明确UTF-8解析及横向空白统计，准确九模块；不以错误命令的退出0或先前统计代替Build真实退出码。

## 结论边界与后续

本轮证明：固定源码无需复制作者私有目录或旧生成物，在当前Windows工具和既有缓存前提下，能用新配置/新数据库构建并跑通一次真实HTTP安全模拟链路，重启后事实保留。首轮辅助静态采集仍有TBD，不宣布全部交付验收闭合。

尚未完成真实浏览器渲染/点击交互、第二机器/无缓存环境、承诺平台、托管CI、新完整Backend/All及正式提交/Tag/发布；本机历史Spec L结果不重新计入本轮测试总数。真实模型、生产Runbook/Broker/基础设施没有接入，本轮模拟诊断与模拟应用不证明这些能力。

下一步优先补充冷启动采集异常的错误证据与独立浏览器/第二环境复核；之后按实际承诺决定Linux等平台验收。提交新增实现、Tag、推送及部署继续需要单独授权。
