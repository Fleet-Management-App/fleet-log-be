-- Global accounts: one per person, not tied to any organization.
CREATE TABLE app_user
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);
-- E-mail is unique regardless of letter case
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

-- An organization is a tenant: all fleet data is scoped to one.
CREATE TABLE organization
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- A user's access to an organization; the role belongs here, not on the user.
CREATE TABLE membership
(
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES app_user (id),
    organization_id UUID        NOT NULL REFERENCES organization (id),
    role            VARCHAR(32) NOT NULL CHECK (role IN ('DRIVER', 'FLEET_MANAGER', 'ACCOUNTANT')),
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_membership_user_organization UNIQUE (user_id, organization_id)
);
CREATE INDEX ix_membership_organization ON membership (organization_id);

-- Invitation to an organization, addressed to an e-mail (the person may not have an account yet).
CREATE TABLE invitation
(
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    email           VARCHAR(320) NOT NULL,
    role            VARCHAR(32)  NOT NULL CHECK (role IN ('DRIVER', 'FLEET_MANAGER', 'ACCOUNTANT')),
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED')),
    invited_by      UUID         NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_invitation_email ON invitation (lower(email)) WHERE status = 'PENDING';
-- At most one open invitation per e-mail and organization
CREATE UNIQUE INDEX ux_invitation_pending ON invitation (organization_id, lower(email)) WHERE status = 'PENDING';
