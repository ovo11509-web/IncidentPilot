# 实施计划：可靠告警到 Incident

**分支**：`feature/incident-platform` | **日期**：2026-09-22 | **规格**：[spec.md](./spec.md)

**输入**：`specs/001-reliable-alert-incident/spec.md` 中已确认的功能规格，以及 DEC-008、DEC-009、DEC-010、DEC-013、DEC-014、DEC-015、DEC-016。

**计划状态**：已确认并完成最终一致性复核；2026-09-23 对齐 Spec A v1.0 基线与 DEC-016。

## 摘要

新增独立 `incident` Maven 模块，完成一条数据库优先的“告警可靠接收 → 数据库 Worker 异步聚合 → Incident/Timeline 原子落库 → Transactional Outbox 发布”链路。Webhook 和机器状态查询使用绑定方法、路径和原始请求体的来源 HMAC；人员查询、重放和状态转换复用现有登录与 `admin` 角色能力。PostgreSQL 的唯一约束、条件唯一索引、行锁和只追加写路径负责幂等、聚合开放唯一性与审计正确性，不使用 Redis 锁、进程内锁或消息系统作为正确性边界。

本阶段只产出设计，不实施代码、Schema 或配置。设计坚持五张核心表，不引入通用工作流、通用审计平台、来源凭据表、逐次 Attempt 表或独立 Alert–Incident 关联表。

## 技术背景

**语言/版本**：Java 17

**主要依赖**：Spring Boot 4.1.0、Spring MVC、Spring Validation、MyBatis-Plus 3.5.17、自定义 SQL Mapper、Sa-Token 1.45.0、Jackson 2 兼容层、现有 RocketMQ Producer 适配器、JDK `Mac`/`MessageDigest`/Unicode API

**存储**：PostgreSQL；Incident 数据全部进入现有主库，时间字段使用 `TIMESTAMPTZ`

**测试**：JUnit 5、Spring Test/MockMvc、Mockito；Testcontainers PostgreSQL 验证真实约束、事务、锁和并发；默认使用模拟事件发布器，RocketMQ 不作为快速测试前置条件

**目标平台**：Linux/容器化服务端；本地开发支持 Windows PowerShell 与 Docker Desktop

**项目类型**：现有多模块 Maven 单体中的独立后端领域模块

**性能目标**：不声明未经实测的吞吐或时延；验收至少可复现 100 个并发相同幂等身份提交、100 个并发同聚合键 Alert，以及 Worker 崩溃/重启和 Outbox 重投场景

**约束**：真实 HTTP 状态；默认 64 KiB/深度 16 输入限制；DEC-016 的同键单调可信排序时间；每键至多一个聚合开放 Incident；Timeline 与 Alert 审计只追加；跨进程恢复；秘密和完整载荷不得进入日志、响应或安全摘要；所有测试命令禁用自动格式化改写

**规模/范围**：一条 v1 告警来源契约、一个默认 30 分钟聚合规则、一个 Incident 主状态机、五张 Incident 核心表、一个领域事件 Topic；不含 Action、审批、RAG、UI、真实资源处置、多租户和额外告警适配器

## 宪章检查

*质量门：Phase 0 研究开始前必须通过，并在 Phase 1 设计完成后重新检查。*

