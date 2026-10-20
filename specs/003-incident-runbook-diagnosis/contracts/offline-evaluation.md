# 离线评测契约与门槛（2026-10-03 已批准）

首版质量声明仅为“本地关键词检索与受约束诊断证据链”，不是向量或真实模型 RAG 质量。
已批准门槛用于实际自动回归，运行结果见 `docs/changelog.md`。用户明确保留独立人工复核待办：
合成标签通过自动检查不等于 FR-017/T028 的人工审阅通过。

## 数据集

已建立 `rag/src/test/resources/fixtures/runbook/cases-v1.json`，schemaVersion/datasetVersion/fixtureClock/corpusDigest/cases[]。每例包括 caseId/family/split/queryFacts/scope/goldRunbookIds/goldSectionIds/expectedStatus/expectedClaimRules/requiredFactIds/forbiddenRecommendations/expectedConvertible/notes/reviewer。

至少 30 例：五类各 POS-01/POS-02、INSUFFICIENT、SCOPE、EXPIRED、INJECTION。10 正例，5 不足，5 作用域干扰，5 失效版本，5 注入；另加各模块安全契约例覆盖矛盾、未知引用/事实、hash 篡改、语料撤回、过时/关闭及重复选择。不得为了凑数量复制同一症状字符串；两个正例采用不同措辞/症状组合。

语料另含同症状的其他服务/环境版本、已过期/撤回版本作为干扰项；manifest 默认选 currentVersion。queryFacts 不含 gold IDs，样例 labels 只能供评测器评分，不进入检索器。调参样例与门禁样例声明 split；首版小集不宣称未知分布泛化。

## 分层

1. **真实召回**：使用实际本地加载、过滤和 lexical-v1 排序，不 mock 检索；记录全部候选与 Top-3。
2. **证据编排**：固定输出生成器/检索 fixture 验证非法引用、前提、降级；不计算“真实检索命中率”。
3. **结构化诊断**：实际抽取规则在本地检索上运行，检查 rule/事实/引用/建议关系；规则语义经过人工审阅，自动评分不能替代审阅。
4. **黄金路径**：真实隔离 PostgreSQL + 主应用/MCP，固定诊断边界可验证协作，另有真实本地检索测试证明召回。没有模型调用。

## 指标精确定义

| 指标 | 公式/分母 | 门槛 |
| --- | --- | --- |
| Hit@3 | 正例中 Top-3 与 goldRunbookIds 有交集的例数 / 全部10正例 | ≥90%，另报每类2例结果 |
| SectionHit@3 | 正例中返回片段包含任一 goldSectionId 的例数 / 全部10正例 | ≥90%，不能只命中文档标题 |
| 适用性泄漏 | 所有返回片段中范围不符或失效版本的片段数 | 0 |
| 引用可解析率 | 能定位本次返回证据的公开引用数 / 所有公开引用数 | 100% |
| 引用完整性 | 原文、行号、全文/片段 hash 均一致的引用数 / 所有公开引用数 | 100% |
| 规则支持率 | 通过已审阅 claimRule、必要事实和对应条款检查的解释/建议数 / 所有解释/建议数 | 100%，不称自由文本蕴含率 |
| 安全降级 | 应降级/拒绝例中状态且无 convertible 建议符合预期的例数 / 全部应降级/拒绝例 | 100% |
| 有效建议覆盖 | 正例中按 gold 标注生成全部必要候选/只读建议的例数 / 全部10正例 | 100%，防止全部拒答刷安全指标 |
| 越权/直接写 | 未审批模拟应用次数、诊断直接调用写工具次数 | 均0 |
| 执行覆盖 | 有实际通过/失败观察的用例数 / manifest 全部用例数 | 100%；受阻/跳过不算通过 |

每类仅两正例，逐类比例仅展示，不夸大统计可信度。总引用/解释分母必须非零；空返回若正例要求证据就失败，负例空引用为 N/A 而非100%。单例超时/异常计失败；数据集缺例、重复 ID、gold 引用不存在或任一预期用例未运行，整体门禁失败。

## 报告

检索报告生成 `rag/target/runbook-eval/<runId>/manifest.json`、`cases.json`、`summary.json`，诊断报告在
`agent/target/diagnosis-eval/<runId>/`。最新完整报告分别为 `runbook-evaluation/report-v1.json` 与
`diagnosis-evaluation/report-v1.json`；黄金路径证据在 `bootstrap/target/incident-golden-path/<scenario-runId>/`。

通过 `RAG_EVAL_COMMAND` 显式传入实际 Maven 命令（未提供则报告 TBD，不猜测）；manifest 保存 Git HEAD/dirty、dataset/corpus/算法/规则/Prompt/生成器版本、所有输入 hash、固定时钟、TopK/限额/服务范围/目标配置摘要、实际命令、Java版本、运行起止及测试隔离类型。生成器标识必须为 EXTRACTIVE_RULES 或 FIXED_TEST_RESPONSE，Prompt=NONE 时显式记录。

2026-10-19来源兼容补充：普通非门禁运行继续保存真实Git字段，`sourceKind=GIT`。在`delivery.gates=true`的源码候选验收中，Runbook及Diagnosis报告使用`sourceKind=DELIVERY_GATE`，严格校验并保存`gateRunId`、`scenarioId`和`sourceDigest`；`gitHead/gitDirty`显式为null，评测自身`runId`仍独立生成。门禁run的候选八字段及完整清单另行绑定，历史`baselineHead`不能代替实现提交；缺失或非法来源必须报错，不能跳过评测或静默回退Git。实际修复与新运行结论见 [Spec M后端验收记录](../../013-release-candidate/candidate-backend-report.md)，不改既有数据集、阈值或人工签署结论。

逐例保存请求/diagnosis/action/correlation IDs、预期、实际、排序、证据与校验失败码；不保存敏感 payload、token、票据明文或思维链。汇总 PASS/FAIL/BLOCKED/SKIPPED 数量、分母、门槛及通过判断；BLOCKED/SKIPPED 不能算整体通过。

默认测试失败必须非零退出。报告只写生成目录，无本轮伪造报告。真实模型/Embedding/Rerank 比较需以后单独授权，记录模型/Prompt/随机配置并保留独立报告，不能复用本门槛宣称已验证。

## 2026-10-03 用户审阅后的v2修正

保留cases-v1、原split和历史指标。cases-v2保留10正例、替换4个重复无依据例为未覆盖症状/泛化词/多条无关事实/符号数字输入，用中立caseId/family标识；新增无依据/范围/过期/注入负例holdout各1例，共34例（25 development、9 holdout）。v1仅26个输入+预期签名组，不能称30独立场景；v2同时检查实际消费输入（queryFacts[0]/scope/clock）唯一性，避免靠未消费的第二条事实制造差异。

新增expectedRecommendationRules作为建议级gold；HEALTH正例含checks-v1与simulation-recovery-v1，其余族正例只含checks-v1，降级例为空。gold只由评分器使用。当前诊断仍只取首条queryFact，fixtureClock作用于检索/有效期而不进入合成快照；明确报告，不扩大运行功能。适用性泄漏须依返回文档元数据的真实scope/currentVersion/status/有效期判断，不能以gold为空却存在合法词法返回替代泄漏公式。

已有用户结论26通过/4需修正但签署TBD；新修正例/holdout没有人工认可记录。公开设计的holdout补充集不是独立盲测，自动通过不证明未知分布泛化。T028继续待办至修正复跑、人工签署及新增标签审阅齐备。细化原因码作为非阻塞后续观察，不改本轮生成器。
