# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.4.0] - 2026-10-09
### Added
- Apache Kafka (single KRaft node) in Docker Compose and in the integration tests.
- The outbox relay publishes events to the `circl.activity-events` topic, keyed by activity id, and marks a row published only after the broker acks it. If Kafka is down the row stays and is retried with backoff.
- Kafka consumers for in-app notifications (`circl-notifications`) and live seat counts (`circl-live-seats`). Both are safe to run twice for the same event.
- Dead letter topic `circl.activity-events-dlt` for records that keep failing.
- Tests for the outbox to Kafka round trip, duplicate delivery, a broker outage and the dead letter topic.
- `KAFKA_BOOTSTRAP_SERVERS` setting.

### Fixed
- Reconfirm reminders failed when the scheduler ran them (no transaction around the scheduled call).
- Login and other Redis-backed endpoints answer `503` instead of `500` when Redis stops responding.
- Signing up with a password over 72 bytes (for example 30 Kannada letters) returns `422` instead of `500`.
- Editing an activity with a title of only spaces is rejected with `400`.
- Check-in and the check-in code are refused for a cancelled activity.
- The host's "check-in was down" button appears once the activity starts, without a page reload.
- The demo data script can be run twice without duplicating activities.
- `backend/mvnw.cmd` no longer shows up as modified right after cloning.
- The outbox relay stops the batch when the poll itself fails, instead of logging the same error up to 100 times.

### Changed
- Notifications and live seat counts are handled by Kafka consumers instead of inside the outbox relay.
- README rewritten: overview, key features, architecture with Kafka, engineering highlights, configuration and testing sections.
- README Windows steps are plain PowerShell lines.

## [0.3.0] - 2026-10-09
### Added
- React + Vite + Leaflet frontend: map of nearby activities, activity page with a live seat ring, bookings, host view and a form to post activities.
- Live seat counts over Server-Sent Events, fanned out to every instance through a Redis channel.
- In-app notifications fed by the outbox, with retries, backoff and parking after 5 failures.
- Reconfirm reminder two hours before the start.
- "Free now" availability in Redis with a ~1 km grid, distance buckets and search limits.
- Redis cache for nearby search (30 s per ~110 m cell).
- My bookings with keyset paging, my status on an activity, and a list of activities I host.
- Host can report that check-in was down, so nobody gets a no-show penalty.
- Login limit: 5 wrong passwords block the email for 15 minutes.
- Swagger UI, Dockerfiles and a compose profile that runs the whole app, Maven wrapper, demo data script, Postman collection, diagrams and screenshots.

### Changed
- The refresh token is also sent as an HttpOnly, SameSite=Strict cookie; the body still works for API clients.
- Nearby results include the pin coordinates.
- CI builds and lints the frontend.

## [0.2.0] - 2026-10-09
### Added
- Join with friends (`partySize` 1-4) and a party-aware waitlist: a freed seat goes to the first party that fits and is held for 15 minutes.
- Claim and decline endpoints for waitlist offers, plus a ShedLock job that expires unclaimed offers.
- Append-only capacity ledger; `SUM(delta)` always equals `seats_taken`.
- Attendance state machine (RSVP, RECONFIRMED, CHECKED_IN, ATTENDED, CANCELLED, NO_SHOW) with an audit table.
- Signed 60-second check-in codes with a 15-minute late grace.
- No-show appeals with a 48-hour window and host decisions.
- Reliability score and a per-activity minimum score to join.

## [0.1.0] - 2026-10-08
### Added
- Plain Java seat simulator showing the race and three fixes.
- Activities API with validation, Problem Details errors and paging.
- Signup and login with BCrypt, JWT access tokens and rotating refresh tokens; host-only edits.
- Nearby search with PostGIS (`ST_DWithin`, GiST index, KNN ordering).
- Join engine with `Idempotency-Key`, three seat strategies (conditional update, pessimistic, optimistic) and a waitlist.
- Concurrency tests: 60 parallel joins for 10 seats and 50 threads for the last seat per strategy.

[Unreleased]: https://github.com/Darshan-dev57/Circl/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/Darshan-dev57/Circl/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Darshan-dev57/Circl/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Darshan-dev57/Circl/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Darshan-dev57/Circl/releases/tag/v0.1.0
