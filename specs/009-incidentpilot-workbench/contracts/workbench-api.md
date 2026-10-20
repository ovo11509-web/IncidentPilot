# 接口契约：管理员只读值班首页

**状态**：设计待实施，当前端点不存在。

## 请求与权限

`GET /incident/v1/workbench`，无自定义窗口、排序、输出或数据源参数。

复用当前登录/admin校验，服务端先鉴权再读DAO；不加入Webhook/MCP内部鉴权豁免。成功HTTP200，Spec A Result信封：`code="0"`、`message="OK"`、data、requestId。

一个只读REPEATABLE_READ事务。第一SELECT读数据库clock_timestamp为observedAt并建立MVCC快照，后续共享快照/时刻。observedAt为采样时间，不是历史回放。

## 响应白名单

```text
data:
  observedAt: UTC ISO string
  windowStart: observedAt-24h
  windowEnd: observedAt
  counts:
    activeIncidents: nonnegative safe integer
    pendingApprovalActions: nonnegative safe integer
    executingActions: nonnegative safe integer
    unknownActions: nonnegative safe integer
  alertBuckets[24]: {startAt,endAt,count}
  attentionIncidents[0..8]:
    {incidentId,lifecycleState,version,alertCount,lastAlertReceivedAt,updatedAt,
     pendingApprovalActionCount,executingActionCount,unknownActionCount}
  pendingActions[0..8]: {actionId,incidentId,state,createdAt,approvalDueAt}
  recentActivity[0..8]:
    {eventId,incidentId,sequenceNo,eventType,createdAt}
```

ID是字符串；version/sequenceNo/计数可安全表示，sequenceNo>=1。时间统一UTC ISO8601。DAO不SELECT原始JSON、related或秘密字段。

## 口径与排序

- activeIncidents：OPEN/ACKNOWLEDGED/MITIGATING。
- 三个Action计数严格PENDING_APPROVAL/EXECUTING/UNKNOWN，不按Incident活动状态过滤；过期未清扫的PENDING_APPROVAL仍计，首页不能据此批准。
- 趋势：[T-24h,T)，k桶为[T-24h+k*h,T-24h+(k+1)*h)，k=0..23；received_at计数，全部处理状态计入、补零，不使用来源occurred_at。
- attentionIncidents：活动事件或有pending/unknown动作；先聚合Action；unknown优先、pending其次，updated_at DESC,id DESC，限8。
- pendingActions：PENDING_APPROVAL，approval_due_at ASC,id ASC，限8。
- recentActivity：created_at DESC,id DESC，限8，不按全局sequence排序。
- 零行才是真实0/空，传输/数据库错误不得降级为零。

## 错误与取消

未登录401、非admin403、数据库不可用503，复用Incident安全错误及requestId；不返回SQL、配置、凭据或载荷。401沿用客户端认证失效流程，其他错误显示失败/刷新。取消旧请求不得覆盖新状态或显示全零。

## 禁止副作用及扩展

不得调用诊断生成、Action创建/审批、MCP、Timeline写入、行/聚合写锁或Outbox。不得返回target/parameters、票据、签名、证明、actor身份或任意summary。无新表/Schema升级。
