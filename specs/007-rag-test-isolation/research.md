# 研究与brainstorming：旧RAG探针分类

日期2026-10-05；用户已选“按行为分类收尾”，用户随后授权检查遗漏后按推荐直接执行。下文现状来自实施前源码、既有测试及历史XML；本轮新增实际运行见quickstart和实施报告，未运行外部联调。

## 方案取舍

1. 推荐：按真实生产行为补测，保留有边界的手工探针。复用JUnit/Mockito/JDK HttpServer，断言能在生产逻辑改变时失败。
2. 九类统一mock：会为SimpleIntentClassifierTests、VectorIntentClassifier等测试内演示算法制造虚假的生产覆盖，不采用。
3. 只保留现有环境门：没有补实际行为断言，且You.com/MinerU尚可能因凭据/文件参数触发，不足以收尾。

不引入测试框架、模型服务、通用沙箱或生产适配层。保留手工探针既有类和许可头；默认以条件跳过，不新增Maven排除profile。保留条件比全面重命名更小，也不以降低跳过数作为目标。

## 现状与分类（11类）

路径均相对于仓库；旧探针位于rag/src/test/java/com/nageoffer/ai/ragent/下。

| 原探针 | 现有行为/风险 | 推荐去向与真实对象 | 未验证界限 |
| --- | --- | --- | --- |
| RagentCoreApplicationTests | 无断言contextLoads、完整Spring/开发配置 | 保留手工装配；引用BootstrapIsolatedStartupTest既有隔离启动，不复制新空context | 开发环境装配不验收 |
| index/InvoiceIndexDocumentTests | 测试内Tika+LLM清洗/分块/写Milvus/打印回答 | 保留手工示例；引用IngestionEngineTest、ChunkingFixtureTest的已覆盖部分，缺失不得冒充等价 | 示例开票端到端与真实模型未验收 |
| service/ConversationMessageServiceTests | 固定历史会话/admin查库后打印 | 保留手工历史查询；扩展实际ConversationMessageServiceImplOrderTest的身份过滤、limit、结果投影/反馈与空列表 | Mapper替身不证明SQL事务 |
| vector/MilvusCollectionTests | 创建test_collection、4096维旧doc_id schema | 保留明确标注的旧演示；新增MilvusVectorStoreAdminTest针对实际ensureVectorSpace及共享collection_name/id（值为chunkId）schema | SDK调用不证明真实Milvus |
| rag/embedding/SiliconFlowEmbeddingServiceTests | 实际供应商embed后打印 | 保留手工供应商检查；infra-ai新增SiliconFlowEmbeddingClientTest，真实客户端接自有回环HttpServer | 不证明远程语义质量/维度模型可用性 |
| rag/Intent/IntentTreeServiceTests | initFromFactory写数据库、getFullTree打印 | 保留手工；新增IntentTreeServiceImplTest对实际树映射、初始化已存在节点不重复写及返回计数断言 | Mapper模拟不证明DB幂等约束 |
| rag/Intent/SimpleIntentClassifierTests | 分类/专家打分主要实现在测试内 | 保留演示；扩展已有DefaultIntentClassifierTest的实际生产解析/过滤/失败边界 | 不迁移演示算法、不声称真实分类质量 |
| rag/Intent/VectorTreeIntentClassifierTests | 调用仅test目录中的VectorIntentClassifier，init未自动执行 | 保留手工实验；标注无直接生产等价路径，不补自我验证测试 | 向量树质量及测试内初始化问题未修复 |
| rag/rewrite/QueryRewriteTests | 4参数输入直调LLM另1调QueryRewriteService，只有非空/打印 | 保留手工prompt；复用MultiQuestionRewriteServiceTests/WindowTest，新增QueryTermMappingServiceTest补缓存与映射真实行为 | 固定输出不代表模型改写质量 |
| rag/core/retrieval/channel/WebSearchChannelLiveTest | 仅YDC_API_KEY存在即外部调用 | 增加RAG_LIVE_TESTS=true许可条件AND原key条件；保留WebSearchChannelTest已有11项本地HTTP测试 | 不调用You.com、不证明远程搜索质量 |
| core/parser/mineru/MinerUPdfUploadFlowTest | 仅mineru.test.pdf参数即启动Spring/SaaS上传 | 增加RAG_LIVE_TESTS=true许可条件AND原文件条件；保留手工说明/标明写出目录.output | 不上传PDF、不证明SaaS解析 |

“只打印”不代表所有相关生产代码不补测：独立生产规则仍按缺口补。保留探针保留原源码及手工用途，补测写在真实模块的现有测试类或小型新测试类，避免大幅覆盖原文件。

## 数量口径

当前9个RAG_LIVE_TESTS类，17个源码@Test/@ParameterizedTest方法；QueryRewriteTests含4个参数输入，全部启用时此类为5次，但类级跳过的历史XML为2。另MinerU和You.com各1，RAG历史合计19跳过，与最近全仓840/21跳过记录相符；其他模块YouCom与Dashboard另2项。不把旧XML当作本轮结果，不承诺改动后跳过数减少。

## 安全与依赖

真实供应商客户端支持显式ModelTarget URL；已有BaiLianRerankClientTest/WebSearchChannelTest使用JDK HttpServer，无需MockWebServer或新依赖。Mapper元数据初始化复用ConversationMessageServiceImplOrderTest现有方式。新测试不加载TestRagentApplication，不使用application.yaml开发连接；实际装配复用已有Bootstrap隔离夹具。

本轮全部未知项已通过源码明确或置于范围外，无需外部资料/服务调研。历史文档与当前代码的主要差别是“19跳过”包含两种其他条件，不是九类17方法的简单相加；目录显式分开。不得把Milvus旧doc_id演示Schema代替当前生产id（值为chunkId）/collection_name契约。

本轮分析纠正文档字段命名：Milvus真实字段为id，值来自chunkId，旧doc_id演示不能代替。QueryTermMappingDO优先级注释称小值优先，但loadMappings当前Comparator对priority逆序；本轮不改生产语义，不将此未确认优先级规则写成正确性结论，范围外待评估。

## 实施收尾（2026-10-05）

已按推荐完成20项任务，新增24项确定性断言，聚焦40项通过；模块392项/19跳过、全仓864项/21跳过，零失败/错误。分类保留全部旧手工探针，没有将演示或SDK替身包装为真实服务验证；当前断言、失败与范围见 [实施报告](implementation-report.md)。
