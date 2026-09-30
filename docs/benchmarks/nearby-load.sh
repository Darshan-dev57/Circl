#!/usr/bin/env bash
# Nearby search through the API under load (k6, 50 virtual users, 30 s), first with the cache off, then with Redis.
# Needs: k6, `./mvnw -B package -DskipTests`, `docker compose up -d` (postgres, redis, kafka) and the 100k seed
# (nearby-index.sh adds it). Stop any backend on port 8080 first; this script starts its own from the jar.
# Usage: docs/benchmarks/nearby-load.sh > docs/benchmarks/results/nearby-load.txt
set -euo pipefail
cd "$(dirname "$0")/../.."
JAR=$(ls backend/target/circl-*.jar | grep -v original | head -1)

for cache in none redis; do
  CACHE_TYPE=$cache ACTUATOR_ENDPOINTS=health java -Xmx512m -jar "$JAR" > /tmp/circl-bench-backend.log 2>&1 &
  backend=$!
  for _ in $(seq 1 60); do
    curl -sf http://localhost:8080/actuator/health/readiness > /dev/null && break
    sleep 2
  done
  docker exec circl-redis redis-cli FLUSHALL > /dev/null
  curl -s "http://localhost:8080/api/v1/activities/nearby?lat=12.9352&lng=77.6245&radiusKm=3" > /dev/null  # warm up
  echo "== CACHE_TYPE=$cache"
  k6 run --quiet --summary-trend-stats="avg,med,p(95),max" load/nearby.js 2>&1 \
    | grep -E "http_req_duration|http_reqs|http_req_failed"
  kill "$backend"
  wait "$backend" 2>/dev/null || true
done
