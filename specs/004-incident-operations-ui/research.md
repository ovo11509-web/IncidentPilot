# 研究与brainstorming：Incident操作界面

**日期**：2026-10-04。**状态**：静态研究完成，设计待确认；无运行质量结论。

## 方案比较与建议

1. **推荐：现有/admin扩展事件列表和详情。** 复用RequireAdmin、AdminLayout、会话、组件和主应用；新增只读API视图与专用客户端，改动集中。
2. **独立运维控制台。** 可独立布局，但需重复登录、导航和部署；本阶段无此需求。
3. **聊天页作为主入口。** 现有确认交互可参考，但聊天消息不能成为Incident/Approval权威记录，持久证据和审批预览难以连续复核。

仅方案1进入待确认计划。审批仍服务端执行，浏览器从不直连MCP。

## 已验证事实、缺口与冲突

| 项目 | 当前权威依据 | 设计处理 |
| --- | --- | --- |
| 管理入口 | frontend/src/router.tsx的RequireAdmin；frontend/src/pages/admin/AdminLayout.tsx | /admin/incidents和/admin/incidents/:incidentId |
| 返回格式冲突 | frontend/src/services/api.ts只接受code=0；IncidentController返回"0"，ActionController/DiagnosisResponses返回"SUCCESS"（实施核对并经用户确认） | 现有契约保持；新增incidentApi.ts，复用storage会话但独立处理Incident envelope，不改全局客户端 |
| Incident列表缺失 | IncidentController只有按ID详情、transitions、timeline；IncidentMapper无全局列表 | 增加受admin保护的有界只读列表，字段和查询限定见contracts |
| 告警视图缺失 | 当前Incident详情仅聚合信息；AlertMapper.findDiagnosisMembers与IncidentSnapshotService已有按incident_id读取白名单字段 | 补充有界关联告警查询，仅已关联PROCESSED记录；复用现有安全清洗规则，不能按aggregationKey串入另一episode |
| 冻结参数缺失 | ActionQueryService已给目标/摘要，但没有parameters；ActionBindingV1唯一参数desiredState=READY或PAUSED | GET视图增加安全预览，来自committed parametersJson；未知格式预览不可用而不暴露原JSON |
| 回执摘要缺失 | ActionQueryService给状态/码/disposition/acceptedAt，无occurredAt/summary；ActionReceiptService限定desiredState/applied/proofCode | 增加白名单回执时间和摘要，不直接返回summaryJson |
| UNKNOWN | ActionQueryService.nextStep=RECONCILE，既有reconcile和manual-conclusion接口 | 首版只提示核对和手动刷新；不加写重试、对账命令或人工结论表单 |
| 旧前端指南冲突 | frontend/TESTING.md写8080和旧机器绝对路径；当前Vite/主应用为9090，context-path=/api/ragent | 当前配置优先；实施时修正文档，不沿用旧端口或绝对目录 |
| 前端测试边界 | package.json无测试框架；tests/agent-timeline.check.mjs以Node assert+独立TS编译验证纯逻辑；build仅Vite | 复用Node模式，另加tsc --noEmit；浏览器用现有computer-use能力操作隔离应用并归档证据，不新增E2E依赖 |

## 数据流与界面

列表→详情→关联告警/Timeline；详情内分为来源事实、诊断与证据、Action与审批、结果四区。明确显示本地模拟范围。引用按文本展示原文和位置，展开查看摘要；不渲染不可信HTML。

人员显式诊断→冻结快照/结果→查看当前资格→选择建议→服务端创建PENDING_APPROVAL→GET完整Action预览→人员批准/拒绝→服务端Worker/MCP→GET可信回执与Timeline。命令后的简化响应只提供身份/反馈，随后读取完整权威视图。

## 确定的设计默认值

- 列表默认20、最大100，以episodeStartAt/id降序游标分页；状态筛选仅已知生命周期。告警按id升序游标，默认20/最大100。
- 同一次网络未知诊断/选择保持原sourceRequestId；路由组件内保留待确认命令，不自动提交。页面刷新不自动重试，先恢复权威历史。
- 非终态读轮询间隔5秒，串行一次在途请求，隐藏时暂停，错误/终态停止；手动刷新独立触发。5秒是配置默认，不是实测响应时间承诺。
- 不新增数据库表或跨领域JOIN；查询与投影全部归incident。选择不接收浏览器工具/目标/参数。
- API类型ID为string；版本/sequenceNo只有Number.isSafeInteger时参与写请求或游标，否则明确阻止操作；历史证据仍展示。
- 当前物理分支仍feature/incident-platform；setup-plan输出BRANCH可能为功能目录标识，不能据此声称切换分支。

## 宪章复核

保留上游声明；持久化仍由Incident保证；所有模拟副作用走原审批/票据/幂等链；仅追加Spec体系产物；没有运行证据就不声明UI完成。前后两次设计检查无需宪章例外。

浏览器隔离fixture使用test-scope显式启动器（非默认Surefire测试），在人员或浏览器工具完成操作后关闭本轮临时进程/容器；默认回归不得等待人工操作。截图/前端显示与真实执行应用计数分开记录。
