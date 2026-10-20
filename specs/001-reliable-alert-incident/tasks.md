# 任务：可靠告警到 Incident

**输入**：[spec.md](./spec.md) v1.0、[plan.md](./plan.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**范围**：本文件仅规划 Spec A 的未来实施；生成本文件不授权修改业务代码、Schema、提交或部署。所有任务保持稳定 `TASK-###` 编号，不因普通 Bug 重排或复用编号。

**执行记录**：开始任何代码任务前，先说明问题根因、关联 TASK、涉及文件、是否需要修改 Spec、修改方案和影响范围。实施中先分类为实现 Bug、Task 描述问题、Spec 设计缺陷或新需求；普通 Bug 优先改代码。任务完成后勾选对应编号并更新 `docs/changelog.md`；重要长期决策先获确认，再追加 `docs/DECISIONS.md`。本表未勾选表示未实施。

**实施前治理门禁**：指纹/窗口/聚合键与完整状态机已分别由批准的 DEC-017、DEC-018 归档。实施仍须遵守 Spec A v1.0、DEC-008～DEC-018 及仓库授权边界；普通实现 Bug 不反向改写规格。

## 格式与路径

- `[P]` 表示不依赖其他未完成任务且触及不同文件时可并行；没有 `[P]` 则按列出顺序及依赖执行。
- `[US1]`～`[US4]` 与规格的四个用户故事一一对应。路径均相对于仓库根目录；新路径是计划目标，不代表文件已存在。
- 测试任务先于所属故事的实现任务建立失败断言；真实 PostgreSQL 集成测试遵循 `docs/development/testing.md`，不得用共享或远程库代替。

## Phase 1：准备（共享基础设施）

**目标**：建立独立领域模块与最小可验证工程基线，不引入 Spec B 或其他领域能力。

- [X] TASK-001 在 `pom.xml`、`incident/pom.xml` 和 `bootstrap/pom.xml` 注册并装配独立 `incident` 模块，保持 `agent`、`rag`、`mcp-server` 和前端业务行为不变（DEC-008）。
- [X] TASK-002 在 `incident/pom.xml` 与 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/PostgresSmokeTest.java` 配置 Testcontainers PostgreSQL 最小冒烟测试，证明隔离容器启动、连接及清理可重复执行（SC-009）。
- [X] TASK-003 在 `resources/database/schema_pg.sql` 和 `resources/database/upgrades/v2.0.0/260923_incident_core.sql` 按 `data-model.md` 添加五张表、外键、CHECK、唯一键、条件索引及只追加权限；只追加升级脚本不得覆写既有迁移（FR-009、FR-024、FR-030、FR-034、FR-041）。
- [X] TASK-004 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentSchemaTest.java` 验证全量 Schema 与升级路径、五张表、约束和 Timeline/审计普通角色不可更新或删除（FR-009、FR-030、SC-004）。

**检查点**：模块可构建，隔离 PostgreSQL 冒烟与 Schema 约束检查可运行；仍没有告警业务行为。

## Phase 2：基础能力（所有故事的前置条件）

**关键要求**：完成共享持久化、配置与安全边界后才实现用户故事。`TASK-005`～`TASK-010` 是后续故事的共同依赖。

- [X] TASK-005 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/config/IncidentProperties.java` 与 `bootstrap/src/main/resources/application.yaml` 定义非秘密默认配置、来源注册表引用、窗口、重试和 Worker/Outbox 开关；密钥只由运行环境注入（FR-003、FR-004、FR-020、FR-035）。
- [X] TASK-006 [P] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/dao/AlertMapper.java` 与 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/dao/AlertAuditMapper.java` 建立 Alert/审计的受限 SQL 访问，审计只暴露插入与查询（FR-009、FR-034）。
- [X] TASK-007 [P] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/dao/IncidentMapper.java` 与 `incident/src/main/java/com/nageoffer/ai/ragent/incident/timeline/dao/TimelineMapper.java` 建立 Incident/只追加 Timeline 访问及版本条件更新（FR-024、FR-027、FR-030）。
- [X] TASK-008 [P] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/outbox/dao/OutboxMapper.java` 建立 Outbox 原子插入、领取、claim 到期恢复及状态条件更新访问（FR-040～FR-043）。
- [X] TASK-009 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/dao/AggregationClockMapper.java` 中封装 DEC-016 的同键事务锁与 `receivedAt`/`sealedAt` 分配；先于 Alert/Incident 行锁，包含无前序事实、微秒递增、回拨和溢出处理（FR-019、FR-022、SC-011）。
- [X] TASK-010 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/AggregationClockTest.java` 以真实 PostgreSQL 验证同键并发/墙钟同值/回拨、接收与解决交错、不同键独立性及锁顺序；证明 anchor 不逆转（SC-011）。

**检查点**：共享边界可由真实数据库测试证明，但故事端点尚未对外开放。

## Phase 3：用户故事 1——可靠提交告警（P1，MVP）

**目标**：来源可以安全、幂等地持久化提交与查询 Alert；人员查询不形成认证旁路。

**独立测试**：停止聚合 Worker，逐项运行 [quickstart.md](./quickstart.md) 的接入、安全和幂等目录；只要求 Alert 与接收审计事实，不要求已有 Incident。

### 测试（先建立失败断言）

- [X] TASK-011 [P] [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/contract/AlertIngressContractTest.java` 按 `contracts/openapi.yaml` 覆盖真实 200/202/400/401/409/413/415/422/503、复合失败优先级、空持久化及安全响应（FR-001～FR-014、SC-004）。
- [X] TASK-012 [P] [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/contract/HmacSigningContractTest.java` 按 `contracts/hmac-signing.md` 覆盖原始字节、方法/路径/目标绑定、时效边界、活动/停用/重叠 key、签名篡改与混合凭据拒绝（FR-002～FR-005）。
- [X] TASK-013 [P] [US1] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/AlertIdempotencyTest.java` 验证 100 路相同身份提交、摘要等价/冲突、首次审计与提交前数据库故障不产生伪接受（FR-009～FR-014、SC-001）。

### 实现

- [X] TASK-014 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/contract/AlertV1Canonicalizer.java` 实现严格 UTF-8/JSON、字段界、NFC/裁剪、可选字段等价和版本化载荷摘要（FR-006、FR-007、FR-010、FR-016）。
- [X] TASK-015 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/security/SourceHmacVerifier.java` 实现来源注册、HMAC 帧编码、常量时间比较、密钥轮换及方法/规范路径/原始体绑定（FR-002～FR-004）。
- [X] TASK-016 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/web/AlertIngressFilter.java` 强制执行媒体类型→实际字节数→认证→JSON→字段契约顺序，拒绝超限与不安全日志内容（FR-003、FR-005、FR-006、FR-008）。
- [X] TASK-017 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/service/AlertReceiveService.java` 原子写入 Alert 与 `ALERT_RECEIVED` 审计、DEC-016 时间、唯一约束幂等及 503 安全重试（FR-009、FR-010、FR-011、FR-012、FR-013、FR-014、FR-034）。
- [X] TASK-018 [US1] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/web/AlertController.java` 与 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/web/IncidentErrorAdvice.java` 暴露提交与查询的真实 HTTP 状态和 `Result` 安全响应（FR-001、FR-011～FR-014）。
- [X] TASK-019 [US1] 在 `system/src/main/java/com/nageoffer/ai/ragent/user/config/SaTokenConfig.java` 与 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/security/AlertQueryAuth.java` 实现共用 GET 路由的来源 HMAC/人员原始 `Authorization` 令牌二选一、`admin` 本地强制校验和跨来源隐藏；不得因全局拦截排除形成匿名旁路（FR-002、SC-004）。
- [X] TASK-020 [US1] 运行 `incident/src/test/java/com/nageoffer/ai/ragent/incident/contract/AlertIngressContractTest.java`、`HmacSigningContractTest.java` 和 `integration/AlertIdempotencyTest.java`，记录真实命令与结果到 `docs/changelog.md`（SC-001、SC-004）。

**检查点**：US1 在无 Worker/MQ 条件下独立通过；MVP 仅证明可靠接收，不声称 Incident 已形成。

## Phase 4：用户故事 2——确定性形成 Incident（P2）

**目标**：异步 Worker 按全部已接收 Alert、窗口与生命周期边界形成唯一 Incident，原子关联并追加安全事实与领域事件。

**独立测试**：可直接在隔离 PostgreSQL 中预置已接收 Alert，不要求 HTTP 接入可用；验证固定集合在不同 Worker 调度下的 episode、成功关联和业务事件身份。

### 测试（先建立失败断言）

- [X] TASK-021 [P] [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/unit/FingerprintEpisodeTest.java` 覆盖 v1 指纹字段、规范化、30 分钟含边界、来源时间乱序、封存切分与 `FAILED` 成员参与分段（FR-015～FR-023、SC-002）。
- [X] TASK-022 [P] [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentAggregationTest.java` 覆盖 100 条同键并发、迟到处理、全失败无 Incident、失败 anchor、重放后关联、创建即封存与仅一个聚合开放位（FR-019～FR-025、SC-002、SC-003）。
- [X] TASK-023 [P] [US2] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentAtomicityTest.java` 注入关联、Timeline 和 Outbox 写入失败，验证全部回滚以及稳定关联身份与无重复事件（FR-024、FR-031、FR-040、SC-003）。

### 实现

- [X] TASK-024 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/service/AlertFingerprint.java` 实现 `alert-fingerprint-v1` 编码、SHA-256 和 `aggregationKey`，与接收事务共用规范值（FR-015、FR-016、FR-017、FR-018）。
- [X] TASK-025 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/EpisodePartitioner.java` 根据 `(receivedAt, alertId)`、实际窗口及不可变封存边界划分全部已持久化成员；来源 `occurredAt` 只保留为证据（FR-019、FR-020、FR-021、FR-022、FR-023）。
- [X] TASK-026 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/worker/AlertWorker.java` 以只读候选→同键锁→`SKIP LOCKED` 领取→重新校验处理；保持崩溃后可发现，Worker 不改变 episode 顺序（FR-019、FR-023、FR-034、FR-039）。
- [X] TASK-027 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentAggregationService.java` 实现唯一 episode 创建或一次性关联、失败成员不强制关联、首末时间/数量重算、历史创建即封存和并发仲裁（FR-020～FR-025、SC-002、SC-003）。
- [X] TASK-028 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/timeline/service/TimelineWriter.java` 只追加创建/关联事实，按 Incident 序号排序，封存后迟到关联复用稳定身份且只包含白名单安全摘要（FR-030～FR-033）。
- [X] TASK-029 [US2] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/outbox/service/IncidentEventWriter.java` 同事务写入 `incident.created` 与 `incident.alert-attached` Outbox，遵守 `contracts/incident-events.md` 的 eventId、版本和负载规则（FR-024、FR-040、FR-041）。
- [X] TASK-030 [US2] 运行 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentAggregationTest.java` 与 `IncidentAtomicityTest.java`，按 `quickstart.md` 比较多次固定输入的最终关联、事件身份和证据计数，并更新 `docs/changelog.md`（SC-002、SC-003、SC-011）。

**检查点**：US2 可用隔离预置数据单独验收；Alert 处理成功才实体化 Incident。

## Phase 5：用户故事 3——控制 Incident 生命周期（P3）

**目标**：只允许完整转换表中的受保护转换，首次解决写永久封存边界和两条指定 Timeline 事实。

**独立测试**：预置 Incident 快照与版本，逐边验证成功/拒绝、过期版本、reopen 约束及 Timeline 计数；不依赖 MQ 发布。

### 测试（先建立失败断言）

- [X] TASK-031 [P] [US3] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/unit/IncidentStateMachineTest.java` 穷举八条允许边与代表性禁止边、原因/操作者前置条件和 CLOSED 终态（FR-026～FR-029、SC-006）。
- [X] TASK-032 [P] [US3] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentTransitionTest.java` 验证状态+版本竞争、首次解决封存净增两条 Timeline、重开不恢复聚合及较新未关闭 Incident 拒绝（FR-022、FR-026～FR-031、SC-006、SC-011）。

### 实现

- [X] TASK-033 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentStateMachine.java` 固化完整状态表、原因和授权主体校验，不允许自动关联修改主状态（FR-026、FR-027、FR-028、FR-029）。
- [X] TASK-034 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/service/IncidentTransitionService.java` 实现预期状态/版本受保护转换、同键锁下首次封存与 reopen 限制，并原子追加状态变更及唯一封存事实（FR-022、FR-027、FR-029、FR-031）。
- [X] TASK-035 [US3] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/web/IncidentController.java` 暴露人员授权的 Incident 查询、状态转换及只读 Timeline，返回安全当前状态/版本与真实 HTTP 错误码（FR-026～FR-032）。
- [X] TASK-036 [US3] 运行 `incident/src/test/java/com/nageoffer/ai/ragent/incident/unit/IncidentStateMachineTest.java` 与 `integration/IncidentTransitionTest.java`，核对全部允许边、禁止边与首次解决两事实，并更新 `docs/changelog.md`（SC-006）。

**检查点**：US3 可凭预置 Incident 独立验收，且与 US2 的 `OPEN`、`ACKNOWLEDGED`、`MITIGATING`、`RESOLVED`、`CLOSED` 名称一致。

## Phase 6：用户故事 4——恢复并证明结果（P4）

**目标**：终态失败、授权重放、Outbox 重投、运行状态和证据目录均可与持久化状态对账。

**独立测试**：在隔离数据库与模拟发布器中注入 Worker/发布故障和重启；验证最终状态、审计、稳定事件 ID 和内容安全，不调用真实远程服务。

### 测试（先建立失败断言）

- [X] TASK-037 [P] [US4] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/AlertRecoveryTest.java` 覆盖接收后崩溃、默认五次自动重试、全失败无 Incident、授权/拒绝/并发重放及恢复审计（FR-034～FR-039、SC-005、SC-008）。
- [X] TASK-038 [P] [US4] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/OutboxRecoveryTest.java` 注入发送失败、`SEND_OK` 后标记失败、claim 过期与重复投递，验证同一 eventId、至多 15 分钟退避和不丢弃（FR-040～FR-043、SC-007）。
- [X] TASK-039 [P] [US4] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/contract/OperationsSecurityTest.java` 验证运行状态与数据库对账、响应/Timeline/审计/捕获日志禁止内容扫描及权限拒绝（FR-003、FR-032、FR-044、SC-010）。

### 实现

- [X] TASK-040 [US4] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/worker/AlertRetryPolicy.java` 实现默认五次可配置退避、有界抖动与当前控制数据更新；仅终态失败追加一次审计事实（FR-034～FR-036）。
- [X] TASK-041 [US4] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/service/AlertReplayService.java` 实现人员授权、`FAILED`+预期版本+原因的原子重放和接受/拒绝审计，不改变身份、载荷、`receivedAt` 或指纹（FR-037、FR-038）。
- [X] TASK-042 [US4] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/alert/web/AlertController.java` 增加人员重放路由和安全的 202/409/422/503 响应，与来源查询认证隔离（FR-001、FR-037、FR-038）。
- [X] TASK-043 [US4] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/outbox/worker/OutboxRelay.java` 实现短事务 claim、事务外发布、`SEND_OK` 条件完成、过期 claim 恢复及稳定 eventId 重投，不承诺 exactly-once（FR-039、FR-040、FR-041、FR-042、FR-043）。
- [X] TASK-044 [US4] 在 `incident/src/main/java/com/nageoffer/ai/ragent/incident/core/web/IncidentOperationsController.java` 实现仅授权人员可见的只读积压、时长、失败、重试与发布状态，并按数据库结果对账（FR-044）。
- [X] TASK-045 [US4] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentEvidenceCatalogTest.java` 和 `incident/src/test/java/com/nageoffer/ai/ragent/incident/evidence/EvidenceManifestWriter.java` 生成 `incident/target/incident-evidence/` 下可复现清单、场景结果、计数与安全扫描摘要；失败/受阻如实标记（FR-045、FR-046、FR-047、FR-048、SC-009）。
- [X] TASK-046 [US4] 运行 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentEvidenceCatalogTest.java` 与相关故障测试，记录实际命令、环境受阻项和结果到 `docs/changelog.md`，不得把生成证据当作已实测的性能结论（SC-005、SC-007～SC-010）。

**检查点**：四个用户故事可分别验收；端到端仅覆盖 Spec A 告警到 Incident，不涉及 Action、审批或处置。

## Phase 7：完善与跨故事检查

- [X] TASK-047 [P] 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/contract/IncidentEventContractTest.java` 对 `contracts/incident-events.md` 的 envelope、两类事件、未知版本与安全负载做契约回归（FR-040～FR-043）。
- [X] TASK-048 在 `incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/IncidentAcceptanceSuiteTest.java` 串联 `quickstart.md` 的接入、聚合、生命周期、恢复、Outbox 与安全目录；核对 FR-045 全项及 SC-001～SC-011，缺少 Docker/Broker 时明确记录受阻而非虚报通过。
- [X] TASK-049 在 `docs/PROJECT_CONTEXT.md`、`docs/ARCHITECTURE.md`、`docs/TODO.md` 和 `docs/changelog.md` 只更新已实现且有证据的能力与 SPC-001 阶段状态；不得把目标设计写成已上线事实。
- [X] TASK-050 在 `specs/001-reliable-alert-incident/tasks.md` 更新实际完成状态，并按 `docs/development/testing.md` 运行适用的模块检查；记录真实命令、结果、未运行的环境检查和任何实施中经批准的 Spec/ADR 变更。

## 依赖与可并行机会

- 准备顺序：`TASK-001` → `TASK-002`/`TASK-003` → `TASK-004`；共享基础：`TASK-005`～`TASK-010` 完成后进入故事阶段。各 Phase 的测试先于本 Phase 实现。
- 用户故事完成顺序建议：US1 → US2 → US3 → US4。US2/US3 可用隔离预置数据独立验证；真实端到端仍依赖 US1 的可靠接收。US4 的重放依赖 US1，Outbox 发布依赖 US2 的事件写入。
- 可并行示例：US1 的契约、HMAC、幂等测试分别在 `TASK-011`～`TASK-013`；US2 的规则、聚合、原子性测试分别在 `TASK-021`～`TASK-023`；US4 的恢复、发布、安全测试分别在 `TASK-037`～`TASK-039`。并行标记不授权使用子代理或在同一文件并发编辑。
- 建议最小可交付增量为 US1；只有通过其独立测试才能声称告警已可靠接收，不能据此声称 Incident 或全链路已完成。
