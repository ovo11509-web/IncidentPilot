# 数据模型：快照、诊断与 Action 来源

**状态**：2026-10-03 已批准并实现增量 SQL，约束已在隔离 PostgreSQL 验证。Runbook 由 rag classpath 语料拥有，Incident 只保存标识与必要快照。

## t_incident_diagnosis_request（可变运行控制 + 冻结输入）

| 字段 | 类型/规则 |
| --- | --- |
| id / incident_id | VARCHAR(20) 主键；内部 Incident FK，禁止业务删除 |
| actor_id / source_request_id | 服务端身份 VARCHAR(128)；请求 ID 为 1～128 安全标识字符 |
| request_digest | CHAR(64)，规范 incidentId/actor/sourceRequestId/expectedVersion/version（固定为1）；不含服务端时间和新生成 ID |
| snapshot_json / snapshot_digest / facts_digest | 版本化 JSONB 与 SHA-256；不可更新的白名单输入，详见 evidence 契约 |
| incident_version / timeline_watermark | BIGINT 非负；捕获前的权威值 |
| corpus_version / corpus_digest | 本次可用语料身份和摘要；通过应用 DTO 传入，冻结后不替换 |
| state / version | RUNNING或COMPLETED；条件更新 version≥0 |
| owner_token_digest | 本次完成权限令牌摘要，明文仅进程内部使用，不出人员响应/日志 |
| created_at / deadline_at / completed_at | TIMESTAMPTZ，deadline>created；RUNNING 无 completed，COMPLETED 有 completed |

唯一约束 `(incident_id, actor_id, source_request_id)`。同请求同 request_digest 返回原请求/结果，不重新捕获快照；不同 digest 冲突。过期后新 sourceRequestId 才能新诊断。冻结字段业务无 UPDATE 接口；应用角色仅为状态/版本/完成时间列授予 UPDATE，其余 SELECT/INSERT。

## t_incident_diagnosis_result（只追加终态）

| 字段 | 类型/规则 |
| --- | --- |
| diagnosis_id | VARCHAR(20) PK，与 request.id 内部 FK；每请求最多一个结果 |
| status / reason_code | SUPPORTED、INSUFFICIENT_EVIDENCE、INVALID_EVIDENCE或FAILED；机器可读安全原因 |
| result_json / result_digest | JSONB 对象、canonical SHA-256；包含 query/scope/ranking/evidence/claims/recommendations/missingFacts/versions |
| stale_at_publish | BOOLEAN；发布时快照是否过时，历史字段不更新 |
| recorded_at | TIMESTAMPTZ 数据库时间 |

SUPPORTED 只表示存在满足受审阅规则的候选解释，不表示已证实根因或自动可执行。其他 status 的 convertible recommendations 数量必须为 0。JSON 格式由服务校验；数据库 CHECK 约束状态、对象形状、摘要格式及基本空建议条件。

运行请求只能 COMPLETED 并存在唯一 result；事务写 result、条件更新请求及 Timeline，应用崩溃不能部分提交。结果无 UPDATE/DELETE 接口，`ragent_app` 和 PUBLIC 无 UPDATE/DELETE 权限。

## t_incident_diagnosis_action_link（只追加关联）

| 字段 | 类型/规则 |
| --- | --- |
| action_id | VARCHAR(20) PK，内部 Action FK，每 Action 至多一个诊断来源 |
| incident_id / diagnosis_id | 内部 FK；服务检查诊断和 Action 都属于该 Incident |
| recommendation_id / recommendation_digest | 稳定建议 ID 与包含规范模拟绑定的摘要 |
| diagnosis_digest / facts_digest | 固定转换依据，不能用当前版本覆盖 |
| actor_id / source_request_id / selection_digest | 服务端人员身份、稳定请求 ID、绑定选择的规范摘要 |
| created_at | TIMESTAMPTZ |

唯一 `(incident_id, actor_id, source_request_id)`；允许同建议用不同请求产生两个需各自审批的 Action。link 禁 UPDATE/DELETE；不同诊断/建议不得占用同一 Action。跨 incident、复用无关 Action、digest 不符均拒绝。

## 其他实体与保留

- RunbookVersion/Manifest 以版本化文件存在；不得建立 Incident→knowledge 外键。
- snapshot/evidence/claims/recommendations 存结果内 JSON，不建立独立通用证据表。
- Runbook 更新会新建版本/manifest；旧结果中原文证据独立保留，初版不提供结果删除或自动清理。
- EvaluationCase 存测试 fixture；EvaluationRun 是 target 下生成文件，不入 Incident 业务表。

## 事务与状态

1. begin：同键锁→Incident 行锁，核对未 CLOSED 和 expectedVersion，读 `incident_id` 精确归属的 Alert（不是同键全部成员），排序后捕获投影；插请求后提交，不调用检索。
2. complete：同键→Incident→request 锁；验证令牌/截止/摘要，计算当前 stale，插 result、请求 COMPLETED、reserveTimeline+DIAGNOSIS_RECORDED 原子提交。
3. interrupted：新诊断 POST 先检查该 Incident 的超时请求，使用同锁顺序和条件更新插 FAILED 结果+Timeline；与迟到 complete 仲裁，相同摘要幂等、不同摘要冲突。GET 只读；没有新 POST 时显示超时未收敛，不承诺主动清扫。
4. select：同键→Incident→request/result→Action；已存在 link 先幂等比较，新请求核对事实和语料再调用人员提议服务；Action/Approval/link/两类 Timeline 原子提交。

不持有锁执行跨域检索、生成或 MCP。语料启动时加载、不支持热更新；新转换通过 RunbookEligibility 本地端口检查当前不可变 manifest 的发布/撤回/有效期。适配器缺失拒绝新选择，不发生跨领域数据库事务；新版本部署重启后旧诊断重新核对当前语料。

## Timeline/Schema 兼容

追加 eventType=DIAGNOSIS_RECORDED、DIAGNOSIS_ACTION_LINKED；relatedEntityType=DIAGNOSIS。业务键分别 `diagnosis:{id}:recorded` 和 `diagnosis:action:{actionId}`。summary 仅 IDs、状态/原因、数量、摘要，不含原文、敏感事实或模型思维链。

迁移同时更新全量 schema 与新增升级脚本，保留 A/B 所有 CHECK 类型；不新增 Outbox 事件。Timeline 序号在 Incident 锁下分配，追加不递增主 version。验证升级链、角色只追加和失败回滚后才能验收。
