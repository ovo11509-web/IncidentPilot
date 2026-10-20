# 规格质量检查清单：Incident Runbook RAG 诊断

**目的**：检查待审设计完整性，不代表执行验收。

**创建日期**：2026-10-03

**功能**：[spec.md](../spec.md)

## 内容质量

- [x] 使用中文，业务需求与实现计划分开。
- [x] 用户故事有独立测试和 Given/When/Then。
- [x] 明确黄金路径价值、只提供建议与审批边界。
- [x] 规格模板必填节完整，无残留模板占位任务。

## 需求完整性

- [x] 需求有稳定 FR/SC 编号，可观察且范围明确。
- [x] 样例、元数据、快照、引用原文/位置/hash 均有具体提案。
- [x] 历史引用不依赖可变知识切片，全文与片段均可复算。
- [x] 正例覆盖和拒答类指标分开，真实检索与固定响应分开。
- [x] 指标有分母、零分母/缺例/失败规则，无虚构通过结果。
- [x] 写操作只经 Spec B，诊断 Action 来源关联有持久化/原子性要求。
- [x] 输入、提示注入、矛盾、过时、重启、权限和同请求冲突有验收。
- [ ] 用户确认五类模拟样例、首版本地检索/抽取式诊断。（TBD）
- [ ] 用户确认版本/快照/证据与 Action 来源长期决策。（TBD）
- [ ] 用户确认至少30例及拟议质量门槛。（TBD）

## 功能就绪

- [x] 当前代码/Schema 的事实依据、旧文档冲突与方案比较已记录。
- [x] plan/data-model/contracts/quickstart/tasks 对应同一草案。
- [ ] `$speckit-analyze` 正式实施前复核。
- [ ] 用户批准后归档已接受 ADR，开放实施门。
- [ ] 功能实现与真实隔离验收完成。

## 说明

因用户要求生成待确认的 spec/plan/tasks，带 TBD 的草案产物可同时供审阅；不能称为已获批 Spec Kit 基线或直接进入 implement。本轮静态检查不计算任何离线质量结果。

## 本轮实际验证记录（2026-10-03）

- `setup-plan.ps1 -Json`、`setup-tasks.ps1 -Json` 实际执行，解析使用项目中文覆盖模板；辅助脚本 BRANCH 字段表示功能上下文，实际 Git 分支仍为 feature/incident-platform。
- `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` 成功，发现 research/data-model/contracts/quickstart/tasks。
- PowerShell 静态检查：10 份文件、全部相对文档链接有效、16 个代码依据路径存在、T001～T036 连续且路径齐全，无模板占位或机器绝对路径。
- `git -c core.safecrlf=false diff --check` 成功；分支、HEAD 和空暂存区不变。
- 排除本轮修改的 docs/TODO.md、活动 .specify/feature.json 和新增本功能目录后，其余1,571个初始文件的路径/内容 SHA-256 聚合摘要一致；现有修改与未跟踪内容保留。TODO 仅追加阶段说明。
- 未执行 Java/数据库/MCP/真实模型测试、迁移或实际离线评测；未提交、推送或部署。正式 analyze、用户批准与实施验收仍待完成。
