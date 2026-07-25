-- ragent v1.6 -> v1.7 升级脚本
-- 一个知识库意图支持关联多个 Collection；旧的单 Collection 配置自动迁移为单元素数组

ALTER TABLE t_intent_node
    ADD COLUMN IF NOT EXISTS collection_names JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE t_intent_node
SET collection_names = jsonb_build_array(collection_name)
WHERE collection_name IS NOT NULL
  AND btrim(collection_name) <> ''
  AND collection_names = '[]'::jsonb;

COMMENT ON COLUMN t_intent_node.collection_names IS '关联的Collection名称列表；同一意图共享一个TopK总预算';
