--liquibase formatted sql

--changeset fleetlog:003-multiple-roles-per-membership
-- A person can hold several roles in the same organization (e.g. a Fleet Manager who also drives),
-- so roles move from a single column into their own tables, one row per role.

CREATE TABLE membership_role
(
    membership_id UUID        NOT NULL REFERENCES membership (id) ON DELETE CASCADE,
    role          VARCHAR(32) NOT NULL CHECK (role IN ('DRIVER', 'FLEET_MANAGER', 'ACCOUNTANT')),
    PRIMARY KEY (membership_id, role)
);

INSERT INTO membership_role (membership_id, role)
SELECT id, role
FROM membership;

ALTER TABLE membership
    DROP COLUMN role;

CREATE TABLE invitation_role
(
    invitation_id UUID        NOT NULL REFERENCES invitation (id) ON DELETE CASCADE,
    role          VARCHAR(32) NOT NULL CHECK (role IN ('DRIVER', 'FLEET_MANAGER', 'ACCOUNTANT')),
    PRIMARY KEY (invitation_id, role)
);

INSERT INTO invitation_role (invitation_id, role)
SELECT id, role
FROM invitation;

ALTER TABLE invitation
    DROP COLUMN role;
