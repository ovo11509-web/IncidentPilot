# 任务：TST-002 旧RAG探针分类与隔离门禁

**输入**：`specs/007-rag-test-isolation/` 设计产物
**前置条件**：完整spec/plan/tasks确认、正式speckit-analyze无阻塞，保留所有已有修改。
**状态**：已完成20/20；用户明确授权直接执行，真实结果与作者审阅见 [实施报告](implementation-report.md)。
**测试**：本功能明确要求真实代码行为断言、门控负例及模块/全仓回归。
**组织方式**：按用户故事与依赖分组，所有路径相对于仓库。

## Phase 1：准备

- [X] T001 确认批准范围，在 `specs/007-rag-test-isolation/plan.md` 记录analyze、当前分支/HEAD/status/diff与实施前保留基线；无批准不实施。

## Phase 2：基础约束

- [X] T002 在 `specs/007-rag-test-isolation/research.md` 核对11类目录、17旧方法/19历史RAG跳过口径与test-only/生产路径，不遗漏原探针（FR-001/002）。
- [X] T003 在 `specs/007-rag-test-isolation/quickstart.md` 固定安全执行窗口、其他模块key/JDBC条件核对、环境finally恢复与新XML时间/精确目标类校验规则；不打印原环境值（FR-005/006/007/008）。

## Phase 3：用户故事1 - 可信分类与已有覆盖（P1）

**独立测试**：11类有去向，test-only算法与真实行为分开，历史探针保留。

- [X] T004 [US1] 更新 `specs/007-rag-test-isolation/research.md` 和必要旧探针类说明，保留上游手工示例；复用 `rag/src/test/java/com/nageoffer/ai/ragent/rag/rewrite/MultiQuestionRewriteServiceTests.java`、`rag/src/test/java/com/nageoffer/ai/ragent/rag/rewrite/MultiQuestionRewriteWindowTest.java`、`rag/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieval/channel/WebSearchChannelTest.java`、`rag/src/test/java/com/nageoffer/ai/ragent/ingestion/engine/IngestionEngineTest.java` 的已有断言，目录明确未等价覆盖的Invoice/向量演示及SaaS能力（FR-001/002/009；SC-001）。

## Phase 4：用户故事2 - 默认确定性行为（P1）

**独立测试**：各组真实服务/客户端正常和适用失败边界，不调用外部服务，目标无跳过。

- [X] T005 [US2] 新增 `rag/src/test/java/com/nageoffer/ai/ragent/rag/core/rewrite/QueryTermMappingServiceTest.java`，实际服务配固定Mapper/cache，覆盖缓存命中/空缓存/未命中回填、合法与禁用/空规则，保留现有改写测试（FR-003）。
- [X] T006 [US2] 运行QueryTermMappingServiceTest及两类既有改写聚焦测试，在 `specs/007-rag-test-isolation/quickstart.md` 记录实际XML与结果；目标存在、零跳过才计通过（FR-007）。
- [X] T007 [US2] 扩展 `rag/src/test/java/com/nageoffer/ai/ragent/rag/service/impl/ConversationMessageServiceImplOrderTest.java`，覆盖身份/会话/删除过滤、limit、空结果、投影/反馈及适用访问拒绝，保留稳定排序原断言（FR-003）。
- [X] T008 [US2] 聚焦运行会话测试并在 `specs/007-rag-test-isolation/quickstart.md` 记录真实结果，不使用原固定历史会话/admin（FR-007/008）。
- [X] T009 [US2] 新增 `rag/src/test/java/com/nageoffer/ai/ragent/ingestion/service/impl/IntentTreeServiceImplTest.java`，实际树服务配Mapper/cache/audit替身，覆盖空树/父子映射、已有节点不写与新增返回计数/父先子后规则（FR-003）。
- [X] T010 [US2] 聚焦运行意图树测试，在 `specs/007-rag-test-isolation/quickstart.md` 记录结果及不证明真实数据库约束的限制（FR-007/009）。
- [X] T011 [US2] 核对并扩展 `rag/src/test/java/com/nageoffer/ai/ragent/rag/core/intent/DefaultIntentClassifierTest.java` 的非法JSON/候选过滤当前契约，不补test-only专家算法或重复已有3项（FR-002/003）。
- [X] T012 [US2] 聚焦运行真实生产分类测试并在 `specs/007-rag-test-isolation/quickstart.md` 保存结果与固定模型边界（FR-007/009）。
- [X] T013 [US2] 新增 `infra-ai/src/test/java/com/nageoffer/ai/ragent/infra/embedding/SiliconFlowEmbeddingClientTest.java`，实际客户端接127.0.0.1随机端口JDK HttpServer，核对model/input/dimension、占位授权、响应映射/顺序、空批与HTTP错误；finally关闭资源（FR-004/008）。
- [X] T014 [US2] 聚焦运行Embedding客户端测试并在 `specs/007-rag-test-isolation/quickstart.md` 记录实际请求/结果层级，不启用真实供应商探针（FR-007/009）。
- [X] T015 [US2] 新增 `rag/src/test/java/com/nageoffer/ai/ragent/rag/core/vector/MilvusVectorStoreAdminTest.java`，实际服务配SDK替身，核对已有集合不写、新建实际共享Schema/维度/索引及SDK失败传播，不复制旧doc_id示例契约（FR-004）。
- [X] T016 [US2] 聚焦运行Milvus实际服务测试，在 `specs/007-rag-test-isolation/quickstart.md` 记录结果及未运行真实Milvus的限制（FR-007/009；SC-002）。

