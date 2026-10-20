# 实施计划：LAB-001 本地故障回放实验室

**分支**：`feature/incident-platform` | **日期**：2026-10-05 | **规格**：[spec.md](spec.md)
**输入**：`specs/006-local-fault-replay/spec.md`
**状态**：2026-10-05 获用户“执行”批准，本地验收完成，18/18任务。采用项目中文覆盖模板，不新增平行设计格式。

## 摘要

复用既有六类故障检查，通过一个显式本地入口组织选定运行，安全归档成功与失败，并输出可读事实及 Trace 证据。保持原业务和生产配置。

## 技术背景

**语言/版本**：Java release 17；脚本兼容仓库现有 PowerShell 使用方式，不要求安装新运行时。
**主要依赖**：现有 Maven Wrapper、JUnit、Testcontainers、PostgreSQL、Bootstrap/MCP 隔离夹具及 OTel 测试 exporter；脚本使用内置 JSON、XML 和 SHA256。
**存储**：`bootstrap/target/fault-lab/<runId>/` 本地制品；无新增业务表。
**测试**：脚本无数据库契约负例；六类既有真实隔离检查；受影响测试的定向回归。
**目标平台**：本机 Windows PowerShell 与 Docker Desktop；不承诺跨主机部署。
**项目类型**：仓库本地实验运行脚本，非生产 HTTP 服务。
**性能目标**：无新增吞吐/耗时承诺，实际执行时间仅作记录。
**约束**：不使用开发库、远程服务、付费调用；不提交/推送/合并/部署；保留所有已有修改与历史制品。
**规模/范围**：六个固定场景，首版串行；四个场景复用实际双 JVM Trace 检查。

## 宪章检查

| 原则 | 研究前核对 | 设计后核对 |
| --- | --- | --- |
| 署名与贡献 | 复用上游工程并保留许可 | 实验室新增入口归属 IncidentPilot，保留 Java 许可头 |
| 持久化正确性 | 事实依旧来自原领域及数据库 | Trace/报告不成为状态机或审计事实源 |
| 人工控制副作用 | 原审批、票据与幂等保持 | 故障仅注入临时安全模拟资源 |
| Spec 控制范围 | 待用户确认设计 | 批准后 analyze，无阻塞才 implement |
| 有证据再结论 | 历史与本轮分开 | 全部声称通过场景必须本轮真跑，失败与跳过不得计通过 |

两轮静态治理核对无设计违规；不等于用户批准或运行验收。

## 实施前分析与基线

2026-10-05 speckit-analyze只读核对12 FR/5 SC、18任务、三个用户故事及宪章：17/17覆盖，无未映射实施任务、无CRITICAL/HIGH或阻塞歧义。草案“待确认”标签由实施授权更新，未变更范围。checklist 14/16，未勾选的批准与analyze已由本次人类明确“执行”和上述只读分析满足，标记按implement要求保持只读；无需重复授权。

分支feature/incident-platform，HEAD 67624931f20812922568d3641a095adb291263ba，暂存为空；起始1771个源码/文档的哈希、status与diff保存于bootstrap/target/lab-implementation-baseline/。Docker Engine 29.4.3可用；extensions.yml不存在。保留原修改，只实施已确认本地范围。

## 项目结构

### 当前功能的文档

`specs/006-local-fault-replay/` 包含 spec、plan、research、data-model、quickstart、contracts/local-runner.md、tasks 及 checklists/requirements.md。

### 源代码（仓库根目录，以下为拟实施文件）

```text
specs/006-local-fault-replay/scripts/
  run-lab.ps1                    # 一个入口，含归档与只读验证
  scenarios.json                 # 固定映射与版本
  test-run-lab.ps1               # 内置断言的脚本契约检查，无新测试框架
incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/
  AlertRecoveryTest.java         # 只补本轮来源与安全事实证明
  OutboxRecoveryTest.java        # 同上，保留 Producer 替身标注
incident/src/test/java/com/nageoffer/ai/ragent/incident/evidence/
  EvidenceManifestWriter.java    # 必要时复用/扩展既有安全写入能力
bootstrap/src/test/java/com/nageoffer/ai/ragent/
  BootstrapIsolatedStartupTest.java
  IncidentObservabilityIsolatedTest.java
```

**结构决策**：不新建生产模块或通用故障抽象；仅将测试证明写到入口分配的隔离目录，原非实验室测试行为兼容。所有新增路径是计划，当前不存在可执行入口。

实施核对Surefire 3.5.6插件metadata确认reportsDirectory无CLI property，故在根pom.xml增加仅lab.reportRoot激活的测试profile，以`${lab.reportRoot}/${project.artifactId}`隔离整个reactor报告。普通门禁与生产配置不变；属于T007所需的测试输出装配，不增加依赖。

## 运行与判定

1. 固定目录解析参数，拒绝未知选择；List/Verify 不启动任何服务。
2. Run 获取仓库实验锁，生成新 runId，保存来源与真实前置条件；不接受连接参数。
3. 每场景单独 Maven 调用；属性指定本轮证明/报告路径和来源身份，只选择声明的类或方法；强制 RAG_LIVE_TESTS=false、跳过自动格式改写。
4. 测试沿用自身 Testcontainers 和 JVM 清理，并导出安全证明及已验证的实际秘密扫描摘要。入口不保存秘密列表，也不终止未经确认归属的进程。
5. 校验精确测试选择、零失败/错误/跳过、本轮来源、必需事实及已声明 Trace；构建 manifest/report/hash。失败记录保留，All继续收集其余场景但最终非零退出。
6. 实施最小实测确认报告重定位属性对整个 reactor 生效；若子 JVM 输出未跟随，修复测试路径传播，不回退到扫描共享旧目录。

## 验证顺序

- 首先脚本参数、路径与七类证据负例，不启动 Docker。
- 告警恢复、Outbox 两个最小本地数据库场景验证证明扩展。
- 四个双 JVM 场景单独验证，按 Execution 核对应用次数，保留原秘密扫描。
- 全部六类统一运行；重复一次轻量场景验证归档互不覆盖；验证成功归档及副本篡改拒绝。
- 改动全部仅测试/脚本时，不重复此前30分钟完整门禁作为惯例；若影响共享生产边界，重新评估并扩大检查。

## 复杂度跟踪

无宪章例外；串行锁与固定目录足够，不引入调度器、插件体系、在线后台或新的收集服务。
