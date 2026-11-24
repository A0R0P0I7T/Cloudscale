ALTER TABLE rollouts
    ADD COLUMN last_auto_paused_at TIMESTAMPTZ;

ALTER TABLE rollout_stages
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE health_policies
    ADD COLUMN minimum_request_count INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN cooldown_seconds INTEGER NOT NULL DEFAULT 300,
    ADD CONSTRAINT health_policies_minimum_request_count_valid CHECK (minimum_request_count > 0),
    ADD CONSTRAINT health_policies_cooldown_valid CHECK (cooldown_seconds > 0);

CREATE TABLE health_observations (
    id UUID PRIMARY KEY,
    rollout_id UUID NOT NULL,
    request_count BIGINT NOT NULL,
    error_rate DOUBLE PRECISION NOT NULL,
    latency_ms DOUBLE PRECISION NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT health_observations_rollout_fk
        FOREIGN KEY (rollout_id) REFERENCES rollouts (id) ON DELETE CASCADE,
    CONSTRAINT health_observations_request_count_valid CHECK (request_count >= 0),
    CONSTRAINT health_observations_error_rate_valid CHECK (error_rate >= 0 AND error_rate <= 1),
    CONSTRAINT health_observations_latency_valid CHECK (latency_ms >= 0)
);

CREATE INDEX health_observations_rollout_time_idx
    ON health_observations (rollout_id, observed_at DESC);

CREATE UNIQUE INDEX rollouts_single_active_feature_environment_idx
    ON rollouts (organization_id, feature_flag_id, environment_id)
    WHERE status IN ('IN_PROGRESS', 'PAUSED');

CREATE TABLE rollout_event_outbox (
    event_id UUID PRIMARY KEY,
    message_key VARCHAR(512) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(128),
    CONSTRAINT rollout_outbox_attempts_nonnegative CHECK (attempts >= 0)
);

CREATE INDEX rollout_event_outbox_pending_idx
    ON rollout_event_outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL;
