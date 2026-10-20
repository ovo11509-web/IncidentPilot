# 开发环境

## 必需工具

- Java 17，以根目录 `pom.xml` 为准。
- 通过 `mvnw.cmd`/`mvnw` 使用 Maven 3.9.11，无需全局安装 Maven。
- 与 `frontend/package-lock.json` 兼容的 Node.js 和 npm。
- Windows 环境使用 PowerShell 7 执行仓库内 Spec Kit 脚本。
- GitHub Spec Kit CLI 0.16.5，项目元数据记录在 `.specify/init-options.json`。
- Docker 为可选工具，用于运行本地 PostgreSQL、Redis、RocketMQ 及配套服务。

## 环境准备

1. 只复制准备运行的服务所需的配置示例。
2. 通过环境变量或被忽略的本地文件提供开发凭据。
3. 按照 `resources/database/README.md` 初始化 PostgreSQL。
4. 在 `frontend/` 目录执行 `npm ci` 安装前端依赖。
5. 先准备各进程声明的依赖，再启动主后端、MCP 服务和前端。

主后端默认监听 9090，MCP 服务默认监听 9099。端口或依赖发生变化时，以 `bootstrap/src/main/resources/` 和 `mcp-server/src/main/resources/` 下的实时配置为准。

## 配置位置

- 主服务：`bootstrap/src/main/resources/application.yaml`
- MCP 服务：`mcp-server/src/main/resources/application.yml`
- 前端：`frontend/.env` 和 Vite 配置
- 容器示例：`resources/docker/`

不得提交有效的数据库密码、访问令牌、模型密钥、对象存储凭据或追踪服务凭据。公开的默认值只能视为开发示例，不能用于生产环境。

## 可选环境

- `resources/docker/lightweight/` 提供仓库中最轻量的容器环境说明。
- `resources/docker/langfuse/` 和 `resources/docker/graphrag/` 是可选环境，不是 IncidentPilot MVP 的必需依赖。
- Milvus、Elasticsearch、Neo4j/LightRAG、Langfuse 和外部对象存储仅在功能 Spec 明确选用时启用。

## 环境验证

```powershell
java -version
.\mvnw.cmd -version
node --version
npm --version
specify --version
git status --short --branch
```

这些命令不会启动服务、修改远程状态或调用付费供应商。

## DEP-001隔离验收入口

`scripts/check-delivery.ps1`提供Smoke/Backend/Frontend/All和CheckOnly，详见 [交付与部署准备](delivery.md)。后端门禁必须具备本机Docker Linux Engine、Java21/keytool及Maven Wrapper3.9.11；POM编译release17。Frontend无需Docker，使用锁文件兼容的Node20以上/npm；CI定义Node22，当前本机实际Node20.19.5。pwsh7入口从脚本位置解析仓库，Windows选择mvnw.cmd/npm.cmd，Linux选择mvnw/npm；Linux分支仍需实际验收。

CheckOnly只查工具/安全配置/Docker，不启动业务；不能替代Smoke。入口关闭已知外部探针条件，恢复原环境，拒绝JVM/远程Docker/复用注入。日志/XML/临时凭据仅本地；只safe/summary.json允许作为CI制品。resources/docker仍是可选示例，常驻栈/生产部署准备TBD；不得把旧README生产措辞作为当前验证依据。
