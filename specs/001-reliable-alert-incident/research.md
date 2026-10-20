# Phase 0 研究结论：可靠告警到 Incident

**日期**：2026-09-22\
**规格**：[spec.md](./spec.md)\
**状态**：完成；同键接收时间与 anchor 排序遵循已批准 DEC-016

本文只记录为实现计划消除技术不确定性所需的选择。已经批准的 DEC-008 至 DEC-016 是约束，不在此重新讨论。

## R-001：领域模块与依赖方向

- **决定**：新增 `incident` Maven 模块，依赖 `framework` 和 `system`；`bootstrap` 增加对 `incident` 的依赖并负责装配。
- **理由**：与 DEC-008 的领域边界一致，避免把告警可靠性和 Incident 状态机放入 `agent`、`rag` 或 `system`。
- **未采用**：直接放入 `bootstrap` 会让启动模块承担领域职责；放入 `agent` 会提前耦合本 Spec 明确排除的诊断能力。

## R-002：机器认证与现有登录认证共存

- **决定**：机器提交由专用 HMAC 过滤器保护；Alert 状态查询共用一路由，来源 HMAC 与现有人员 `Authorization` 登录令牌二选一，混用、缺失或来源头不完整一律失败关闭。若该共用路由从全局 Sa-Token 拦截中排除，人员分支必须在路由本地强制 Sa-Token 登录及 `admin` 角色；人工重放和 Incident 状态转换仍走 Sa-Token。
- **理由**：来源机器没有交互式登录会话；现有 `SaTokenConfig` 当前拦截所有非 `/auth/**` 路径，而权限列表尚未形成细粒度模型。该方案满足 Spec 的参与者边界，又不引入通用 IAM。
- **未采用**：给机器创建登录用户会混淆服务身份和人员身份；为本 Spec 新建权限平台超出范围。

## R-003：真实 HTTP 状态与统一响应体

- **决定**：复用 `Result<T>` 的 `code/message/data/requestId` 外形，但 Incident 控制器通过 `ResponseEntity<Result<T>>` 和 Incident 范围的异常映射返回真实 HTTP 状态。
- **理由**：现有 `GlobalExceptionHandler` 返回包装体但未统一设置 HTTP 状态，无法满足规格的 `200/202/4xx/503` 语义。局部映射可避免改变其他模块既有行为。
- **未采用**：全局重构所有异常响应会扩大风险与范围；只在响应体写状态码不满足协议要求。

## R-004：来源密钥与轮换配置

- **决定**：来源、活动 `keyId` 和密钥值由运行时配置/环境秘密构建只读注册表；不新增数据库来源表。重叠期通过同一来源同时配置多个活动 key 实现。
- **理由**：Spec 只要求已注册来源和受控轮换，不要求管理 UI 或动态凭据生命周期；运行时秘密可以避免密钥进入业务数据库和版本控制。
- **未采用**：数据库凭据表需要加密、管理和审计能力，属于通用 IAM/秘密管理扩展。

## R-005：HMAC 输入编码

- **决定**：使用版本化、长度前缀的二进制编码，按固定顺序覆盖协议版本、来源 ID、时间戳、大写方法、契约规范路径和原始请求体；HMAC-SHA256 结果使用 `sha256=<64 位小写十六进制>`，通过常量时间比较验证。
- **理由**：长度前缀消除字符串分隔歧义；方法和带 `alertId` 的路径落实 DEC-014；原始体字节落实 DEC-013。
- **未采用**：以换行或字符拼接会留下转义/分隔歧义；对 JSON 重序列化后验签会错误接受或拒绝字节不同的请求。

## R-006：输入读取与 JSON 规范化

- **决定**：专用有界请求包装器按实际读取字节强制 64 KiB 上限；Jackson 严格解析器拒绝重复键、非对象顶层、深度超限、未知字段和显式 null。业务字符串先检查原始 Unicode 标量数量，再执行 NFC、Unicode 首尾空白裁剪及字段字符集验证。规范 JSON 使用固定字段顺序、排序后的 label 键和缺失标记计算 SHA-256 摘要。
- **理由**：既能按规定优先级在 JSON 解析前完成字节/HMAC 检查，又能产生与无意义空白和对象键顺序无关的幂等摘要。
- **未采用**：依赖 `Content-Length` 无法防止错误或缺失头；持久化任意 JSON 后再校验违反信任边界。

## R-007：可信时间与标识

- **决定**：`receivedAt` 与首次生命周期 `sealedAt` 遵循 DEC-016：同键持久化事务锁内，以 PostgreSQL 墙钟和已提交最大业务排序时间分配单调时间。重试到期、审计/Timeline/Outbox 发生时间以数据库时间为准；业务 ID 延续仓库 Snowflake 字符串风格，但不承担同键顺序保证。纯规则单元测试可以注入固定 Clock。
- **理由**：多实例的聚合窗口、生命周期边界和排序需要同一持久化可信时间线；普通墙钟同值或回拨与 Snowflake ID 不能单独保证该性质。
- **未采用**：各 JVM 的本地时钟会造成跨进程边界漂移；来源 `occurredAt` 不可信且规格明确不得参与聚合时钟。

## R-008：指纹计算时机

- **决定**：在接收校验后确定规范指纹字段、`fingerprint` 和 `aggregationKey`，并在 Alert 接收事务中持久化；事务内先按键取得 DEC-016 的仲裁锁，再分配 `receivedAt`。Incident 的创建和关联仍由异步 Worker 完成。
- **理由**：延迟处理的 Alert 已经存在于数据库。让所有已接收同键 Alert 立即可查询，Worker 才能按 `(receivedAt, alertId)` 而非领取顺序确定 episode。
- **未采用**：Worker 领取后才计算键会让尚未处理的 Alert 对 episode 不可见，破坏调度无关性。

