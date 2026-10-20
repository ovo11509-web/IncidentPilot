# Phase 0 研究：Incident Runbook 诊断

**日期**：2026-10-03；**状态**：只读代码研究完成，设计提案待确认。没有执行模型、数据库迁移或运行验收。

> 上述状态保留设计阶段时点。后续用户已确认执行，DEC-023～025 归档；实际实现/运行以
> `tasks.md`、`quickstart.md` 和 `docs/changelog.md` 为准。独立人工标签复核由用户明确保留待办。

## 工作区基线

- 当前分支 `feature/incident-platform`；HEAD `67624931f20812922568d3641a095adb291263ba`。
- 初始有 31 个已跟踪修改文件、无暂存差异，以及大量未跟踪 Incident、Spec A/B、指南和测试。保留当前工作区，不创建隔离工作树丢失未提交实现。
- 已检查 branch、HEAD、status、diff 的统计/numstat、审批/MCP/Schema 相关差异；`git diff --check` 初始成功，存在 LF/CRLF 提示，未做全仓格式化。
- `.specify/feature.json` 原指向 Spec B，本轮按 Spec Kit 生命周期切到本功能目录；不等于 Git 切分支。
- 已读 AGENTS、PROJECT_CONTEXT、ARCHITECTURE、DECISIONS、TODO、NOTES、testing、宪章、Spec A/B 的 spec/plan 与相关契约/任务入口。历史测试通过记录仅作为历史证据，本轮不重新宣称测试已通过。

## 已确认代码事实

