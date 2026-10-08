package com.darshan.circl.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    @Query(value = """
            SELECT a.id, a.title, a.category, a.starts_at AS startsAt, a.capacity, a.seats_taken AS seatsTaken,
                   ST_Distance(a.location, ST_MakePoint(:lng, :lat)::geography) AS distanceM
            FROM activities a
            WHERE a.status = 'OPEN'
              AND a.starts_at > :now
              AND (CAST(:category AS varchar) IS NULL OR a.category = CAST(:category AS varchar))
              AND ST_DWithin(a.location, ST_MakePoint(:lng, :lat)::geography, :radiusM)
            ORDER BY a.location <-> ST_MakePoint(:lng, :lat)::geography, a.id
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyActivityRow> findNearby(@Param("lat") double lat,
                                       @Param("lng") double lng,
                                       @Param("radiusM") double radiusM,
                                       @Param("category") String category,
                                       @Param("now") Instant now,
                                       @Param("limit") int limit);

    Page<Activity> findByStatusAndStartsAtAfter(ActivityStatus status, Instant after, Pageable pageable);

    Page<Activity> findByStatusAndCategoryAndStartsAtAfter(ActivityStatus status, Category category,
                                                           Instant after, Pageable pageable);
}
