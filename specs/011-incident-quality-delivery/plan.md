# 实施计划：事件查询、复盘体验与本地交付完善

**分支**：`feature/incident-platform` | **日期**：2026-10-11 | **规格**：[spec.md](spec.md)
**输入**：用户已批准六项建议，未授权提交、推送或生产部署。

## 摘要

修正读取一致性及共享事实重复查询，完善复盘和列表，按需加载页面；新增本机隔离持久化演示启动流程。既有 A～J 证据不替代本轮验证。

## 技术背景

**语言/版本**：Java 编译release17、当前JDK21；React18/TypeScript/Vite5；pwsh7/Node20。
**主要依赖**：现有Spring/MyBatis/JUnit/Testcontainers、Docker Compose、Node标准库。
**存储**：现有Schema、新本地持久卷与ignored `bootstrap/target/local-demo/` 配置。
**测试**：查询预算、真实两连接、自检、类型/lint/build及实际启动重启。
**目标平台**：本机Windows/Docker Linux Engine；其他平台未实测则保留边界。
**项目类型**：多模块Web应用及本地运行脚本。
**性能目标**：列表一次共同Timeline、诊断页一次需要的当前事实；无延迟或吞吐承诺。
**约束**：不跨请求缓存，不调用真实模型/资源，不删除卷或修改开发库。
**规模/范围**：沿用分页限额；Action无分页仍是扩大规模边界。

## 宪章检查

- 保留历史、许可与上游署名，不重命名Java包。
- 写入仍经原事务guard，Timeline只追加。
- local-demo仅启用protected simulation，普通远程工具/付费入口关闭；审批/幂等/UNKNOWN保持。
- 用户已授权，规格/计划/契约/任务均在Spec Kit目录。
- 并发/持久化有真实本地检查，未验证平台不宣称通过。

Phase0和Phase1均无宪章例外。

## 项目结构

### 当前功能的文档

`specs/011-incident-quality-delivery/` 保存spec、plan、research、data-model、contracts、quickstart、tasks和checklists。

### 源代码（仓库根目录）

- `incident/`：ActionQueryService与TimelineMapper只读快照/轻量分组/回执复用；DiagnosisQueryService与ResultMapper同页共享事实及有界批查；Incident查询/API摘要筛选。
- `frontend/`：复盘目标/超时/进度，错误定位，列表组合筛选，router lazy和标题；既有检查扩充。
- `resources/local-demo/`、`scripts/local-demo.*`、`scripts/test-local-demo.ps1`：四依赖Compose、真实JAR、Node静态及流式代理，Init/Build/Start/Status/Stop/Verify。
- `bootstrap/src/main/` 与 `mcp-server/src/main/`：local-demo profile及必要装配；关闭Broker端口明确拒绝而非假成功；HTTP允许auth/incident/必要当前用户读取，其余返回本地范围不可用。

**结构决策**：三名子任务分别独占incident、frontend、本地运行；根负责Spec/项目文档/整体验证。Maven同模块检查顺序运行避免target竞争。

## 实施顺序

先冻结契约与任务并执行一致性核对，再并行动作/诊断查询、复盘和运行脚本；随后对齐筛选与lazy，构建真实制品，实际启动/登录/Stop-Start验证；最后更新真实结果。

## 复杂度跟踪

无宪章例外。Node标准库服务生产dist，避免新服务依赖；新增local-demo装配解决测试专用Bean不能进入真实JAR的问题。
