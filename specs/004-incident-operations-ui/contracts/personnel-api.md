# 人员API及页面契约（已实现，2026-10-04本地验证）

所有领域路径位于主应用/api/ragent context内；前端baseURL沿用VITE_API_BASE_URL（未配置时明确使用/api/ragent），领域路径/incident/v1。Vite当前/api代理目标9090。不同领域成功码需专用Incident客户端逐接口解包；认证header沿用Authorization。服务器对全部人员接口要求登录及admin。

## 新增只读列表

`GET /incident/v1/incidents?limit=20&state=OPEN&afterEpisodeStartAt=...&afterId=...`

- limit 1..100，默认20；state可省略，仅OPEN/ACKNOWLEDGED/MITIGATING/RESOLVED/CLOSED。
- 两个after字段同时省略或同时提供；时间合法ISO offset时间，id为既有1..20数字字符串。
- 仅查询t_incident，以episode_start_at DESC,id DESC排序，游标使用相同二元组严格小于；读取limit+1，返回items、hasMore、nextCursor（无下一页时null）。筛选切换重置游标，不承诺跨请求一致快照。
- items含incidentId/lifecycleState/version/alertCount/episodeStartAt/firstAlertReceivedAt/lastAlertReceivedAt。不执行每行snapshot、没有跨领域JOIN，不返回全库或无界数组。

## 新增关联告警

`GET /incident/v1/incidents/{incidentId}/alerts?limit=20&afterId=...`

只读取incident_id精确匹配且PROCESSED的记录，id ASC keyset，1..100；返回incidentId/items/hasMore/nextAfterId。数据字段见data-model的Alert白名单。未知Incident 404；不得用aggregationKey扩大关联范围。新告警造成版本变化时提示刷新详情/重新诊断；分页不承诺冻结历史，历史诊断快照另看既有接口。

## 复用接口（当前已存在）

| 操作 | 领域路径 | 前端约束 |
| --- | --- | --- |
| 详情 | GET /incident/v1/incidents/{incidentId} | 返回权威version/lifecycleState/count |
| 时间线 | GET /incident/v1/incidents/{incidentId}/timeline?afterSequence=0&limit=100 | 既有上限200，按sequenceNo追加/去重 |
| 诊断历史 | GET /incident/v1/incidents/{incidentId}/diagnoses?afterId=...&limit=20 | 既有上限100 |
| 发起诊断 | POST /incident/v1/incidents/{incidentId}/diagnoses | sourceRequestId、expectedIncidentVersion；新显式意图新ID，网络未知重用原ID |
| 诊断详情 | GET /incident/v1/diagnoses/{diagnosisId} | 读取历史原文和服务器当前适用性，不直接生成授权 |
| 选择建议 | POST /incident/v1/diagnoses/{diagnosisId}/recommendations/{recommendationId}/actions | sourceRequestId、expectedIncidentVersion、diagnosisDigest、recommendationDigest；明确新提案可带originalActionId |
| Action列表 | GET /incident/v1/incidents/{incidentId}/actions | 当前接口无分页，本轮不改契约；仅按选中Incident读取，不全局轮询 |
| Action详情 | GET /incident/v1/actions/{actionId} | 增补安全参数与回执投影 |
| 批准/拒绝 | POST /incident/v1/actions/{actionId}/decision | decision、expectedVersion、reason按服务端规则；之后GET完整Action |

未接入：transitions、reconcile、manual-conclusion、用户自由参数propose、内部票据及MCP。保留服务端现有能力，不新增浏览器授权入口。

## Action查询投影补充

合法冻结parametersJson经ActionBindingV1.canonicalParameters校验后，增加parametersAvailable=true与parameters={desiredState}。未知/非法格式增加parametersAvailable=false且不返回任意JSON，界面不能批准；目标/工具/bindingDigest仍取持久化Action。不更改冻结参数或绑定摘要。

receipt增加occurredAt、summaryAvailable及合法白名单summary。desiredState、applied与可选proofCode按照既有回执校验规则；非法原JSON不传输。状态/码/disposition/接受时间仍可查看，异常摘要不能伪造成功。

## 请求与错误契约

- ID始终string；版本为非负安全整数才可写。时间按ISO offset输入，显示明确时区。
- 2026-10-04用户确认保留现有A/B/C契约：IncidentController的列表/详情/关联告警/Timeline仅接受code="0"；Diagnosis和Action接口仅接受code="SUCCESS"。专用客户端逐接口指定成功码，保留data与requestId，其他码一律错误；不改全局客户端。
- 401清理会话；403权限不足；404不存在；409提示状态/绑定冲突并读取当前视图。错误保留status/code/requestId，不输出请求秘密。
- 写响应可能不含完整查询投影，不能用其覆盖GET的完整Action。
- 轮询限读请求，5秒串行、后台暂停、路由切换取消、错误/终态停止。POST不自动重试，不因页面刷新提交；网络未知只允许人员确认原请求重发或先读取历史。
- 不可信原文以文本展示；禁用外部资源自动加载和原HTML执行。

回执proofCode仅显示NOT_STARTED、ROLLED_BACK或PRE_EFFECT_REJECTION等现有已知安全枚举；其他任意字符串不投影，不能以64字符长度校验代替秘密保护。相关非法/秘密值由T022验证。
