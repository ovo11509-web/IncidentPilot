# 实施计划：DEP-001 隔离本地编排与交付门禁

**分支**：实际 `feature/incident-platform`；目录 `008-local-delivery-gates` | **日期**：2026-10-06 | **规格**：[spec.md](spec.md)
**输入**：`specs/008-local-delivery-gates/spec.md`
**状态**：完整方案已批准；analyze无阻塞，implement 20/20及本地门禁完成；Linux/托管CI/部署未运行。

## 摘要

推荐复用Testcontainers临时四容器＋双JVM，增加PowerShell 7固定门禁入口、最小GitHub Actions定义和部署准备说明。不建第二套Compose，不改业务代码/Schema/生产配置。LAB继续由Spec F维护。

## 技术背景

**语言/版本**：PowerShell 7；编译Java17，执行先用已有证据的JDK21；Node.js22/npm锁文件，补丁版本与Actions SHA实施时官方核实。
**主要依赖**：现有Maven Wrapper/JUnit/Testcontainers、前端本地TypeScript/npm；不新增框架或生产依赖。
**存储**：既有临时主pgvector PostgreSQL、模拟PostgreSQL、Redis、RustFS，本轮target文件；无Schema变化。
**测试**：入口契约、既有黄金路径/全仓test、incident-view.check.mjs/lint/build。
**目标平台**：当前Windows＋Docker Linux Engine；CI拟Ubuntu24.04＋pwsh，同一入口选择不同Wrapper。Linux和托管CI运行尚未验证。
**项目类型**：仓库工程脚本及文档。
**性能目标**：无性能承诺；后端90分钟/前端20分钟超时仅保护资源，不是实测SLA。
**约束**：保留所有dirty/未跟踪文件；不继承外部联调条件；原始输出仅本地，CI仅白名单摘要。
**规模/范围**：Smoke/Backend/Frontend/All四范围，加CheckOnly模式。

## 宪章检查

研究前/设计后核对五原则：许可和历史保留；持久化/只追加Timeline不变；审批模拟边界不变；Spec H限定工程入口；静态/实际/远程结果分列。无已知宪章违规，不新增生产抽象，不把草案追加为已确认DEC。

## 项目结构

### 当前功能的文档

`specs/008-local-delivery-gates/`：spec、plan、research、data-model、contracts/gates、quickstart、tasks及requirements。

### 源代码（仓库根目录，计划路径尚未创建）

```text
scripts/check-delivery.ps1
scripts/test-check-delivery.ps1
.github/workflows/incidentpilot-ci.yml
docs/development/delivery.md
```

**结构决策**：工程入口放scripts；复用既有 `lab.reportRoot` Surefire隔离profile、`lab.proofRoot` 证明目录属性指向新delivery目录，不新增profile或改LAB入口。

## 执行设计

- CheckOnly无容器/Java业务启动，仅工具、Docker和安全选项检查。Frontend不要求Docker，其余执行范围要求。Smoke固定IncidentDiagnosisGoldenPathTest；Backend为全仓test；All顺序Backend→Frontend，任一失败保留。Frontend固定npm ci --no-audit --no-fund、node tests/incident-view.check.mjs、npm run lint、npm run build；不纳入旧Agent timeline演示检查。
- 新UUID目录 `bootstrap/target/delivery-gates/<UUID>/`：run/source/stages/summary和本地raw/reports/proof、安全safe。源摘要使用固定源码/POM/入口和前端受控文件，排除.env/生成/缓存，不输出内容。拒绝输出路径链接或越界，不接受任意输出根/命令参数。
- 强制RAG_LIVE_TESTS=false、临时移除YDC_API_KEY；拒绝非空MAVEN_OPTS/JAVA_TOOL_OPTIONS/_JAVA_OPTIONS/JDK_JAVA_OPTIONS等不受控JVM参数及远程Docker覆盖/容器复用。finally恢复；合法代理/内存需求后续明确白名单，不默许继承。
- 本轮独立XML禁止外部实体，只读结构/计数，核对契约中12个必需类存在且零跳过。手工跳过完整类/方法数量实施前据源码固定，不硬编码历史总数864/21。重复类、缺失目标、过时/空报告、不一致计数或非零退出失败关闭。
- PASS/FAIL/BLOCKED/CHECKED/INCOMPLETE明确；取消或超时不产生PASS。错误只给固定reasonCode，raw捕获原始stdout/stderr；不输出Exception消息/凭据。资源由原夹具关闭，入口无全局prune/kill。
- CI为pull_request/workflow_dispatch、contents:read、checkout不持久化凭据；Backend与Frontend独立调用同一入口。无Secrets/services重复栈/部署/publish步骤；官方Actions完整SHA实施时核实，超时90/20分钟。
- artifact仅safe/*.json（选定公共计数/来源/状态），失败仍可生成安全摘要。不得上传原始日志、XML、TLS/账号/classpath临时文件或target/**。敏感fixture证明白名单不会泄露，摘要生成失败不退回上传raw。

## 研究与验收顺序

批准后analyze→入口契约→Smoke实际→安全摘要→完整Backend/Frontend→CI静态→文档/作者审阅。完整后端执行一次，后续仅文档不机械重跑。Linux缺环境则记录未验证；远程CI发布触发需未来授权，不伪造执行证据。

## 复杂度跟踪

无违规项。redis:latest等既有浮动标签只记录实际镜像ID和重现风险，不混入夹具升级。JVM loopback不能证明容器映射端口仅loopback，文档不得如此声称。

## 2026-10-06 实施批准与正式 analyze

用户明确批准完整 Spec H，授权无阻塞后直接实施全部20项，仅本仓库及本地临时安全模拟。实际分支 `feature/incident-platform`、HEAD `67624931f20812922568d3641a095adb291263ba`，暂存为空；42个已有修改文件及全部未跟踪文件保留。1745个非忽略文件摘要、原status/diff/staged存于 `bootstrap/target/dep001-implementation/e5a7c477-70f1-455a-a02e-59b0bec0d38a/`。

正式执行 `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`，解析到008；extensions.yml不存在。需求清单16/16，只读。analyze：12 FR＋5 SC共17/17有任务，20任务均有覆盖，歧义/重复/CRITICAL均0，未发现宪章冲突。覆盖对应tasks末尾的逐项映射；故事1 T005～009、故事2 T010～016、故事3 T017～018及基础/收尾T001～004、019～020。按用户预授权直接进入implement。

实施核对发现测试夹具 lab.proofRoot 原来仅允许 target/fault-lab，与计划的 delivery 输出冲突；在原测试helper中按固定delivery.gates属性增加target/delivery-gates白名单并保留逐场景独立证明。属于既有测试编排复用所需的测试侧修正，无生产代码/配置/Schema/协议扩展。Testcontainers自动缓存的本地Npipe/Unix/Desktop provider允许；任何其他属性/连接/复用覆盖仍拒绝，不修改用户配置文件。

禁止提交、推送、合并、部署、远程CI触发、付费、真实基础设施/开发库以及全局容器清理。当前实际pwsh7.6.5、JDK21.0.12，PATH Node20.19.5兼容既有锁文件；CI声明Node22，Node22/Linux/托管CI未经实际运行不得宣称通过。最初Docker Linux Engine未就绪，仅前置受阻，不作Smoke通过。


完整Backend另发现Incident测试侧EvidenceManifestWriter也有lab身份/路径前提；按同一批准边界扩展该测试helper，并传固定DELIVERY-SMOKE/BACKEND与本轮lab.sourceDigest。没有生产代码、Schema、新profile或任意场景命令扩展，先最小三个恢复检查后重跑失败的完整入口。原失败记录保留。
