-- Seeds a demo host and N activities spread around five Bengaluru areas.
-- Usage: docker exec -i circl-postgres psql -U circl -d circl -v n=100000 < scripts/seed_activities.sql
\if :{?n}
\else
\set n 100000
\endif

INSERT INTO users (email, name, password_hash, role)
VALUES ('seed-host@circl.dev', 'Seed Host',
        -- bcrypt of "seed-host-pass", only for local demo data
        '$2a$10$GpmlVridk.3.NladF6/ggOby.S1o8a.kXQ2ConzJDRUMaGH779Sxa', 'HOST')
ON CONFLICT (email) DO NOTHING;

WITH areas(name, lat, lng) AS (
    VALUES ('Koramangala', 12.9352, 77.6245),
           ('Indiranagar', 12.9784, 77.6408),
           ('HSR Layout', 12.9116, 77.6474),
           ('Jayanagar', 12.9250, 77.5938),
           ('Whitefield', 12.9698, 77.7500)
), host AS (
    SELECT id FROM users WHERE email = 'seed-host@circl.dev'
), cats AS (
    SELECT ARRAY['CRICKET', 'BADMINTON', 'FOOTBALL', 'COFFEE', 'TREK', 'STUDY'] AS c
)
INSERT INTO activities (host_id, title, category, description, latitude, longitude, starts_at, capacity)
SELECT host.id,
       cats.c[1 + (g % 6)] || ' meetup #' || g,
       cats.c[1 + (g % 6)],
       'seeded demo activity',
       a.lat + (random() - 0.5) * 0.3,   -- about +-16 km around the area
       a.lng + (random() - 0.5) * 0.3,
       now() + (random() * interval '20 days') + interval '1 hour',
       2 + (random() * 20)::int
FROM generate_series(1, :n) AS g
CROSS JOIN host
CROSS JOIN cats
JOIN LATERAL (SELECT * FROM areas OFFSET (g % 5) LIMIT 1) a ON true;

ANALYZE activities;
