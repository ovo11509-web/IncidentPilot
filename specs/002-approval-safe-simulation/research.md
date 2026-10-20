# Phase 0 研究：审批到安全模拟处置

**日期**：2026-09-23\
**依据**：[spec.md](./spec.md)、DEC-008/009/011/012/017/018/019/020、Spec A v1.0；本文件记录设计选择，不表示已经实现或实测。

## R1：MCP 受保护工具的所有入口

- **决定**：为 P0 的唯一模拟写工具设置专用受保护 MCP 入口；该工具只在该入口注册。旧 `/mcp` 和其他普通注册/发现路径只开放逐项核查后的只读允许清单；旧写工具和未知新注册默认停用，已知名称直调也不得执行。受保护入口在 HTTP 层验证主应用服务身份，工具统一包装在票据在线消费守卫之内。
- **理由**：当前 `McpServerConfig` 把所有 `SyncToolSpecification` 放入一个 `/mcp`，只检查 `readOnlyHint`；现有工具有写入演示库的操作，`rag` 的检索流程还可不经 Agent 确认直接调用 MCP。只改确认卡或 Agent Bridge 无法满足宪章对所有有副作用 MCP 操作的服务端强制要求。
- **未采用**：只在 Agent 侧检查；把 `_meta` 自报用户 ID 或 `readOnlyHint` 当作授权；对现有全部写工具一次性迁移。
- **证据**：`mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/config/McpServerConfig.java`；`rag/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieval/RetrievalEngine.java`；`agent/src/main/java/com/nageoffer/ai/ragent/agent/tool/McpToolBridge.java`。

## R2：服务身份与传输异常

- **决定**：主应用到专用 MCP 入口、MCP 服务到主应用内部接口使用两套独立可轮换高熵 Bearer 凭据；仅在服务认证后处理 `_meta` 的关联字段。出站适配器保留“消费前明确未开始／消费后不确定／已取得结构化回执”三类结果，不复用现有文本 `isError` 作为安全结论。
- **理由**：DEC-011 已规定双向独立服务身份；当前 `McpClientToolExecutor` 和 `McpToolBridge` 将异常或文本结果折为普通失败，不能证明副作用未发生。
- **未采用**：共享一个方向的凭据；消息 ID 或 `_meta` 自报身份；把网络超时一律映射 `FAILED`。
- **证据**：DEC-011；`rag/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/McpClientToolExecutor.java`；`mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/McpToolResults.java`。

## R3：Incident 关闭与 Action 的并发裁决

- **决定**：沿 Spec A 的同键锁与 Incident 行锁顺序，使关闭与尚未消费票据的 Action 失效在同一主库事务完成；票据消费锁定并核对同一 Incident 当前状态。先消费则 Action `EXECUTING`，关闭后仍处理回执；先关闭则不得开始副作用。
- **理由**：跨进程分别更新 Incident 与 Action 会留下已关闭仍能消费的窗口；Spec A 的 `RESOLVED -> CLOSED` 已有受保护状态与 Timeline 事务。
- **未采用**：只依靠 Agent 卡片失效；只在异步清扫中取消；关闭后强行把已 `EXECUTING` 解释为未执行。
- **证据**：Spec A `data-model.md` 的 T5、锁顺序与 Timeline 序号；Spec B FR-038；已批准的 DEC-019。

## R4：共享 Timeline、Outbox 与多条回执

- **决定**：沿用 `t_incident_timeline` 和 `t_incident_outbox` 的稳定事件身份、顺序、只追加和至少一次投递协议；兼容扩展类型与关联实体。另以只追加回执历史保存首次 `UNKNOWN`、迟到确定回执和冲突证据，重复同一回执不增行。审批决定及权威回执各自生成稳定待发布事件。
- **理由**：Spec A 当前数据模型仅允许五类 Timeline 事实和两类领域事件；若复用原类型会误写历史语义。单个可覆盖结果字段不能同时解释 `UNKNOWN`、迟到结果和终态冲突。
- **未采用**：另建第二条 Incident 时间线；把每条 Timeline 都发布；只以日志保存冲突；重写 Spec A 已发布事件。
- **证据**：Spec A `data-model.md` 与 `contracts/incident-events.md`；DEC-011/012；Spec B FR-039/040；已批准的 DEC-020。

## R5：模拟资源与未执行证明

- **决定**：P0 注册一个 `simulation.set-state` v1 工具，只允许隔离 `SIM_SERVICE` 目标及 `READY/PAUSED` 两个状态。演示库中目标变更与按执行幂等键唯一、记录变更前后状态的应用事实原子提交。只读对账发现该应用事实可证明本次已应用，即使目标后来被另一 Action 改变；未发现应用事实不能单独证明未应用，仍须确认调用已停止且本地事务已中止/回滚，否则维持 `UNKNOWN`。
- **理由**：`ragent_bit` 当前是可重建演示库，适合模拟资源，不可承载 Incident 审批主数据。仅凭超时或记录暂时不存在不能排除稍后提交。
- **未采用**：连接真实基础设施；把模拟状态或权威回执写入可重建库；因“看不到变化”直接判 `FAILED`。
- **证据**：`docs/ARCHITECTURE.md` 的主库与演示库边界；DEC-012 超时证明规则。

## R6：绑定、票据替换与截止时间

- **决定**：采用版本化、无歧义的 UTF-8 长度前缀字段编码计算 SHA-256 绑定摘要；P0 参数仅为受限枚举，服务端解析目标。Action 创建时固定执行幂等键；替换票据只变票据身份，不改 Execution 或绑定。所有状态时间由主库可信时钟取得，`receiptDueAt` 在首次合法消费时固定。
- **理由**：普通 JSON 文本摘要易受键顺序和等价编码影响；票据 TTL 与执行/回执截止属于不同时间边界。DEC-011/012 已确定不可变绑定、一次性票据和独立截止。
- **未采用**：直接哈希原始 JSON 文本；崩溃后重建票据明文；票据到期即认定执行失败。
- **证据**：DEC-011/012；Spec B FR-004/014/020/021/027。

## 研究结论

上述选择均能在现有模块和依赖内落实，无需新服务或付费能力。R3、R4 对应的长期决策已分别归档为 DEC-019、DEC-020；其余是已批准决策在本功能的具体化。所有精确线协议、数据约束与验收方法见本目录的 Phase 1 产物。
