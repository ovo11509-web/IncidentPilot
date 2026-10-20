-- PostgreSQL Schema for Ragent
-- Converted from MySQL schema_table.sql

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- User & Conversation Tables
-- ============================================

CREATE TABLE t_user (
    id           VARCHAR(20)  NOT NULL PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    avatar       VARCHAR(128),
    create_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username)
);
COMMENT ON TABLE t_user IS '系统用户表';
COMMENT ON COLUMN t_user.id IS '主键ID';
COMMENT ON COLUMN t_user.username IS '用户名，唯一';
COMMENT ON COLUMN t_user.password IS '密码';
COMMENT ON COLUMN t_user.role IS '角色：admin/user';
COMMENT ON COLUMN t_user.avatar IS '用户头像';
COMMENT ON COLUMN t_user.create_time IS '创建时间';
COMMENT ON COLUMN t_user.update_time IS '更新时间';
COMMENT ON COLUMN t_user.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_conversation (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    title           VARCHAR(128) NOT NULL,
    last_time       TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    DEFAULT 0,
    CONSTRAINT uk_conversation_user UNIQUE (conversation_id, user_id)
);
CREATE INDEX idx_user_time ON t_conversation (user_id, last_time);
COMMENT ON TABLE t_conversation IS '会话列表';
COMMENT ON COLUMN t_conversation.id IS '主键ID';
COMMENT ON COLUMN t_conversation.conversation_id IS '会话ID';
COMMENT ON COLUMN t_conversation.user_id IS '用户ID';
COMMENT ON COLUMN t_conversation.title IS '会话名称';
COMMENT ON COLUMN t_conversation.last_time IS '最近消息时间';
COMMENT ON COLUMN t_conversation.create_time IS '创建时间';
COMMENT ON COLUMN t_conversation.update_time IS '更新时间';
COMMENT ON COLUMN t_conversation.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_conversation_summary (
    id              VARCHAR(20)      NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    last_message_id VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    DEFAULT 0
);
CREATE INDEX idx_conv_user ON t_conversation_summary (conversation_id, user_id);
COMMENT ON TABLE t_conversation_summary IS '会话摘要表（与消息表分离存储）';

CREATE TABLE t_message (
    id                VARCHAR(20)      NOT NULL PRIMARY KEY,
    conversation_id   VARCHAR(20) NOT NULL,
    user_id           VARCHAR(20) NOT NULL,
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    thinking_content  TEXT,
    thinking_duration INTEGER,
    sources              JSONB,
    recommended_questions JSONB,
    retrieved_chunks  JSONB,
    reply_to_message_id VARCHAR(20),
    message_status    VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    DEFAULT 0
);
CREATE INDEX idx_conversation_user_time ON t_message (conversation_id, user_id, create_time);
CREATE INDEX idx_conversation_summary ON t_message (conversation_id, user_id, create_time);
COMMENT ON TABLE t_message IS '会话消息记录表';

CREATE TABLE t_message_feedback (
    id              VARCHAR(20)       NOT NULL PRIMARY KEY,
    message_id      VARCHAR(20)       NOT NULL,
    conversation_id VARCHAR(20)  NOT NULL,
    user_id         VARCHAR(20)  NOT NULL,
    vote            SMALLINT     NOT NULL,
    reason          VARCHAR(255),
    comment         VARCHAR(1024),
    create_time     TIMESTAMP  NOT NULL,
    update_time     TIMESTAMP  NOT NULL,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_msg_user UNIQUE (message_id, user_id)
);
CREATE INDEX idx_conversation_id ON t_message_feedback (conversation_id);
CREATE INDEX idx_user_id ON t_message_feedback (user_id);
COMMENT ON TABLE t_message_feedback IS '会话消息反馈表';

CREATE TABLE t_sample_question (
    id          VARCHAR(20)        NOT NULL PRIMARY KEY,
    title       VARCHAR(64),
    description VARCHAR(255),
    question    VARCHAR(255) NOT NULL,
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT      DEFAULT 0
);
CREATE INDEX idx_sample_question_deleted ON t_sample_question (deleted);
COMMENT ON TABLE t_sample_question IS '示例问题表';

-- ============================================
-- Business Change Audit Tables
-- ============================================

