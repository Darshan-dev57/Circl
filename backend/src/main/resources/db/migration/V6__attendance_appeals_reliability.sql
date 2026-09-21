ALTER TABLE activities
    ADD COLUMN min_reliability       int     NOT NULL DEFAULT 0,
    ADD COLUMN attendance_finalized  boolean NOT NULL DEFAULT false,
    ADD COLUMN attendance_unreliable boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT activities_min_reliability_range CHECK (min_reliability BETWEEN 0 AND 100);

ALTER TABLE participants
    ADD COLUMN attendance_status varchar(16)  NOT NULL DEFAULT 'RSVP',
    ADD COLUMN late              boolean      NOT NULL DEFAULT false,
    ADD COLUMN checked_in_at     timestamptz,
    ADD COLUMN no_show_at        timestamptz,
    ADD COLUMN host_evidence     varchar(500),
    ADD COLUMN score_applied     boolean      NOT NULL DEFAULT false,
    ADD CONSTRAINT participants_attendance_valid
        CHECK (attendance_status IN ('RSVP', 'RECONFIRMED', 'CHECKED_IN', 'ATTENDED', 'CANCELLED', 'NO_SHOW'));

CREATE TABLE attendance_events (
    id             bigserial PRIMARY KEY,
    participant_id uuid        NOT NULL REFERENCES participants (id) ON DELETE CASCADE,
    from_status    varchar(16) NOT NULL,
    to_status      varchar(16) NOT NULL,
    actor          varchar(8)  NOT NULL CHECK (actor IN ('SYSTEM', 'HOST', 'USER')),
    note           varchar(500),
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_attendance_events_participant ON attendance_events (participant_id);

CREATE TABLE no_show_appeals (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id   uuid         NOT NULL REFERENCES participants (id) ON DELETE CASCADE,
    user_reason      varchar(500) NOT NULL,
    status           varchar(16)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED')),
    appeal_closes_at timestamptz  NOT NULL,
    decided_at       timestamptz,
    decision_note    varchar(500),
    created_at       timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT no_show_appeals_one_per_participant UNIQUE (participant_id)
);

CREATE TABLE reliability (
    user_id    uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    committed  int         NOT NULL DEFAULT 0,
    checked_in int         NOT NULL DEFAULT 0,
    no_shows   int         NOT NULL DEFAULT 0,
    score      int         NOT NULL DEFAULT 50,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_activities_unfinalized ON activities (starts_at) WHERE attendance_finalized = false;
