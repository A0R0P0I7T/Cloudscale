CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INTEGER NOT NULL,
    organization_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64),
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT audit_events_event_id_unique UNIQUE (event_id)
);

CREATE TABLE processed_events (
    event_id UUID NOT NULL,
    consumer_group VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX idx_audit_events_org_id ON audit_events (organization_id);
CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at);
