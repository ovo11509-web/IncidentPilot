# 实施计划：Incident Runbook RAG 诊断与可复核引用

**分支**：`feature/incident-platform` | **日期**：2026-10-03 | **规格**：[spec.md](./spec.md)

**输入**：本目录已确认规格、Spec A/B、DEC-008/009/011/012/017～022、当前代码和 Schema。

**计划状态**：已确认（2026-10-03 用户“确认执行”），本地代码已实现，隔离验证结果见 `docs/changelog.md`；T028 独立人工标签复核保留待办。费用、真实基础设施及远程变更仍不授权。

## 摘要

`agent` 编排人员诊断，`rag` 提供独立离线 Runbook 检索，`incident` 提供一致快照、证据和建议转 Action 服务。使用版本化本地 Markdown、确定性关键词召回与人工审阅规则的抽取式诊断，不调用可执行 WORKFLOW/MCP 的高层检索链。历史引用保存原文快照，不依赖可变聊天切片。

不新增模块、模型供应商、向量平台、通用工作流或前端。本轮已按确认范围实现 Java/SQL/fixture；没有新增运行依赖。

## 技术背景

**语言/版本**：Java 17。

**主要依赖**：当前 Spring Boot 4.1.0、Spring MVC、MyBatis、Jackson、Sa-Token、JDK 文本/摘要；复用 JUnit/Mockito/Testcontainers，不增运行依赖。

**存储**：classpath 版本化 Runbook + JSON manifest；Incident 主 PostgreSQL 存请求、终态证据、Action link；生成报告进 `target/`。

**测试**：真实本地检索、固定输出编排、真实 PostgreSQL、既有 Bootstrap/MCP 隔离双进程模式。

**目标平台**：当前 Java 主应用与回环 MCP 模拟服务，PowerShell/Docker。

**项目类型**：跨模块后端功能，保持 `agent -> rag + incident`、`incident -> framework + system`。

**性能目标**：不承诺未经实测的吞吐/时延；截止 30 秒、最多 100 条 Alert、20 返回片段、Top-3 展示。截断会禁止处置建议，数值已批准。

**约束**：无外部回退，锁内不检索/生成，来源报告非实时观测；证据原文不进日志，保持 A/B 绑定与审批协议。

**规模/范围**：五类模拟 Runbook、≥30 离线例、一条拒绝/批准/重启黄金路径。

## 宪章检查

| 原则 | Phase 0 静态检查 | Phase 1 静态复核 |
| --- | --- | --- |
| 署名可追溯 | 保留历史与许可 | 标明上游基础与 IncidentPilot 专用语料/编排 |
| 持久化正确性 | 数据库幂等、只追加 | 结果+Timeline、Action+link 在 Incident 本地事务 |
| 人工控制副作用 | 诊断无 MCP 写能力 | 转换仅待审批，B 票据/Worker/UNKNOWN 保持 |
| Spec 控制范围 | 用户已批准 | **实施门通过**：2026-10-03 确认与正式 analyze，DEC-023～025 |
| 有证据再结论 | 分离实际召回/替身编排 | 自动指标与黄金路径有实际记录；人工标签复核待办，不宣称真实模型或生产质量 |
| 模块与安全 | 无倒置依赖 | 无跨域外键/JOIN/写事务和外部回退 |

没有计划中的宪章例外；静态设计复核不等于用户确认或执行验收。

## 项目结构

### 当前功能的文档

```text
specs/003-incident-runbook-diagnosis/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/diagnosis-api.md
├── contracts/runbook-evidence.md
├── contracts/offline-evaluation.md
├── checklists/requirements.md
└── tasks.md
```

### 源代码（已实现，仓库根目录）

```text
rag/src/main/java/com/nageoffer/ai/ragent/rag/runbook/             语料、过滤、确定性检索
rag/src/main/resources/runbooks/                                manifest 与版本化 Markdown
rag/src/test/resources/fixtures/runbook/                        离线集与审阅标签
agent/src/main/java/com/nageoffer/ai/ragent/agent/diagnosis/       编排、抽取生成与输出校验
incident/src/main/java/com/nageoffer/ai/ragent/incident/diagnosis/ 快照、证据、查询和 Action link
resources/database/upgrades/v2.0.0/261003_incident_diagnosis.sql  追加迁移
bootstrap/src/test/java/com/nageoffer/ai/ragent/                  黄金路径装配
```

**结构决策**：检索/推理不进入 Incident；Agent 仅消费应用 DTO/服务，不直接引用 Incident Mapper/DO；RAG 不查询 Incident 表；模拟库不存诊断主事实。

## 设计方案

### 1. 语料与检索

五份版本化 Markdown + manifest 打包 classpath，不读任意路径/URL。发布校验 UTF-8/LF 规范文本、唯一 ID/version、稳定章节与全文摘要；发布状态改变产生新 corpusVersion，历史诊断保存必要 manifest 快照。

首版 `lexical-v1` 使用中文双字片段与小写英文/数字词；查询来自快照白名单症状及经审阅同义词。先过滤，再按查询词覆盖率降序，同分 runbookId/version/sectionId 升序，无交集不返回。禁止 gold case/Runbook ID 作为检索输入。它是离线检索基线，不宣称向量或真实模型 RAG 已验证。

专用 Evidence DTO 可利用 RetrievedChunk 基础字段，不改全局聊天 SourceRef。现有 pgvector 未执行 metadataFilters，不能仅靠传字段保证隔离。

