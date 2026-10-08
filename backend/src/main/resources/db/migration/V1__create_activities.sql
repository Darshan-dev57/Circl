CREATE TABLE activities (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title            varchar(80)      NOT NULL,
    category         varchar(20)      NOT NULL,
    description      varchar(1000),
    latitude         double precision NOT NULL,
    longitude        double precision NOT NULL,
    starts_at        timestamptz      NOT NULL,
    duration_minutes int              NOT NULL DEFAULT 60,
    capacity         int              NOT NULL,
    seats_taken      int              NOT NULL DEFAULT 0,
    status           varchar(16)      NOT NULL DEFAULT 'OPEN',
    version          bigint           NOT NULL DEFAULT 0,
    created_at       timestamptz      NOT NULL DEFAULT now(),
    updated_at       timestamptz      NOT NULL DEFAULT now(),

    CONSTRAINT activities_capacity_range CHECK (capacity BETWEEN 2 AND 50),
    -- last line of defence: no code path may ever overbook
    CONSTRAINT activities_seats_within_capacity CHECK (seats_taken >= 0 AND seats_taken <= capacity),
    CONSTRAINT activities_lat_range CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT activities_lng_range CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_activities_status_starts_at ON activities (status, starts_at);
