# TST-002 实施与作者审阅

2026-10-05，用户授权检查遗漏并按推荐直接执行。analyze核对9 FR/4 SC与20任务，13/13覆盖，无阻塞宪章冲突；清单14/16保持只读。本轮全仓回归864项：843通过、21跳过、零失败/错误，退出0。20项任务按实际检查收尾。

## 改动与真实调用路径

保留九类旧RAG手工探针和上游许可头；11类目录说明生产映射、test-only演示、补测及缺口。新增4个测试类、扩展2个现有测试类，共24项新增确定性检查。生产代码、Schema、UI、POM、依赖与全局Mockito配置未改。

- QueryTermMappingService→固定Mapper/cache→缓存命中或空缓存不读库、未命中回填、合法映射且不重复目标、禁用/不完整规则忽略。
- ConversationMessageServiceImpl→Mapper/反馈服务→身份/会话/逻辑删除过滤、limit与稳定排序、空结果、消息投影及当前用户反馈。
- IntentTreeServiceImpl→Mapper/cache/audit及局部固定Factory→父子映射、Collection绑定、已有节点不写、初始化返回计数与父先子后、再次初始化零新增。状态替身不证明真实DB并发幂等。
- DefaultIntentClassifier→cache/LLM/prompt→异常和非法JSON返回空意图且不重试，过滤未知/不完整候选，已知分数降序。
- SiliconFlowEmbeddingClient→本轮127.0.0.1随机端口HTTP→请求内容、响应映射/数组顺序、空批零调用、429错误分类、畸形向量拒绝；不证明供应商质量或乱序index响应支持。
- MilvusVectorStoreAdmin→SDK替身→共享集合存在时不重建，实际id字段（值为chunkId）/collection_name/维度及索引请求，SDK错误原样传播；未创建真实集合。

两个外部门加固：You.com类级要求RAG_LIVE_TESTS，方法起始检查key；MinerU要求RAG许可AND原PDF参数。无许可时在测试方法/应用初始化之前跳过。显式许可值仍需人类另行授权，凭据本身不构成授权。

## 取舍与剩余能力

原打印示例保留手工用途，不mock演示输出来制造生产覆盖。已有改写窗口、WebSearch本地HTTP、IngestionEngine及Chunking测试复用；Invoice完整示例和test-only向量/专家分类不是等价覆盖。

不增加标签排除、Maven profile、测试框架、统一沙箱或生产适配层。两个局部静态Factory mock选用现有inline MockMaker并自动关闭，避免改变全仓subclass配置。

QueryTermMappingDO注释称priority较小优先，但loadMappings的Comparator当前逆序；这是尚未确认的遗留差异。本轮只测明确的映射规则，不改排序、不声称该差异已解决。

## 聚焦与模块检查

| 阶段 | 检查数 | 失败/错误/跳过 | 退出码 |
| --- | --- | --- | --- |
| 术语映射及既有改写 | 16 | 0/0/0 | 0 |
| 会话查询 | 6 | 0/0/0 | 0 |
| 意图树 | 4 | 0/0/0 | 0 |
| 生产意图分类 | 6 | 0/0/0 | 0 |
| Embedding本地HTTP | 5 | 0/0/0 | 0 |
| Milvus SDK交互 | 3 | 0/0/0 | 0 |
| 两个许可门负例 | 2 | 0/0/2 | 0 |
| rag及依赖模块 | 392 | 0/0/19 | 0 |
| 默认全仓 | 864 | 0/0/21 | 0 |

六组聚焦合计40项，零失败/错误/跳过。门负例2项均因RAG=false跳过，XML说明阻断原因，日志无Spring应用启动；不计为2项外部服务通过。模块中373项实际通过，19项RAG手工探针跳过。具体命令、退出码及本轮新XML在独立target归档内。

## 真实失败与修复

