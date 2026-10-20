# 本地运行契约

入口 `scripts/local-demo.ps1`，命令Init/Build/Start/Status/Stop/Verify；Linux薄包装调用同一pwsh，未实测不算平台通过。

- Init只操作新演示project，随机凭据外置，首次全量Schema/受限角色/模拟目标，成功marker最后记录；重复初始化保持数据。
- Build产出应用/MCP executable JAR与frontend dist，禁用Maven隐式格式改写，不用测试classpath运行。
- Start核对初始化、制品、端口及所有权，仅回环，依赖/HTTP就绪后报告成功，自有运行实例不重复创建。
- Status区分进程存在与就绪，只输出受控状态/端口/阶段/制品，无凭据。
- Stop匹配instance/目录/创建时刻和Compose标签，只关闭自有资源，保留卷；无猜测归属、全局清理或默认销毁入口。
- Verify本地认证/只读工作台/目录/事件核验，不自动批准或执行处置。

配置在ignored `bootstrap/target/local-demo/`。普通远程MCP/模型/解析器不可用，HTTP仅本地Incident演示所需入口，禁用Broker操作明确报错。protected simulation服务端审批路径保持。

验收包含真实打包启动/登录和Stop-Start后事件保留；无此证据只能报告实现，不称运行通过。