## Phase 5：用户故事3 - 安全许可与默认门禁（P2）

**独立测试**：两个无许可但有占位资源负例均跳过，不启动Spring或HTTP；随后模块/全仓真实回归。

- [X] T017 [US3] 更新 `rag/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieval/channel/WebSearchChannelLiveTest.java` 为RAG类级许可加key方法前置，更新 `rag/src/test/java/com/nageoffer/ai/ragent/core/parser/mineru/MinerUPdfUploadFlowTest.java` 为RAG许可AND文件条件；按 `specs/007-rag-test-isolation/contracts/test-gates.md` 执行两个负例并保存XML/零外部上下文证据，环境finally恢复（FR-005/006；SC-003）。
- [X] T018 [US3] 执行相关模块及默认全仓回归，核对本轮目标XML、失败/错误/跳过名单、源码摘要/命令/退出码，将失败与最终通过分别存至新的模块target目录并在 `specs/007-rag-test-isolation/quickstart.md` 记录，不覆盖OBS/LAB历史（FR-007/008/009；SC-004）。

## Phase 6：收尾

- [X] T019 同步 `docs/development/testing.md`、`docs/PROJECT_CONTEXT.md`、`docs/TODO.md`、`docs/changelog.md` 与 `specs/007-rag-test-isolation/research.md`：实际新覆盖、手工探针/跳过、默认安全门及未验收范围；仅满足本Spec门槛后关闭TST-002（FR-009）。
- [X] T020 在 `specs/007-rag-test-isolation/quickstart.md` 或实施报告做作者走读、真实失败/取舍与3自检题；检查相对链接/许可/秘密/原文件摘要/差异与Git状态，确认无生产改动或授权越界（FR-008/009；SC-004）。

## 依赖关系与执行顺序

T001→T002→T003→T004；T005/006→T007/008→T009/010→T011/012→T013/014→T015/016→T017→T018→T019→T020。每组定义、真实运行、记录后再下一组。

## 并行示例

无必要并行代理。虽有独立测试文件，Maven模块报告与日志共用，串行避免证明混用；任务不标[P]。

## 实施策略

先目录和安全窗口，再复用/补最小业务检查；门禁负例之前先改门且审查可确保无外部调用。默认全仓由本次治理要求明确需要，完成后不机械重跑，后续仅变文档不要求再跑全仓。不得以放宽断言或排除失败类达标；需要生产变更时报告实质范围冲突。

## 需求覆盖

FR-001:T002/004；FR-002:T002/004/011；FR-003:T005/007/009/011；FR-004:T013/015；FR-005:T003/017；FR-006:T003/017；FR-007:T003/006/008/010/012/014/016/018；FR-008:T003/008/013/018/020；FR-009:T004/010/012/014/016/018/019/020。
SC-001:T002/004；SC-002:T005～016；SC-003:T017；SC-004:T018～020。
