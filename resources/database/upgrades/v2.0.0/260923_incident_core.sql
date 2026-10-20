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
