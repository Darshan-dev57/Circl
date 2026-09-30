-- The query ActivityRepository.findNearby runs, 3 km around Koramangala, 20 results.
-- Run 5 times with the GiST index, then 5 times with it dropped inside a transaction that is rolled back.
PREPARE nearby(double precision, double precision, double precision) AS
SELECT a.id, a.title, ST_Distance(a.location, ST_MakePoint($2, $1)::geography) AS distance_m
  FROM activities a
 WHERE a.status = 'OPEN'
   AND a.starts_at > now()
   AND ST_DWithin(a.location, ST_MakePoint($2, $1)::geography, $3)
 ORDER BY a.location <-> ST_MakePoint($2, $1)::geography, a.id
 LIMIT 20;

\echo == with GiST index
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);

BEGIN;
DROP INDEX idx_activities_location;
\echo == without GiST index
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
EXPLAIN (ANALYZE) EXECUTE nearby(12.9352, 77.6245, 3000);
ROLLBACK;
