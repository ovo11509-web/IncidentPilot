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
