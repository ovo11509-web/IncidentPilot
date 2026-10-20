# 实施计划：TST-002 旧RAG探针分类与隔离门禁

**分支**：实际 `feature/incident-platform`，Spec G目录 `007-rag-test-isolation` | **日期**：2026-10-05 | **规格**：[spec.md](spec.md)
**输入**：`specs/007-rag-test-isolation/spec.md`
**状态**：用户授权按推荐直接实施，analyze 13/13覆盖，无阻塞；实施完成，20/20任务，实际证据见 [实施报告](implementation-report.md)。

## 摘要

复用已有确定性测试，补真实服务与客户端缺口；旧演示/外部检查保留手工用途并列出缺口。新增You.com/MinerU显式联调许可条件，避免只有凭据/参数就启动。默认仍报告手工探针跳过，不改全仓跳过口径来凑成功。

## 技术背景

**语言/版本**：Java 17编译基线；实际运行JDK实施时记录。
**主要依赖**：现有JUnit 5、Mockito、Spring Boot 4、MyBatis-Plus、OkHttp、Milvus SDK；不新增依赖。
**存储**：本轮新增测试只用固定Mapper数据或自有HTTP；全仓现有集成测试使用临时容器。
**测试**：JUnit聚焦→infra-ai/rag及依赖模块→默认全仓；逐组记录XML。
**目标平台**：本机Windows/PowerShell及现有Maven Wrapper。
**项目类型**：既有多模块仓库的测试治理；没有新生产接口。
**性能目标**：不做性能宣称；确定性断言和本地隔离为门槛。
**约束**：保留dirty/未跟踪文件、许可证/署名；禁Git写操作、付费/外部资源/开发库。禁止启用真实联调。
**规模/范围**：11类盘点；6组真实行为补测（改写映射、会话、意图树、生产分类、Embedding、Milvus），2补充门条件与安全负例。

## 宪章检查

| 原则 | Phase 0 / Phase 1检查 |
| --- | --- |
| 署名与可追溯 | 保留原探针和上游许可头，测试治理属IncidentPilot新增；通过 |
| 持久化正确性 | 不改Schema/领域/事务；Mapper替身不宣称SQL正确性；通过 |
| 人工副作用控制 | 无实际业务操作，外部条件默认关闭；通过 |
| Spec范围控制 | 本目录唯一规格；DEP/模型质量/平台重构范围外；通过 |
| 证据后结论 | 默认及聚焦实际运行后才关闭，旧XML仅现状参考；通过 |

## 项目结构

```text
specs/007-rag-test-isolation/
  spec.md plan.md tasks.md research.md data-model.md quickstart.md
  contracts/test-gates.md checklists/requirements.md
infra-ai/src/test/java/com/nageoffer/ai/ragent/infra/embedding/
  SiliconFlowEmbeddingClientTest.java             # 新增
rag/src/test/java/com/nageoffer/ai/ragent/
  ingestion/service/impl/IntentTreeServiceImplTest.java  # 新增
  rag/core/rewrite/QueryTermMappingServiceTest.java      # 新增
  rag/core/vector/MilvusVectorStoreAdminTest.java        # 新增
  rag/service/impl/ConversationMessageServiceImplOrderTest.java # 扩展
  rag/core/intent/DefaultIntentClassifierTest.java       # 扩展
  rag/core/retrieval/channel/WebSearchChannelLiveTest.java # AND许可门
  core/parser/mineru/MinerUPdfUploadFlowTest.java        # AND许可门
  TestRagentApplication.java                            # 仅核对，不改
```

**结构决策**：原九类探针代码保留，本功能对其手工边界在文档与必要类说明中标注；不复制/升格VectorIntentClassifier等test-only代码。不新增通用测试抽象、Maven排除profile、生产代码或依赖。MinerU用JUnit不同条件注解AND；You.com在RAG类级门之后检查方法key前置，避免重复不可叠加的环境注解。

## 断言与运行设计

- QueryTermMappingService：缓存命中/空缓存不查库，未命中查库回填；合法规则归一化、禁用/不支持/空规则边界。改写正常/非法输出与历史窗口直接复用现有两类测试。
- ConversationMessageServiceImpl：身份/会话/deleted/limit查询条件、ASC/DESC稳定排序（已有）、结果投影和assistant反馈、空列表；检查调用及确切字段而非assertNotNull。不要求改当前返回顺序语义。
- IntentTreeServiceImpl：getFullTree父子映射、空树；initFromFactory已有节点不写/部分新增返回计数与父先子后映射。所有Mapper/cache/audit均固定替身；不要测试私有打印函数。
- DefaultIntentClassifier：先核对已有3项，不重复；补非法JSON或不合法候选的当前过滤/失败结果。生产行为如与文档冲突先明确，不自动改实现。
- SiliconFlowEmbeddingClient：回环HTTP请求model/input/dimensions/授权占位符，批量响应映射/顺序，空批不发请求、HTTP错误分类。服务绑定127.0.0.1随机端口，测试指定完整URL、固定占位key，不读取供应商配置。
- MilvusVectorStoreAdmin：ensureVectorSpace存在不创建；不存在时实际生产共享集合名、id（值为chunkId）/collection_name/向量维度与索引请求；SDK错误保持当前传播。Mockito只替SDK，不替被测服务。

外部门方案统一RAG_LIVE_TESTS：You.com把key检查放在方法内Assumptions且类上先RAG_LIVE_TESTS（它不启动Spring）；MinerU保留file system-property并类上添加RAG环境门。You.com不会因无许可构造HTTP对象；有许可但无key也不得发送。门禁负例用占位key与不存在的临时文件，在RAG=false时精确选择两类，实际XML均skipped，且不得启动Spring/HTTP；未开启授权路径不做真实调用。

## 保留与实施前门槛

分支feature/incident-platform、HEAD 67624931f20812922568d3641a095adb291263ba，暂存为空；本轮1776个文件含功能指针建立摘要基线。setup-plan输出BRANCH是Spec逻辑名称，不表示Git分支改变。

完整设计已获用户直接执行授权；analyze已完成，implement进行中。无extensions.yml钩子。按依赖顺序逐组测试记录；已有修改不回滚、格式不全仓改写。发现需要生产变更或未规划外部探针时先报告实质范围冲突。

## 复杂度跟踪

无宪章例外；不新增profile、标签筛选体系、统一沙箱或模型代理。

默认全仓启动前检查其他既有门：MCP YouCom也靠YDC_API_KEY，执行窗口需暂时移除该环境值并finally恢复；不改MCP代码。AgentDashboardReaderTest靠ragent.dashboard.test.jdbc-url，必须确认未提供任何开发连接参数。不得输出原环境值、读取全配置或把外部联调并入本轮。

## 本轮实施授权与analyze

用户要求检查遗漏后按推荐直接执行并总结；无需重复确认。分析9 FR/4 SC与20任务，13/13覆盖，无未映射任务或宪章冲突；Milvus字段说明按实际id（值为chunkId）修正，未改变业务契约。清单14/16，两项未勾选标记保持只读，实际授权与analyze在此记录。实施前 1784 文件摘要建立于新的target归档，Git分支/HEAD/空暂存不变。
