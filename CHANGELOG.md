# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

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

[Unreleased]: https://github.com/Darshan-dev57/Circl/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Darshan-dev57/Circl/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Darshan-dev57/Circl/releases/tag/v0.1.0
