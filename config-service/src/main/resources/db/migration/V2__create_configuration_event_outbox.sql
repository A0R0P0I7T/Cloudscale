CREATE TABLE configuration_event_outbox (
    event_id UUID PRIMARY KEY,
    message_key VARCHAR(512) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(128),
    CONSTRAINT configuration_outbox_attempts_nonnegative CHECK (attempts >= 0)
);

CREATE INDEX configuration_event_outbox_pending_idx
    ON configuration_event_outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL;
