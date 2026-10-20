# 本地专项验收

当前平台Java21/pwsh7/Docker Linux Engine。仅使用H入口，关闭真实外部门且使用自有临时资源。

```powershell
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Backend -CheckOnly
pwsh -NoProfile -File scripts/test-check-delivery.ps1
pwsh -NoProfile -File scripts/check-delivery.ps1 -Gate Backend
```

CheckOnly不等于业务通过。新Backend必须包含两新增类、全部固定case/种子和四组样本；检查原始JSON与报告的事实分母，safe仅为整体安全摘要。
结果位于新run的ignored target，原始日志/配置不得发布。真实执行结果只记入implementation-report，不以计划冒充通过。
