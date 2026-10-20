# OBS-001 数据与身份模型

状态：待确认设计。无新表、字段、SQL迁移或历史回填。

## 现有持久身份

| 标识 | 分配/生命周期 | 新OBS用途 |
| --- | --- | --- |
| alertId / correlationId | 首次持久接收；相同Alert重试/重放保持 | 聚合前入口检索；原correlation值保持 |
| incidentId | episode形成后的聚合主键；每条Alert最多一个归属 | 聚合后所有主链操作的统一检索键 |
| diagnosisId | 诊断请求/结果身份；actor+incident+sourceRequestId幂等 | 实际检索/生成/复用操作关联 |
| sourceRequestId | 人员来源意图，作用域由原唯一约束决定 | 只用于业务幂等，不当HTTP尝试或OTel ID |
| actionId / approvalId | 冻结拟执行操作及一份审批 | 建议选择、审批、分发/回执关联 |
| executionId / dispatchRequestId | 一个Action的逻辑执行与稳定调度请求 | 首次/恢复/迟到回执共同身份 |
| executionKey | 业务幂等绑定、实际模拟应用唯一 | 保持原合同；遥测可凭executionId定位，无需输出键 |
| receiptId / eventId | 幂等回执和领域事件身份 | 双通道重复、迟到、冲突及Relay重投定位 |

关系：Alert.incidentId→Incident；Diagnosis.request.incidentId→Incident；Action.incidentId→Incident；来源link→Diagnosis/Action；Approval/Execution→Action；Receipt→Execution；Timeline/Outbox→Incident+关联实体。仍由incident独占Mapper；无跨领域FK/JOIN。

## 临时关联上下文

- HTTP requestId：服务端UUID（36字符），整个入站请求含错误分派只分配一次；每次网络回调重试都是新请求。业务正文requestId仍是原dispatchRequestId或人工结论意图。
- traceId/spanId：OTel有效32/16位小写十六进制，不用Snowflake/UUID转换，不与ragTraceId混用。
- 可选业务字段：incidentId、alertId、correlationId、diagnosisId、actionId、approvalId、executionId、dispatchRequestId、receiptId、eventId、sourceRequestId；只填该阶段实际知道且可信者。
- 操作字段：固定operation、phase、outcome、reasonCode、当前attempt、processingEpoch；attempt说明具体阶段语义，不能拿它推断应用次数。

上下文scope是短时快照，不持久化Span/Scope、票据明文或用户自由文本。不引入全局业务关联注册表、额外Trace关系表或常驻根span。

## 校验与可信来源

范围内HTTP requestId仅由服务端生成；业务数字ID保持字符串，符合现有[0-9]{1,20}且权限/事实资格成立。source/dispatch请求ID遵循原合同≤128字符；遥测只记录白名单格式值，非法值不回显。

MCP meta业务身份在GRANTED前不能写可信字段。HTTP认证的有效traceparent只提供追踪父关系，不能证明业务身份；消费/回执仍对持久记录逐项绑定。外部输入不允许覆盖incidentId/correlationId或MDC。

现有correlationId取值有多来源，明确不建立“同Incident全部correlation相等”约束。诊断snapshot/hash、Action binding、Receipt digest、Outbox payload都不写任何新增可观测字段。

## 状态与恢复

所有Incident/Action/Approval/Ticket/Receipt状态转换保持原定义。日志/span记录观察结果，不能触发转换。

跨进程重启继续从数据库恢复原Execution和dispatchRequestId；新Trace没有旧父节点时直接root。采集缺失只报告遥测缺失，不证明业务未执行。UNKNOWN必须由既有可信回执、只读对账或人工证据收敛。