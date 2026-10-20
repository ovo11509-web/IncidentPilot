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
