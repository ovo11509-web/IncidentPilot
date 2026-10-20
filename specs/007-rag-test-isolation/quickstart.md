# TST-002 验证指南（已验收）

用户已授权直接按推荐实施；下方早期计划和后续实际记录分开，未启用外部探针。详细分类见 [研究记录](research.md)，安全门见 [契约](contracts/test-gates.md)。

## 聚焦检查

在仓库根目录、RAG_LIVE_TESTS=false下逐组执行（新增测试已实现）：

```powershell
$env:RAG_LIVE_TESTS='false'
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl rag -am '-Dtest=QueryTermMappingServiceTest,MultiQuestionRewriteServiceTests,MultiQuestionRewriteWindowTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl rag -am '-Dtest=ConversationMessageServiceImplOrderTest,IntentTreeServiceImplTest,DefaultIntentClassifierTest,MilvusVectorStoreAdminTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl infra-ai -am '-Dtest=SiliconFlowEmbeddingClientTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

必须查看实际XML确认各目标类/方法运行，无失败/错误/跳过；failIfNoSpecifiedTests=false只为上游无目标类的模块，不允许目标类不存在却算通过。

## 两个外部门负例

```powershell
# 实际执行时保存并在finally恢复环境值；占位值不是可用凭据
$env:RAG_LIVE_TESTS='false'
$env:YDC_API_KEY='local-gate-placeholder'
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl rag -am '-Dtest=WebSearchChannelLiveTest,MinerUPdfUploadFlowTest' '-Dmineru.test.pdf=nonexistent-gate-fixture.pdf' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

期望精确XML两个skipped、零错误；无Spring启动/HTTP请求。这是门控负例，不能计为两个外部服务验收通过。许可门已实施，负例已实际运行；两个skipped理由均为RAG=false，日志没有Spring启动，不能计为外部服务通过。

## 扩大检查

```powershell
$env:RAG_LIVE_TESTS='false'
# 此次实施还须对其他模块凭据条件作安全核对；清空或以无效值禁用外部门且finally恢复，不输出原值
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl rag -am test
.\mvnw.cmd '-Dspotless.apply.skip=true' test
```

沿用 [仓库测试说明](../../docs/development/testing.md)。全仓既有隔离测试需要Docker及临时镜像；不使用H2、共享库或远程服务代替。需要拉取依赖/镜像与调用付费业务服务是不同边界；当前不启动远程联调。

## 记录与限制

保存当前源码摘要、实际命令/退出码、聚焦与默认新XML计数和跳过名单，target内新目录不覆盖历史记录。手工probe与test-only演示没有被运行，不冒充真实模型、Milvus、MinerU或搜索质量。新增默认检查实际数量由实施结果记录，不预填性能或可靠性数字。

## 本轮阶段记录

- rewrite：16项，0失败/错误/跳过，退出0；归档 `bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/rewrite-471e6ad6-85d7-443b-8f07-47799eea0e9d`。
- conversation-final：6项，0失败/错误/跳过，退出0；归档 `bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/conversation-final-55868867-91fd-4e45-a330-3a318182e6de`。

首次临时记录命令缺少Maven目标，未运行测试、退出1，修复后重跑；会话首次与诊断复跑各6项/2失败，是MyBatis手工元数据未开启下划线映射造成SQL列名断言不符。按Spring配置修正测试夹具后6项通过，生产代码未变，旧失败记录保留。

意图树4项通过、0失败/错误/跳过，退出0；首次4项中2项因全局subclass MockMaker不支持静态mock报错，两个局部factory mock显式选用现有inline MockMaker后重跑通过，不改全局配置/依赖。新证明目录 `bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/intent-tree-final-95f6ea8c-a851-4314-b637-76317c688a31`，旧失败保留。

classification 6项，0失败/错误/跳过，退出0；`bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/classification-a19352d4-c6de-4dbb-b308-a7e1a7957375`。

embedding 5项，0失败/错误/跳过，退出0；`bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/embedding-135f06ad-4e92-4d26-8110-dcbcd87afee3`。

Milvus管理3项通过、0失败/错误/跳过，退出0；`bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/milvus-2b69dc12-21db-4972-a57f-be50cba64f72`；SDK为替身，未启动真实Milvus。

两个许可门负例归档：`bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/gate-1836ad11-099a-4063-b3bd-de7a751fa1bc/`；两个类均1 skipped，原因明确为RAG_LIVE_TESTS=false，没有Spring应用启动；不是外部服务运行证据。

相关模块回归：`./mvnw.cmd -Dspotless.apply.skip=true -pl rag -am test`，2026-10-05T23:16:32+08:00结束，392项/0失败/0错误/19跳过，退出0；framework38、infra-ai33、rag321。归档 `bootstrap/target/tst002-implementation/7b044467-b7c0-4e61-821a-2915202cc5a7/module-regression-b1495c5b-364a-442a-9d34-40e23809b90a/`。默认全仓已完成：864项，843通过、21跳过、0失败/错误，退出0；2026-10-05T23:49:05+08:00结束，归档 `full-regression-bc26d580-6143-4b7e-811a-f1393ad8823f/`（同一证据根目录）。

## 可复现安全执行窗口

以上命令都必须放在以下窗口内执行；保存原值但不输出，在finally恢复。MCP的旧You.com探针仍单独由key控制，不能只设RAG=false；可选Dashboard JDBC参数也不得提供。负例阶段才在窗口内设无效占位key，并只运行两个RAG目标类。

```powershell
$tstOriginalRag = $env:RAG_LIVE_TESTS
$tstOriginalYdc = $env:YDC_API_KEY
try {
    $env:RAG_LIVE_TESTS = 'false'
    Remove-Item Env:YDC_API_KEY -ErrorAction SilentlyContinue
    # 先核对MAVEN_OPTS及Java选项未配置ragent.dashboard.test.jdbc-url。
    # 按上文需要选择聚焦或模块命令；全仓仅在必要时执行：
    .\mvnw.cmd '-Dspotless.apply.skip=true' test
    if ($LASTEXITCODE -ne 0) { throw "Maven验收失败：$LASTEXITCODE" }
} finally {
    $env:RAG_LIVE_TESTS = $tstOriginalRag
    $env:YDC_API_KEY = $tstOriginalYdc
}
```

本轮运行JVM为Java 21.0.12、编译目标17。聚焦40项全部通过；模块392项中373通过、19跳过；全仓864项中843通过、21跳过。具体跳过名单、真实失败及作者走读见 [实施报告](implementation-report.md)。Mapper/SDK为替身，本地HTTP使用固定响应；没有真实模型质量、Milvus服务、开发库或生产基础设施验收。