## R-009：Worker 领取、重试与恢复

- **决定**：Alert Worker 使用 PostgreSQL `FOR UPDATE SKIP LOCKED` 有界批次领取到期 `RECEIVED` 行；聚合与关联在短事务中完成。失败通过独立受保护事务更新当前 `attemptCount/nextAttemptAt/lastFailure*`，首次处理失败后默认最多调度五次自动重试；全部尝试耗尽后再追加终态失败审计事实。
- **理由**：数据库是已批准的可靠工作源，`SKIP LOCKED` 支持多实例领取；当前控制字段满足恢复，不需要 Attempt 表。
- **未采用**：内存队列会在崩溃时丢工作；Redis 锁不应成为数据库事实正确性的前置条件；逐次 Attempt 表与 DEC-010/015 冲突。

## R-010：确定性 episode 与聚合开放唯一性

- **决定**：按所有已持久化同键 Alert（包括 `RECEIVED` 和 `FAILED`）的 `(receivedAt, alertId)` 排序，用相邻 gap 和生命周期 `sealedAt` 划分 episode。至少一条成员处理成功后才实体化 Incident；对外聚合资格由该 episode 全部持久化成员的末次 `receivedAt`、窗口和 `lifecycleSealedAt` 推导，不能用仅汇总已关联成员的 Incident 时间代替。Incident 保存 `aggregation_slot_active` 作为并发仲裁位，并建立每个 `aggregationKey` 最多一行 true 的条件唯一索引。
- **理由**：DEC-016 使同键正常接收的 `receivedAt` 严格递增，未来新接收 Alert 不会事后改变已有 episode 的 anchor 或桥接已分开的时间间隔；解决操作使用同一键顺序，封存边界也不会倒退。仲裁位解决并发创建，而派生资格避免为纯时间流逝写 Timeline。
- **实现约束**：接收、聚合和首次解决必须使用同一版本化聚合键取得事务级锁，且在读取 episode/封存状态和写入前持有至提交；索引、唯一约束与条件更新仍是最终业务不变量。不得以普通 PostgreSQL 墙钟天然单调或 Snowflake ID 随提交递增作为假设。
- **未采用**：只查询“当前 Incident”无法正确处理积压历史 episode；定时为每个窗口写封存事实会制造没有自然业务动作的 Timeline 事件，并与已确认规格不符。

## R-011：五张表与关联建模

- **决定**：使用 DEC-015 的五张表；Alert 行保存可空 `incidentId`、稳定 `attachmentEventId` 和 `attachedAt`，不建独立关联表。Alert 审计事实单独只追加，不与 Timeline 或 Outbox 混用。
- **理由**：当前关系严格为每条 Alert 最多一个 Incident，直接外键即可证明唯一归属；独立审计表能在 Incident 尚不存在时保存接收和终态失败事实。
- **未采用**：第六张关联表没有当前多对多需求；行内审计 JSON 需要覆盖更新；Outbox 可清理，不是长期审计来源。

## R-012：Timeline 顺序与稳定事实身份

- **决定**：Incident 行维护 `timelineSeq`，在同一 Incident 受保护事务中递增并写入 Timeline。每条事实还有全局唯一 `businessEventKey`；重试先按业务键收敛到既有事实。Alert 关联事实的稳定身份由持久化 `attachmentEventId`/业务键确定，`ALERT_ATTACHED` 与 `LATE_ALERT_ATTACHED_AFTER_SEAL` 只是由提交边界确定的类型标签。
- **理由**：同一 Incident 的单调序号便于展示和对账；业务键解决 Worker 在提交后崩溃再处理的重复问题，同时符合 Spec 对跨封存竞争的例外说明。
- **未采用**：只按时间排序无法解决相同时间和并发提交；随机生成但不持久化的事件 ID 无法恢复。

## R-013：Outbox 发布语义

- **决定**：Outbox Relay 在短事务内通过 `SKIP LOCKED` 设置 `claimToken/claimUntil`，事务外使用现有 `MessageQueueProducer.send` 同步发布，并显式要求 `SendResult.sendStatus == SEND_OK`；随后以 claim token 条件标记 `PUBLISHED`。失败保持 `PENDING` 并退避。
- **理由**：不在数据库事务中持有网络调用；稳定 `eventId` 使“已发出但未标记”可安全重投并由消费者去重。
- **未采用**：直接在领域事务发 MQ 会产生双写；RocketMQ 事务消息不能替代本地持久事实；长事务内发送会放大锁持有时间。

## R-014：PostgreSQL 集成验证

- **决定**：测试范围新增 Testcontainers PostgreSQL/JUnit 测试依赖；真实验证唯一约束、条件索引、外键、`TIMESTAMPTZ`、`SKIP LOCKED`、并发与回滚。消息层默认使用模拟发布器；只有专门 Broker 验证才启动本地 RocketMQ。
- **理由**：这些关键行为是 PostgreSQL 专属，H2 或 Mock 无法形成宪章要求的证据；仓库测试指南也明确要求在首项 Incident 持久化能力中建立容器冒烟测试。
- **未采用**：共享开发数据库不隔离且不可复现；所有测试都启动 RocketMQ 会增加不必要的环境成本。

## R-015：验收证据

- **决定**：测试运行在 `incident/target/incident-evidence/<run-id>/` 生成未提交的 `manifest.json`、场景结果、数据库计数快照和安全日志扫描摘要；manifest 记录提交 SHA（如可用）、运行时间、JDK/PostgreSQL 版本、测试配置、随机种子和命令。
- **理由**：满足可复现与计数对账要求，同时不把一次性运行输出混入源码。
- **未采用**：只保存控制台截图无法自动核对；提交生成结果会快速过期并污染仓库。
