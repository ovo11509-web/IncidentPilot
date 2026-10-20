# 验证与演示指南

2026-10-04完成本地实施和隔离验收。下文区分可复跑步骤与本次实际结果；遵循docs/development/testing.md；不使用真实开发库、付费模型或生产目标。

## 前置条件

批准本Spec/plan/tasks；现有Node/npm/JDK及Maven Wrapper；前端锁定依赖按测试指南准备；临时PostgreSQL/Redis及独立模拟库、主应用与真实MCP采用既有隔离测试模式。主应用context=/api/ragent、Vite仅本地访问，实际端口按隔离环境配置（9090为仓库默认，不强行占用）。使用隔离管理员，安全注入凭据，不写入材料。

诊断选Action需隔离演示资源白名单和注册目标映射；默认无映射是预期安全行为，不能为方便演示解除服务端检查。告警通过已有签名Webhook或隔离测试准备，两条可聚合告警不在页面伪造。

## 最小检查命令

仓库根：
```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl incident -am '-Dtest=IncidentQueryContractTest,IncidentControllerContractTest,ActionQueryContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

frontend目录：
```powershell
node tests/incident-view.check.mjs
npm run lint
node node_modules/typescript/bin/tsc --noEmit -p tsconfig.app.json
npm run build
```

命令须按真正新增测试类/入口核对；node逻辑检查应调用锁定的本地TypeScript，不允许npx临时联网下载。缺少依赖只记录前置缺失，不能把未运行记为通过。根据实际变更再选bootstrap已有诊断黄金路径/安全测试；测试场景名称以代码为准。

## 真实浏览器验收

用可用browser/computer-use工具登录隔离应用并操作实际页面，保留操作记录及截图；后端真实记录关联到同一runId。不得以fixture展示或mock网络完成此门禁。

1. 两条Webhook告警聚合同一Incident，从列表进入详情，核对两条来源事实与Timeline；刷新保持。
2. 显式本地诊断，查看根因未确认候选、缺失事实和引用原文/位置/hash；只读建议不能转Action。
3. 选择健康模拟建议，GET冻结目标/READY参数/绑定摘要，先拒绝，核实拒绝Action未应用。
4. 明确另建提案并重新审阅批准，等待真实Worker/MCP回执，核实同一执行仅应用1次；刷新后来源、审批、回执都可查。
5. 网络响应丢失/重复选择使用相同请求ID；并发审批冲突不能自动重批。新告警导致旧诊断失效、无映射、证据失效分别失败关闭。
6. UNKNOWN明确显示未知且无写重试；可用隔离安全测试夹具预置UNKNOWN用于显示验证，该结果与真实成功主流程分开记录。
7. 关联告警/Runbook注入HTML及测试秘密占位值，检验不执行脚本/不加载外部资源、不回传禁止字段；非admin直访和未登录访问被服务器拒绝。

## 制品与判定

制品输出至incident/target/ui-evaluation/<runId>/，记录日期、gitHead/dirty、实际命令、浏览器与环境、用例分母/通过/失败/跳过、HTTP语义错误码、incidentId/diagnosisId/actionId/executionId、模拟库应用计数及截图引用。只输出安全审计字段，不保存Authorization、票据秘密或原告警载荷。

真实浏览器成功/拒绝的应用计数取隔离模拟库和可信回执，不由前端计数或toast推断。所有SC具备实际结果后才勾选任务；若无法启动隔离环境，列为受阻待验收。引用当前开发库或线上服务不构成替代。

## 2026-10-04实际命令与结果

实施前speckit-analyze只读核对20 FR/7 SC/31任务，覆盖完整，无阻塞；实施时发现草案成功码错误，用户明确选择保留既有A的"0"与B/C的"SUCCESS"，记录DEC-027。检查清单9/11为审阅者标记，依据用户已授权实施继续，未代改标记。

Node v20.19.5、JDK21.0.12（项目Java支持基线17）、Docker/Testcontainers临时PostgreSQL16、Redis、rustfs；主应用普通ragent_app数据库角色，真实Bootstrap和MCP分离JVM。日常数据库未使用。

根目录实际扩大回归：

```powershell
.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-Dtest=IncidentOperationsQueryTest,IncidentQueryContractTest,IncidentControllerContractTest,Action*ContractTest,ActionSecretLeakTest,ActionApprovalRaceTest,ActionReceiptTest,ActionUnknownConvergenceTest,Diagnosis*Test,IncidentSnapshotIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

