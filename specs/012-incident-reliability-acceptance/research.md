# 研究：复用边界与缺口

- 决定：复用IncidentDbFixture包内接口及BootstrapIsolatedStartupTest生命周期；不复制巨型夹具、不引入故障平台。
- 原因：现有夹具拥有临时数据库、双JVM和自动清理；现有副作用断言已经覆盖B/F。
- 替代：新建独立常驻Compose被拒绝，会重复资源与安全规则。
- 固定HTTP业务payload只生成一次；旧submitDiagnosisAlert每次生成occurredAt，不能用它验证重复载荷。
- HTTP状态依据processingStatus而非是否新建：RECEIVED重复仍返回202；以duplicate字段和持久化受理事实区分1次新建与39次重复，不写成39次200。
- 告警中断在Worker关闭且RECEIVED已提交时发生，重新开启Worker；证明重发现，不宣称事务中任意位置崩溃。
- 数据库暂停通过本轮容器ID进行，有限请求/SQL等待，finally恢复；不用Testcontainers stop/start重建数据库。
- 测量用System.nanoTime，接收与观察到PROCESSED分别统计，轮询间隔明确；同键历史会影响重新分段成本。
- 现有六F场景已包含于当前All；ALERT为真实PG直接调用，OUTBOX为固定Producer，其他跨JVM并非executable JAR。
- H必需类可增加，总数动态累计；新检查还要内部断言案例ID/样本数量防止空跑。
- 未解析研究问题：无；真实模型/Broker、Linux与生产仍范围外。
