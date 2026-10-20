# 数据模型与投影

不新增业务表或迁移，不改变状态机。

## Action观察

Action、Approval、Execution、Ticket、Receipt和安全Timeline共享同次已提交快照。投影沿既有白名单，不含票据秘密或任意summary_json。

## Diagnosis页

同页requests/results共享当前Incident观察时刻与需要的factsDigest。列表不构造snapshot/result正文，保留版本/摘要/发布失效/当前适用性校验。

## Incident摘要

新增summary对象或null，字段title/service/environment/severity来自已处理且关联该事件的锚点。游标仍为episodeStartAt/id。

## ReviewProgress

事件、告警、时间线、诊断列表、诊断详情、动作、复核阶段与分类型已采集数量；仅页面内状态。新增报告安全字段targetType/targetId/desiredState及deadlineAt/deadlineExceeded。

## 本地运行清单

ignored目录保存instanceId、所有权标记、制品来源、配置路径、进程身份与阶段；凭据不进入公开状态。卷仅属于新演示project，初始化成功标记最后写入。
