-- A small, realistic set of activities around Koramangala, Indiranagar and HSR for trying the app.
-- Usage (Linux/macOS): docker exec -i circl-postgres psql -U circl -d circl < scripts/demo_data.sql
-- Usage (PowerShell):  Get-Content scripts/demo_data.sql | docker exec -i circl-postgres psql -U circl -d circl
-- Demo host login: demo.host@circl.dev / circl-demo-pass (local only)

INSERT INTO users (email, name, password_hash, role)
VALUES ('demo.host@circl.dev', 'Kiran', '$2a$10$OqjDO5xxUgV2r3qBqBgSh.D.6aYPuYGxd2gW7prXMn1fON4hxhNFC', 'HOST')
ON CONFLICT (email) DO NOTHING;

INSERT INTO activities (host_id, title, category, description, latitude, longitude, starts_at, capacity)
SELECT u.id, v.title, v.category, v.description, v.lat, v.lng, v.starts_at, v.capacity
  FROM users u,
       (VALUES
       ('Sunday morning cricket, tennis ball', 'CRICKET', 'Koramangala 4th block ground. Bring a bat if you have one.', 12.9339, 77.6229, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 2 + time '07:00') AT TIME ZONE 'Asia/Kolkata', 14),
       ('Badminton doubles at the club court', 'BADMINTON', 'We split the court fee, about 120 each.', 12.937, 77.6265, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '18:30') AT TIME ZONE 'Asia/Kolkata', 4),
       ('Five-a-side football after work', 'FOOTBALL', 'Turf near Sony signal. Bibs provided.', 12.9311, 77.618, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '19:00') AT TIME ZONE 'Asia/Kolkata', 10),
       ('DSA practice, graphs and DP', 'STUDY', 'Quiet table at the library cafe. Bring a laptop.', 12.9358, 77.617, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '10:00') AT TIME ZONE 'Asia/Kolkata', 6),
       ('Filter coffee and a walk', 'COFFEE', 'Slow walk around the block, nothing serious.', 12.9406, 77.6262, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '17:30') AT TIME ZONE 'Asia/Kolkata', 4),
       ('Nandi Hills sunrise ride', 'TREK', 'Meet at 4 am, we carpool from here.', 12.9352, 77.6301, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 3 + time '04:00') AT TIME ZONE 'Asia/Kolkata', 8),
       ('Box cricket, 6 overs a side', 'CRICKET', NULL, 12.9272, 77.6273, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '20:00') AT TIME ZONE 'Asia/Kolkata', 12),
       ('Spring Boot study group', 'STUDY', 'Going through security and JWT this week.', 12.9288, 77.6216, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 2 + time '11:00') AT TIME ZONE 'Asia/Kolkata', 8),
       ('Shuttle rally for beginners', 'BADMINTON', 'No smashing contests, promise.', 12.9395, 77.6203, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '07:00') AT TIME ZONE 'Asia/Kolkata', 6),
       ('Evening football in HSR', 'FOOTBALL', NULL, 12.9121, 77.6446, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '18:00') AT TIME ZONE 'Asia/Kolkata', 14),
       ('GRE vocab sprint', 'STUDY', NULL, 12.9142, 77.639, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 2 + time '09:30') AT TIME ZONE 'Asia/Kolkata', 5),
       ('Third wave coffee crawl', 'COFFEE', 'Three cafes in Indiranagar, about two hours.', 12.9719, 77.6412, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 2 + time '16:00') AT TIME ZONE 'Asia/Kolkata', 6),
       ('Indiranagar night cricket', 'CRICKET', NULL, 12.9762, 77.6381, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 2 + time '20:30') AT TIME ZONE 'Asia/Kolkata', 16),
       ('Morning badminton, 6:30 sharp', 'BADMINTON', NULL, 12.9681, 77.645, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '06:30') AT TIME ZONE 'Asia/Kolkata', 4),
       ('Skandagiri night trek', 'TREK', 'Booked via the forest office, ID needed.', 12.979, 77.643, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 4 + time '22:00') AT TIME ZONE 'Asia/Kolkata', 10),
       ('Football at Ulsoor', 'FOOTBALL', NULL, 12.9817, 77.6189, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 3 + time '18:00') AT TIME ZONE 'Asia/Kolkata', 12),
       ('OS revision for internals', 'STUDY', 'Process scheduling and deadlocks.', 12.9447, 77.61, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '15:00') AT TIME ZONE 'Asia/Kolkata', 6),
       ('Chai and chess', 'COFFEE', NULL, 12.9217, 77.621, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 1 + time '17:00') AT TIME ZONE 'Asia/Kolkata', 4),
       ('BTM gully cricket', 'CRICKET', NULL, 12.9166, 77.6101, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 2 + time '17:30') AT TIME ZONE 'Asia/Kolkata', 10),
       ('System design mock interviews', 'STUDY', 'One designs, one interviews, then swap.', 12.933, 77.614, ((now() AT TIME ZONE 'Asia/Kolkata')::date + 2 + time '14:00') AT TIME ZONE 'Asia/Kolkata', 4)
       ) AS v(title, category, description, lat, lng, starts_at, capacity)
 WHERE u.email = 'demo.host@circl.dev'
   -- running the script again only adds what is missing, or what has already happened
   AND NOT EXISTS (SELECT 1 FROM activities a
                    WHERE a.host_id = u.id AND a.title = v.title AND a.starts_at > now());
