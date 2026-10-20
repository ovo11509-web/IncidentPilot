# 只读API与前端契约

## Incident列表

`GET /incident/v1/incidents` 保留管理员权限、code="0"、limit(1～100)、state、afterId及afterEpisodeStartAt。

新增可选q/service/environment/severity。q、service、environment trim/NFC后最多128字符、拒绝控制字符，空白等于未筛选。q匹配Incident ID或标题字面包含、忽略大小写。service/environment忽略大小写精确匹配。severity沿现有合法值，归一大写。非法输入沿既有错误响应422。

单一EXISTS成员分支必须PROCESSED且关联本Incident，并同时满足全部成员条件。q=ID无其他成员筛选时允许没有成员；启用任一其他成员条件时仍须存在匹配成员。筛选在cursor/limit前。

items保留原字段，新增 `summary:{title,service,environment,severity}|null`，只取已PROCESSED且归属匹配的episodeAnchorAlert。前端标注“首条告警摘要”，缺失为摘要不可用。

## Action/Diagnosis

路径/成功码/安全投影保持。Action一次响应内一致快照；Diagnosis页共享观察时刻。读接口不能替代写版本校验。

## 报告

可选进度回调取消后不能更新页面。导出仅选targetType、targetId和parametersAvailable时的desiredState；不可读明确提示。依据服务器deadlineExceeded明确超时、deadlineAt及返回详情下一步。

## 请求错误与路由

分类REQUEST_TIMEOUT/TRANSPORT_UNKNOWN/INVALID_ENVELOPE及既有业务码。仅固定读取/操作类别、状态、requestId，不输出原响应或敏感URL值。读提示手动刷新，写保留结果未知且不自动重试。

lazy在认证/角色守卫内渲染，登录与守卫静态；加载失败可见，不改变角色落点。
