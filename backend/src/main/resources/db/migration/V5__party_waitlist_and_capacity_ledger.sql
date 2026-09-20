ALTER TABLE participants
    ADD COLUMN party_size int NOT NULL DEFAULT 1,
    ADD CONSTRAINT participants_party_size_range CHECK (party_size BETWEEN 1 AND 4);

ALTER TABLE waitlist
    ADD COLUMN party_size     int         NOT NULL DEFAULT 1,
    ADD COLUMN status         varchar(16) NOT NULL DEFAULT 'WAITING',
    ADD COLUMN offered_at     timestamptz,
    ADD COLUMN claim_deadline timestamptz,
    ADD CONSTRAINT waitlist_party_size_range CHECK (party_size BETWEEN 1 AND 4),
    ADD CONSTRAINT waitlist_status_valid CHECK (status IN ('WAITING', 'OFFERED', 'CLAIMED', 'EXPIRED', 'DECLINED')),
    ADD CONSTRAINT waitlist_offer_has_deadline CHECK (status <> 'OFFERED' OR claim_deadline IS NOT NULL);

CREATE INDEX idx_waitlist_open_offers ON waitlist (claim_deadline) WHERE status = 'OFFERED';

-- append-only: every change to seats_taken writes one row, so SUM(delta) must always equal seats_taken
CREATE TABLE capacity_ledger (
    id          bigserial PRIMARY KEY,
    activity_id uuid        NOT NULL REFERENCES activities (id) ON DELETE CASCADE,
    delta       int         NOT NULL,
    reason      varchar(16) NOT NULL,
    ref_id      uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT capacity_ledger_reason_valid
        CHECK (reason IN ('JOIN', 'CANCEL', 'OFFER', 'CLAIM', 'EXPIRE', 'DECLINE', 'BACKFILL'))
);

CREATE INDEX idx_capacity_ledger_activity ON capacity_ledger (activity_id);

INSERT INTO capacity_ledger (activity_id, delta, reason)
SELECT id, seats_taken, 'BACKFILL' FROM activities WHERE seats_taken > 0;

CREATE TABLE shedlock (
    name       varchar(64)  PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);
