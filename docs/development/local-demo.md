# 本机持久化安全演示

第二机器的校验/新Name初始化、构建、HMAC告警、UI审批、只读SQL和重启取证步骤见 [接手手册](../../specs/013-release-candidate/second-machine-handoff.md)。2026-10-18用户确认暂无第二机器，本轮只准备材料，异机尚未执行。候选ZIP无`.git`时原All/CheckOnly门禁受HEAD前置条件限制；本页的演示生命周期入口与该门禁分开核对，不初始化Git伪造来源。

2026-10-18固定070候选的新持久实例已完成真实JAR主链、完整117静态HTTP和5命名卷Stop/Start留存；本轮复用阶段11已核验制品，未重复Build/All。最终Stop保留数据，当前不是运行中的预览。实际来源、命令、SQL断言及限制见 [阶段12报告](../../specs/013-release-candidate/persistent-candidate-runtime-report.md)，不代替异机或生产验收。

IncidentPilot 衍生自 `nageoffer/ragent`，保留 Apache-2.0 许可证、历史和上游署名。本页的生命周期脚本、隔离演示装配与事件响应链路属于 IncidentPilot 新增贡献。

## 运行范围

此入口运行真实 executable JAR 和前端 production dist，不依赖测试 classpath。四个独占 Docker Compose 依赖保存数据，使用独占的普通 bridge 网络以供宿主机回环端口访问；主应用和 MCP 为本机 Java 进程，Node 标准库服务静态页面并代理 HTTP/SSE。全部端口绑定 `127.0.0.1`，与已有开发数据库、容器和临时 UI 夹具分开。此入口限制服务配置和资源范围，不提供操作系统级出站网络防火墙。

2026-10-17日志挂载修复后，新实例使用5个命名卷：主库、模拟库、Redis、存储数据和存储日志。RustFS的`/data`与`/logs`均显式挂载，全部卷带本instance标签并在Stop后保留，避免重建容器生成新的匿名日志卷。旧4卷实例和其私有配置不自动迁移；验证修复请使用新的`-Name`，不要手改旧初始化摘要。此修复只控制卷身份和留存，不包含日志轮转或容量上限；实测范围见 [日志卷修复记录](../../specs/013-release-candidate/storage-volume-fix-report.md)。

本地演示只开放认证、Incident API、只读当前用户和受保护的模拟 MCP。关闭普通远程 MCP、Broker、模型与 MinerU；上游聊天、知识入库、用户管理等接口返回 `LOCAL_DEMO_SCOPE_UNAVAILABLE`。Broker 调用明确拒绝，不返回伪造发布成功。Runbook 仍为仓库内模拟语料，处置仅能影响 `demo` 模拟目标，人工审批、幂等与 `UNKNOWN` 不自动重试规则保持。

## 前置条件与命令

需要本机 Docker Linux Engine/Compose、pwsh 7、JDK、Node/npm 与 Maven Wrapper 可用的依赖。当前实测工具为 Windows、Java 21.0.12、Node 20.19.5、pwsh 7.6.5、Docker 29.4.3、Compose 5.1.3。POM 编译 release17；Java17、Node22、Linux及托管 CI 仍须各自实际验证。

脚本从自身位置解析仓库，可从任意目录调用。以下命令在仓库根目录执行：

```powershell
pwsh -NoProfile -File scripts/test-local-demo.ps1
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Init
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Build
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Start
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Status
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Verify
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Stop
```

`Build` 使用 Wrapper，关闭隐式 Spotless 改写并跳过测试，仅表示生成制品；质量检查另按 [测试指南](testing.md) 执行。可用 `-Offline`、`-MavenSettings` 和 `-MavenRepository` 选择用户已配置的 Maven 环境，不修改全局配置、不把机器路径写入仓库。前端执行锁文件安装与 production build。没有缓存镜像时 `Init` 明确失败，不自行拉取；所需镜像为 `pgvector/pgvector:pg16`、`postgres:16-alpine`、`redis:latest`、`rustfs/rustfs:1.0.0-alpha.72`，初始化记录本轮实际 image ID。

