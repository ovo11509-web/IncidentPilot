# 研究：B 风格值班工作台

**日期**：2026-10-10。基于当前代码/最终Schema；B选择已确认，实施范围待审阅。研究子任务只读核对DAO、权限与真实测试夹具，无代码变更。

## 产品主体与范围

**决策**：值班首页和公共导航围绕IncidentPilot，统一既有事件页面；原配置功能及地址保留。

**理由**：AdminLayout仍显示Ragent / Knowledge Console，后台index进入旧Dashboard。用户选择B视觉，并未要求重做所有上游页面。

**替代方案**：只改标题/颜色不足以形成值班体验；全站换肤与Runbook/演练GUI扩大未确认业务，延后。

## 真实数据来源

**决策**：一个管理员只读首页查询，读取现有主PostgreSQL，不前端遍历分页求总数。

**理由**：IncidentQueryService只给限额列表，当前客户端无全局Action/告警趋势。OperationsMapper.snapshot可参考查询结构，但它统计接收/Outbox运行情况，不是业务首页。

**替代方案**：概念数字、分页数量冒充总量、上游模型流量Dashboard均不能准确表达运维事件。

## 状态与单位

**决策**：活动事件为OPEN/ACKNOWLEDGED/MITIGATING；后三卡为全部持久化PENDING_APPROVAL、EXECUTING、UNKNOWN Action数。

**理由**：aggregation_slot_active不是生命周期；DISPATCHING不是EXECUTING；Action与Incident不是一一对应。过期清扫前仍可能PENDING_APPROVAL，数量不代表每项有批准资格，详情仍强制校验。

**替代方案**：合并APPROVED/DISPATCHING或隐式时间过滤会改变卡片口径，首阶段不采用。

## 时间与快照

**决策**：只读REPEATABLE_READ内第一数据库查询固定observedAt；以received_at统计[T-24h,T)的24个连续60分钟桶。

**理由**：AggregationClockMapper.nextReceivedAt通过数据库时钟及同键前值+1µs分配持久化接收时间，不是来源occurred_at或HTTP精确到达时刻。READ_COMMITTED多次SQL会混用Worker状态；自然小时可能跨25个片段或依赖会话时区。

**替代方案**：无事务SQL、写锁、客户端分桶均不采用。observedAt只是采样时刻，不是历史回放承诺。

## 有界投影与权限

**决策**：所有ID保持字符串；关注事件不新增推测标题/严重级别；最近活动按created_at排序并省略任意summary、actor和凭据。

**理由**：Incident主表没有标题。IncidentController.timeline整体反序列化summary_json，首页不需要这个宽边界。sequence_no只在同一Incident内有序。当前人员权限是admin全局视图，没有租户/个人归属模型。

**替代方案**：主表新增展示事实、读取Alert/Diagnosis全JSON、全局sequence排序、新权限系统均不采用。

## 主题与依赖

**决策**：公共外壳与Incident页面使用范围类和现有Tailwind/lucide/Recharts，原知识搜索保留准确用途。

**理由**：全局改.bg-white等通用类会破坏配置表格/弹窗/文档预览；新动画/主题框架没有必要。概念图未实现入口不能仅为外观保留。

**替代方案**：全body换肤、独立新前端、3D展示层和全局事件搜索均不采用。

## 验证与环境

**决策**：契约、真实SQL、实际两连接快照和浏览器核对；复用ActionDbFixture与真实UI夹具。

**理由**：ActionDbFixture加载A+B并采用Spring管理MyBatis会话，IncidentDbFixture只加载A。诊断Timeline类型须显式加载C或现有全量Schema。规划核对时Docker不可用，本机Node20/Java21不能证明Node22/Java17、Linux或托管CI。

**替代方案**：开发库代替隔离库、纯Mock代替SQL、概念图代替运行截图、历史门禁代替本轮检查均不采用。

## 当前依据

- `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentQueryService.java`：鉴权、限额和事件投影。
- `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/dao/OperationsMapper.java`、`AggregationClockMapper.java`：统计与时间分配。
- `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/web/IncidentController.java`及`timeline/dao/TimelineMapper.java`：单事件Timeline与created_at。
- `resources/database/schema_pg.sql`：最终Schema和类型集合，优先于旧升级片段。
- `incident/src/test/java/com/nageoffer/ai/ragent/incident/action/integration/ActionDbFixture.java`：真实数据库/事务复用。
- `frontend/src/pages/admin/AdminLayout.tsx`、`frontend/src/router.tsx`、`frontend/src/pages/LoginPage.tsx`：品牌与入口。

技术澄清已解决，等待首阶段功能范围及实施计划审阅。
