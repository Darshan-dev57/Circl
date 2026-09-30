# Benchmarks

Every number in the main README's Performance table comes from one of the commands below. The raw output of
the last run is committed in [`results/`](results).

## Machine and setup

- One shared Linux box: 4 CPUs, 8 GB RAM, Debian 12, Docker 20.10. Application, databases and load generator all
  ran on it at the same time, and other builds were running on it too (load average is written at the top of
  each result file). Absolute numbers will differ on your machine; compare the pairs, not the values.
- Java 21 (Temurin), Spring Boot 3.5, PostgreSQL 16 + PostGIS 3.4, Redis 7, Apache Kafka 3.9 (single broker),
  k6 for HTTP load. Backend started from the jar with the default settings (`CACHE_TYPE` switched per run).
- Measured on Oct 11, 2026 at commit `d0c80ed`.

## Start the stack

```bash
./mvnw -B package -DskipTests        # builds backend/target/circl-*.jar (needs Java 21)
docker compose up -d --wait postgres redis kafka
java -jar backend/target/circl-*.jar # creates the schema; leave it running unless a script says otherwise
```

## Results

| What | Command | Source | Result (this machine) |
|---|---|---|---|
| Nearby search query, 100k activities, 3 km, 20 results | `docs/benchmarks/nearby-index.sh` | [`nearby-index.sql`](nearby-index.sql) (same SQL as `ActivityRepository.findNearby`, `EXPLAIN ANALYZE`, index dropped inside a rolled-back transaction) | without the GiST index 97 to 157 ms (seq scan); with it 15 to 38 ms (bitmap index scan), first cold run 92 ms. [results](results/nearby-index.txt) |
| Nearby search API under load | stop the backend, then `docs/benchmarks/nearby-load.sh` | [`load/nearby.js`](../../load/nearby.js) (k6, 50 virtual users, 30 s, 5 areas with jitter) | cache off: p95 492 to 661 ms, 166 to 227 req/s; Redis: p95 73 to 86 ms, 1318 to 1594 req/s; 0 errors. [results](results/nearby-load.txt) |
| 60 concurrent HTTP joins for 10 seats | `./mvnw verify` | `JoinRaceIT.sixtyParallelHttpJoinsForTenSeats` | 10 joined, 50 waitlisted, 0 overbooked, in all 7 runs. [results](results/race-results.txt) |
| 50 threads for the last seat, 5 rounds per strategy | `./mvnw verify` | `JoinRaceIT.fiftyThreadsForTheLastSeat` | 0 overbooked in all 7 runs. p50: conditional update 126 to 166 ms, pessimistic lock 148 to 213 ms, optimistic 78 to 147 ms with 31 to 45 retries. [results](results/race-results.txt) |
| 10 parallel requests with one `Idempotency-Key` | `./mvnw verify` | `JoinRaceIT.tenParallelRetriesWithTheSameKeyJoinOnce` | 1 participant row, 1 seat taken (assertion) |
| Kafka broker down during 20 joins | `python3 docs/benchmarks/kafka-outage.py` | [`kafka-outage.py`](kafka-outage.py) (pauses the broker container) | all 20 events waited in the outbox, all published 0.7 to 6.4 s after the broker came back (depends on where the retry backoff is), 20 of 20 notifications, 0 lost. [results](results/kafka-outage.txt) |
| Join to live seat update (SSE) | `python3 docs/benchmarks/seat-update.py` | [`seat-update.py`](seat-update.py) | 0.73 to 0.94 s, median 0.90 s over 10 joins, measured at the API (most of it is the 2 s outbox poll). [results](results/seat-update.txt) |
| Seat simulator, 100 threads, 10 seats | `java -cp seat-simulator/target/classes com.darshan.circl.sim.SeatSimulator` | [`seat-simulator`](../../seat-simulator) | no lock: 28 to 51 booked (18 to 41 overbooked); synchronized, CAS and ReentrantLock: exactly 10. [results](results/seat-simulator.txt) |
| Endpoint count | `curl -s localhost:8080/v3/api-docs \| jq '[.paths[] \| keys[]] \| length'` | OpenAPI document | 34 |

The race and idempotency numbers are also hard assertions in the test suite, so CI fails if they ever break.
