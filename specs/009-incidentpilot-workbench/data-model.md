# 数据模型：只读值班首页

不新增表，不改变状态。下面是读取投影，字段名以接口契约为准。

## WorkbenchSnapshot

| 字段 | 来源/约束 |
| --- | --- |
| observedAt | 第一数据库查询捕获时间；UTC ISO，不是历史回放 |
| windowStart / windowEnd | observedAt-24h / observedAt，左闭右开 |
| counts.activeIncidents | 三个活动生命周期的Incident行数 |
| counts.pendingApprovalActions | Action.state=PENDING_APPROVAL |
| counts.executingActions | Action.state=EXECUTING |
| counts.unknownActions | Action.state=UNKNOWN |
| alertBuckets | 24个连续60分钟桶；received_at统计，缺桶补零 |
| attentionIncidents | 最多8，活动事件或有关联pending/unknown动作 |
| pendingActions | 最多8，PENDING_APPROVAL简单摘要 |
| recentActivity | 最多8，Timeline按created_at DESC,id DESC |

计数为非负安全整数；所有ID保持原始十进制字符串。投影来自一个REPEATABLE_READ只读事务，不取得业务写锁。

## AttentionIncident

`incidentId,lifecycleState,version,alertCount,lastAlertReceivedAt,updatedAt,pendingApprovalActionCount,executingActionCount,unknownActionCount`。

先按Incident聚合动作计数再关联，防止连接放大；unknown存在优先、pending存在其次，updatedAt DESC、id DESC，固定limit8。名称显示真实完整事件ID，不生成标题、服务名或严重级别。

## PendingActionSummary

`actionId,incidentId,state,createdAt,approvalDueAt`；state固定PENDING_APPROVAL，approvalDueAt ASC,id ASC限8。摘要不能证明批准资格；原详情负责截止时间及审批校验。不返回target/parameters、票据或证明。

## ActivitySummary

`eventId,incidentId,sequenceNo,eventType,createdAt`；仅导航到实际所属Incident。事件类型采用固定中文映射，未知类型不推断成功。

省略summary_json、actor_id、correlation_id、business_event_key、任意related字段、载荷与凭据。sequenceNo只在同一Incident内有序，全局按createdAt/id排序。

## 状态与关系

- Incident生命周期与Action状态独立，一Incident可有多个Action。
- 三项Action计数包括非活动Incident关联动作，不隐式按Incident状态过滤。
- 告警趋势包含持久化RECEIVED/PROCESSED/FAILED行，重复身份只有一行，不等同成功处理量。
- 首页不增加状态转换，详情继续使用原接口/冻结事实，不能凭摘要重建参数或执行资格。
