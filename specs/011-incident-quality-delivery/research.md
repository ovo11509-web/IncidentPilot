# 研究与决策

## 查询一致性

- 决策：Action get/list使用Workbench模式的只读REPEATABLE_READ TransactionTemplate；权限先校验。
- 理由：服务为final，不依赖未经确认的事务代理；快照避免旧Action与新Approval拼接，不取得业务写锁。
- 替代：读写锁增加竞争，跨请求缓存会陈旧，均不采用。

## 共同事实复用

- 列表一次安全Timeline读取；详情限定Incident与关联Action/Execution，回执同次读取复用。
- 诊断页一次guard/观察时刻，需要时一次factsDigest，Result有界批查；仍逐项判断适用性。
- 不声称消除全部逐动作查询，不顺带加生产索引或改变Action无分页契约。

## 事件检索

- 指纹包含source/service/environment/resource/rule，不含title/severity；锚点摘要必须标注“首条告警摘要”。
- summary仅来自已PROCESSED且incident_id一致的episodeAnchorAlert，缺失为null。
- 同一关联告警满足组合条件；q匹配Incident ID允许无关联告警，但有其他成员筛选时仍必须匹配成员。
- service/environment忽略大小写精确匹配，标题关键词字面包含，%/_不作通配符，输入绑定。

## 复盘及错误

- 目标复用安全投影；参数不可读不猜测。超时使用服务器deadlineExceeded，非浏览器推断。
- 进度是阶段及已完成数量，不承诺百分比。取消后旧回调不得改页面。
- 分类超时/传输/无效响应，仅受控状态和requestId；不保存原始正文、认证头或敏感查询值，不自动重发写操作。
- 此前偶发浏览器采集错误根因未知；本轮增加可定位性，只有新运行证据才称恢复。

## 本地交付

- 实际工具Docker29.4.3/Compose5.1.3、JDK21.0.12、Node20.19.5、pwsh7.6.5，四依赖镜像已缓存。
- 决策：新隔离Compose project与持久卷、外置随机凭据、真实java -jar、Node静态/流式代理；同一pwsh入口及Linux薄包装。
- 使用isolated抑制RocketMQ；local-demo提供明确拒绝Broker操作的端口装配（原Bean只在测试classpath）。普通远程MCP禁用，模型/解析器端点覆盖为禁用回环；主应用HTTP仅auth/incident及必要当前用户读入口，其余明确范围不可用。
- Init首次全量Schema、受限应用角色与模拟目标；不使用上游admin/admin。初始化成功marker最后写，部分失败允许受控恢复；再次初始化不覆盖账户或历史。
- Stop核对进程身份/instance/目录及Compose标签，只处理自有资源，保留持久卷。
- 不采用扩大临时fixture生命周期作为交付，不新增全应用镜像构建。
- 未验证Java17/Node22/Linux/托管CI/生产，本轮按实际条件记录。
