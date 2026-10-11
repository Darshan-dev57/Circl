#!/usr/bin/env bash
# Nearby search on 100k activities, with and without the GiST index (EXPLAIN ANALYZE inside Postgres).
# Needs the stack from `docker compose --profile app up -d --build` (the backend creates the tables).
# Usage: docs/benchmarks/nearby-index.sh > docs/benchmarks/results/nearby-index.txt
set -euo pipefail
cd "$(dirname "$0")/../.."
PSQL="docker exec -i circl-postgres psql -U circl -d circl -X -q"

seeded=$($PSQL -tA -c "SELECT count(*) FROM activities WHERE description = 'seeded demo activity'")
if [ "$seeded" -lt 100000 ]; then
  $PSQL -v n=$((100000 - seeded)) < scripts/seed_activities.sql > /dev/null
fi
$PSQL -c "ANALYZE activities" > /dev/null

echo "activities in table: $($PSQL -tA -c 'SELECT count(*) FROM activities')"
$PSQL < docs/benchmarks/nearby-index.sql | grep -E "^==|Execution Time|Index Scan|Seq Scan|Bitmap"