2026-10-17版本统一为`0.1.0`，Build按根POM版本和模块artifactId精确选择当前JAR；旧target中存在历史包也不回退使用。当前包缺失、版本非法或模块parent不一致均失败。此改动不自动替换已经复制到演示实例的app.jar/mcp.jar，不操作其数据；候选与历史移交边界见 [源码候选](../releases/source-candidate-v0.1.0.md)。

Linux 薄入口使用同一 pwsh 实现：`sh scripts/local-demo.sh -Command Status`。交付入口不等于 Linux 已通过。

## 账户、配置和数据所有权

默认实例位于 ignored `bootstrap/target/local-demo/default/`。账号为 `local-admin`，首次初始化生成随机密码。用户在本机私有编辑器打开其中的 `credentials.json` 获取登录信息；不要复制到聊天、截图、日志、提交或发布材料。目录仅授予当前用户访问。Windows 的受限工具沙箱可能需要现有授权升级才能读此目录；普通用户在本机执行脚本无需以管理员身份读取密码。

`owner.json`、初始化成功标记、配置摘要、制品摘要和进程身份分别用于核对所有权与当前事实。端口随机分配后保持，`Start`/`Status` 的脱敏输出给出浏览器地址。首次主库初始化用全量 `schema_pg.sql`、受限 `ragent_app` 和随机演示账户；不载入上游 `admin/admin`，不重复应用历史增量。MCP 库只有演示结构和 `demo` 目标，首始状态 `PAUSED`。

启动时仅在子进程环境关闭已有的 Spring、Incident、模型等配置覆盖，再注入自有模拟身份；Compose 调用期间清空同名镜像、端口、归属标签与凭据的 shell 变量，以固定私有 `compose.env` 为准；父进程环境随后恢复。JVM/Node自动预加载参数和远程 Docker 配置会被拒绝，避免原机器环境把演示配置替换为其他数据库或真实服务。

重复 `Init` 保留密码和业务数据。Schema或生成配置摘要变化会明确拒绝自动继续，不自动迁移现有数据。首次初始化部分失败可以检查自有资源后重跑；已成功的事务不重灌。初始工作台可以为空，告警仍需通过既有 HMAC Webhook；不得将空数据或脚本通过当作端到端处置证明。

## 启动、核验与停止

生命周期操作使用自有排他文件锁，并行操作返回 `LIFECYCLE_BUSY`。`Start` 先核对成功标记、配置与制品摘要、端口和 Compose 所有权，再启动依赖、MCP、主应用及静态服务；全部 HTTP 检查成功才返回 `READY`。进程存在与就绪分列，`DEPENDENCIES_READY` 表示仅依赖已运行。

`Verify` 认证后读取工作台、手册和至多100个事件及各自至多100个诊断，输出白名单 ID、状态和回执编号；分页未读完会明确标记 `hasMore`，不把局部核验称为全库归档。它不提交告警、不批准动作、不触发 MCP。脱敏结果保存在私有目录的 `verify.json`，根因排查使用同目录的进程日志，日志不能直接发布。

`Stop` 核对 owner/初始化归属、Compose 文件摘要、自有进程的 PID、创建时刻和命令行配置标识，以及全部自有容器和卷的 instance 标签；任一身份冲突即拒绝猜测。Schema 或 app/web 配置发生漂移仍可安全停止，Compose 自身被改动则拒绝执行。只停止自身进程并执行当前 project 的 Compose down，保留持久卷。再次 `Start` 后账户、Incident、诊断、审批和回执应仍可读取。没有 Destroy、全局 prune 或自动删除数据入口。

首次本地运行、实际审批演练、Stop-Start留存核对及尚未验证项以 [Spec K实施记录](../../specs/011-incident-quality-delivery/implementation-report.md) 为准。此入口不包含生产部署、备份恢复、线上迁移、跨主机认证或真实模型质量验收。
