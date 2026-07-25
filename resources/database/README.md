# PostgreSQL 数据库脚本

## 全量初始化

- `schema_pg.sql`：最新版本的完整表结构
- `init_data_pg.sql`：最新版本的初始化数据

新环境直接按顺序执行这两个文件，不需要再执行历史升级脚本

## 增量升级

`upgrades/` 按正式发布版本划分目录。当前正式版本是 v1.0，因此开发期间产生的 v1.1.0 数据库升级脚本统一放在 `upgrades/v1.1.0/`

目录内的脚本使用三位顺序号和变更含义命名。已有环境必须按顺序号逐个执行，不合并过程脚本：

1. `upgrades/v1.1.0/001_knowledge_chunk_log_duration.sql`
2. `upgrades/v1.1.0/002_message_thinking.sql`
3. `upgrades/v1.1.0/003_knowledge_vector_collection.sql`
4. `upgrades/v1.1.0/004_biz_change_log.sql`
5. `upgrades/v1.1.0/005_message_sources.sql`
6. `upgrades/v1.1.0/006_message_recommendation_context.sql`
7. `upgrades/v1.1.0/007_intent_multi_collections.sql`

后续正式版本在 `upgrades/` 下新建对应目录。已经对外提供或被其他开发者执行过的升级脚本保持不变，新的数据库变更继续追加独立脚本