| 原则/约束 | Phase 0 前检查 | Phase 1 后检查 |
| --- | --- | --- |
| 一、署名与贡献可追溯 | 通过：新增独立模块，不改写上游历史或许可证 | 通过：设计要求新 Java 源文件沿用仓库 Apache-2.0 许可头，并在文档中保留 IncidentPilot 新增边界 |
| 二、由持久化边界保证事件正确性 | 通过：规格要求数据库幂等、原子关联和只追加事实 | 通过：数据模型以唯一约束、条件唯一索引、外键、版本检查和同事务写入落实 |
| 三、人工控制有副作用的操作 | 通过：本 Spec 不含任何资源处置 | 通过：契约只含告警、Incident 和只读/恢复管理能力，不引入 MCP 副作用 |
| 四、按 Spec 控制交付范围 | 通过：Spec A 已确认，范围外明确 | 通过：设计只覆盖 Spec A；Action/审批/RAG/UI 留给后续独立 Spec |
| 五、有证据后再做结论 | 通过：规格要求可复现清单和并发证据 | 通过：Quickstart 与数据模型规定 Testcontainers PostgreSQL、模拟发布器和证据清单；未宣称尚未取得的结果 |
| 技术基线与复用 | 通过：使用 Java 17、Spring Boot 4、Maven、PostgreSQL | 通过：复用 `framework`、`system`、`Result` 和现有 MQ 适配器；仅新增 Testcontainers 测试依赖和必要模块 |
| 安全与秘密 | 通过：规格禁止密钥进入业务数据、日志和响应 | 通过：来源密钥仅来自运行时配置，HMAC 采用定长编码与常量时间比较，不新增凭据表 |

**门禁结论**：Phase 0 前和 Phase 1 后的宪章检查均通过，无宪章例外；已确认的 Timeline 计数修订与设计一致。

## 一致性复核结论

- 已确认首次进入 `RESOLVED` 在同一事务中各追加一条 `INCIDENT_STATUS_CHANGED` 和 `INCIDENT_AGGREGATION_SEALED`，该次转换净增两条 Timeline 事实。
- `SC-006` 已与 `FR-031` 对齐：每次成功转换恰好增加一条状态变更事实，首次解决时额外增加且仅增加一条封存事实；重开和再次解决不重复产生封存事实。

该结论已反映在数据模型的 T5 状态转换事务设计中。`tasks.md` 仍按原定流程在计划获确认后生成。

## 项目结构

### 当前功能的文档

```text
specs/001-reliable-alert-incident/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── openapi.yaml
│   ├── hmac-signing.md
│   └── incident-events.md
└── checklists/
    └── requirements.md
```

`tasks.md` 由计划确认后的 `$speckit-tasks` 生成，本阶段不创建。

### 源代码（规划目标，仓库根目录）

```text
incident/
├── pom.xml
└── src/
    ├── main/java/com/nageoffer/ai/ragent/incident/
    │   ├── alert/          # 接口、安全、规范化、持久化与处理 Worker
    │   ├── core/           # Incident 聚合、状态机、查询与持久化
    │   ├── timeline/       # 只追加 Timeline 写入与查询
    │   ├── outbox/         # Outbox 写入、领取、发布与恢复
    │   └── config/         # 模块配置、来源密钥注册表与调度开关
    └── test/java/com/nageoffer/ai/ragent/incident/
        ├── unit/
        ├── contract/
        └── integration/

pom.xml                                           # 注册 incident 模块
bootstrap/pom.xml                                 # 依赖 incident 并装配运行时
bootstrap/src/main/resources/application.yaml     # 非秘密默认配置与环境变量占位
resources/database/schema_pg.sql                  # 全量初始化 Schema
resources/database/upgrades/                      # 追加式 PostgreSQL 升级脚本
```

**结构决策**：遵循 DEC-008 新增独立 `incident` 模块；该模块依赖 `framework` 与 `system`，`bootstrap` 依赖并装配 `incident`。Spec A 不修改 `agent`、`rag`、`mcp-server` 或前端业务行为。

## 设计方案

### 1. 接入与安全边界

- 在 Servlet 过滤器最前部按“媒体类型 → 实际字节数 → HMAC/时效 → JSON 结构 → 字段契约”的顺序处理机器提交；有体请求使用有界缓存包装器保留实际原始字节。
- 来源注册表由运行时配置构建，记录 `sourceId`、活动 `keyId` 与密钥引用；密钥值只从环境变量或部署秘密注入，不写入数据库、仓库配置、日志或错误响应。
- 机器提交路径从现有 Sa-Token 登录拦截中排除，并由专用 HMAC 过滤器强制认证。Alert 状态查询是机器与人员共用路径：携带来源 HMAC 头时只走 HMAC，携带现有 `Authorization` 登录令牌时只走 Sa-Token；混用两类凭据、缺少凭据或来源头不完整均失败关闭。即使共用查询路径需要从全局 Sa-Token 拦截器排除，路由本地仍必须强制人员登录和 `admin` 角色校验，不得形成匿名旁路。人员重放和状态转换继续使用现有 Sa-Token。
- HMAC 的无歧义二进制编码、规范路径和空体规则以 [hmac-signing.md](./contracts/hmac-signing.md) 为唯一线协议定义。
- Incident 控制器返回 `ResponseEntity<Result<T>>`，配合仅覆盖 Incident 包异常的 Advice 映射真实 `200/202/4xx/503`，避免改变现有全局接口行为。

