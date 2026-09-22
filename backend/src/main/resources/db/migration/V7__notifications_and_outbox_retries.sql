ALTER TABLE outbox_events
    ADD COLUMN attempts        int NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at timestamptz,
    ADD COLUMN last_error      varchar(500),
    ADD COLUMN failed_at       timestamptz;

DROP INDEX idx_outbox_unpublished;
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL AND failed_at IS NULL;

ALTER TABLE participants ADD COLUMN reminded_at timestamptz;

CREATE TABLE notifications (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        varchar(40)  NOT NULL,
    message     varchar(300) NOT NULL,
    activity_id uuid REFERENCES activities (id) ON DELETE CASCADE,
    event_id    uuid,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    read_at     timestamptz,

    -- one notification per user per event, even if an event is handled twice
    CONSTRAINT notifications_event_user_unique UNIQUE (event_id, user_id)
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
