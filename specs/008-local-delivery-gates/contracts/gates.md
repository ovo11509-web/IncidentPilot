# 门禁接口契约

## 命令与输出

`pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Smoke|Backend|Frontend|All`

`pwsh -NoProfile -File scripts/check-delivery.ps1 -CheckOnly [-Gate Smoke|Backend|Frontend|All]`

默认Gate为All；非法参数/任意执行参数拒绝。仓库从脚本位置解析，不依赖调用cwd。输出固定在bootstrap/target/delivery-gates/新UUID；禁止链接路径、越界、覆盖LAB/OBS或任意输出根。

## 状态/退出

0为所选范围PASS或CheckOnly的CHECKED（二者区分）；1为执行/断言/报告/安全摘要FAIL；2为前置工具/Docker/安全选项BLOCKED。中断不生成PASS。首个失败不能被后续覆盖，环境在成功/失败/异常finally恢复，原值永不输出。

## 确定性门槛

- Smoke：精确IncidentDiagnosisGoldenPathTest，一项零失败/错误/跳过，上游模块无指定类不能冒充目标运行。
- Backend：全仓test零失败/错误，必须有QueryTermMappingServiceTest、MultiQuestionRewriteServiceTests、MultiQuestionRewriteWindowTest、ConversationMessageServiceImplOrderTest、IntentTreeServiceImplTest、DefaultIntentClassifierTest、SiliconFlowEmbeddingClientTest、MilvusVectorStoreAdminTest，以及BootstrapIsolatedStartupTest、IncidentDiagnosisGoldenPathTest、IncidentDiagnosisSafetyTest、IncidentObservabilityIsolatedTest，目标实际执行零跳过。不固定总数864。
- 手工跳过目录：RAG的InvoiceIndexDocumentTests、SiliconFlowEmbeddingServiceTests、IntentTreeServiceTests、SimpleIntentClassifierTests、VectorTreeIntentClassifierTests、QueryRewriteTests、RagentCoreApplicationTests、ConversationMessageServiceTests、MilvusCollectionTests、WebSearchChannelLiveTest、MinerUPdfUploadFlowTest，另MCP YouComSearchMcpExecutorLiveTest、AgentDashboardReaderTest。完整类名/允许方法数量据源码固定，新增目录或方法跳过不得默许。
- Frontend每阶段退出0；契约/lint/build不证明真实浏览器与服务验收。

## 安全/资源/CI

仅本机Docker Linux Engine，拒绝远程覆盖和复用配置；缺前置环境不降级。不全局prune/kill或清理开发库；原夹具管理其拥有资源。强制取消的清理无保证，记录本轮残留，不删除无关资源。

原始stdout/stderr仅raw，XML结构禁外部实体。CI无业务Secrets/部署/镜像发布，最小权限且不持久化checkout凭据。safe只白名单JSON；摘要失效则失败、不回退上传raw。XML系统输出/失败正文、classpaths、临时TLS/账号与日志均不可发布，不声称全仓或生产秘密扫描完成。
