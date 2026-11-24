CREATE INDEX audit_events_org_time_idx
    ON audit_events (organization_id, occurred_at DESC, event_id DESC);

CREATE INDEX audit_events_org_entity_time_idx
    ON audit_events (organization_id, entity_type, entity_id, occurred_at DESC);
