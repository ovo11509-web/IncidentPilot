# IncidentPilot 本地交付候选：2026-10-12

## 交付身份

这是当前工作区的本地交付候选说明，不是已发布版本或 Git Tag。分支为
`feature/incident-platform`，基线 HEAD 为 `67624931f20812922568d3641a095adb291263ba`。
新增实现包含未提交及未跟踪文件，仅检出该 HEAD 不能获得完整项目。
首次正式版本号已由用户确认为 `v0.1.0`，当前待发布。2026-10-17构建版本已统一`0.1.0`，见 [当前源码候选](source-candidate-v0.1.0.md)；数据库增量目录名不作为产品版本或迁移兼容承诺。

项目源自 `nageoffer/ragent`，保留 Git 历史、[Apache-2.0 许可证](../../LICENSE)
和上游署名。历史 Ragent 发布说明与本候选分别归档。

## 本候选的能力与归属

| 能力 | 来源与本项目贡献 | 验证边界 |
| --- | --- | --- |
| RAG、Agent、知识入库、模型路由及原管理页 | 主要继承上游，不作为从零实现的个人贡献 | 本机入口关闭真实模型、知识入库等外部能力 |
| 告警与事件治理 | 新增 HMAC 接入、持久化幂等、聚合、状态机、只追加 Timeline、Outbox 与恢复 | 隔离数据库、固定发布器及本地检查 |
| 安全响应 | 新增诊断证据、Action/Approval/Execution、一次性票据、幂等模拟执行及 UNKNOWN 对账 | 服务端强制审批，目标限安全模拟资源 |
| 观测与故障实验 | 新增主链关联、默认关闭的短时 Trace、六类本地故障实验和归档核验 | 不证明外部观测平台或生产恢复 |
| 界面与内容 | 新增值班工作台、事件操作、复盘报告、版本化处置手册；实施已选 B 视觉方案、侧栏滚动和窄屏布局 | 四视口、角色、取消状态及桌面/窄屏运行图 |
| 本机交付 | 新增生命周期脚本、独立持久演示、门禁、安全摘要及 CI 定义 | Windows 实际运行；CI 定义不等于托管通过 |

逐项事实见 [项目上下文](../PROJECT_CONTEXT.md)、[完成度核对](../development/local-acceptance-review.md)
和 [Spec K 最终记录](../../specs/011-incident-quality-delivery/implementation-report.md)。

## 接手与启动

接收完整源码及已有 Git 历史。当前新增文件尚未提交，不能以 `git archive HEAD`
或仅复制已跟踪文件代替完整交付。本页保存原接手说明；当前源码包由新候选入口生成，Git历史因潜在凭据暂不随包分发，完整历史移交尚未完成。

准备 Docker Linux Engine/Compose、pwsh 7、JDK、Node/npm 与 Maven Wrapper 依赖。
实测组合为 Windows、Java 21.0.12、Node 20.19.5、pwsh 7.6.5、Docker 29.4.3、
Compose 5.1.3。检查实际工具路径，避免 PATH 中旧 Node 优先命中。
按 [本机演示说明](../development/local-demo.md) 准备四个镜像，在仓库根目录执行：

```powershell
pwsh -NoProfile -File scripts/test-local-demo.ps1
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Init
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Build
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Start
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Status
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Verify
```

`Build` 只生成制品，质量门禁另见 [测试指南](../development/testing.md)。使用 `Status`
给出的地址，不把本机随机端口当成固定交付端口。首次初始化在
`bootstrap/target/local-demo/default/credentials.json` 生成 `local-admin` 私有登录信息，
密码仅在接收者自己机器查看。首次工作台没有既有演练数据，初始化不会伪造历史事件。

结束时执行 `pwsh -NoProfile -File scripts/local-demo.ps1 -Command Stop`，保留自有数据；
再次 Start 后 Verify。不要删除卷、重灌已有数据库或转交私有配置。
Linux 的 `.sh` 入口要求 pwsh，尚无 Linux 实际通过记录。

## 接收者验收走读

1. 登录打开值班工作台；旧管理菜单中的事件入口仍可进入事件操作页。
2. 按本机说明的黄金路径显式提交两条相关告警，核对聚合事件及追加时间线。
3. 创建模拟诊断，查看冻结来源、手册引用与建议边界。
4. 显式创建 Action，验证拒绝零应用，再对另一 Action 批准一次模拟执行；核对回执
   和 Timeline。UNKNOWN 不得自动重试。
5. 查看复盘与手册；采集期间取消后禁止导出，窄屏验证导航及内容滚动。
6. Stop/Start 后只读核对事件、诊断、审批及模拟结果保留。

当前机器的历史截图不能替代接收者独立验收。

## 已有证据与平台矩阵

2026-10-12 Spec L新增检查前的源码 All 门禁通过，runId `f989e36a-9e11-4f92-a719-2149762870f1`，
sourceDigest `F73360C5C7B4489540C1256AA86783307C9EAF5A24A5A80759C5D6AB8A44F674`。
197 份 XML：884 项、863 通过、21 受控跳过、0 失败、0 错误；
前端 Install/IncidentView/Lint/Build 均通过。受控跳过不算外部服务验收。
摘要在该 run 的 `bootstrap/target/delivery-gates/<runId>/safe/summary.json`，不随源码保存。
此历史All不包含Spec L新增检查；新增后的Backend结论单独见 [Spec L实施记录](../../specs/012-incident-reliability-acceptance/implementation-report.md)。前端未改，不重复已经通过的前端门禁，不把两轮记录合称新的All运行。

| 范围 | 状态 |
| --- | --- |
| Windows / Java21 / Node20 本机运行与 All | 已通过，见 Spec K 记录 |
| 本机同库安全模拟与停止后重启保留 | 已通过 |
| Spec L当前源码可靠性专项与Backend | 已通过：45组规则、HTTP幂等、真实进程/数据库故障及400样本；930项/909通过/21受控跳过，详见专项记录 |
| Spec I 四视口、角色、滚轮及旧配置页 | 29/29 本地完成 |
| Java17 运行 | 未验证；release17 不证明运行兼容，门禁入口当前要求 Java21 |
| Node22 / 独立干净 Linux | 未验证；工具盘点未发现对应可直接使用的环境 |
| 托管 CI | 仅有定义，未触发 |
| 真实模型、真实 Broker 联合链路、生产部署 | 未验收 |

## 初始化、升级与发布边界

新隔离演示库由 Init 使用全量 Schema，不重复应用历史增量。
数据库 README 的 v1.0/v1.1.0 口径与现存 `resources/database/upgrades/v2.0.0/`
不一致；既有数据库升级矩阵为 TBD。目录名不能作为自动升级依据，本次不改历史 SQL、
不执行既有库迁移。

正式交付仍需同步构建版本、审查并提交完整工作区、形成保留历史的源码交付物，
以及在承诺平台完成干净复现。提交、推送、Tag、远程 CI、发布及部署须分别明确授权。
本候选没有执行这些操作。

交付物应包含许可证、归属、完整源码、规格与运行说明；排除账号、密钥、私有配置、
数据库卷、原始日志、原始测试报告、target、node_modules 和 dist。
可供发布的测试事实仅采用安全白名单摘要，不上传 properties、system-out 或整个 target。
