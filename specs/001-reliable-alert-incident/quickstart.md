# 验收指南：可靠告警到 Incident

**用途**：实现完成后复现 Spec A 验收证据\
**当前状态**：Spec A 50 项实施任务已有本地通过记录，见 tasks/changelog。下方保留原验收要求；2026-10-05 仅复跑与 LAB-000 有关的核心故障子集，其 [统一清单](../002-approval-safe-simulation/evidence-index.md#2026-10-05-验收收尾本轮统一清单) 区分真实数据库断言与完整进程证据，不能当作本日 Spec A 全门禁重跑。

## 1. 前置条件

- JDK 17，使用仓库 Maven Wrapper。
- Docker Desktop 或等价 Docker 环境，用于 Testcontainers PostgreSQL。
- 不连接共享开发数据库、远程数据库或付费服务。
- 默认使用模拟领域事件发布器；核心验收不要求 RocketMQ Broker。
- 来源测试密钥仅通过测试进程的临时环境/容器配置注入，不写入仓库、manifest 或测试输出。

Docker 不可用时，普通单元/契约测试仍可运行；所有 PostgreSQL 约束、事务和并发场景必须明确报告为“环境受阻”，不得改用 H2 或共享数据库冒充通过。

## 2. 实现完成后的执行入口

先运行覆盖当前变更的聚焦测试，再执行仓库已有的完整 Java 检查：

```powershell
.\mvnw.cmd -Dspotless.apply.skip=true test
```

`incident` 模块创建后，`tasks.md` 必须补充并验证真实可执行的聚焦模块命令；在命令实际存在前，本文不虚构模块测试类名、Profile 或辅助脚本。所有只读构建均保留 `-Dspotless.apply.skip=true`，防止绑定在 compile 阶段的 Spotless 改写源码。

## 3. 固定测试配置

每次完整验收必须在 manifest 中记录实际值；默认基线为：

| 项目 | 默认值 |
| --- | --- |
| 协议 | v1 |
| Webhook 实际字节上限 | 65,536 |
| JSON 最大深度 | 16 |
| HMAC 时效 | 可信当前时间前后各 300 秒，包含边界 |
| 聚合规则 | `alert-incident-v1` |
| 聚合窗口 | 1,800 秒，包含边界 |
| 自动重试 | 首次失败后最多 5 次；10s、30s、2m、10m、30m，加有界抖动 |
| Outbox 单次退避上限 | 15 分钟 |
| 已发布 Outbox 默认保留 | 7 天 |
| PostgreSQL | Testcontainers 启动的隔离实例，记录实际镜像版本 |

并发测试使用固定输入前缀和已记录随机种子；时间边界测试使用受控数据库时间或可重复的时间夹具，不依赖人工等待 30 分钟。

## 4. 最小验证顺序

### 阶段 A：数据库基线

1. 启动临时 PostgreSQL、应用全量 Schema 并执行一条读写冒烟测试。
2. 验证五张表、外键、状态 CHECK、幂等唯一键、episode 唯一键和活动位条件唯一索引。
3. 使用应用业务角色尝试更新/删除 Timeline 与 Alert 审计，确认失败；受控测试清理身份仍可销毁整个临时容器。

### 阶段 B：接入与签名契约

1. 对 `contracts/hmac-signing.md` 的固定向量逐字节验证客户端/服务端摘要一致。
2. 验证媒体类型、实际字节、认证、JSON 结构、字段契约的复合失败优先级。
3. 覆盖活动、停用、未知和轮换重叠 key；覆盖跨方法、跨路径、跨 `alertId` 和 body 篡改。
4. 检查每个失败的真实 HTTP 状态和安全 `Result` 响应体，并确认数据库五张表均零新增。

### 阶段 C：幂等与处理恢复

1. 提交有效 Alert，检查 `202`、一条 Alert 和一条接收审计。
2. 相同业务对象仅改变 JSON 空白/键顺序，检查稳定 ID 与相同重试语义。
3. 改变任一规范业务字段，检查 `409` 只返回原有安全对账标识。
4. 100 路并发提交同一幂等身份，最终只存在一条 Alert/接收事实。
5. 在接收提交后、聚合提交前和聚合提交观察前分别注入崩溃，重启后收敛到一个终态且无部分事实。
6. 注入每次可恢复失败，检查首次尝试后的五次重试计划；耗尽后检查一条终态失败审计，确认没有逐次 Attempt 审计行。
7. 验证授权/未授权/过期版本/并发人工重放和恢复审计。

### 阶段 D：episode、并发和生命周期

1. 运行窗口内、恰好 30 分钟、超过边界、来源时间乱序、相同接收时间和延迟处理数据集。
2. 用不同 Worker 领取顺序重复同一持久化集合，比较 episode、关联、首末时间、计数和稳定事件身份。
3. 100 条同键、不同外部 ID 并发处理，验证一个聚合开放 Incident、100 个关联和100 条唯一关联事实。
4. 准备同键 episode：较早 Alert 终态 `FAILED`、较晚 Alert 成功处理；验证失败成员参与分段及窗口资格，但 Incident 仅有成功成员关联，anchor 可以指向未关联失败成员。另准备全部终态 `FAILED` 的 episode，验证其尚无 Incident；授权重放一条成员并成功处理后，验证只形成一个 Incident。
5. 覆盖 resolve 与关联竞争、封存边界前/等于/后、较旧历史 episode 创建即封存、reopen 不恢复聚合资格。
6. 对状态表每条允许边做正向测试，对未列出边、过期版本、CLOSED 出边和存在较新未关闭 Incident 的 reopen 做拒绝测试。
7. 在真实 PostgreSQL 并发事务中注入同键墙钟同值、回拨及接收/首次解决交错，核对 DEC-016 的单调 `receivedAt`、封存边界、anchor 稳定性与最终归属；不同键不需要共享顺序。

### 阶段 E：Timeline、Outbox 与运行状态

1. 对每个业务事件核对唯一 Timeline 事实、Incident 内连续序号和稳定业务键。
2. 在 Timeline 类型因封存竞争为 `ALERT_ATTACHED` 或 `LATE_ALERT_ATTACHED_AFTER_SEAL` 时，确认关联/Outbox 事件身份不变且不重复。
3. 让模拟发布器持续失败，确认领域事务仍提交、Outbox 不丢弃且退避不超过 15 分钟。
4. 模拟发送成功但标记失败，重新发布后比较两次捕获 envelope 的 `eventId` 与内容完全相同。
5. 构造已知 Alert/Outbox 状态，核对运行状态中的数量、最老时长、到期重试、过期 claim 和最近成功时间。
6. 对响应、捕获日志、Timeline、Alert 审计和 Outbox payload 扫描秘密、签名、完整请求体、堆栈及不安全探针值，命中数必须为零。

## 5. 验收数据集

完整目录至少包含以下稳定场景 ID，任务阶段可以增加但不能改写既有含义：

| 场景前缀 | 内容 |
| --- | --- |
| `ING-*` | 有效提交、每个响应类、复合失败优先级、字节/深度/重复键/null/未知字段 |
| `AUTH-*` | HMAC 正向、时效边界、轮换、停用 key、方法/路径/目标/body 篡改 |
| `IDEM-*` | 相同/冲突重复、100 路并发幂等、持久化前故障 |
| `AGG-*` | 指纹、窗口边界、episode 分段、延迟处理、创建即封存、同键并发 |
| `LIFE-*` | 完整主状态机、乐观版本、生命周期封存与 reopen 限制 |
| `REC-*` | Worker 崩溃、五次重试、终态失败、人工重放与竞争 |
| `OUT-*` | 原子 Outbox、发布失败、过期 claim、相同 eventId 重投 |
| `SAFE-*` | 所有权隐藏、人员越权、只追加保护、内容安全扫描 |
| `OPS-*` | 运行状态计数和时长与 SQL 快照对账 |

## 6. 证据目录

实现后的测试将一次运行的生成内容写入未提交目录：

```text
incident/target/incident-evidence/<run-id>/
├── manifest.json
├── scenario-results.json
├── persistence-counts.json
├── event-captures.jsonl
└── content-scan.json
```

`manifest.json` 至少记录：

- 场景目录版本和运行 ID；
- Git commit SHA（工作树无提交时明确记录 `uncommitted`，不伪造 SHA）；
- 开始/结束 UTC 时间；
- JDK、Maven、PostgreSQL/Testcontainers 版本；
- 实际限制、聚合窗口、重试/抖动配置和随机种子；
- 实际执行命令或入口；
- 每个场景的 `PASSED/FAILED/BLOCKED` 结果及阻塞原因。

证据只记录稳定 ID、安全码、状态、数量和经过白名单处理的摘要，不记录密钥、签名或完整告警请求体。

## 7. 通过条件

- Spec 的 SC-001 至 SC-011 全部有对应场景，且没有未解释的失败或跳过；SC-011含同键可信时间同值/回拨和接收与首次解决竞争。
- 100个重复提交任务与100条同键Alert结果满足精确计数；当前测试分别使用20/16线程池，不将任务数表述为同时活跃连接或吞吐，不以“未复现”代替断言。
- 数据库约束、事务回滚、`SKIP LOCKED` 和只追加保护来自真实 PostgreSQL 证据。
- Outbox 重投明确表述为 at-least-once，不声称 exactly-once。
- 任何依赖环境而未运行的检查标为 `BLOCKED`；只有完成的 manifest 才能支持可靠性结论。

2026-10-06项目级核对：本指南的SC范围按现行spec/tasks补至SC-011。独立incident-evidence目录只声明自身三个smoke场景，完整A验收还须读取同轮其他契约/并发/恢复/权限报告；最新实现、证据及未验证范围见 [本地完成度核对](../../docs/development/local-acceptance-review.md)。本次只核对已有运行制品，没有重新执行Java或Broker验收。
