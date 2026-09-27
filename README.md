<h1 align="center">Circl</h1>

<p align="center">
  Find small games, study groups and plans happening near you, and grab a seat before it fills.
</p>

<p align="center">
  <a href="https://github.com/Darshan-dev57/Circl/actions/workflows/ci.yml"><img src="https://github.com/Darshan-dev57/Circl/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Java-21-007396" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F" alt="Spring Boot 3.5">
  <img src="https://img.shields.io/badge/PostgreSQL-16%20%2B%20PostGIS-336791" alt="PostgreSQL 16 + PostGIS">
  <img src="https://img.shields.io/badge/React-19-61DAFB" alt="React 19">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT license"></a>
</p>

![Circl: activities on a map with seats left](docs/images/screens/explore-desktop.png)

Someone posts "badminton doubles at 6:30, 4 people". People nearby see it on a map and join.
The interesting part is the last seat: when 50 people tap **Join** at the same moment, exactly
the right number get in, nobody is double booked, and the rest go to a fair waitlist.

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Data model](#data-model)
- [How the join engine works](#how-the-join-engine-works)
- [Waitlist offers](#waitlist-offers)
- [Login and refresh tokens](#login-and-refresh-tokens)
- [Attendance and reliability](#attendance-and-reliability)
- [Live seat count](#live-seat-count)
- [Measured numbers](#measured-numbers)
- [Tech choices](#tech-choices)
- [Run it locally](#run-it-locally)
- [API](#api)
- [Tests](#tests)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)

## Features

- **Nearby search** with PostGIS: activities within a radius, sorted by distance, filtered by category.
- **Join engine** that never overbooks: one conditional `UPDATE` per join, `Idempotency-Key` so retries are safe, and a database `CHECK` as the last line of defence.
- **Groups**: join with up to 3 friends (`partySize` 1 to 4). The whole group gets in or waits together.
- **Waitlist with a 15 minute claim**: a freed seat is held for the first party that fits. Claim it or it moves on.
- **Capacity ledger**: every seat change is an append-only row, so `SUM(delta)` always equals `seats_taken`.
- **Attendance**: rotating QR check-in, a state machine, no-show appeals with a 48 hour window and host decisions.
- **Reliability score** per user, and hosts can ask for a minimum score to join.
- **Free now**: "free for an hour, up for cricket". Stored only in Redis with a TTL, location snapped to about 1 km, and searches are rate limited so nobody can triangulate you.
- **Notifications** through a transactional outbox, with retries and backoff.
- **Live seat count** in the browser with Server-Sent Events.
- **Auth**: BCrypt, short JWT access tokens, rotating refresh tokens in an HttpOnly cookie with reuse detection, a login limit, and host-only edits.
- **Frontend**: React + Vite + Leaflet, with a map, an activity page, bookings and a host view.

<table>
  <tr>
    <td><img src="docs/images/screens/activity-joined.png" alt="Activity page with the seat ring"></td>
    <td><img src="docs/images/screens/host-view.png" alt="Host view with participants and check-in"></td>
  </tr>
  <tr>
    <td align="center">Activity page: each dot is a seat, yours are orange, updates live</td>
    <td align="center">Host view: who is coming, rotating check-in QR, capacity</td>
  </tr>
</table>

## Architecture

![Architecture](docs/images/architecture.png)

A plain layered Spring Boot app (controller, service, repository) with two stores:

- **PostgreSQL + PostGIS** is the source of truth for everything that matters: seats, participants, the waitlist, the ledger and the outbox.
- **Redis** holds only things that are fine to lose: the nearby cache, free-now posts, rate-limit counters and the pub/sub channel for live seats. If Redis is down, nearby search still works (it goes straight to Postgres) and login fails closed with a `503`.

Background work runs as `@Scheduled` jobs. ShedLock makes sure only one instance runs the offer-expiry,
finalizer and reminder jobs at a time; the outbox relay uses `FOR UPDATE SKIP LOCKED` so several
instances can share it without picking the same rows. No message broker is needed at this size.

## Data model

![ER diagram](docs/images/er.png)

Rules the database itself enforces:

| Rule | How |
|---|---|
| Never more seats taken than capacity | `CHECK (seats_taken >= 0 AND seats_taken <= capacity)` |
| A user joins an activity once | `UNIQUE (activity_id, user_id)` on participants and waitlist |
| A retried request does the work once | primary key `(user_id, idem_key)` on `idempotency_keys` |
| One notification per event per user | `UNIQUE (event_id, user_id)` |
| One appeal per no-show | `UNIQUE (participant_id)` on `no_show_appeals` |
| Location and lat/lng never disagree | `location` is a generated column from `latitude`, `longitude` |

Schema changes are Flyway migrations (`V1` to `V7`), and Hibernate only validates the schema.

## How the join engine works

![Join race](docs/images/join-race.png)

The whole seat check is one statement:

```sql
UPDATE activities
   SET seats_taken = seats_taken + :party, version = version + 1
 WHERE id = :id AND status = 'OPEN'
   AND seats_taken + :party <= capacity;
```

Postgres locks the row for the first `UPDATE`; the second one waits, then re-checks the `WHERE`
against the new row. So one request gets `1 row updated` and joins, the other gets `0` and goes to the
waitlist. There is no "read seats, then write" gap for a race to slip into.

The same code also has a pessimistic version (`SELECT ... FOR UPDATE`) and an optimistic one
(`@Version` with retries), switchable with `CIRCL_JOIN_STRATEGY`, so they can be compared under the same test.

Before any of that, the `Idempotency-Key` row is inserted in the same transaction. A retry with the same
key gets the saved response; the same key with a different body is rejected; a key still in flight
gets `409`.

The [`seat-simulator`](seat-simulator) module shows the same race in plain Java first
(`ExecutorService` + `CountDownLatch`): in one run with no lock, 100 threads booked **35** of 10 seats (the exact number
changes every run); `synchronized`, `AtomicInteger` CAS and `ReentrantLock` all stop at exactly 10.
After a build you can run it yourself: `java -cp seat-simulator/target/classes com.darshan.circl.sim.SeatSimulator`.

## Waitlist offers

![Waitlist claim](docs/images/waitlist-claim.png)

When seats free up, the first waiting party that fits gets an offer and the seats are held for 15 minutes.
A party of 3 that does not fit keeps its place, and a party of 1 behind it can take the single seat.
Claim and expiry both run `UPDATE ... WHERE status = 'OFFERED'`, so whichever comes first wins and the
other changes nothing.

## Login and refresh tokens

![Refresh token flow](docs/images/refresh-token.png)

- Access token: a JWT (HS256) that lives 15 minutes and is only kept in memory in the browser.
- Refresh token: random, 7 days, stored as a SHA-256 hash, sent to the browser as an `HttpOnly`, `SameSite=Strict` cookie limited to `/api/v1/auth`. API clients can still send it in the body.
- Every refresh rotates the token. If an old one is used again, someone copied it, so every token of that user is revoked.
- Five wrong passwords block the email for 15 minutes (counted in Redis).

## Attendance and reliability

![Attendance states](docs/images/attendance.png)

The host shows a QR code that changes every 60 seconds (an HMAC-signed token). Scanning it with a phone
camera opens the activity with the code filled in. Two hours after the end a finalizer marks everyone
who did not check in as `NO_SHOW`. The user then has 48 hours to appeal; only a rejected or missing
appeal counts against them.

```
score = (checked_in + 1) / (committed + 2) * 100
```

The `+1 / +2` (Laplace smoothing) means a new user starts at 50 and one bad day does not sink them.
If check-in itself was broken that day, the host reports it
(`POST /activities/{id}/attendance/unreliable`) and nobody on that activity is penalised.

## Live seat count

![Live seats](docs/images/live-seats.png)

The activity page opens an `EventSource` to `/api/v1/activities/{id}/seats`. After a join commits,
the outbox relay reads the fresh numbers and publishes them on a Redis channel; every app instance
forwards them to the browsers connected to it. Seats in the browser update about 1.5 seconds after a join,
and a heartbeat every 25 seconds keeps proxies from closing the stream.

## Measured numbers

All measured on one 4 CPU machine (app, database and load generator together), so they are
relative numbers, not production promises.

| What | Result |
|---|---|
| Nearby search, 100k activities, 3 km | 72 to 85 ms without an index, about **8 ms** with a GiST index and KNN ordering |
| Nearby search under load (k6, 50 users, 30 s) | without cache: p95 363 ms, 294 req/s. With Redis cache: **p95 34 ms, 3014 req/s**, 0 errors |
| 60 people join 10 seats at once (HTTP) | **10 joined, 50 waitlisted, 0 overbooked** |
| 50 threads for the last seat, 20 rounds, per strategy | conditional update p50 85 ms / pessimistic 96 ms / optimistic 57 ms with 143 retries, **0 overbooked** in all three |
| 10 parallel requests with one `Idempotency-Key` | 1 participant row, 1 seat taken |
| Seat simulator, 100 threads, 10 seats, no lock | 35 booked, 25 overbooked (one run, it changes every run) |
| Join to live seat update in the browser | about 1.5 s |

## Tech choices

| Need | Choice | Why | Not chosen |
|---|---|---|---|
| Language and framework | Java 21, Spring Boot 3.5 | the standard, records, virtual-thread ready | Node: weaker typing for a domain with rules |
| Database | PostgreSQL 16 + PostGIS | transactions, row locks, `CHECK`s, real geo indexes | MongoDB: no multi-row transactions I would trust for seats |
| Seat safety | conditional `UPDATE` | one round trip, no retry loop, the DB re-checks | `synchronized`: only works in one JVM |
| Geo search | `ST_DWithin` + GiST + `<->` | uses the index, sorts by real distance | Haversine in SQL: full table scan |
| Cache, free-now, limits | Redis | TTLs, GEO commands, atomic counters | in-memory map: breaks with two instances |
| Events | transactional outbox + scheduled relay | no lost or ghost events, no extra infra | Kafka or RabbitMQ: more to run than this app needs |
| Live seats | Server-Sent Events | one-way updates over plain HTTP, auto-reconnect | WebSocket: two-way is not needed; polling: wasteful |
| Auth | JWT access + rotating refresh | stateless API, refresh can be revoked | server sessions: sticky sessions or a session store |
| Tests | JUnit 5, Testcontainers | real Postgres + PostGIS + Redis in tests | H2: no PostGIS, different locking |
| Frontend | React + Vite + Leaflet | small, fast to build, free map tiles | Next.js: server rendering is not needed here |

## Run it locally

You need **Docker** (Docker Desktop on Windows or Mac). For development without Docker for the app,
also **JDK 21** and **Node 22**.

### Everything in Docker

```bash
git clone https://github.com/Darshan-dev57/Circl.git
cd Circl
docker compose --profile app up -d --build
```

Open http://localhost:3000. The API is on http://localhost:8080 and Swagger UI on
http://localhost:8080/swagger-ui.html.

Load some demo activities around Koramangala:

```bash
docker exec -i circl-postgres psql -U circl -d circl < scripts/demo_data.sql
```

Log in as the demo host `demo.host@circl.dev` / `circl-demo-pass`, or sign up as yourself.

### Development mode

```bash
docker compose up -d                        # only Postgres and Redis
./mvnw -pl backend spring-boot:run          # API on :8080
cd frontend && npm install && npm run dev   # app on :5173, /api is proxied to :8080
```

### On Windows (PowerShell)

Start Docker Desktop first (WSL 2 backend) and wait until it shows "Engine running". Then run these one line at a time:

```powershell
git clone https://github.com/Darshan-dev57/Circl.git
cd Circl
docker compose --profile app up -d --build
Get-Content scripts\demo_data.sql | docker exec -i circl-postgres psql -U circl -d circl
```

Development mode, with the frontend in a second PowerShell window:

```powershell
docker compose up -d
.\mvnw.cmd -pl backend spring-boot:run
```

```powershell
cd frontend
npm install
npm run dev
```

Tests: `.\mvnw.cmd verify` (Docker Desktop must be running, the integration tests start their own containers).

- PowerShell has no `<` redirect, which is why the SQL file is piped in with `Get-Content`.
- Windows PowerShell 5.1 does not understand `&&`, so run the commands as separate lines like above.
- `mvnw.cmd` needs JDK 21: `java -version` should say 21. If Maven picks another JDK, set `JAVA_HOME` to the JDK 21 folder.
- If port 5432, 6379, 8080 or 3000 is already taken (often a local PostgreSQL service on 5432), stop that service or change the left side of the port in `docker-compose.yml`, e.g. `"5433:5432"`. For development mode then also set `$env:DB_URL="jdbc:postgresql://localhost:5433/circl"` before starting the backend.
- Line endings: `.gitattributes` keeps `mvnw` LF and `mvnw.cmd` CRLF, so the default Git for Windows settings work.

### Configuration

| Variable | Default | Notes |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local compose values | |
| `REDIS_HOST`, `REDIS_PORT` | `localhost`, `6379` | |
| `CIRCL_JWT_SECRET` | dev-only value | set 32+ random characters anywhere real |
| `CIRCL_CHECKIN_SECRET` | dev-only value | signs the check-in QR codes |
| `COOKIE_SECURE` | `false` | set `true` behind HTTPS |
| `CIRCL_JOIN_STRATEGY` | `CONDITIONAL_UPDATE` | or `PESSIMISTIC`, `OPTIMISTIC` |
| `CACHE_TYPE` | `redis` | `none` turns the nearby cache off |
| `CORS_ORIGINS` | `http://localhost:5173` | browser origins allowed to call the API directly |
| `PORT` | `8080` | |

Copy `.env.example` to `.env` for Docker Compose. No real secret is committed.

## API

Everything is under `/api/v1`. Errors are [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457)
with a list of field errors for validation failures. A ready-made Postman collection is in
[`docs/postman`](docs/postman/circl.postman_collection.json). Run it top to bottom; the offer,
check-in and appeal requests only succeed when an activity is actually full, about to start or finished.

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/auth/signup` | anyone | create an account (role `PARTICIPANT` or `HOST`) |
| POST | `/auth/login` | anyone | tokens + refresh cookie |
| POST | `/auth/refresh` | anyone | rotate the refresh token, new access token |
| POST | `/auth/logout` | anyone | revoke the refresh token |
| GET | `/me` | user | my profile and score |
| POST | `/activities` | host | create an activity |
| GET | `/activities` | anyone | upcoming activities, paged |
| GET | `/activities/nearby?lat&lng&radiusKm&category` | anyone | nearby, sorted by distance |
| GET | `/activities/{id}` | anyone | details |
| PATCH | `/activities/{id}` | owner | edit title, time, capacity... |
| DELETE | `/activities/{id}` | owner | cancel (everyone is notified) |
| GET | `/activities/{id}/seats` | anyone | live seat count (Server-Sent Events) |
| POST | `/activities/{id}/join` | user | join or get waitlisted, needs `Idempotency-Key` |
| DELETE | `/activities/{id}/participants/me` | user | leave or leave the waitlist |
| GET | `/activities/{id}/participants/me` | user | my status: joined, waitlisted (position), offered |
| GET | `/activities/{id}/participants` | owner | who is coming |
| GET | `/me/activities?when=upcoming\|past&cursor` | user | my bookings, keyset paged |
| GET | `/me/hosting` | host | activities I host |
| GET | `/me/offers` | user | open waitlist offers |
| POST | `/waitlist/offers/{id}/claim` | user | claim held seats |
| POST | `/waitlist/offers/{id}/decline` | user | pass them on |
| POST | `/activities/{id}/confirm` | user | "still coming" |
| GET | `/activities/{id}/checkin-code` | owner | current QR code (60 s) |
| POST | `/activities/{id}/checkin` | user | check in with the code |
| POST | `/activities/{id}/attendance/{userId}/evidence` | owner | fix a failed scan |
| POST | `/activities/{id}/attendance/unreliable` | owner | check-in was down, no penalties |
| POST | `/me/attendance/{participantId}/appeal` | user | appeal a no-show (48 h) |
| POST | `/appeals/{id}/decision` | owner | accept or reject an appeal |
| GET | `/me/reliability` | user | score breakdown |
| POST | `/availability` | user | free now, for 15 to 180 minutes |
| GET | `/availability/nearby` | user | who is free nearby (rounded distance) |
| DELETE | `/availability/me` | user | not free any more |
| GET | `/me/notifications` | user | latest notifications |
| POST | `/me/notifications/{id}/read` | user | mark as read |

## Tests

```bash
./mvnw verify      # unit tests + integration tests against real Postgres/PostGIS and Redis (needs Docker)
cd frontend && npm run lint && npm run build
```

**127 tests**: 9 for the seat simulator, 32 unit tests and 86 integration tests that run against real PostgreSQL + PostGIS and Redis started by Testcontainers. No H2, because H2 has no PostGIS and locks rows differently.

What the integration tests cover, among other things: 60 parallel HTTP joins for 10 seats,
50 threads for the last seat under each strategy, idempotent replays, waitlist offers racing with
cancels, refresh token reuse, check-in windows, appeals, the free-now privacy rules, the outbox
retry and parking, and the live seat stream. CI runs the same on every push.

## Project structure

```
Circl
├── backend/                     Spring Boot API
│   └── src/main/java/com/darshan/circl
│       ├── activity/            activities, nearby search, live seat stream
│       ├── participation/       join engine, waitlist, offers, idempotency, ledger, my bookings
│       ├── attendance/          check-in, state machine, finalizer, appeals, reminders
│       ├── reliability/         score
│       ├── availability/        free now (Redis)
│       ├── identity/            signup, login, JWT, refresh tokens
│       ├── notification/        in-app notifications from outbox events
│       ├── common/              errors, outbox, paging, request id
│       └── config/              security, cache, scheduling, OpenAPI
├── frontend/                    React + Vite + Leaflet
├── seat-simulator/              the race condition in plain Java
├── load/                        k6 script for nearby search
├── scripts/                     demo data and a 100k seed
├── docs/                        diagrams (sources + PNG), screenshots, Postman collection
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## Roadmap

- Push notifications (web push) on top of the existing outbox events
- Host screen to review appeals in the UI (the API is there)
- Recurring activities ("every Sunday 7 am")
- Chat inside an activity
- Deploy with a managed Postgres and a public demo

## License

[MIT](LICENSE)
