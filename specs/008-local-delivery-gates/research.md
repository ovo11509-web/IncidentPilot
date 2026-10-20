# 研究与brainstorming：DEP-001

日期2026-10-06。用户“继续”启动下一步审查设计，并选择隔离本地编排＋CI＋说明；完整方案尚待确认。只读研究一项由speckit-plan Phase 0派发，未编辑代码或运行服务。

## 三种方案与推荐

1. 文档/CI-only：较小，但临时命令仍分散，本地/CI容易分叉。
2. 推荐复用既有Testcontainers编排，统一本地入口＋CI＋部署准备说明：保留现有数据库权限、模拟审批及真实断言，新增文件最少；首版不交付常驻演示栈。
3. 常驻Compose全栈：需要额外启动配置、初始化、凭据/数据生命周期及浏览器验收；现有可选Milvus/RocketMQ/Graph/Trace示例不足以支撑主链，超出本轮范围。

用户选择了第二项所对应首版范围，尚未批准具体spec/plan/tasks实施。

## 当前事实与依据

- 实际分支feature/incident-platform、HEAD 67624931f20812922568d3641a095adb291263ba，暂存空；原dirty/未跟踪文件保留。
- 没有.github目录或主/MCP应用Dockerfile，resources/docker是可选服务示例。旧lightweight README将上级配置称“生产环境”，和项目“未生产验收”边界冲突；不能沿用为生产就绪依据。原文保留，后续部署说明明确示例范围，生产能力TBD。
- BootstrapIsolatedStartupTest使用pgvector/pgvector:pg16、postgres:16-alpine、redis:latest、rustfs/rustfs:1.0.0-alpha.72，初始化两个临时DB及模拟目标，启动真实主/MCP JVM，try/finally关闭。Java/keytool及classpath separator已有OS分支；Linux完整行为未实测。
- JVM绑定127.0.0.1，TLS MCP可127.0.0.2；Testcontainers exposed ports不代表宿主映射端口只loopback，不能声称整个资源栈仅loopback可见。
- LAB run-lab.ps1硬编码mvnw.cmd；新跨平台入口按OS选择Wrapper，复用测试而非复用LAB执行函数或改LAB。根POM已有lab.reportRoot，Java证明可用lab.proofRoot隔离；无需第二个profile。
- frontend有package-lock、incident-view.check.mjs、lint/build。Incident检查用process.execPath和本地TypeScript；旧Agent timeline检查调用npx，不作为此次新门槛，避免顺带重构。
- 最近实际门禁是Spec G：JDK21.0.12，编译17，全仓864/0失败/0错误/21跳过。它不证明新入口或Java17运行验证；新增门禁不硬编码历史总数。
- 外部条件不仅RAG_LIVE_TESTS，还有MCP YDC_API_KEY和AgentDashboardReaderTest JDBC参数；安全窗口移除key、关闭许可、拒绝不受控JVM注入。
- target包含临时密码参数、classpath参数文件、TLS临时文件、原始日志/XML properties/system-out；不上传target/**或XML。只发布独立safe白名单，日志仍局部排错，不伪称全部安全审查。

## 技术选择与理由

- PowerShell7单入口：Windows现有开发流程＋Linux可用命令形式，复用标准库，不加脚本框架。真实跨平台运行证据仍待实施获取。
- GitHub Actions Ubuntu VM、后端/前端分阶段、contents:read、pull_request/workflow_dispatch，无Secrets或pull_request_target。官方文档说明最小权限和fork凭据边界；此处是依据选择的设计，未证明托管运行：[工作流语法](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)、[安全使用指南](https://docs.github.com/en/actions/reference/security/secure-use)。官方Action完整SHA实施时核实，不猜测版本。
- 使用本机Docker API兼容运行时，不用付费Cloud或Docker-in-Docker；依据 [Testcontainers运行时说明](https://java.testcontainers.org/supported_docker_environment/) 和 [容器内运行模式](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/)。Windows应Linux Engine，未用Windows容器。
- 新入口先契约fixture、再Smoke实际、再完整门禁。结果读取本轮XML结构、固定必需类/手工目录与新源码摘要；不会仅由退出0或历史计数判断通过。
- redis:latest等镜像漂移作为限制，记录本轮实际镜像ID；不顺带升级冻结夹具。

## 未解决运行事实

Linux/托管CI资源、TLS loopback别名及对象存储端点行为尚未实际运行，标为待验证；当前方案不承诺云上通过。常驻Compose/生产安全/真实Broker/外部模型/真实Milvus/OTLP不属于本Spec。数据库README历史版本说明与当前upgrades/v2.0.0路径需在部署说明据当前清单核对，正式发布版本TBD，不执行升级或改历史SQL。

## 本轮保留基线

1788文件摘要保存在bootstrap/target/dep001-design/5f907e58-7543-446c-b957-83673503b0df/，含原status/diff；有意切feature指针到008，不切Git分支。setup-plan返回BRANCH为功能目录名，实际Git独立核对。extensions.yml不存在，无钩子。不创建并行规格或修改核心模板；中文模板由resolve-template/setup-plan/setup-tasks解析。

## 2026-10-06 官方Action核实与静态检查

以官方仓库 `git ls-remote ... refs/tags/...` 实际读取完整SHA，并读取对应action.yml：checkout v6=d23441a48e516b6c34aea4fa41551a30e30af803，setup-java v6=de7274f081f381c8f8158605e0321c36c376e2e6，setup-node v6=249970729cb0ef3589644e2896645e5dc5ba9c38，upload-artifact v4.6.2=ea165f8d65b6e75b540449e92b4886f43607fa02。没有调用远程工作流或写入远程仓库；版本标签未来可漂移，workflow固定SHA。

官方依据：[checkout固定版本](https://github.com/actions/checkout/blob/d23441a48e516b6c34aea4fa41551a30e30af803/action.yml)、[setup-java](https://github.com/actions/setup-java/blob/de7274f081f381c8f8158605e0321c36c376e2e6/action.yml)、[setup-node](https://github.com/actions/setup-node/blob/249970729cb0ef3589644e2896645e5dc5ba9c38/action.yml)、[upload-artifact](https://github.com/actions/upload-artifact/blob/ea165f8d65b6e75b540449e92b4886f43607fa02/action.yml)。checkout persist-credentials=false；setup-java overwrite-settings=false；setup-node package-manager-cache=false，不引入隐式依赖缓存发布。CI只用官方Actions和自动contents:read token，不需要业务Secrets。

本机Python3/PyYAML6.0.1解析实际YAML并核对触发、两job、Ubuntu/pwsh、权限、90/20分钟、固定入口、SHA、checkout凭据、安全artifact和无services/业务Secrets，六组静态检查通过；记录 `bootstrap/target/dep001-implementation/e5a7c477-70f1-455a-a02e-59b0bec0d38a/workflow-static.json`。PyYAML的YAML1.1会把on识别为True，本检查显式兼容，不据此改GitHub工作流语法。未安装新依赖；这不是actionlint或托管CI执行证据。

本机实际PATH Node20.19.5/npm10.8.2；设计的Node22只在CI定义中选择，未安装或实测Node22。前端入口接受与锁文件兼容的Node20以上，实际验证与版本差异必须单列。首次Docker API缺管道，自动启动Docker Desktop后延迟就绪；当前读取到Linux Engine29.4.3。未改用户Docker/Testcontainers配置，也未清理任何容器。用户Testcontainers只缓存Npipe provider，按固定本地类名允许，其余属性仍拒绝。

实现期间全仓真实报告确认两个兼容性前提：JUnit @DisplayName改变testcase.classname，规范类身份仍取testsuite.name；共享Incident与OBS证明写入器还需lab.scenarioId/lab.sourceDigest。后者使用固定DELIVERY场景和本轮实际摘要，Bootstrap及Incident测试helper都保留受控target路径白名单。87项契约与最小三个真实恢复检查通过后，用新UUID5576b210-ed88-4ef9-b841-e0a755d8f627重新完整运行；旧报告不改写、不复制。详细失败和实际命令见quickstart/实施报告。
