CREATE TABLE projects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_key VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT projects_key_format CHECK (project_key ~ '^[a-z0-9][a-z0-9._-]{0,63}$'),
    CONSTRAINT projects_organization_key_unique UNIQUE (organization_id, project_key)
);

CREATE TABLE environments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    environment_key VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT environments_project_fk
        FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT environments_key_format CHECK (environment_key ~ '^[a-z0-9][a-z0-9._-]{0,63}$'),
    CONSTRAINT environments_project_key_unique UNIQUE (project_id, environment_key)
);

CREATE TABLE feature_flags (
    id UUID PRIMARY KEY,
    environment_id UUID NOT NULL,
    feature_key VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL,
    published_version_id UUID,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feature_flags_environment_fk
        FOREIGN KEY (environment_id) REFERENCES environments (id),
    CONSTRAINT feature_flags_key_format CHECK (feature_key ~ '^[a-z0-9][a-z0-9._-]{0,63}$'),
    CONSTRAINT feature_flags_revision_positive CHECK (revision >= 0),
    CONSTRAINT feature_flags_environment_key_unique UNIQUE (environment_id, feature_key)
);

CREATE TABLE feature_versions (
    id UUID PRIMARY KEY,
    feature_flag_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    value JSONB NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feature_versions_feature_fk
        FOREIGN KEY (feature_flag_id) REFERENCES feature_flags (id),
    CONSTRAINT feature_versions_number_positive CHECK (version_number > 0),
    CONSTRAINT feature_versions_status_valid CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT feature_versions_value_valid CHECK (value IS NOT NULL),
    CONSTRAINT feature_versions_feature_number_unique UNIQUE (feature_flag_id, version_number)
);

ALTER TABLE feature_flags
    ADD CONSTRAINT feature_flags_published_version_fk
    FOREIGN KEY (published_version_id) REFERENCES feature_versions (id);

CREATE TABLE targeting_rules (
    id UUID PRIMARY KEY,
    feature_version_id UUID NOT NULL,
    priority INTEGER NOT NULL,
    conditions JSONB NOT NULL,
    value JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT targeting_rules_version_fk
        FOREIGN KEY (feature_version_id) REFERENCES feature_versions (id),
    CONSTRAINT targeting_rules_priority_positive CHECK (priority > 0),
    CONSTRAINT targeting_rules_conditions_valid CHECK (jsonb_typeof(conditions) IN ('object', 'array')),
    CONSTRAINT targeting_rules_value_valid CHECK (value IS NOT NULL),
    CONSTRAINT targeting_rules_version_priority_unique UNIQUE (feature_version_id, priority)
);

CREATE UNIQUE INDEX feature_versions_one_draft_idx
    ON feature_versions (feature_flag_id)
    WHERE status = 'DRAFT';

CREATE UNIQUE INDEX feature_versions_one_published_idx
    ON feature_versions (feature_flag_id)
    WHERE status = 'PUBLISHED';

CREATE INDEX projects_organization_idx ON projects (organization_id);
CREATE INDEX environments_project_idx ON environments (project_id);
CREATE INDEX feature_flags_environment_idx ON feature_flags (environment_id);
CREATE INDEX feature_versions_feature_idx ON feature_versions (feature_flag_id);
CREATE INDEX targeting_rules_version_idx ON targeting_rules (feature_version_id);
