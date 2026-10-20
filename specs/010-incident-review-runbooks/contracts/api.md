# 界面与查询契约

- `/admin/incidents/:incidentId/review`：管理员报告，来自现有 Incident/Alert/Diagnosis/Action/Timeline GET，完整分页。无 POST。
- 导出 `IncidentPilot-incident-<id>.md`：仅完整且复核无已知变化时启用；仍是注明采集时间范围的非事务事实汇编。
- `/admin/runbooks`：手册目录；查询参数 runbookId/version/digest 定位引用版本。参数须全匹配，失败显示缺失/不匹配，不回退。
- `GET /incident/v1/runbooks`：登录且 admin，成功 code=0；data 为 Catalog。不可用/超限返回503 RUNBOOK_CORPUS_UNAVAILABLE；认证错误由既有框架处理。
- 历史引用点击只查目录，原 quote 继续从持久诊断结果显示。页面无编辑、执行或审批按钮。
