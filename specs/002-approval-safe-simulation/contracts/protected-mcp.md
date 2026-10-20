# 受保护 MCP 模拟处置契约 v1

**状态**：设计草案；仅适用于 Spec B 的一个隔离模拟写工具。\
**权威来源**：Action/审批/票据/回执由主应用 Incident 持久化；`mcp-server` 只处理演示资源。

## 1. 入口和身份

- 专用 Streamable HTTP MCP 入口为 `/mcp/incident`，只注册 `simulation.set-state` v1。现有 `/mcp` 与其他工具发现路径不注册此工具；直接在这些路径调用同名工具必须失败，不能退回旧确认卡。
- 旧 `/mcp` 与其他普通注册/发现路径只保留经服务端核查的只读工具允许清单；现有未纳入 Incident 审批链的写工具从可执行注册中排除，已知名称直调须失败。新增或未分类工具默认不注册；`readOnlyHint` 只是辅助声明，不能单独证明只读或授予执行权。启动与契约检查应核对实际注册表及全部调用入口。
- 主应用调用专用入口时以 `Authorization: Bearer <outbound-service-token>` 证明服务身份；服务端先认证该身份，再接受 `_meta` 关联信息。MCP Server 回调主应用票据消费、回执接口时使用另一套 `Authorization: Bearer <callback-service-token>`。
- 两套凭据均从运行环境或秘密设施注入，支持并行新旧值短期轮换，不能写入仓库、响应、Trace、日志或验收证据。跨主机通信使用 TLS；本机回环是唯一开发例外。认证失败在工具代码与票据消费前拒绝，不依赖 `_meta` 中的用户或 `actionId`。
- 主应用只有 `RemediationDispatcher` 使用专用入口；Agent 的恢复和普通 RAG 检索不连接该入口。模拟工具的注册清单与所有调用路径须通过测试证明不存在另一个未受守卫的入口。

## 2. 工具与参数

| 项 | v1 定义 |
| --- | --- |
| 工具名/版本 | `simulation.set-state` / `1` |
| 目标类型 | `SIM_SERVICE` |
| 工具参数 | `targetId`：已登记的隔离模拟服务 ID；`desiredState`：`READY` 或 `PAUSED` |
| 目标解析 | Incident 提议时由服务端将 `targetId` 解析到唯一模拟资源并冻结；执行端对同一登记集再次验证 |
| 强制超时 | 不大于注册的 `maxExecutionDuration`；P0 默认 2 分钟 |
| 对账 | 只读查目标状态、应用记录与执行实例是否仍活动；不得调用本工具写入口 |

请求中的工具名、版本、目标和规范参数必须逐项与 Action、Execution、Approval、Ticket 绑定一致。未知字段、错误类型、未登记目标和未经允许的状态在票据消费前失败关闭。

## 3. `action-binding-v1`

绑定字段顺序固定为：协议版本、`actionId`、`incidentId`、工具名、工具版本、目标类型、目标 ID、规范参数对象、执行幂等键。每个字段以 UTF-8 字节长度前缀和字节值编码；空值、缺失和空字符串分别编码，不可混同。v1 参数对象只含键 `desiredState`，枚举值使用上述大写规范值；拒绝未知键与重复 JSON 键。对完整编码求 SHA-256 小写十六进制作为 `bindingDigest`。审批保存同一摘要，消费时由 Incident 对收到的原始参数重新校验、规范化和计算，不信任调用方自报摘要。

创建 Action 前另计算 `proposalDigest`：按同一无歧义编码规则绑定 `incidentId`、`sourceType`、`sourceRequestId`、工具名/版本、服务端解析的目标及规范参数，不含尚未生成的 `actionId` 与执行幂等键。相同来源请求重试只比较该摘要；相同则返回原 Action，不同则报冲突。

`_meta` 仅传 `actionId`、`incidentId`、`executionId`、`requestId`、`executionKey`、`ticket` 和 `bindingVersion`；`requestId` 由 Incident 首次创建逻辑 Execution 时固定，补票与消费前安全重试保持不变。其中 `ticket` 是不透明一次性秘密，不能出现在响应或日志中。票据摘要只在主库，MCP Server 不缓存可复用授权结论。

