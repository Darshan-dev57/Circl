CREATE TABLE users (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email             varchar(254) NOT NULL,
    name              varchar(60)  NOT NULL,
    password_hash     varchar(100) NOT NULL,
    role              varchar(16)  NOT NULL DEFAULT 'PARTICIPANT',
    reliability_score int          NOT NULL DEFAULT 50,
    created_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_role_valid CHECK (role IN ('PARTICIPANT', 'HOST', 'ADMIN')),
    CONSTRAINT users_score_range CHECK (reliability_score BETWEEN 0 AND 100)
);

-- only a SHA-256 of the refresh token is stored, never the token itself
CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64)    NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT refresh_tokens_hash_unique UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

ALTER TABLE activities ADD COLUMN host_id uuid NOT NULL REFERENCES users (id);
CREATE INDEX idx_activities_host ON activities (host_id);
