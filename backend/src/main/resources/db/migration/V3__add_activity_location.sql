CREATE EXTENSION IF NOT EXISTS postgis;

-- derived from latitude/longitude so the two can never disagree; note ST_MakePoint takes (lng, lat)
ALTER TABLE activities
    ADD COLUMN location geography(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;

CREATE INDEX idx_activities_location ON activities USING gist (location);