| 依据 | 事实 | 对设计的影响 |
| --- | --- | --- |
| `incident/pom.xml`、`agent/pom.xml` | incident 主依赖 framework/system，agent 已依赖 rag/incident | 编排放 agent，不向 incident 引入检索/模型 |
| `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/web/IncidentController.java` | 人员 admin；当前 snapshot 是实时 Map 投影，没有冻结诊断事实 | 新增应用快照 DTO；不能把 Controller 私有方法当稳定跨域端口 |
| `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/dao/IncidentMapper.java` | 关联/生命周期/聚合槽变更递增 version；reserveTimeline 只增序号 | factsDigest 与 Timeline 水位分离，避免诊断自己使自己过时 |
| `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionProposalService.java` | 当前公开人员入口 proposeUser，受 admin/目标白名单/幂等摘要保护 | 不臆造可直接调用的 Agent propose 接口，人员显式转换复用现入口 |
| `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/contract/ActionBindingV1.java` | 固定 simulation.set-state/1、SIM_SERVICE、desiredState=READY或PAUSED | 所有建议转 Action 保持真实参数契约，不加入重启/扩容工具 |
| `incident/src/main/java/com/nageoffer/ai/ragent/incident/action/service/ActionTransactionGuard.java` | 同键→Incident→Action 锁顺序，事务回调禁止网络 I/O | 快照/保存事务短，检索与生成在事务外 |
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/convention/SourceRef.java`、`framework/src/main/java/com/nageoffer/ai/ragent/framework/convention/RetrievedChunk.java` | 文档级展示来源与切片 ID/内容/分数，无不可变 Runbook 版本/位置 | 使用专用 Evidence 契约，不把聊天来源编号当历史证据身份 |
| `resources/database/schema_pg.sql` | knowledge chunk 有 content_hash，document 无 Runbook 不可变版本；Incident 无诊断表 | 既有 hash 不等于历史保留；新增最小 Incident 事实表 |
| `rag/src/main/java/com/nageoffer/ai/ragent/knowledge/sink/RelationalChunkSink.java`、`rag/src/main/java/com/nageoffer/ai/ragent/knowledge/service/impl/KnowledgeChunkServiceImpl.java` | replaceDocument 删除重建，update 可编辑 | 历史引用保存原文快照，不只存当前 chunkId |
| `rag/src/main/java/com/nageoffer/ai/ragent/rag/core/vector/PgVectorRetrieverService.java` | 实际 SQL 按 collection_name，没有执行 metadataFilters | 不能声称传入 metadataFilters 就保证范围；首版独立本地过滤 |
| `rag/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieval/postprocessor/EvidenceGatePostProcessor.java` | 批级 rerank 闸门，缺 rerankScore 可放行 | 不作为逐条建议支持性校验 |
| `rag/src/main/java/com/nageoffer/ai/ragent/rag/eval/EvalController.java` | 调改写、意图和混合检索，可能模型/外部检索/WORKFLOW MCP | 不直接调用 /rag/eval 作无外部依赖门禁 |
| `bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java` | 当前有隔离双应用启动模式 | 实施后扩展黄金路径，不新建一套环境框架 |

路径细节在实施 T001 中再次核对，已有事实不代替实际测试。

## 实质冲突登记

1. `docs/ARCHITECTURE.md` 当前依赖图未反映 agent→incident；当前能力文字仍称 Spec B 安全事实未证明或未实现。当前 POM、Action/Approval/Ticket/回执 Schema 和业务类已存在。
2. ARCHITECTURE 仍称未做真实 Broker 跨进程演练，PROJECT_CONTEXT 与 changelog 记载本地隔离演练完成；这是验收记录时点冲突，本轮未重跑，不宣称当前运行环境已经验证。
3. `docs/NOTES.md` 2026-09-21 的旧条目称审批票据未实现，旧“待补”称全仓测试待通过；与随后 2026-10-03 记录不一致。保留历史记录，实施前确认是否增加显式时点说明。
4. Spec B spec 的功能分支元数据为 002-approval-safe-simulation，plan 与当前 Git 为 feature/incident-platform；按功能目录与分支独立处理，不据旧标签切分支。
5. Spec B 已确认文本的“假设”仍称 Spec A 代码及当前审批入口尚未实施；这是规格编写时点的历史假设，当前代码/Schema 已改变，不把它用作当前能力断言。

已向用户报告。依用户明确指定，新增草案以当前代码/Schema 为准，不静默改写冲突文档或既有决策。

## 方案比较（brainstorming）

| 方案 | 优点 | 代价/局限 | 推荐 |
| --- | --- | --- | --- |
| 版本化 Runbook + 本地关键词检索 + 抽取规则 | 无付费/外部调用，可复核真实检索和证据链 | 不证明向量或真实模型质量，表达能力有限 | 首版推荐，待批准 |
| 直接接既有混合 RAG | 复用改写/向量/Rerank | 要隔离外部调用、补过滤和不可变证据，超出最小黄金路径 | 后续独立对比 |
| 仅固定检索/模型返回 | 编排验收最简单 | 没有真实检索质量证明 | 仅测试替身，不当产品检索 |

## 具体提案、理由与替代方案

- **版本和引用**：classpath Markdown/manifest + 诊断原文快照；避免更改全局知识入库或建立 Runbook 管理平台。替代是全面改现有知识表/入库版本机制，首版范围过大。
- **快照**：Incident 应用服务在当前锁顺序内读已关联事实；禁止 Agent Mapper 引用和跨域 JOIN。替代 Controller HTTP 自调用不能保证一致捕获且增加传输边界。
- **生成**：抽取式审阅规则，固定模型输出仅用于负面校验测试。替代任意模型文字需要另行语义评测与费用/模型授权。
- **来源关联**：Incident 本地 link 表，同事务创建待审批 Action；不改变 B 摘要协议。替代只在聊天里存 diagnosisId，无法独立持久复核。
- **评测**：真实关键词检索与固定生成器校验分层；30 例与门槛是初版提案。替代直接复用 /rag/eval 会触发未授权边界。

## 待批准长期决策

Runbook 版本/不可变证据、诊断与 Action 来源关联、离线评测分层与门槛为 `TBD`。确认后追加 docs/DECISIONS.md，不替代 DEC-008/009/011/012/017～022 的已有结论，不预占 ADR 编号。