### 2. 同步可靠接收

- 在开启持久化事务前完成全部信任边界检查、Unicode 规范化、规范业务对象和载荷摘要计算。
- 校验后先确定规范指纹与 `aggregationKey`。首次接收事务按该键获取 PostgreSQL 事务级 advisory lock；在锁内读取同键已提交 Alert 最大 `received_at` 与已形成的最大 `lifecycle_sealed_at`。若无前序事实，取 `clock_timestamp()`；否则把新 `receivedAt` 定为 `max(clock_timestamp(), previousMax + 1 microsecond)`，并在范围溢出时安全失败。生成 `alertId` 和稳定首次 `correlationId` 后原子插入 Alert 与 `ALERT_RECEIVED` 审计事实；同键锁持有至提交，不依赖 ID 顺序。
- `(source_id, external_alert_id)` 唯一约束是最终幂等仲裁。并发唯一冲突后读取既有行，比较规范载荷摘要并返回相同重试或 `409`；不得把暂时数据库故障误报为已接收。
- 指纹字段、`fingerprint` 和 `aggregation_key` 在接收事务内持久化，而 Incident 创建和关联仍完全异步。这样所有已接收但尚未处理的同键 Alert 都能参与确定性 episode 计算。

### 3. 数据库 Worker 与 episode 计算

- Alert Worker 使用自定义 PostgreSQL SQL 按 `(next_attempt_at, received_at, id)` 有界发现候选；先取得候选的同键事务锁，再用 `FOR UPDATE SKIP LOCKED` 领取并重新检查资格、episode。所有接收、聚合和解决路径统一遵循“同键锁 → Alert/Incident 行锁”的顺序，避免锁环；不使用 Redis 锁、内存队列或通用 Job 框架。
- 聚合事务读取同 `aggregationKey` 的全部已持久化 Alert（包括 `RECEIVED` 和 `FAILED`），并按 `(receivedAt, alertId)` 排序，结合已持久化生命周期 `sealedAt` 划分极大连续 episode。Worker 领取顺序不参与规则；只有其中至少一条 Alert 成功处理，episode 才实体化为 Incident。
- Incident 行保存 episode anchor、首末接收时间、计数、实际窗口、规则版本及生命周期封存。一个条件唯一索引只允许每个键存在一行 `aggregation_slot_active=true`，作为跨进程并发仲裁位。
- 业务上的“聚合开放”由 `sealedAt` 为空且 episode 内全部已持久化 Alert 的最大 `receivedAt` 加实际窗口未到期推导；Incident 行的 `last_alert_received_at` 仅汇总已关联 Alert，不能替代这一判据。发现仲裁位已过期时，在同一聚合事务内先关闭该位再创建新 Incident；纯窗口过期不产生 Timeline 事实。历史 episode 创建时若已过窗口，则创建即封存且不占活动位。
- Alert 行直接保存唯一 `incident_id`、稳定关联事件身份和关联时间；条件更新保证每条 Alert 只能关联一次。Incident 摘要从唯一关联 Alert 重算或以受保护增量更新，并以数据库查询对账。

### 4. 生命周期、Timeline 与审计