### 2. 快照与请求完成

Agent 人员 Controller 鉴权后调用 Incident `beginDiagnosis`；按 A/B 同键→Incident 行锁顺序捕获已关联 Alert 白名单投影，短事务写请求与快照，返回独立 DTO。factsDigest 不含 Timeline 水位、生成时间或本次诊断事实；水位单独保存。

事务外检索/生成，再调用 `completeDiagnosis`。Incident 核对请求令牌、状态、输入摘要与截止，原子写唯一终态与 `DIAGNOSIS_RECORDED`。当前主版本或 factsDigest 改变时仍保留历史结果，派生 stale 并禁止新 Action 转换。

请求初态 RUNNING；超过截止后由该 Incident 的新诊断 POST（包括同请求重试）惰性收敛为 FAILED/DIAGNOSIS_INTERRUPTED，不自动重跑模型。GET 只报告超时未收敛，不写业务事实。完成/收敛使用请求行条件更新与结果主键仲裁；相同终态摘要重复幂等，不同摘要冲突。没有后续 POST 时不承诺后台主动收敛。

### 3. 诊断支持性

首版生成器按审阅规则抽取条款，以“候选”表述，检查事实/返回引用集合、原文、摘要、范围、前提和缺失。机械检查无法证明任意自由文本蕴含，故不开放任意模型建议转换；固定模型输出注入只在测试中使用。

通用“重启/扩池”等条款仅供阅读，不能变成可执行项。黄金路径唯一模拟建议是 demo 健康检查失败的模拟恢复条款：来源报告演示探针失败且范围、前提和服务端映射齐全时建议 READY；不从资源 ID 直接生成目标，更不声称模拟恢复证明真实根因。

### 4. 建议转 Action

Incident 人员选择接口锁内先查 sourceRequestId 的历史 link：相同绑定重试返回原结果，不因后来 stale 失去幂等。新请求检查 expectedIncidentVersion、factsDigest、未 CLOSED、有效证据/语料、建议摘要和模拟白名单；参数来自校验后的服务端建议，客户端不可改写工具/目标/参数。

外层 Incident 事务复用 `ActionProposalService.proposeUser`，内层加入同一事务，保持锁顺序；Action/Approval/link/Timeline 原子提交，link 失败全部回滚。不能把已存在的无关来源 Action 补成诊断 Action。该嵌套调用必须有真实 PostgreSQL 原子性与并发测试。

link 固定 diagnosisId/recommendationId/digests/actionId/actorId/sourceRequestId；不改变 `action-binding-v1`。拒绝后新 Action 使用新 sourceRequestId，可引用 originalActionId；B 的审批、票据、UNKNOWN 与生命周期规则保持。

语料适用性通过 Incident 定义的窄 `RunbookEligibility` 出站端口核对，由 agent 的本地适配器读取 rag 已加载 manifest；这是实际领域边界，不增加 Incident 对 rag 的依赖。适配器缺失时新选择失败关闭，历史 GET 仍可读。首版语料只在启动加载，无热更新；端口锁内仅查不可变内存，无网络/跨领域数据库访问。发布/撤回随新 manifest 重启生效，旧诊断按当前 manifest 重新核对。

### 5. Timeline 与接口

新增 `DIAGNOSIS_RECORDED`、`DIAGNOSIS_ACTION_LINKED` 和 relatedEntityType=DIAGNOSIS，兼容扩展检查约束，无诊断 Outbox。沿用 `reserveTimeline`：追加本身不递增 Incident 主 version；新告警、生命周期或聚合槽变化使 version 变化，首版保守判 stale。

接口沿用 `/api/ragent`、`admin` 与 Result 信封；精确草案见 [diagnosis-api.md](./contracts/diagnosis-api.md)。Runbook 适用性和来源事实通过服务 DTO 协作，不跨领域 SQL。

## Phase 0：研究结论

[research.md](./research.md) 记录代码依据、冲突与三个方案。设计已获批准并归档 DEC-023～025；研究正文保留设计时点，与现状差异见该文开头。

## Phase 1：设计产物

- [data-model.md](./data-model.md)：三张表、状态/约束/权限/事务。
- [runbook-evidence.md](./contracts/runbook-evidence.md)：元数据、五类样例、位置和证据规则。
- [offline-evaluation.md](./contracts/offline-evaluation.md)：指标公式、案例和报告。
- [quickstart.md](./quickstart.md)：当前可运行检查与实施后验收分开说明。

## 复杂度跟踪

三张表分别为可恢复请求、只追加终态证据和 Action 来源关联；快照/引用/建议用版本化 JSONB，不增通用平台、额外快照/引用表、语料 UI 或真实模型 adapter。

## 实施前确认门

用户已批准 spec/plan/tasks；版本/证据保留、诊断 Action 来源及离线评测口径已归档为 DEC-023～025。旧文档时点冲突以当前代码/Schema 核对，研究与历史决策保留。

## T028 v2有界修正计划（用户审阅指示）

先归档未签署结论→以缺v2输入的红测暴露缺口→新增34例cases-v2及建议级gold→沿用真实检索/抽取复跑v1+v2→输出按split区分的实际分母→准备v2证据与人工待审记录。仅修改测试/fixture与功能文档，不改运行算法、Schema或审批协议；不调用外部服务。完整设计口径见offline-evaluation契约v2补充。
