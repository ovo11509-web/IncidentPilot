# 测试执行边界契约（已批准）

默认自动测试仅固定LLM/Mapper/SDK及本轮自有回环HTTP；新增用例不加载完整TestRagentApplication、不从application.yaml获取开发资源。不修改生产流量。

旧九类保留RAG_LIVE_TESTS=true类级条件；本轮默认false，手工探针的跳过保留并记录。You.com采用同一RAG许可类级条件，key仅方法前置；MinerU采用RAG许可AND现有mineru.test.pdf条件，阻断发生在Spring初始化前。保留的文件参数、key存在都不足以授权执行。

两项负例：仅注入无效占位YDC_API_KEY及不存在的mineru.test.pdf，RAG_LIVE_TESTS=false；定向运行两个类应skipped各1、零错误且不初始化外部上下文。不提供真实API key，不启用实际许可，不上传文件。运行后恢复原环境变量，禁止写出原值。

JUnit条件同类注解不可未经核对重复叠加：You.com将旧key annotation替为RAG gate并在方法起始assumeTrue非空key；MinerU是不同条件注解AND。复用已有依赖；无需Maven profile或新许可服务。

通过条件：新聚焦业务检查实际执行且零跳过；默认模块/全仓退出0且零失败/错误，跳过逐类说明。若有失败保存原结果，不以测试排除、宽泛断言或后续PASS覆盖旧失败。
