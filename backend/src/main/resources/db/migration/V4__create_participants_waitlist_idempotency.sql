CREATE TABLE participants (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id uuid        NOT NULL REFERENCES activities (id) ON DELETE CASCADE,
    user_id     uuid        NOT NULL REFERENCES users (id),
    status      varchar(16) NOT NULL,
    joined_at   timestamptz NOT NULL DEFAULT now(),
    left_at     timestamptz,
    version     bigint      NOT NULL DEFAULT 0,

    CONSTRAINT participants_activity_user_unique UNIQUE (activity_id, user_id),
    CONSTRAINT participants_status_valid CHECK (status IN ('JOINED', 'LEFT'))
);

CREATE INDEX idx_participants_user ON participants (user_id);

CREATE SEQUENCE waitlist_position_seq;

CREATE TABLE waitlist (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id uuid        NOT NULL REFERENCES activities (id) ON DELETE CASCADE,
    user_id     uuid        NOT NULL REFERENCES users (id),
    position    bigint      NOT NULL DEFAULT nextval('waitlist_position_seq'),
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT waitlist_activity_user_unique UNIQUE (activity_id, user_id)
);

CREATE INDEX idx_waitlist_activity_position ON waitlist (activity_id, position);

CREATE TABLE idempotency_keys (
    user_id         uuid        NOT NULL,
    idem_key        varchar(80) NOT NULL,
    request_hash    varchar(64) NOT NULL,
    response_status int,
    response_body   text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,

    PRIMARY KEY (user_id, idem_key)
);

CREATE TABLE outbox_events (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id uuid        NOT NULL,
    type         varchar(64) NOT NULL,
    payload      jsonb       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