CREATE TABLE t_biz_change_log (
    id               VARCHAR(20)  NOT NULL PRIMARY KEY,
    biz_type         VARCHAR(64)  NOT NULL,
    biz_id           VARCHAR(64)  NOT NULL,
    operation_type   VARCHAR(32)  NOT NULL,
    action_desc      VARCHAR(512),
    before_snapshot  JSONB,
    after_snapshot   JSONB,
    change_diff      JSONB,
    operator_id      VARCHAR(64),
    operator_name    VARCHAR(128),
    operator_role    VARCHAR(64),
    success          BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message    TEXT,
    class_name       VARCHAR(255),
    method_name      VARCHAR(255),
    ip               VARCHAR(64),
    user_agent       VARCHAR(512),
    create_time      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_biz_change_log_biz ON t_biz_change_log (biz_type, biz_id);
CREATE INDEX idx_biz_change_log_time ON t_biz_change_log (create_time);
CREATE INDEX idx_biz_change_log_operator ON t_biz_change_log (operator_id);
COMMENT ON TABLE t_biz_change_log IS '业务数据变更审计日志表';
COMMENT ON COLUMN t_biz_change_log.biz_type IS '业务对象类型';
COMMENT ON COLUMN t_biz_change_log.biz_id IS '业务对象主键';
COMMENT ON COLUMN t_biz_change_log.operation_type IS '操作类型';
COMMENT ON COLUMN t_biz_change_log.action_desc IS '操作描述';
COMMENT ON COLUMN t_biz_change_log.before_snapshot IS '变更前快照';
COMMENT ON COLUMN t_biz_change_log.after_snapshot IS '变更后快照';
COMMENT ON COLUMN t_biz_change_log.change_diff IS '变更差异';
COMMENT ON COLUMN t_biz_change_log.operator_id IS '操作人ID';
COMMENT ON COLUMN t_biz_change_log.operator_name IS '操作人名称';
COMMENT ON COLUMN t_biz_change_log.operator_role IS '操作人角色';
COMMENT ON COLUMN t_biz_change_log.success IS '是否成功';
COMMENT ON COLUMN t_biz_change_log.error_message IS '失败信息';
COMMENT ON COLUMN t_biz_change_log.class_name IS '触发类名';
COMMENT ON COLUMN t_biz_change_log.method_name IS '触发方法名';
COMMENT ON COLUMN t_biz_change_log.ip IS '来源IP';
COMMENT ON COLUMN t_biz_change_log.user_agent IS 'User-Agent';
COMMENT ON COLUMN t_biz_change_log.create_time IS '创建时间';

-- ============================================
-- Knowledge Base Tables
-- ============================================

CREATE TABLE t_knowledge_base (
    id              VARCHAR(20)       NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(64)  NOT NULL,
    collection_name VARCHAR(64) NOT NULL,
    created_by      VARCHAR(20)  NOT NULL,
    updated_by      VARCHAR(20),
    create_time     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_collection_name UNIQUE (collection_name)
);
CREATE INDEX idx_kb_name ON t_knowledge_base (name);
COMMENT ON TABLE t_knowledge_base IS '知识库表';

CREATE TABLE t_knowledge_document (
    id               VARCHAR(20)        NOT NULL PRIMARY KEY,
    kb_id            VARCHAR(20)        NOT NULL,
    doc_name         VARCHAR(256)  NOT NULL,
    enabled          SMALLINT      NOT NULL DEFAULT 1,
    chunk_count      INTEGER       DEFAULT 0,
    file_url         VARCHAR(1024) NOT NULL,
    file_type        VARCHAR(16)   NOT NULL,
    mime_type        VARCHAR(128),
    file_size        BIGINT,
    process_mode     VARCHAR(16)   DEFAULT 'chunk',
    status           VARCHAR(16)   NOT NULL DEFAULT 'pending',
    source_type      VARCHAR(16),
    source_location  VARCHAR(1024),
    schedule_enabled SMALLINT,
    schedule_cron    VARCHAR(64),
    ingestion_spec   JSONB,
    pipeline_id      VARCHAR(20),
    created_by       VARCHAR(20)   NOT NULL,
    updated_by       VARCHAR(20),
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_kb_id ON t_knowledge_document (kb_id);
COMMENT ON TABLE t_knowledge_document IS '知识库文档表';

CREATE TABLE t_knowledge_chunk (
    id             VARCHAR(20)      NOT NULL PRIMARY KEY,
    kb_id          VARCHAR(20)      NOT NULL,
    doc_id         VARCHAR(20)      NOT NULL,
    chunk_index    INTEGER     NOT NULL,
    content        TEXT        NOT NULL,
    content_hash   VARCHAR(64),
    char_count     INTEGER,
    token_count    INTEGER,
    embedding_text TEXT,
    enabled        SMALLINT    NOT NULL DEFAULT 1,
    created_by     VARCHAR(20) NOT NULL,
    updated_by     VARCHAR(20),
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_doc_id ON t_knowledge_chunk (doc_id);
COMMENT ON TABLE t_knowledge_chunk IS '知识库文档分块表';

CREATE TABLE t_knowledge_document_chunk_log (
    id                 VARCHAR(20)      NOT NULL PRIMARY KEY,
    doc_id             VARCHAR(20)      NOT NULL,
    status             VARCHAR(16)      NOT NULL,
    process_mode       VARCHAR(16),
    parse_profile      VARCHAR(16),
    pipeline_id        VARCHAR(20),
    extract_duration   BIGINT,
    chunk_duration     BIGINT,
    embed_duration     BIGINT,
    persist_duration   BIGINT,
    total_duration     BIGINT,
    chunk_count        INTEGER,
    error_message      TEXT,
    start_time         TIMESTAMP,
    end_time           TIMESTAMP,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_doc_id_log ON t_knowledge_document_chunk_log (doc_id);
COMMENT ON TABLE t_knowledge_document_chunk_log IS '知识库文档分块日志表';

CREATE TABLE t_knowledge_document_schedule (
    id                VARCHAR(20)       NOT NULL PRIMARY KEY,
    doc_id            VARCHAR(20)       NOT NULL,
    kb_id             VARCHAR(20)       NOT NULL,
    cron_expr         VARCHAR(64),
    enabled           SMALLINT     DEFAULT 0,
    next_run_time     TIMESTAMP,
    last_run_time     TIMESTAMP,
    last_success_time TIMESTAMP,
    last_status       VARCHAR(16),
    last_error        VARCHAR(512),
    last_etag         VARCHAR(256),
    last_modified     VARCHAR(256),
    last_content_hash VARCHAR(128),
    lock_owner        VARCHAR(128),
    lock_until        TIMESTAMP,
    create_time       TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doc_id UNIQUE (doc_id)
);
CREATE INDEX idx_next_run ON t_knowledge_document_schedule (next_run_time);
CREATE INDEX idx_lock_until ON t_knowledge_document_schedule (lock_until);
COMMENT ON TABLE t_knowledge_document_schedule IS '知识库文档定时刷新任务表';

CREATE TABLE t_knowledge_document_schedule_exec (
    id            VARCHAR(20)       NOT NULL PRIMARY KEY,
    schedule_id   VARCHAR(20)       NOT NULL,
    doc_id        VARCHAR(20)       NOT NULL,
    kb_id         VARCHAR(20)       NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    message       VARCHAR(512),
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    file_name     VARCHAR(512),
    file_size     BIGINT,
    content_hash  VARCHAR(128),
    etag          VARCHAR(256),
    last_modified VARCHAR(256),
    create_time   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_schedule_time ON t_knowledge_document_schedule_exec (schedule_id, start_time);
CREATE INDEX idx_doc_id_exec ON t_knowledge_document_schedule_exec (doc_id);
COMMENT ON TABLE t_knowledge_document_schedule_exec IS '知识库文档定时刷新执行记录';

-- ============================================
-- RAG Intent & Query Tables
-- ============================================

CREATE TABLE t_intent_node (
    id                    VARCHAR(20)       NOT NULL PRIMARY KEY,
    kb_id                 VARCHAR(20),
    intent_code           VARCHAR(64)  NOT NULL,
    name                  VARCHAR(64)  NOT NULL,
    level                 SMALLINT     NOT NULL,
    parent_code           VARCHAR(64),
    description           VARCHAR(512),
    examples              TEXT,
    collection_name       VARCHAR(128),
    collection_names      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    top_k                 INTEGER,
    mcp_tool_id           VARCHAR(128),
    require_confirm       SMALLINT     NOT NULL DEFAULT 0,
    kind                  SMALLINT     NOT NULL DEFAULT 0,
    prompt_snippet        TEXT,
    prompt_template       TEXT,
    param_prompt_template TEXT,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    enabled               SMALLINT     NOT NULL DEFAULT 1,
    create_by             VARCHAR(20),
    update_by             VARCHAR(20),
    create_time           TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE t_intent_node IS '意图树节点配置表';

CREATE TABLE t_query_term_mapping (
    id          VARCHAR(20)       NOT NULL PRIMARY KEY,
    domain      VARCHAR(64),
    source_term VARCHAR(128) NOT NULL,
    target_term VARCHAR(128) NOT NULL,
    match_type  SMALLINT     NOT NULL DEFAULT 1,
    priority    INTEGER      NOT NULL DEFAULT 100,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    remark      VARCHAR(255),
    create_by   VARCHAR(20),
    update_by   VARCHAR(20),
    create_time TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_domain ON t_query_term_mapping (domain);
CREATE INDEX idx_source ON t_query_term_mapping (source_term);
COMMENT ON TABLE t_query_term_mapping IS '关键词归一化映射表';

CREATE TABLE t_rag_trace_run (
    id              VARCHAR(20)           NOT NULL PRIMARY KEY,
    trace_id        VARCHAR(64)      NOT NULL,
    trace_name      VARCHAR(128),
    entry_method    VARCHAR(256),
    conversation_id VARCHAR(20),
    task_id         VARCHAR(20),
    user_id         VARCHAR(20),
    status          VARCHAR(16)      NOT NULL DEFAULT 'RUNNING',
    error_message   VARCHAR(1000),
    start_time      TIMESTAMP(3),
    end_time        TIMESTAMP(3),
    duration_ms     BIGINT,
    extra_data      TEXT,
    create_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT         DEFAULT 0,
    CONSTRAINT uk_run_id UNIQUE (trace_id)
);
CREATE INDEX idx_task_id ON t_rag_trace_run (task_id);
CREATE INDEX idx_user_id_trace ON t_rag_trace_run (user_id);
COMMENT ON TABLE t_rag_trace_run IS 'Trace 运行记录表';

CREATE TABLE t_rag_trace_node (
    id             VARCHAR(20)           NOT NULL PRIMARY KEY,
    trace_id       VARCHAR(20)      NOT NULL,
    node_id        VARCHAR(20)      NOT NULL,
    parent_node_id VARCHAR(20),
    depth          INTEGER          DEFAULT 0,
    node_type      VARCHAR(16),
    node_name      VARCHAR(128),
    class_name     VARCHAR(256),
    method_name    VARCHAR(128),
    status         VARCHAR(16)      NOT NULL DEFAULT 'RUNNING',
    error_message  VARCHAR(1000),
    start_time     TIMESTAMP(3),
    end_time       TIMESTAMP(3),
    duration_ms    BIGINT,
    extra_data     TEXT,
    create_time    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT         DEFAULT 0,
    CONSTRAINT uk_run_node UNIQUE (trace_id, node_id)
);
COMMENT ON TABLE t_rag_trace_node IS 'Trace 节点记录表';

-- ============================================
-- Agent Profile Tables
-- ============================================

CREATE TABLE t_agent_profile (
    id          VARCHAR(20)  NOT NULL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(512),
    avatar      VARCHAR(32),
    builtin     SMALLINT     NOT NULL DEFAULT 0,
    active      SMALLINT     NOT NULL DEFAULT 0,
    create_by   VARCHAR(20),
    update_by   VARCHAR(20),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_name UNIQUE (name)
);
CREATE INDEX idx_agent_active ON t_agent_profile (active);
COMMENT ON TABLE t_agent_profile IS '智能体人设配置表';

CREATE TABLE t_agent_prompt (
    id          VARCHAR(20)  NOT NULL PRIMARY KEY,
    agent_id    VARCHAR(20)  NOT NULL,
    slot_key    VARCHAR(64)  NOT NULL,
    content     TEXT,
    create_by   VARCHAR(20),
    update_by   VARCHAR(20),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_slot UNIQUE (agent_id, slot_key)
);
CREATE INDEX idx_agent_prompt_agent ON t_agent_prompt (agent_id);
COMMENT ON TABLE t_agent_prompt IS '智能体提示词槽位表';

CREATE TABLE t_agent_skill (
    id          VARCHAR(20)  NOT NULL PRIMARY KEY,
    skill_code  VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(512) NOT NULL,
    content     TEXT         NOT NULL,
    tool_ids    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    create_by   VARCHAR(20),
    update_by   VARCHAR(20),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
-- 逻辑删除后 skill_code 应可重新占用，唯一性只约束未删除的行
CREATE UNIQUE INDEX uk_agent_skill_code ON t_agent_skill (skill_code) WHERE deleted = 0;
CREATE INDEX idx_agent_skill_enabled ON t_agent_skill (enabled, deleted);
COMMENT ON TABLE t_agent_skill IS '智能体技能表';

-- ============================================
-- Agent Engine Tables (v2 ReAct，与 workflow 会话两套分立)
-- ============================================

CREATE TABLE t_agent_conversation (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    title           VARCHAR(128) NOT NULL,
    last_time       TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    DEFAULT 0
);
-- 部分唯一索引：逻辑删的旧行不再占用唯一键，否则删除后同 ID 重开会话必撞约束
CREATE UNIQUE INDEX uk_agent_conversation_user ON t_agent_conversation (conversation_id, user_id) WHERE deleted = 0;
CREATE INDEX idx_agent_conv_user_time ON t_agent_conversation (user_id, last_time);
COMMENT ON TABLE t_agent_conversation IS 'Agent 会话列表';

CREATE TABLE t_agent_message (
    id                  VARCHAR(20) NOT NULL PRIMARY KEY,
    conversation_id     VARCHAR(20) NOT NULL,
    user_id             VARCHAR(20) NOT NULL,
    role                VARCHAR(16) NOT NULL,
    content             TEXT,
    thinking_content    TEXT,
    blocks              JSONB,
    reply_to_message_id VARCHAR(20),
    message_status      VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    duration_ms         BIGINT,
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT    DEFAULT 0
);
CREATE INDEX idx_agent_msg_conv ON t_agent_message (conversation_id, user_id, create_time);
COMMENT ON TABLE t_agent_message IS 'Agent 消息记录';

CREATE TABLE t_agent_state (
    user_id     VARCHAR(64) NOT NULL,
    session_id  VARCHAR(64) NOT NULL,
    state_key   VARCHAR(64) NOT NULL,
    payload     JSONB,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, session_id, state_key)
);
COMMENT ON TABLE t_agent_state IS 'AgentScope 工作状态存储，payload 为框架自有编码的不透明 JSON';

CREATE TABLE t_agent_context_compaction (
    id                   VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id              VARCHAR(20) NOT NULL,
    conversation_id      VARCHAR(20) NOT NULL,
    generation           INTEGER     NOT NULL,
    summary              TEXT,
    material_msg_count   INTEGER     NOT NULL,
    material_chars       INTEGER     NOT NULL,
    summary_chars        INTEGER     NOT NULL,
    context_chars_before INTEGER     NOT NULL,
    context_chars_after  INTEGER     NOT NULL,
    create_time          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_agent_compaction_conv ON t_agent_context_compaction (conversation_id, user_id, create_time);
COMMENT ON TABLE t_agent_context_compaction IS 'Agent 上下文压缩事件，追加型审计日志，应用侧无读路径';

CREATE TABLE t_agent_memory (
    id            VARCHAR(20)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(20)  NOT NULL,
    content       VARCHAR(500) NOT NULL,
    source_type   VARCHAR(16)  NOT NULL,
    invalid_at    TIMESTAMP,
    superseded_by VARCHAR(20),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 部分索引：读路径只查 ACTIVE，失效行不进索引
CREATE INDEX idx_agent_memory_active ON t_agent_memory (user_id) WHERE invalid_at IS NULL;
COMMENT ON TABLE t_agent_memory IS 'Agent长期记忆事实表';

CREATE TABLE t_agent_memory_extraction (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id         VARCHAR(20) NOT NULL,
    conversation_id VARCHAR(20) NOT NULL,
    from_message_id VARCHAR(20) NOT NULL,
    to_message_id   VARCHAR(20) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    trigger_type    VARCHAR(16) NOT NULL,
    decision_count  INTEGER     NOT NULL DEFAULT 0,
    attempt_count   INTEGER     NOT NULL DEFAULT 1,
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settle_time     TIMESTAMP
);
CREATE INDEX idx_agent_memory_extraction_conv ON t_agent_memory_extraction (user_id, conversation_id, to_message_id);
-- 部分唯一索引即分布式 claim：同一会话同时只允许一次在飞抽取
CREATE UNIQUE INDEX uk_agent_memory_extraction_processing
    ON t_agent_memory_extraction (user_id, conversation_id) WHERE status = 'PROCESSING';
COMMENT ON TABLE t_agent_memory_extraction IS 'Agent长期记忆抽取台账';

CREATE TABLE t_agent_memory_control (
    user_id     VARCHAR(20) NOT NULL PRIMARY KEY,
    revision    BIGINT      NOT NULL DEFAULT 0,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE t_agent_memory_control IS 'Agent长期记忆控制面';

-- ============================================
-- Ingestion Pipeline Tables
-- ============================================

CREATE TABLE t_ingestion_pipeline (
    id          VARCHAR(20)      NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_by  VARCHAR(20) DEFAULT '',
    updated_by  VARCHAR(20) DEFAULT '',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_name UNIQUE (name, deleted)
);
COMMENT ON TABLE t_ingestion_pipeline IS '摄取流水线表';

CREATE TABLE t_ingestion_pipeline_node (
    id             VARCHAR(20)      NOT NULL PRIMARY KEY,
    pipeline_id    VARCHAR(20)      NOT NULL,
    node_id        VARCHAR(20) NOT NULL,
    node_type      VARCHAR(16) NOT NULL,
    next_node_id   VARCHAR(20),
    settings_json  JSONB,
    condition_json JSONB,
    created_by     VARCHAR(20) DEFAULT '',
    updated_by     VARCHAR(20) DEFAULT '',
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_node UNIQUE (pipeline_id, node_id, deleted)
);
CREATE INDEX idx_ingestion_pipeline_node_pipeline ON t_ingestion_pipeline_node (pipeline_id);
COMMENT ON TABLE t_ingestion_pipeline_node IS '摄取流水线节点表';

CREATE TABLE t_ingestion_task (
    id               VARCHAR(20)      NOT NULL PRIMARY KEY,
    pipeline_id      VARCHAR(20)      NOT NULL,
    source_type      VARCHAR(20) NOT NULL,
    source_location  TEXT,
    source_file_name VARCHAR(255),
    status           VARCHAR(16) NOT NULL,
    chunk_count      INTEGER     DEFAULT 0,
    error_message    TEXT,
    logs_json        JSONB,
    metadata_json    JSONB,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_by       VARCHAR(20) DEFAULT '',
    updated_by       VARCHAR(20) DEFAULT '',
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_ingestion_task_pipeline ON t_ingestion_task (pipeline_id);
CREATE INDEX idx_ingestion_task_status ON t_ingestion_task (status);
COMMENT ON TABLE t_ingestion_task IS '摄取任务表';

CREATE TABLE t_ingestion_task_node (
    id            VARCHAR(20)      NOT NULL PRIMARY KEY,
    task_id       VARCHAR(20)      NOT NULL,
    pipeline_id   VARCHAR(20)      NOT NULL,
    node_id       VARCHAR(20) NOT NULL,
    node_type     VARCHAR(16) NOT NULL,
    node_order    INTEGER     NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL,
    duration_ms   BIGINT      NOT NULL DEFAULT 0,
    message       TEXT,
    error_message TEXT,
    output_json   TEXT,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_ingestion_task_node_task ON t_ingestion_task_node (task_id);
CREATE INDEX idx_ingestion_task_node_pipeline ON t_ingestion_task_node (pipeline_id);
CREATE INDEX idx_ingestion_task_node_status ON t_ingestion_task_node (status);
COMMENT ON TABLE t_ingestion_task_node IS '摄取任务节点表';

-- ============================================
-- Vector Storage Table (pgvector)
-- ============================================

CREATE TABLE t_knowledge_vector (
    id              VARCHAR(20) PRIMARY KEY,
    collection_name VARCHAR(64) NOT NULL,
    content         TEXT,
    metadata        JSONB,
    embedding       vector(1536)
);

CREATE INDEX idx_kv_collection_name ON t_knowledge_vector (collection_name);
CREATE INDEX idx_kv_metadata ON t_knowledge_vector USING gin(metadata);
CREATE INDEX idx_kv_embedding ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops);
COMMENT ON TABLE t_knowledge_vector IS '知识库向量存储表';
COMMENT ON COLUMN t_knowledge_vector.id IS '分块ID';
COMMENT ON COLUMN t_knowledge_vector.collection_name IS '知识库Collection';
COMMENT ON COLUMN t_knowledge_vector.content IS '分块文本内容';
COMMENT ON COLUMN t_knowledge_vector.metadata IS '元数据';
COMMENT ON COLUMN t_knowledge_vector.embedding IS '向量';

-- ============================================
-- Column Comments
-- ============================================

-- t_conversation_summary
COMMENT ON COLUMN t_conversation_summary.id IS '主键ID';
COMMENT ON COLUMN t_conversation_summary.conversation_id IS '会话ID';
COMMENT ON COLUMN t_conversation_summary.user_id IS '用户ID';
COMMENT ON COLUMN t_conversation_summary.last_message_id IS '摘要最后消息ID';
COMMENT ON COLUMN t_conversation_summary.content IS '会话摘要内容';
COMMENT ON COLUMN t_conversation_summary.create_time IS '创建时间';
COMMENT ON COLUMN t_conversation_summary.update_time IS '更新时间';
COMMENT ON COLUMN t_conversation_summary.deleted IS '是否删除 0：正常 1：删除';

-- t_message
COMMENT ON COLUMN t_message.id IS '主键ID';
COMMENT ON COLUMN t_message.conversation_id IS '会话ID';
COMMENT ON COLUMN t_message.user_id IS '用户ID';
COMMENT ON COLUMN t_message.role IS '角色：user/assistant';
COMMENT ON COLUMN t_message.content IS '消息内容';
COMMENT ON COLUMN t_message.thinking_content IS '深度思考内容';
COMMENT ON COLUMN t_message.thinking_duration IS '深度思考耗时（秒）';
COMMENT ON COLUMN t_message.sources IS '回答来源';
COMMENT ON COLUMN t_message.recommended_questions IS '推荐追问问题';
COMMENT ON COLUMN t_message.retrieved_chunks IS '推荐问题 grounding 片段';
COMMENT ON COLUMN t_message.reply_to_message_id IS '当前助手消息对应的用户消息ID';
COMMENT ON COLUMN t_message.message_status IS '消息结束状态：NORMAL=正常完成，INTERRUPTED=用户中断，REJECTED=限流拒绝';
COMMENT ON COLUMN t_message.create_time IS '创建时间';
COMMENT ON COLUMN t_message.update_time IS '更新时间';
COMMENT ON COLUMN t_message.deleted IS '是否删除 0：正常 1：删除';

-- t_message_feedback
COMMENT ON COLUMN t_message_feedback.id IS '主键ID';
COMMENT ON COLUMN t_message_feedback.message_id IS '消息ID';
COMMENT ON COLUMN t_message_feedback.conversation_id IS '会话ID';
COMMENT ON COLUMN t_message_feedback.user_id IS '用户ID';
COMMENT ON COLUMN t_message_feedback.vote IS '投票 1：赞 -1：踩';
COMMENT ON COLUMN t_message_feedback.reason IS '反馈原因';
COMMENT ON COLUMN t_message_feedback.comment IS '反馈评论';
COMMENT ON COLUMN t_message_feedback.create_time IS '创建时间';
COMMENT ON COLUMN t_message_feedback.update_time IS '更新时间';
COMMENT ON COLUMN t_message_feedback.deleted IS '是否删除 0：正常 1：删除';

-- t_sample_question
COMMENT ON COLUMN t_sample_question.id IS 'ID';
COMMENT ON COLUMN t_sample_question.title IS '展示标题';
COMMENT ON COLUMN t_sample_question.description IS '描述或提示';
COMMENT ON COLUMN t_sample_question.question IS '示例问题内容';
COMMENT ON COLUMN t_sample_question.create_time IS '创建时间';
COMMENT ON COLUMN t_sample_question.update_time IS '更新时间';
COMMENT ON COLUMN t_sample_question.deleted IS '是否删除 0：正常 1：删除';

-- t_knowledge_base
COMMENT ON COLUMN t_knowledge_base.id IS '主键 ID';
COMMENT ON COLUMN t_knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN t_knowledge_base.embedding_model IS '嵌入模型标识';
COMMENT ON COLUMN t_knowledge_base.collection_name IS 'Collection名称';
COMMENT ON COLUMN t_knowledge_base.created_by IS '创建人';
COMMENT ON COLUMN t_knowledge_base.updated_by IS '修改人';
COMMENT ON COLUMN t_knowledge_base.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_base.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_base.deleted IS '是否删除 0：正常 1：删除';

-- t_knowledge_document
COMMENT ON COLUMN t_knowledge_document.id IS 'ID';
COMMENT ON COLUMN t_knowledge_document.kb_id IS '知识库ID';
COMMENT ON COLUMN t_knowledge_document.doc_name IS '文档名称';
COMMENT ON COLUMN t_knowledge_document.enabled IS '是否启用 1：启用 0：禁用';
COMMENT ON COLUMN t_knowledge_document.chunk_count IS '分块数量';
COMMENT ON COLUMN t_knowledge_document.file_url IS '文件存储路径';
COMMENT ON COLUMN t_knowledge_document.file_type IS '文件类型';
COMMENT ON COLUMN t_knowledge_document.mime_type IS '真实MIME类型';
COMMENT ON COLUMN t_knowledge_document.file_size IS '文件大小（字节）';
COMMENT ON COLUMN t_knowledge_document.process_mode IS '处理模式：chunk/pipeline';
COMMENT ON COLUMN t_knowledge_document.status IS '状态：pending/running/success/failed';
COMMENT ON COLUMN t_knowledge_document.source_type IS '来源类型：file/url';
COMMENT ON COLUMN t_knowledge_document.source_location IS '来源地址';
COMMENT ON COLUMN t_knowledge_document.schedule_enabled IS '是否启用定时刷新';
COMMENT ON COLUMN t_knowledge_document.schedule_cron IS '定时表达式';
COMMENT ON COLUMN t_knowledge_document.ingestion_spec IS '文档级摄取配置：解析档位 + 分块预算';
COMMENT ON COLUMN t_knowledge_document.pipeline_id IS 'Pipeline ID';
COMMENT ON COLUMN t_knowledge_document.created_by IS '创建人';
COMMENT ON COLUMN t_knowledge_document.updated_by IS '修改人';
COMMENT ON COLUMN t_knowledge_document.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_document.deleted IS '是否删除 0：正常 1：删除';

-- t_knowledge_chunk
COMMENT ON COLUMN t_knowledge_chunk.id IS 'ID';
COMMENT ON COLUMN t_knowledge_chunk.kb_id IS '知识库ID';
COMMENT ON COLUMN t_knowledge_chunk.doc_id IS '文档ID';
COMMENT ON COLUMN t_knowledge_chunk.chunk_index IS '分块序号';
COMMENT ON COLUMN t_knowledge_chunk.content IS '分块内容';
COMMENT ON COLUMN t_knowledge_chunk.content_hash IS '内容哈希';
COMMENT ON COLUMN t_knowledge_chunk.char_count IS '字符数';
COMMENT ON COLUMN t_knowledge_chunk.token_count IS 'Token数';
COMMENT ON COLUMN t_knowledge_chunk.embedding_text IS '向量文本';
COMMENT ON COLUMN t_knowledge_chunk.enabled IS '是否启用';
COMMENT ON COLUMN t_knowledge_chunk.created_by IS '创建人';
COMMENT ON COLUMN t_knowledge_chunk.updated_by IS '修改人';
COMMENT ON COLUMN t_knowledge_chunk.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_chunk.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_chunk.deleted IS '是否删除 0：正常 1：删除';

-- t_knowledge_document_chunk_log
COMMENT ON COLUMN t_knowledge_document_chunk_log.id IS 'ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.doc_id IS '文档ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.status IS '状态';
COMMENT ON COLUMN t_knowledge_document_chunk_log.process_mode IS '处理模式';
COMMENT ON COLUMN t_knowledge_document_chunk_log.parse_profile IS '解析档位';
COMMENT ON COLUMN t_knowledge_document_chunk_log.pipeline_id IS 'Pipeline ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.extract_duration IS '提取耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_duration IS '分块耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.embed_duration IS '向量化耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.persist_duration IS 'DB持久化耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.total_duration IS '总耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_count IS '分块数量';
COMMENT ON COLUMN t_knowledge_document_chunk_log.error_message IS '错误信息';
COMMENT ON COLUMN t_knowledge_document_chunk_log.start_time IS '开始时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.end_time IS '结束时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.update_time IS '更新时间';

-- t_knowledge_document_schedule
COMMENT ON COLUMN t_knowledge_document_schedule.id IS 'ID';
COMMENT ON COLUMN t_knowledge_document_schedule.doc_id IS '文档ID';
COMMENT ON COLUMN t_knowledge_document_schedule.kb_id IS '知识库ID';
COMMENT ON COLUMN t_knowledge_document_schedule.cron_expr IS 'Cron表达式';
COMMENT ON COLUMN t_knowledge_document_schedule.enabled IS '是否启用';
COMMENT ON COLUMN t_knowledge_document_schedule.next_run_time IS '下次执行时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_run_time IS '上次执行时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_success_time IS '上次成功时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_status IS '上次状态';
COMMENT ON COLUMN t_knowledge_document_schedule.last_error IS '上次错误';
COMMENT ON COLUMN t_knowledge_document_schedule.last_etag IS '上次ETag';
COMMENT ON COLUMN t_knowledge_document_schedule.last_modified IS '上次修改时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_content_hash IS '上次内容哈希';
COMMENT ON COLUMN t_knowledge_document_schedule.lock_owner IS '锁持有者';
COMMENT ON COLUMN t_knowledge_document_schedule.lock_until IS '锁过期时间';
COMMENT ON COLUMN t_knowledge_document_schedule.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_schedule.update_time IS '更新时间';

-- t_knowledge_document_schedule_exec
COMMENT ON COLUMN t_knowledge_document_schedule_exec.id IS 'ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.schedule_id IS '调度ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.doc_id IS '文档ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.kb_id IS '知识库ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.status IS '状态';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.message IS '消息';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.start_time IS '开始时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.end_time IS '结束时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.file_name IS '文件名';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.file_size IS '文件大小';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.content_hash IS '内容哈希';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.etag IS 'ETag';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.last_modified IS '最后修改时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.update_time IS '更新时间';

-- t_intent_node
COMMENT ON COLUMN t_intent_node.id IS '自增主键';
COMMENT ON COLUMN t_intent_node.kb_id IS '知识库ID';
COMMENT ON COLUMN t_intent_node.intent_code IS '业务唯一标识';
COMMENT ON COLUMN t_intent_node.name IS '展示名称';
COMMENT ON COLUMN t_intent_node.level IS '层级 0：DOMAIN 1：CATEGORY 2：TOPIC';
COMMENT ON COLUMN t_intent_node.parent_code IS '父节点标识';
COMMENT ON COLUMN t_intent_node.description IS '语义描述';
COMMENT ON COLUMN t_intent_node.examples IS '示例问题';
COMMENT ON COLUMN t_intent_node.collection_name IS '兼容旧版本，后续删除';
COMMENT ON COLUMN t_intent_node.collection_names IS '知识库Collection集合';
COMMENT ON COLUMN t_intent_node.top_k IS '知识库检索TopK';
COMMENT ON COLUMN t_intent_node.mcp_tool_id IS 'MCP工具ID';
COMMENT ON COLUMN t_intent_node.require_confirm IS '执行前是否需要用户确认 1：需要 0：不需要';
COMMENT ON COLUMN t_intent_node.kind IS '类型 0：RAG知识库类 1：SYSTEM系统交互类';
COMMENT ON COLUMN t_intent_node.prompt_snippet IS '提示词片段';
COMMENT ON COLUMN t_intent_node.prompt_template IS '提示词模板';
COMMENT ON COLUMN t_intent_node.param_prompt_template IS '参数提取提示词模板（MCP模式专属）';
COMMENT ON COLUMN t_intent_node.sort_order IS '排序字段';
COMMENT ON COLUMN t_intent_node.enabled IS '是否启用 1：启用 0：禁用';
COMMENT ON COLUMN t_intent_node.create_by IS '创建人';
COMMENT ON COLUMN t_intent_node.update_by IS '修改人';
COMMENT ON COLUMN t_intent_node.create_time IS '创建时间';
COMMENT ON COLUMN t_intent_node.update_time IS '修改时间';
COMMENT ON COLUMN t_intent_node.deleted IS '是否删除 0：正常 1：删除';

-- t_query_term_mapping
COMMENT ON COLUMN t_query_term_mapping.id IS 'ID';
COMMENT ON COLUMN t_query_term_mapping.domain IS '领域';
COMMENT ON COLUMN t_query_term_mapping.source_term IS '源词';
COMMENT ON COLUMN t_query_term_mapping.target_term IS '目标词';
COMMENT ON COLUMN t_query_term_mapping.match_type IS '匹配类型 1：精确 2：模糊';
COMMENT ON COLUMN t_query_term_mapping.priority IS '优先级';
COMMENT ON COLUMN t_query_term_mapping.enabled IS '是否启用';
COMMENT ON COLUMN t_query_term_mapping.remark IS '备注';
COMMENT ON COLUMN t_query_term_mapping.create_by IS '创建人';
COMMENT ON COLUMN t_query_term_mapping.update_by IS '修改人';
COMMENT ON COLUMN t_query_term_mapping.create_time IS '创建时间';
COMMENT ON COLUMN t_query_term_mapping.update_time IS '修改时间';
COMMENT ON COLUMN t_query_term_mapping.deleted IS '是否删除 0：正常 1：删除';

-- t_rag_trace_run
COMMENT ON COLUMN t_rag_trace_run.id IS 'ID';
COMMENT ON COLUMN t_rag_trace_run.trace_id IS '全局链路ID';
COMMENT ON COLUMN t_rag_trace_run.trace_name IS '链路名称';
COMMENT ON COLUMN t_rag_trace_run.entry_method IS '入口方法';
COMMENT ON COLUMN t_rag_trace_run.conversation_id IS '会话ID';
COMMENT ON COLUMN t_rag_trace_run.task_id IS '任务ID';
COMMENT ON COLUMN t_rag_trace_run.user_id IS '用户ID';
COMMENT ON COLUMN t_rag_trace_run.status IS 'RUNNING/SUCCESS/ERROR';
COMMENT ON COLUMN t_rag_trace_run.error_message IS '错误信息';
COMMENT ON COLUMN t_rag_trace_run.start_time IS '开始时间';
COMMENT ON COLUMN t_rag_trace_run.end_time IS '结束时间';
COMMENT ON COLUMN t_rag_trace_run.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN t_rag_trace_run.extra_data IS '扩展字段(JSON)';
COMMENT ON COLUMN t_rag_trace_run.create_time IS '创建时间';
COMMENT ON COLUMN t_rag_trace_run.update_time IS '更新时间';
COMMENT ON COLUMN t_rag_trace_run.deleted IS '是否删除';

-- t_rag_trace_node
COMMENT ON COLUMN t_rag_trace_node.id IS 'ID';
COMMENT ON COLUMN t_rag_trace_node.trace_id IS '所属链路ID';
COMMENT ON COLUMN t_rag_trace_node.node_id IS '节点ID';
COMMENT ON COLUMN t_rag_trace_node.parent_node_id IS '父节点ID';
COMMENT ON COLUMN t_rag_trace_node.depth IS '节点深度';
COMMENT ON COLUMN t_rag_trace_node.node_type IS '节点类型';
COMMENT ON COLUMN t_rag_trace_node.node_name IS '节点名称';
COMMENT ON COLUMN t_rag_trace_node.class_name IS '类名';
COMMENT ON COLUMN t_rag_trace_node.method_name IS '方法名';
COMMENT ON COLUMN t_rag_trace_node.status IS 'RUNNING/SUCCESS/ERROR';
COMMENT ON COLUMN t_rag_trace_node.error_message IS '错误信息';
COMMENT ON COLUMN t_rag_trace_node.start_time IS '开始时间';
COMMENT ON COLUMN t_rag_trace_node.end_time IS '结束时间';
COMMENT ON COLUMN t_rag_trace_node.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN t_rag_trace_node.extra_data IS '扩展字段(JSON)';
COMMENT ON COLUMN t_rag_trace_node.create_time IS '创建时间';
COMMENT ON COLUMN t_rag_trace_node.update_time IS '更新时间';
COMMENT ON COLUMN t_rag_trace_node.deleted IS '是否删除';

-- t_ingestion_pipeline
COMMENT ON COLUMN t_ingestion_pipeline.id IS 'ID';
COMMENT ON COLUMN t_ingestion_pipeline.name IS '流水线名称';
COMMENT ON COLUMN t_ingestion_pipeline.description IS '流水线描述';
COMMENT ON COLUMN t_ingestion_pipeline.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_pipeline.updated_by IS '更新人';
COMMENT ON COLUMN t_ingestion_pipeline.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_pipeline.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_pipeline.deleted IS '是否删除 0：正常 1：删除';

-- t_ingestion_pipeline_node
COMMENT ON COLUMN t_ingestion_pipeline_node.id IS 'ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.pipeline_id IS '流水线ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_id IS '节点标识(同一流水线内唯一)';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_pipeline_node.next_node_id IS '下一个节点ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.settings_json IS '节点配置JSON';
COMMENT ON COLUMN t_ingestion_pipeline_node.condition_json IS '条件JSON';
COMMENT ON COLUMN t_ingestion_pipeline_node.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_pipeline_node.updated_by IS '更新人';
COMMENT ON COLUMN t_ingestion_pipeline_node.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_pipeline_node.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_pipeline_node.deleted IS '是否删除 0：正常 1：删除';

-- t_ingestion_task
COMMENT ON COLUMN t_ingestion_task.id IS 'ID';
COMMENT ON COLUMN t_ingestion_task.pipeline_id IS '流水线ID';
COMMENT ON COLUMN t_ingestion_task.source_type IS '来源类型';
COMMENT ON COLUMN t_ingestion_task.source_location IS '来源地址或URL';
COMMENT ON COLUMN t_ingestion_task.source_file_name IS '原始文件名';
COMMENT ON COLUMN t_ingestion_task.status IS '任务状态';
COMMENT ON COLUMN t_ingestion_task.chunk_count IS '分块数量';
COMMENT ON COLUMN t_ingestion_task.error_message IS '错误信息';
COMMENT ON COLUMN t_ingestion_task.logs_json IS '节点日志JSON';
COMMENT ON COLUMN t_ingestion_task.metadata_json IS '扩展元数据JSON';
COMMENT ON COLUMN t_ingestion_task.started_at IS '开始时间';
COMMENT ON COLUMN t_ingestion_task.completed_at IS '完成时间';
COMMENT ON COLUMN t_ingestion_task.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_task.updated_by IS '更新人';
COMMENT ON COLUMN t_ingestion_task.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_task.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_task.deleted IS '是否删除 0：正常 1：删除';

-- t_ingestion_task_node
COMMENT ON COLUMN t_ingestion_task_node.id IS 'ID';
COMMENT ON COLUMN t_ingestion_task_node.task_id IS '任务ID';
COMMENT ON COLUMN t_ingestion_task_node.pipeline_id IS '流水线ID';
COMMENT ON COLUMN t_ingestion_task_node.node_id IS '节点标识';
COMMENT ON COLUMN t_ingestion_task_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_task_node.node_order IS '节点顺序';
COMMENT ON COLUMN t_ingestion_task_node.status IS '节点状态';
COMMENT ON COLUMN t_ingestion_task_node.duration_ms IS '执行耗时(毫秒)';
COMMENT ON COLUMN t_ingestion_task_node.message IS '节点消息';
COMMENT ON COLUMN t_ingestion_task_node.error_message IS '错误信息';
COMMENT ON COLUMN t_ingestion_task_node.output_json IS '节点输出JSON(全量)';
COMMENT ON COLUMN t_ingestion_task_node.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_task_node.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_task_node.deleted IS '是否删除 0：正常 1：删除';

-- t_agent_profile
COMMENT ON COLUMN t_agent_profile.id IS '主键ID';
COMMENT ON COLUMN t_agent_profile.name IS '智能体名称，唯一';
COMMENT ON COLUMN t_agent_profile.description IS '智能体描述';
COMMENT ON COLUMN t_agent_profile.avatar IS '头像预设标识，取值由前端预设表定义，认不出时按 id 哈希兜底';
COMMENT ON COLUMN t_agent_profile.builtin IS '是否内置 0：否 1：是。内置智能体不可编辑不可删除，是所有空槽位的回落终点';
COMMENT ON COLUMN t_agent_profile.active IS '是否激活 0：否 1：是。全局仅允许一条为 1';
COMMENT ON COLUMN t_agent_profile.create_by IS '创建人';
COMMENT ON COLUMN t_agent_profile.update_by IS '更新人';
COMMENT ON COLUMN t_agent_profile.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_profile.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_profile.deleted IS '是否删除 0：正常 1：删除';

-- t_agent_prompt
COMMENT ON COLUMN t_agent_prompt.id IS '主键ID';
COMMENT ON COLUMN t_agent_prompt.agent_id IS '所属智能体ID';
COMMENT ON COLUMN t_agent_prompt.slot_key IS '槽位标识，见 AgentPromptSlot 枚举';
COMMENT ON COLUMN t_agent_prompt.content IS '提示词全文，空白视为未配置并回落内置智能体';
COMMENT ON COLUMN t_agent_prompt.create_by IS '创建人';
COMMENT ON COLUMN t_agent_prompt.update_by IS '更新人';
COMMENT ON COLUMN t_agent_prompt.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_prompt.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_prompt.deleted IS '是否删除 0：正常 1：删除';

-- t_agent_skill
COMMENT ON COLUMN t_agent_skill.id IS '主键ID';
COMMENT ON COLUMN t_agent_skill.skill_code IS '技能标识，模型按此名加载正文';
COMMENT ON COLUMN t_agent_skill.name IS '技能展示名';
COMMENT ON COLUMN t_agent_skill.description IS '技能适用场景，随清单一起交给模型判断是否加载';
COMMENT ON COLUMN t_agent_skill.content IS '技能正文 Markdown，模型加载后按此执行';
COMMENT ON COLUMN t_agent_skill.tool_ids IS '加载技能后才解锁的 MCP 工具 ID，取自意图树 MCP 节点';
COMMENT ON COLUMN t_agent_skill.sort_order IS '排序，越小越靠前';
COMMENT ON COLUMN t_agent_skill.enabled IS '是否启用 0：停用 1：启用';
COMMENT ON COLUMN t_agent_skill.create_by IS '创建人';
COMMENT ON COLUMN t_agent_skill.update_by IS '更新人';
COMMENT ON COLUMN t_agent_skill.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_skill.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_skill.deleted IS '是否删除 0：正常 1：删除';

-- t_agent_conversation
COMMENT ON COLUMN t_agent_conversation.id IS '主键ID';
COMMENT ON COLUMN t_agent_conversation.conversation_id IS '会话ID';
COMMENT ON COLUMN t_agent_conversation.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_conversation.title IS '会话标题';
COMMENT ON COLUMN t_agent_conversation.last_time IS '最后活动时间';
COMMENT ON COLUMN t_agent_conversation.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_conversation.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_conversation.deleted IS '是否删除 0：正常 1：删除';

-- t_agent_message
COMMENT ON COLUMN t_agent_message.id IS '主键ID';
COMMENT ON COLUMN t_agent_message.conversation_id IS '会话ID';
COMMENT ON COLUMN t_agent_message.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_message.role IS '角色 user：用户 assistant：助手';
COMMENT ON COLUMN t_agent_message.content IS '消息正文';
COMMENT ON COLUMN t_agent_message.thinking_content IS '思考内容';
COMMENT ON COLUMN t_agent_message.blocks IS '运行轨迹块（reasoning/answer/tool 有序序列），回放还原时间线';
COMMENT ON COLUMN t_agent_message.reply_to_message_id IS '回复的用户消息ID';
COMMENT ON COLUMN t_agent_message.message_status IS '消息终态 NORMAL：正常 INTERRUPTED：用户中断';
COMMENT ON COLUMN t_agent_message.duration_ms IS '本轮 run 的服务端耗时（毫秒），仅 assistant 有值';
COMMENT ON COLUMN t_agent_message.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_message.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_message.deleted IS '是否删除 0：正常 1：删除';

-- t_agent_state
COMMENT ON COLUMN t_agent_state.user_id IS '用户ID，匿名会话为 __anon__';
COMMENT ON COLUMN t_agent_state.session_id IS '会话ID，即 AgentScope 的 sessionId';
COMMENT ON COLUMN t_agent_state.state_key IS '状态键，AgentScope 侧固定传 agent_state';
COMMENT ON COLUMN t_agent_state.payload IS '框架自有编码的状态 JSON，业务侧不解析';
COMMENT ON COLUMN t_agent_state.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_state.update_time IS '更新时间';

-- t_agent_context_compaction
COMMENT ON COLUMN t_agent_context_compaction.id IS '主键ID';
COMMENT ON COLUMN t_agent_context_compaction.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_context_compaction.conversation_id IS '会话ID，即 AgentScope 的 sessionId';
COMMENT ON COLUMN t_agent_context_compaction.generation IS '同一会话内的第几代摘要，从 1 起';
COMMENT ON COLUMN t_agent_context_compaction.summary IS '本代摘要正文，回填进上下文的那一份';
COMMENT ON COLUMN t_agent_context_compaction.material_msg_count IS '被换出的原文消息条数';
COMMENT ON COLUMN t_agent_context_compaction.material_chars IS '被换出的原文字符数';
COMMENT ON COLUMN t_agent_context_compaction.summary_chars IS '摘要正文字符数';
COMMENT ON COLUMN t_agent_context_compaction.context_chars_before IS '压缩前上下文总字符数';
COMMENT ON COLUMN t_agent_context_compaction.context_chars_after IS '压缩后上下文总字符数';
COMMENT ON COLUMN t_agent_context_compaction.create_time IS '创建时间';

-- t_agent_memory
COMMENT ON COLUMN t_agent_memory.id IS '主键ID';
COMMENT ON COLUMN t_agent_memory.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_memory.content IS '记忆正文';
COMMENT ON COLUMN t_agent_memory.source_type IS '写入来源：FLUSH/BACKGROUND/CONSOLIDATION';
COMMENT ON COLUMN t_agent_memory.invalid_at IS '失效时刻，NULL 即 ACTIVE';
COMMENT ON COLUMN t_agent_memory.superseded_by IS '取代者ID，撤回行留空';
COMMENT ON COLUMN t_agent_memory.create_time IS '创建时间';

-- t_agent_memory_extraction
COMMENT ON COLUMN t_agent_memory_extraction.id IS '主键ID';
COMMENT ON COLUMN t_agent_memory_extraction.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_memory_extraction.conversation_id IS '会话ID，即 AgentScope 的 sessionId';
COMMENT ON COLUMN t_agent_memory_extraction.from_message_id IS '本批首条用户消息ID';
COMMENT ON COLUMN t_agent_memory_extraction.to_message_id IS '本批末条用户消息ID，水位取已结束抽取的最大值';
COMMENT ON COLUMN t_agent_memory_extraction.status IS '抽取状态：PROCESSING/WRITTEN/NOOP/DROPPED/CONFLICT';
COMMENT ON COLUMN t_agent_memory_extraction.trigger_type IS '触发方：FLUSH/BACKGROUND';
COMMENT ON COLUMN t_agent_memory_extraction.decision_count IS '实际落库的决策条数';
COMMENT ON COLUMN t_agent_memory_extraction.attempt_count IS '第几次尝试，达上限记 DROPPED';
COMMENT ON COLUMN t_agent_memory_extraction.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_memory_extraction.settle_time IS '抽取结束时刻，非终态为空';

-- t_agent_memory_control
COMMENT ON COLUMN t_agent_memory_control.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_memory_control.revision IS '记忆集版本号，提交期与水位一同双校验';
COMMENT ON COLUMN t_agent_memory_control.create_time IS '建行时刻，兼作抽取下界：更早的历史消息不倒灌';
COMMENT ON COLUMN t_agent_memory_control.update_time IS '更新时间';

-- Spec A: reliable Alert to Incident

-- Spec A: reliable Alert to Incident. Run as the schema owner/migration role.
-- ragent_app has no password here; provision its credential outside the repository.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ragent_app') THEN
        CREATE ROLE ragent_app LOGIN;
    END IF;
END
$$;

CREATE TABLE t_incident (
    id VARCHAR(20) PRIMARY KEY,
    aggregation_key VARCHAR(96) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    aggregation_rule_version VARCHAR(32) NOT NULL,
    aggregation_window_seconds INTEGER NOT NULL CHECK (aggregation_window_seconds > 0),
    episode_start_at TIMESTAMPTZ NOT NULL,
    episode_anchor_alert_id VARCHAR(20) NOT NULL,
    first_alert_received_at TIMESTAMPTZ NOT NULL,
    last_alert_received_at TIMESTAMPTZ NOT NULL,
    alert_count INTEGER NOT NULL CHECK (alert_count > 0),
    lifecycle_state VARCHAR(24) NOT NULL CHECK (lifecycle_state IN ('OPEN', 'ACKNOWLEDGED', 'MITIGATING', 'RESOLVED', 'CLOSED')),
    lifecycle_sealed_at TIMESTAMPTZ,
    aggregation_slot_active BOOLEAN NOT NULL,
    born_sealed BOOLEAN NOT NULL,
    created_event_id VARCHAR(20) NOT NULL UNIQUE,
    timeline_seq BIGINT NOT NULL CHECK (timeline_seq >= 1),
    version BIGINT NOT NULL CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_incident_episode UNIQUE (aggregation_key, episode_start_at, episode_anchor_alert_id),
    CONSTRAINT ck_incident_time_order CHECK (first_alert_received_at <= last_alert_received_at),
    CONSTRAINT ck_incident_seal CHECK ((lifecycle_state IN ('RESOLVED', 'CLOSED')) = (lifecycle_sealed_at IS NOT NULL)
        OR (lifecycle_state IN ('OPEN', 'ACKNOWLEDGED', 'MITIGATING') AND lifecycle_sealed_at IS NOT NULL)),
    CONSTRAINT ck_incident_slot CHECK (NOT aggregation_slot_active OR (NOT born_sealed AND lifecycle_sealed_at IS NULL))
);
CREATE UNIQUE INDEX uk_incident_active_slot ON t_incident (aggregation_key) WHERE aggregation_slot_active;
CREATE INDEX idx_incident_state ON t_incident (lifecycle_state);
CREATE INDEX idx_incident_updated_at ON t_incident (updated_at);

CREATE TABLE t_incident_alert (
    id VARCHAR(20) PRIMARY KEY,
    source_id VARCHAR(128) NOT NULL,
    external_alert_id VARCHAR(128) NOT NULL,
    protocol_version SMALLINT NOT NULL CHECK (protocol_version = 1),
    payload_json JSONB NOT NULL,
    payload_digest CHAR(64) NOT NULL,
    event_type VARCHAR(16) NOT NULL CHECK (event_type = 'FIRING'),
    occurred_at TIMESTAMPTZ NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    environment VARCHAR(64) NOT NULL,
    service VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(256) NOT NULL,
    summary VARCHAR(2048),
    labels_json JSONB NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    aggregation_key VARCHAR(96) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(20) NOT NULL,
    processing_status VARCHAR(16) NOT NULL CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED')),
    processing_epoch INTEGER NOT NULL DEFAULT 0 CHECK (processing_epoch >= 0),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    next_attempt_at TIMESTAMPTZ,
    last_failure_code VARCHAR(64),
    last_failure_summary VARCHAR(512),
    failed_at TIMESTAMPTZ,
    incident_id VARCHAR(20) REFERENCES t_incident (id),
    attachment_event_id VARCHAR(20) UNIQUE,
    attached_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_incident_alert_identity UNIQUE (source_id, external_alert_id),
    CONSTRAINT ck_alert_attachment CHECK ((incident_id IS NULL AND attachment_event_id IS NULL AND attached_at IS NULL)
        OR (incident_id IS NOT NULL AND attachment_event_id IS NOT NULL AND attached_at IS NOT NULL)),
    CONSTRAINT ck_alert_processing CHECK (
        (processing_status = 'RECEIVED' AND next_attempt_at IS NOT NULL AND failed_at IS NULL)
        OR (processing_status = 'PROCESSED' AND next_attempt_at IS NULL AND incident_id IS NOT NULL AND failed_at IS NULL)
        OR (processing_status = 'FAILED' AND next_attempt_at IS NULL AND last_failure_code IS NOT NULL AND failed_at IS NOT NULL))
);
ALTER TABLE t_incident ADD CONSTRAINT fk_incident_anchor_alert
    FOREIGN KEY (episode_anchor_alert_id) REFERENCES t_incident_alert (id) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX idx_alert_due ON t_incident_alert (next_attempt_at, received_at, id) WHERE processing_status = 'RECEIVED';
CREATE INDEX idx_alert_episode ON t_incident_alert (aggregation_key, received_at, id);
CREATE INDEX idx_alert_incident ON t_incident_alert (incident_id);
CREATE INDEX idx_alert_correlation ON t_incident_alert (correlation_id);

CREATE TABLE t_incident_alert_audit (
    id VARCHAR(20) PRIMARY KEY,
    alert_id VARCHAR(20) NOT NULL REFERENCES t_incident_alert (id),
    fact_key VARCHAR(160) NOT NULL UNIQUE,
    fact_type VARCHAR(40) NOT NULL CHECK (fact_type IN ('ALERT_RECEIVED', 'ALERT_TERMINAL_FAILED', 'ALERT_REPLAY_ACCEPTED', 'ALERT_REPLAY_REJECTED', 'ALERT_RECOVERED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_type VARCHAR(24) NOT NULL CHECK (actor_type IN ('SOURCE', 'SYSTEM', 'USER')),
    actor_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(40),
    correlation_id VARCHAR(20) NOT NULL,
    processing_epoch INTEGER NOT NULL CHECK (processing_epoch >= 0),
    reason_code VARCHAR(64),
    reason_summary VARCHAR(512),
    failure_code VARCHAR(64),
    attempt_count_snapshot INTEGER CHECK (attempt_count_snapshot >= 0),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_alert_audit_alert ON t_incident_alert_audit (alert_id, occurred_at);

CREATE TABLE t_incident_timeline (
    id VARCHAR(20) PRIMARY KEY,
    incident_id VARCHAR(20) NOT NULL REFERENCES t_incident (id),
    sequence_no BIGINT NOT NULL CHECK (sequence_no >= 1),
    business_event_key VARCHAR(180) NOT NULL UNIQUE,
    event_type VARCHAR(48) NOT NULL CHECK (event_type IN ('INCIDENT_CREATED', 'ALERT_ATTACHED', 'LATE_ALERT_ATTACHED_AFTER_SEAL', 'INCIDENT_AGGREGATION_SEALED', 'INCIDENT_STATUS_CHANGED')),
    actor_type VARCHAR(24) NOT NULL CHECK (actor_type IN ('SOURCE', 'SYSTEM', 'USER', 'SERVICE')),
    actor_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(20) NOT NULL,
    related_entity_type VARCHAR(24) CHECK (related_entity_type IN ('ALERT', 'INCIDENT')),
    related_entity_id VARCHAR(20),
    summary_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_incident_timeline_sequence UNIQUE (incident_id, sequence_no),
    CONSTRAINT ck_timeline_related CHECK ((related_entity_type IS NULL) = (related_entity_id IS NULL))
);

CREATE TABLE t_incident_outbox (
    event_id VARCHAR(20) PRIMARY KEY,
    business_event_key VARCHAR(180) NOT NULL UNIQUE,
    incident_id VARCHAR(20) NOT NULL REFERENCES t_incident (id),
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version >= 0),
    event_type VARCHAR(64) NOT NULL CHECK (event_type IN ('incident.created', 'incident.alert-attached')),
    schema_version SMALLINT NOT NULL CHECK (schema_version = 1),
    correlation_id VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload_json JSONB NOT NULL,
    publish_status VARCHAR(16) NOT NULL CHECK (publish_status IN ('PENDING', 'PUBLISHED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ,
    claim_token VARCHAR(64),
    claim_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    broker_message_id VARCHAR(128),
    last_error_code VARCHAR(64),
    last_error_summary VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_outbox_claim CHECK ((claim_token IS NULL) = (claim_until IS NULL)),
    CONSTRAINT ck_outbox_status CHECK ((publish_status = 'PENDING' AND next_attempt_at IS NOT NULL AND published_at IS NULL)
        OR (publish_status = 'PUBLISHED' AND next_attempt_at IS NULL AND claim_token IS NULL AND published_at IS NOT NULL))
);
CREATE INDEX idx_outbox_due ON t_incident_outbox (next_attempt_at, created_at, event_id) WHERE publish_status = 'PENDING';
CREATE INDEX idx_outbox_published ON t_incident_outbox (published_at) WHERE publish_status = 'PUBLISHED';

GRANT USAGE ON SCHEMA public TO ragent_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ragent_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ragent_app;
REVOKE UPDATE, DELETE ON t_incident_alert_audit, t_incident_timeline FROM ragent_app;
REVOKE UPDATE, DELETE ON t_incident_alert_audit, t_incident_timeline FROM PUBLIC;

-- Spec B: approval to safe simulation. Run as the schema owner after Spec A.

CREATE TABLE t_incident_action (
    id VARCHAR(20) PRIMARY KEY,
    incident_id VARCHAR(20) NOT NULL REFERENCES t_incident (id),
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('USER', 'AGENT')),
    source_request_id VARCHAR(128) NOT NULL,
    proposed_by_type VARCHAR(16) NOT NULL CHECK (proposed_by_type IN ('USER', 'AGENT')),
    proposed_by_id VARCHAR(128) NOT NULL,
    original_action_id VARCHAR(20) REFERENCES t_incident_action (id),
    tool_name VARCHAR(64) NOT NULL CHECK (tool_name = 'simulation.set-state'),
    tool_version VARCHAR(16) NOT NULL CHECK (tool_version = '1'),
    target_type VARCHAR(32) NOT NULL CHECK (target_type = 'SIM_SERVICE'),
    target_id VARCHAR(128) NOT NULL,
    parameters_json JSONB NOT NULL CHECK (jsonb_typeof(parameters_json) = 'object'),
    proposal_digest CHAR(64) NOT NULL CHECK (proposal_digest ~ '^[0-9a-f]{64}$'),
    binding_version VARCHAR(32) NOT NULL CHECK (binding_version = 'action-binding-v1'),
    binding_digest CHAR(64) NOT NULL CHECK (binding_digest ~ '^[0-9a-f]{64}$'),
    execution_key VARCHAR(128) NOT NULL UNIQUE,
    state VARCHAR(24) NOT NULL CHECK (state IN ('PENDING_APPROVAL', 'APPROVED',
        'APPROVAL_REJECTED', 'DISPATCHING', 'EXECUTING', 'SUCCEEDED', 'FAILED',
        'EXECUTION_REJECTED', 'EXPIRED', 'UNKNOWN')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    approval_due_at TIMESTAMPTZ NOT NULL,
    authorization_due_at TIMESTAMPTZ,
    last_reason_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_action_source UNIQUE (incident_id, source_type, source_request_id),
    CONSTRAINT ck_action_authorization CHECK (state NOT IN ('APPROVED', 'DISPATCHING',
        'EXECUTING', 'SUCCEEDED', 'FAILED', 'EXECUTION_REJECTED', 'UNKNOWN')
        OR authorization_due_at IS NOT NULL)
);
CREATE INDEX idx_action_incident ON t_incident_action (incident_id, created_at);
CREATE INDEX idx_action_dispatch ON t_incident_action (state, authorization_due_at, id)
    WHERE state IN ('APPROVED', 'DISPATCHING');
CREATE INDEX idx_action_approval_due ON t_incident_action (approval_due_at, id)
    WHERE state = 'PENDING_APPROVAL';

CREATE TABLE t_incident_action_approval (
    id VARCHAR(20) PRIMARY KEY,
    action_id VARCHAR(20) NOT NULL UNIQUE REFERENCES t_incident_action (id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    binding_digest CHAR(64) NOT NULL CHECK (binding_digest ~ '^[0-9a-f]{64}$'),
    approval_due_at TIMESTAMPTZ NOT NULL,
    decider_id VARCHAR(128),
    role_snapshot VARCHAR(64),
    decided_at TIMESTAMPTZ,
    authorization_due_at TIMESTAMPTZ,
    reason_code VARCHAR(64),
    reason_summary VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_approval_decision CHECK (
        (status = 'PENDING' AND decider_id IS NULL AND decided_at IS NULL AND authorization_due_at IS NULL)
        OR (status = 'APPROVED' AND decider_id IS NOT NULL AND decided_at IS NOT NULL
            AND authorization_due_at IS NOT NULL)
        OR (status = 'REJECTED' AND decider_id IS NOT NULL AND decided_at IS NOT NULL)
        OR (status = 'EXPIRED' AND decided_at IS NOT NULL))
);

CREATE TABLE t_incident_action_execution (
    id VARCHAR(20) PRIMARY KEY,
    action_id VARCHAR(20) NOT NULL UNIQUE REFERENCES t_incident_action (id),
    execution_key VARCHAR(128) NOT NULL UNIQUE,
    dispatch_request_id VARCHAR(128) NOT NULL UNIQUE,
    tool_name VARCHAR(64) NOT NULL CHECK (tool_name = 'simulation.set-state'),
    tool_version VARCHAR(16) NOT NULL CHECK (tool_version = '1'),
    target_type VARCHAR(32) NOT NULL CHECK (target_type = 'SIM_SERVICE'),
    target_id VARCHAR(128) NOT NULL,
    binding_digest CHAR(64) NOT NULL CHECK (binding_digest ~ '^[0-9a-f]{64}$'),
    max_execution_duration_ms BIGINT NOT NULL CHECK (max_execution_duration_ms > 0),
    receipt_grace_period_ms BIGINT NOT NULL CHECK (receipt_grace_period_ms > 0),
    execution_started_at TIMESTAMPTZ,
    receipt_due_at TIMESTAMPTZ,
    current_receipt_id VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_execution_deadline CHECK ((execution_started_at IS NULL) = (receipt_due_at IS NULL))
);

CREATE TABLE t_incident_execution_ticket (
    id VARCHAR(20) PRIMARY KEY,
    execution_id VARCHAR(20) NOT NULL REFERENCES t_incident_action_execution (id),
    secret_digest CHAR(64) NOT NULL UNIQUE CHECK (secret_digest ~ '^[0-9a-f]{64}$'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ISSUED', 'CONSUMED', 'EXPIRED')),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    invalidation_reason VARCHAR(64),
    replaced_by_ticket_id VARCHAR(20) REFERENCES t_incident_execution_ticket (id),
    CONSTRAINT ck_ticket_time CHECK (issued_at < expires_at),
    CONSTRAINT ck_ticket_consumed CHECK ((status = 'CONSUMED') = (consumed_at IS NOT NULL))
);
CREATE UNIQUE INDEX uk_execution_issued_ticket ON t_incident_execution_ticket (execution_id)
    WHERE status = 'ISSUED';
CREATE INDEX idx_ticket_execution ON t_incident_execution_ticket (execution_id, issued_at);

CREATE TABLE t_incident_action_receipt (
    id VARCHAR(20) PRIMARY KEY,
    execution_id VARCHAR(20) NOT NULL REFERENCES t_incident_action_execution (id),
    action_id VARCHAR(20) NOT NULL REFERENCES t_incident_action (id),
    request_id VARCHAR(128) NOT NULL,
    receipt_version SMALLINT NOT NULL CHECK (receipt_version = 1),
    receipt_digest CHAR(64) NOT NULL CHECK (receipt_digest ~ '^[0-9a-f]{64}$'),
    result_status VARCHAR(16) NOT NULL CHECK (result_status IN ('SUCCESS', 'FAILED', 'REJECTED', 'UNKNOWN')),
    result_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL CHECK (tool_name = 'simulation.set-state'),
    tool_version VARCHAR(16) NOT NULL CHECK (tool_version = '1'),
    target_type VARCHAR(32) NOT NULL CHECK (target_type = 'SIM_SERVICE'),
    target_id VARCHAR(128) NOT NULL,
    execution_key VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    summary_json JSONB NOT NULL CHECK (jsonb_typeof(summary_json) = 'object'),
    disposition VARCHAR(16) NOT NULL CHECK (disposition IN ('APPLIED', 'CONSISTENT', 'CONFLICT')),
    CONSTRAINT uk_execution_receipt UNIQUE (execution_id, id)
);
CREATE INDEX idx_receipt_action ON t_incident_action_receipt (action_id, accepted_at);
ALTER TABLE t_incident_action_execution ADD CONSTRAINT fk_execution_current_receipt
    FOREIGN KEY (id, current_receipt_id) REFERENCES t_incident_action_receipt (execution_id, id)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE t_incident_timeline DROP CONSTRAINT t_incident_timeline_event_type_check;
ALTER TABLE t_incident_timeline ADD CONSTRAINT ck_timeline_event_type CHECK (event_type IN (
    'INCIDENT_CREATED', 'ALERT_ATTACHED', 'LATE_ALERT_ATTACHED_AFTER_SEAL',
    'INCIDENT_AGGREGATION_SEALED', 'INCIDENT_STATUS_CHANGED', 'ACTION_PROPOSED',
    'ACTION_APPROVAL_DECIDED', 'ACTION_DISPATCHED', 'ACTION_TICKET_ISSUED',
    'ACTION_TICKET_EXPIRED', 'ACTION_TICKET_CONSUMED', 'ACTION_STATUS_CHANGED',
    'ACTION_RECEIPT_ACCEPTED', 'ACTION_RECONCILIATION_RECORDED',
    'ACTION_MANUAL_CONCLUSION', 'ACTION_RECEIPT_CONFLICT'));
ALTER TABLE t_incident_timeline DROP CONSTRAINT t_incident_timeline_related_entity_type_check;
ALTER TABLE t_incident_timeline ADD CONSTRAINT ck_timeline_related_type
    CHECK (related_entity_type IN ('ALERT', 'INCIDENT', 'ACTION', 'EXECUTION'));
ALTER TABLE t_incident_outbox DROP CONSTRAINT t_incident_outbox_event_type_check;
ALTER TABLE t_incident_outbox ADD CONSTRAINT ck_outbox_event_type CHECK (event_type IN (
    'incident.created', 'incident.alert-attached', 'incident.action-approval-decided',
    'incident.action-receipt-accepted'));

GRANT SELECT, INSERT, UPDATE ON t_incident_action, t_incident_action_approval,
    t_incident_action_execution, t_incident_execution_ticket TO ragent_app;
GRANT SELECT, INSERT ON t_incident_action_receipt TO ragent_app;
REVOKE UPDATE, DELETE ON t_incident_action_receipt FROM ragent_app;
REVOKE UPDATE, DELETE ON t_incident_action_receipt FROM PUBLIC;

-- Spec C, schema owner only. No credentials or knowledge-domain foreign keys.
CREATE TABLE t_incident_diagnosis_request (
    id VARCHAR(20) PRIMARY KEY,
    incident_id VARCHAR(20) NOT NULL REFERENCES t_incident(id),
    actor_id VARCHAR(128) NOT NULL,
    source_request_id VARCHAR(128) NOT NULL,
    request_digest CHAR(64) NOT NULL CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    snapshot_json JSONB NOT NULL CHECK (jsonb_typeof(snapshot_json)='object'),
    snapshot_digest CHAR(64) NOT NULL CHECK (snapshot_digest ~ '^[0-9a-f]{64}$'),
    facts_digest CHAR(64) NOT NULL CHECK (facts_digest ~ '^[0-9a-f]{64}$'),
    incident_version BIGINT NOT NULL CHECK (incident_version>=0),
    timeline_watermark BIGINT NOT NULL CHECK (timeline_watermark>=0),
    corpus_version VARCHAR(64) NOT NULL,
    corpus_digest CHAR(64) NOT NULL CHECK (corpus_digest ~ '^[0-9a-f]{64}$'),
    owner_token_digest CHAR(64) NOT NULL CHECK (owner_token_digest ~ '^[0-9a-f]{64}$'),
    state VARCHAR(16) NOT NULL CHECK (state IN ('RUNNING','COMPLETED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
    created_at TIMESTAMPTZ NOT NULL,
    deadline_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    UNIQUE (incident_id,actor_id,source_request_id),
    CHECK (deadline_at>created_at),
    CHECK ((state='COMPLETED')=(completed_at IS NOT NULL))
);
CREATE INDEX idx_diagnosis_incident ON t_incident_diagnosis_request(incident_id,created_at,id);
CREATE INDEX idx_diagnosis_due ON t_incident_diagnosis_request(incident_id,deadline_at) WHERE state='RUNNING';
CREATE TABLE t_incident_diagnosis_result (
    diagnosis_id VARCHAR(20) PRIMARY KEY REFERENCES t_incident_diagnosis_request(id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('SUPPORTED','INSUFFICIENT_EVIDENCE','INVALID_EVIDENCE','FAILED')),
    reason_code VARCHAR(64) NOT NULL,
    result_json JSONB NOT NULL CHECK (jsonb_typeof(result_json)='object'),
    result_digest CHAR(64) NOT NULL CHECK (result_digest ~ '^[0-9a-f]{64}$'),
    stale_at_publish BOOLEAN NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CHECK (status='SUPPORTED' OR NOT jsonb_path_exists(result_json,'$.recommendations[*] ? (@.convertible == true)'))
);
CREATE TABLE t_incident_diagnosis_action_link (
    action_id VARCHAR(20) PRIMARY KEY REFERENCES t_incident_action(id),
    incident_id VARCHAR(20) NOT NULL REFERENCES t_incident(id),
    diagnosis_id VARCHAR(20) NOT NULL REFERENCES t_incident_diagnosis_result(diagnosis_id),
    recommendation_id VARCHAR(128) NOT NULL,
    recommendation_digest CHAR(64) NOT NULL CHECK (recommendation_digest ~ '^[0-9a-f]{64}$'),
    diagnosis_digest CHAR(64) NOT NULL CHECK (diagnosis_digest ~ '^[0-9a-f]{64}$'),
    facts_digest CHAR(64) NOT NULL CHECK (facts_digest ~ '^[0-9a-f]{64}$'),
    actor_id VARCHAR(128) NOT NULL,
    source_request_id VARCHAR(128) NOT NULL,
    selection_digest CHAR(64) NOT NULL CHECK (selection_digest ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(incident_id,actor_id,source_request_id)
);
ALTER TABLE t_incident_timeline DROP CONSTRAINT ck_timeline_event_type;
ALTER TABLE t_incident_timeline ADD CONSTRAINT ck_timeline_event_type CHECK (event_type IN (
    'INCIDENT_CREATED','ALERT_ATTACHED','LATE_ALERT_ATTACHED_AFTER_SEAL','INCIDENT_AGGREGATION_SEALED',
    'INCIDENT_STATUS_CHANGED','ACTION_PROPOSED','ACTION_APPROVAL_DECIDED','ACTION_DISPATCHED',
    'ACTION_TICKET_ISSUED','ACTION_TICKET_EXPIRED','ACTION_TICKET_CONSUMED','ACTION_STATUS_CHANGED',
    'ACTION_RECEIPT_ACCEPTED','ACTION_RECONCILIATION_RECORDED','ACTION_MANUAL_CONCLUSION',
    'ACTION_RECEIPT_CONFLICT','DIAGNOSIS_RECORDED','DIAGNOSIS_ACTION_LINKED'));
ALTER TABLE t_incident_timeline DROP CONSTRAINT ck_timeline_related_type;
ALTER TABLE t_incident_timeline ADD CONSTRAINT ck_timeline_related_type
    CHECK(related_entity_type IN ('ALERT','INCIDENT','ACTION','EXECUTION','DIAGNOSIS'));
GRANT SELECT,INSERT ON t_incident_diagnosis_request,t_incident_diagnosis_result,t_incident_diagnosis_action_link TO ragent_app;
REVOKE UPDATE,DELETE ON t_incident_diagnosis_request,t_incident_diagnosis_result,t_incident_diagnosis_action_link FROM ragent_app,PUBLIC;
GRANT UPDATE(state,version,completed_at) ON t_incident_diagnosis_request TO ragent_app;