## 4. 消费结果与工具启动

1. MCP Server 验证入站服务身份和工具参数契约。
2. 使用独立回调服务身份提交票据秘密、原始工具参数及上述稳定标识到 Incident 消费接口。
3. Incident 在持久化边界核对未 `CLOSED` Incident、Action/Approval/Execution、稳定 `requestId`、授权截止、票据状态和完整绑定；首次成功同时写 `CONSUMED`、`EXECUTING`、`executionStartedAt`、`receiptDueAt`。
4. 只有消费结果为 `GRANTED` 才进入模拟工具。`DUPLICATE`、`EXPIRED`、`MISMATCH`、`UNAUTHORIZED`、`UNAVAILABLE` 均不得调用写工具。重复请求返回已知当前结果或结构化 `DUPLICATE`，不签发第二次执行许可。
5. 消费接口响应不回显票据秘密。旧票据被替换后到达只拒绝旧票据，不终结仍有有效替代票据的 Action。
   `GRANTED` 响应另返回非秘密的 `maxExecutionDurationMs`，值取冻结的 Execution；MCP 执行端使用本地声明上限与该值中较小者，缺失或非正值失败关闭。

消费前传输故障只有主库仍证明同一票据为 `ISSUED`、原进程仍持有明文并在有效期内，才可有限重试。状态不可查、已消费或回执不明时停止自动派发。

## 5. 结构化回执

`mcp-server` 生成一次结果后固定一个 `receiptId` 和下列版本化对象，两条提交通道逐字段相同：

```json
{
  "schemaVersion": 1,
  "receiptId": "198765432100000101",
  "actionId": "198765432100000001",
  "incidentId": "198765432100000000",
  "executionId": "198765432100000050",
  "executionKey": "opaque-execution-key",
  "requestId": "opaque-request-id",
  "toolName": "simulation.set-state",
  "toolVersion": "1",
  "target": {"type": "SIM_SERVICE", "id": "demo-service-1"},
  "resultStatus": "SUCCESS",
  "resultCode": "SIMULATION_APPLIED",
  "occurredAt": "2026-09-23T12:00:00Z",
  "summary": {"desiredState": "PAUSED", "applied": true}
}
```

- `resultStatus` 只允许 `SUCCESS/FAILED/REJECTED/UNKNOWN` 作为权威回执。`DUPLICATE` 是重复请求的结构化响应，不是第二条执行回执。`isError` 和自然语言文本仅供展示，不作安全裁决。
- `FAILED` 必须有明确的未开始/已回滚或工具失败证据；超时只有 `TIMEOUT_PROVEN_ROLLED_BACK` 且可核查证明时使用。无证明时立即 `UNKNOWN/TIMEOUT_UNPROVEN`。`REJECTED` 必须证明副作用前的已认证策略/工具拒绝。
- MCP Server 先以回调身份提交回执，再返回含同一对象的 MCP 结构化结果；主应用收到响应后以内部路径重复提交。回调暂时失败只有限重试同一回执，不能重调工具。双路径都失败则由 `receiptDueAt` 清扫为 `UNKNOWN`。
- Incident 验证服务身份、Action/Execution/工具/目标/执行键/请求绑定与版本；同 ID 同内容幂等，同 ID 异内容或相反确定终态留冲突证据并告警，不覆盖原结论。已 `UNKNOWN` 的 Action 可接受完整绑定的迟到确定回执。

## 6. 只读对账

受保护的只读对账入口仅接受主应用服务身份与 `executionId/executionKey/targetId`，返回 `APPLIED`、`PROVEN_NOT_APPLIED` 或 `INDETERMINATE` 及安全证据引用。`APPLIED` 必须有与本次执行键绑定、含前后状态的已提交应用记录；目标后来被另一 Action 改变不否定本次已应用。`PROVEN_NOT_APPLIED` 必须同时证明写调用已停止、事务已中止/回滚且应用记录不存在。仅“暂时查不到记录”、执行仍活动、演示库重建或对账端不可用均为 `INDETERMINATE`。对账无写权限，不得改变模拟目标。
