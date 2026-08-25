CREATE TABLE rollouts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    feature_flag_id UUID NOT NULL,
    environment_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_percentage INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT rollouts_status_valid CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'ROLLED_BACK')),
    CONSTRAINT rollouts_percentage_valid CHECK (current_percentage >= 0 AND current_percentage <= 100)
);

CREATE TABLE rollout_stages (
    id UUID PRIMARY KEY,
    rollout_id UUID NOT NULL,
    stage_order INTEGER NOT NULL,
    target_percentage INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT rollout_stages_rollout_fk
        FOREIGN KEY (rollout_id) REFERENCES rollouts (id) ON DELETE CASCADE,
    CONSTRAINT rollout_stages_target_percentage_valid CHECK (target_percentage >= 0 AND target_percentage <= 100),
    CONSTRAINT rollout_stages_duration_valid CHECK (duration_minutes > 0),
    CONSTRAINT rollout_stages_status_valid CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED')),
    CONSTRAINT rollout_stages_rollout_order_unique UNIQUE (rollout_id, stage_order)
);

CREATE TABLE health_policies (
    id UUID PRIMARY KEY,
    rollout_id UUID NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    evaluation_window_seconds INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT health_policies_rollout_fk
        FOREIGN KEY (rollout_id) REFERENCES rollouts (id) ON DELETE CASCADE,
    CONSTRAINT health_policies_window_valid CHECK (evaluation_window_seconds > 0)
);

CREATE INDEX idx_rollouts_org_id ON rollouts (organization_id);
CREATE INDEX idx_rollouts_feature_env ON rollouts (feature_flag_id, environment_id);
CREATE INDEX idx_rollout_stages_rollout_id ON rollout_stages (rollout_id);
CREATE INDEX idx_health_policies_rollout_id ON health_policies (rollout_id);
