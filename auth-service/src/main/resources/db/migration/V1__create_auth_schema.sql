CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT organizations_slug_format CHECK (slug ~ '^[a-z0-9][a-z0-9._-]{0,63}$'),
    CONSTRAINT organizations_slug_unique UNIQUE (slug)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT memberships_org_fk
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT memberships_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT memberships_role_valid CHECK (role IN ('ADMIN', 'DEVELOPER', 'VIEWER')),
    CONSTRAINT memberships_org_user_unique UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_organizations_slug ON organizations (slug);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_memberships_org_id ON memberships (organization_id);
CREATE INDEX idx_memberships_user_id ON memberships (user_id);