- Incident 状态转换严格使用规格完整转换表；命令必须携带预期状态和版本，受保护更新失败返回 `409`。
- 首次 `RESOLVED` 与同键接收、聚合使用同一事务锁；在锁内将不可移除的 `lifecycle_sealed_at` 分配为 `max(clock_timestamp(), 此前已提交同键 Alert 最大 received_at)`。后续同键首次接收时间严格晚于该封存边界；reopen 只恢复生命周期，不恢复聚合资格。
- Incident 行维护 `timeline_seq`；锁定/版本保护同一 Incident 后分配下一序号。Timeline 以稳定 `business_event_key` 防重，只提供插入与查询 Mapper。
- `t_incident_alert_audit` 只记录接收、终态失败、人工重放接受/拒绝和恢复，不记录逐次失败或重试调度；同样只暴露插入和查询。
- 日志只记录请求 ID、稳定标识、安全错误码和有限运行元数据，不记录来源密钥、签名、完整请求体、堆栈内容片段或不安全 label 值。

### 5. Transactional Outbox

- 所有 `incident.created` 和 `incident.alert-attached` 事件与对应 Incident/Timeline/Alert 变更在同一数据库事务插入 Outbox。
- Relay 以 `FOR UPDATE SKIP LOCKED` 领取到期 `PENDING` 行，写入短期 `claim_token/claim_until` 后在事务外调用现有 MQ Producer；仅 `SEND_OK` 视为成功。
- 成功后以 claim token 条件更新为 `PUBLISHED`；失败则安全摘要化错误并安排下一次尝试。发送成功但标记失败会导致相同 `eventId` 重投，这是契约允许且要求消费者去重的 at-least-once 语义。
- 领域事件使用单一 Topic、稳定 envelope 和 schema version；不使用 RocketMQ 事务消息代替本地 Transactional Outbox。

### 6. 恢复、运行状态与证据

- 首次处理失败后默认最多调度五次自动重试，退避参数可配置；全部尝试耗尽后，终态失败写 Alert 当前失败数据和一条稳定审计事实，不要求该 Alert 已关联 Incident。人工重放以预期状态+版本+原因原子地恢复为 `RECEIVED`；成功后才按原 episode 关联。
- 运行状态接口聚合待处理数、到期重试数、终态失败数、最老待处理年龄、Outbox 待发布/过期 claim 数和最近安全错误码；不得返回载荷或秘密。
- 测试输出运行清单、固定配置、数据集摘要、计数对账与日志捕获结果到 Maven `target/` 下的临时证据目录，不把生成证据提交为源码。

## Phase 0：研究结论

所有会影响计划的技术未知项已经在 [research.md](./research.md) 固化；没有遗留待澄清项。DEC-015 与 DEC-016 均已获用户批准并同步到项目级文档。其余选择均为 DEC-008 至 DEC-016 或现有仓库技术边界的直接落实，不新增 ADR。

## Phase 1：设计产物

- [data-model.md](./data-model.md)：五张表、字段、约束、索引、状态转换和事务边界。
- [contracts/openapi.yaml](./contracts/openapi.yaml)：提交、查询、重放、Incident 查询/转换、Timeline 与运行状态的 HTTP 契约。
- [contracts/hmac-signing.md](./contracts/hmac-signing.md)：来源 HMAC 的精确字节编码、路径规范化、密钥轮换与失败规则。
- [contracts/incident-events.md](./contracts/incident-events.md)：Outbox 事件 envelope、两种事件负载、Topic 和去重义务。
- [quickstart.md](./quickstart.md)：实现完成后的可复现验证顺序、环境边界和证据清单。

## 实施顺序建议

1. 模块与 PostgreSQL 测试基线。
2. 五张表、约束、Mapper 与只追加保护测试。
3. 规范化、HMAC、输入限制和同步幂等接收。
4. episode 规则、Alert Worker、Incident 状态机和 Timeline 原子事务。
5. Outbox Relay、失败恢复、重放和运行状态。
6. 契约、并发、崩溃恢复、内容安全和证据清单的综合验收。

具体依赖有序任务将在计划获确认后由 `$speckit-tasks` 生成。

## 复杂度跟踪

无宪章违规或需要豁免的复杂度项。五张表为 DEC-015 已确认的最小持久化集合；不增加第六张关联表、来源表、Attempt 表、通用审计平台或通用调度框架。