BUILD SUCCESS；Incident 31、Agent 3，共34测试，0失败/错误/跳过。日志 `incident/target/spec-d-java-final.log`。此前最小契约5项及扩展16项通过；真正行为红测为Action参数预览缺失，非不存在文件的编译失败；日志分别 `spec-d-contract-red.log`、`spec-d-contract.log`、`spec-d-java.log`。

frontend目录执行上述逻辑/tsc/lint/build，全部退出0；最终日志 `frontend/.output/spec-d-logic-final.log`、`spec-d-tsc-final.log`、`spec-d-lint-final.log`、`spec-d-build-final.log`。Codex闪退时一轮build仅到transforming，保存在 `spec-d-build-interrupted.log`，不计成功；恢复后tsc/lint/build重跑退出0（build 1m1s），仅原大chunk警告。未新增依赖。

显式夹具实际启动命令（bootstrap目录，classpath参数文件为本次已编译测试及隔离启动检查生成的本地依赖路径，不纳入Git）：

```powershell
java '@target/ui-fixture-classpath.args' com.nageoffer.ai.ragent.IncidentOperationsUiFixture
# 无目标映射，独立临时资源
java '@target/ui-fixture-classpath.args' com.nageoffer.ai.ragent.IncidentOperationsUiFixture no-mapping
```

编译准备使用根目录 `.\mvnw.cmd '-Dspotless.apply.skip=true' -pl bootstrap -am '-DskipTests' test-compile`。复跑时classpath参数文件需由当前bootstrap测试运行classpath重新生成，包含reactor的main/test classes及现有依赖；不能复制其他机器路径。临时ready和账号文件在bootstrap/target，夹具结束删除。每次ready记录实际前端与主应用端口，不占用日常9090。STOP/CAPTURE/STALE标记仅创建在ready指向的本次证据目录；STOP关闭本次资源，CAPTURE归档安全服务器快照，STALE提交真实第三条签名告警。DROP_SELECT_RESPONSE由隔离Vite代理消费真实服务端选择响应后关闭客户端连接，绝非mock成功结果。

## 本次浏览器判定（22/22，0最终失败，0跳过）