- 首次临时记录命令缺Maven目标，退出1、无测试；修复参数构造并添加test后重跑。这不是Java失败。
- 会话首次和诊断复跑各6项/2失败：手工MyBatis元数据默认驼峰列名与SQL断言不符；设置与Spring一致的下划线映射后6项通过，原失败保留。
- 意图树首次4项/2错误：全局subclass不支持静态mock；两个局部作用域选用既有inline后4项通过，未改全局或依赖。

## 作者自检

1. 为什么保留19项RAG跳过仍能完成测试治理？哪些生产断言新增，哪些能力未验证？
2. Mapper/SDK替身和本地HTTP各能证明什么？为什么不能据此声称真实SQL并发、Milvus可用性或模型质量？
3. 为什么key或PDF参数不能作为执行授权？RAG许可门在You.com/MinerU哪一步阻止初始化或调用？

测试治理属于IncidentPilot新增贡献；上游Ragent及Apache-2.0署名保留。未提交、推送、合并、部署、调用付费服务或操作开发库/真实基础设施。DEP-001、真实模型质量、生产/跨主机/真实Milvus/Broker与SaaS未在本轮验收。

## 证据目录与源码

本轮独立根目录 `bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/`，每条实际命令分别保存start.json、maven.log、result.json和只收集开始时间后的XML副本。tested-source-hashes.json记录1159项实际源码，默认模块及全仓执行期间核对未变化；HEAD不代表未提交实现内容。原始Maven诊断仅本地排错，不作为可公开凭据安全证明。

默认模块统计：framework 38、infra-ai 33、rag 321（其中19跳过）；复用的IngestionEngine 4、Chunking 1、WebSearch本地HTTP 11已在该运行实际执行。

全仓实际命令 `./mvnw.cmd -Dspotless.apply.skip=true test`，2026-10-05T23:18:20+08:00开始，23:49:05+08:00结束；独立归档 `full-regression-bc26d580-6143-4b7e-811a-f1393ad8823f/`。framework 38、infra-ai 33、mcp-server 68（1跳过）、incident 122、rag 321（19跳过）、agent 269（1跳过）、bootstrap 13，合计864；21跳过不计通过。Bootstrap七个启动/安全场景、诊断黄金路径1项、安全1项及OBS四场景均实际通过。当前全仓结果不替代各Spec历史验收或证明生产环境。

跳过名单：RAG中MinerUPdfUploadFlowTest 1、InvoiceIndexDocumentTests 3、WebSearchChannelLiveTest 1、SiliconFlowEmbeddingServiceTests 1、IntentTreeServiceTests 2、SimpleIntentClassifierTests 2、VectorTreeIntentClassifierTests 4、QueryRewriteTests 2、RagentCoreApplicationTests 1、ConversationMessageServiceTests 1、MilvusCollectionTests 1；另MCP YouComSearchMcpExecutorLiveTest 1、AgentDashboardReaderTest 1。均是条件未开放的手工/环境检查；本轮新增确定性检查无跳过。

其他模块边界：MCP YouCom的旧联调仍单靠YDC_API_KEY，本Spec不修改它；默认模块/全仓运行窗口暂时移除key并finally恢复，XML中该探针跳过。后续DEP-001的CI应沿用这一安全窗口，不把RAG=false误认为自动关闭所有模块的外部测试。可选Dashboard JDBC未提供任何连接参数。

## 最终保留与审阅核对

20/20任务完成。1784个起始文件无缺失，变化仅为本轮列明的4个已有测试、Spec G及上下文文档；原未跟踪文件和已有修改保留。全仓结束后1159项实际源码摘要仍与执行前一致，未在运行期间改源码；相对链接、任务编号、四个新测试Apache许可头、差异空白检查均通过。需求清单保持只读，暂存为空，分支和HEAD未变。本轮临时Testcontainers容器已退出。新增测试静态凭据模式扫描零命中，只是局部检查，不代表原始诊断日志可公开。

最终保留检查见证据根下final-preservation.json；全仓结束后的改动均为文档，不重复执行全仓。下一步推荐DEP-001本地容器编排/CI/部署说明的现状审查与设计，真实部署或外部联调仍须单独授权。
