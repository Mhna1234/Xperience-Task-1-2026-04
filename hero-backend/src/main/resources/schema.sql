CREATE TABLE IF NOT EXISTS hero.events
(
    id           BIGSERIAL    PRIMARY KEY,
    host_id      VARCHAR(255) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    start_time   TIMESTAMPTZ  NOT NULL,
    location     VARCHAR(500),
    max_capacity INTEGER,
    status       VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hero.invitations
(
    id           BIGSERIAL    PRIMARY KEY,
    event_id     BIGINT       NOT NULL REFERENCES hero.events (id),
    email        VARCHAR(255) NOT NULL,
    invite_token VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_invitations_token      UNIQUE (invite_token),
    CONSTRAINT uq_invitations_event_email UNIQUE (event_id, email)
);

CREATE TABLE IF NOT EXISTS hero.rsvps
(
    id            BIGSERIAL   PRIMARY KEY,
    invitation_id BIGINT      NOT NULL REFERENCES hero.invitations (id),
    status        VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    responded_at  TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_rsvps_invitation UNIQUE (invitation_id)
);

CREATE INDEX IF NOT EXISTS idx_invitations_token        ON hero.invitations (invite_token);
CREATE INDEX IF NOT EXISTS idx_invitations_event_id     ON hero.invitations (event_id);
CREATE INDEX IF NOT EXISTS idx_rsvps_invitation_id      ON hero.rsvps (invitation_id);
CREATE INDEX IF NOT EXISTS idx_rsvps_status             ON hero.rsvps (status);