| 场景 | 实际结果与制品 |
| --- | --- |
| 1 列表分页 | 20→25条，ID无重复；最终run的01-list-more-dom.txt |
| 2 筛选空态 | CLOSED无事件，切回全部重置分页；02-empty-filter-dom.txt |
| 3 两签名告警 | 同一Incident、两条已处理关联事实；server-evidence-1.json |
| 4 Timeline | 来源/诊断/提案/审批/票据/回执记录，顺序只追加 |
| 5 显式诊断 | COMPLETED/SUPPORTED；候选标明根因未确认、缺失事实/限制 |
| 6 引用复核 | RB-HEALTH 1.0.0、章节/行号、全文/片段hash及原文；首run的03-diagnosis-dom.txt |
| 7 只读建议 | R1转换按钮禁用，无写调用 |
| 8 冻结预览 | 服务端GET返回demo/READY/tool v1及bindingDigest |
| 9 人工拒绝 | APPROVAL_REJECTED，模拟库应用0；server-evidence-2.json |
| 10 明确新提案 | 新actionId、originalActionId保留，独立PENDING审批 |
| 11 人工批准/轮询 | Worker→真实MCP→SUCCESS/SIMULATION_APPLIED可信回执，应用1；05-poll-success-dom.txt、server-evidence-3.json |
| 12 重复意图 | 原意图重发返回原Action；批准后的重复选择仍2个Action/1次应用；server-evidence-4.json |
| 13 刷新 | 审批/来源/execution/receipt仍存在，Incident保持OPEN；06-refresh-dom.txt、07-receipt.jpg |
| 14 响应丢失 | 实际201落库后断连，TRANSPORT_UNKNOWN；手动原意图重发同actionId，无自动重试；lost-response.json、03/04 DOM |
| 15 多页审批冲突 | 旧页再次批准ACTION_CONFLICT，不自动重批；首run的06-conflict-dom.txt |
| 16 新事实/旧诊断 | 真实第三告警使version 2→3，旧页选择STALE_SNAPSHOT；历史仍读、选择禁用；08-stale-conflict-dom.txt |
| 17 无映射 | 真实独立环境UNSUPPORTED_RECOMMENDATION，无Action，应用0 |
| 18 UNKNOWN | 明确未知且无写重试；09-unknown-dom.txt，仅展示fixture，不冒充实际UNKNOWN执行 |
| 19 告警HTML | <img/onerror>纯文本，[REDACTED]，注入图片/脚本节点均0；visual-security.json |
| 20 未登录 | 浏览器直访回login，真实API 401 UNAUTHORIZED |
| 21 非admin | 浏览器直访回chat，真实API 403 FORBIDDEN；每环境6匿名+6普通用户门禁，security-gates.json |
| 22 不存在事件 | 404清空上一事件内容；首run的12-not-found-dom.txt |

三个成功运行目录位于 `incident/target/ui-evaluation/`：首黄金路径 `spec-d-2026-10-04T12-07-07-227268800Z`；无映射 `spec-d-2026-10-04T12-26-21-121557300Z`；最终响应丢失/分页/黄金路径 `spec-d-2026-10-04T12-35-13-135553Z`。最终acceptance-summary.json和server-evidence-1～5.json核对应用计数0/0/1/1/1，只有拒绝和成功两个Action，成功executionId=`2106725697257996288`、receiptId=`2106725699494191104`。这是数据库/可信回执证据，不以截图推断副作用。

## 限制与失败记录

首夹具UNKNOWN种子缺authorization_due_at，真实Schema拒绝，修复后重跑；保留bootstrap/target/spec-d-fixture-launch.log，失败运行不计通过。另一次在compile尚未完成时启动导致测试类不存在，也保留launch-2.log；后续显式编译与3次隔离运行成功。两次浏览器等待/视口能力异常未当作业务成功依据，使用后续实际DOM/服务器证据确认；视口override未改变实际454px宽度，最终截图采用折叠导航的真实紧凑界面，不宣称桌面断点覆盖。

UNKNOWN是隔离owner SQL预置展示；运行UNKNOWN恢复由上述Java回归覆盖。注入页面场景使用告警文本，未另行构造Runbook HTML浏览器样例；诊断/引用复用React纯文本输出和既有Java安全门禁。路由取消/后台暂停与终态停止有实现及逻辑检查，未声称覆盖所有浏览器/网络时序。没有真实模型、真实基础设施、生产负载/性能、跨浏览器或人工独立签署验收。所有临时主应用/MCP/Vite已停止、凭据删除；恢复时Docker引擎不可达，不拿此状态重跑或伪造既有容器证据。

最终收尾补充：诊断详情回调增加取消检查后，再跑逻辑/tsc/lint/build均退出0，最后build57.45s。`python incident/target/verify-spec-d.py`及`git diff --check`退出0；基线1720文件，1700逐字节未变，20项仅授权变化，0丢失/越界。31/31任务、空暂存区、三运行目录秘密扫描/日志归档/临时凭据删除均核对；最终audit.json和artifact-sha256.json可独立复核。
