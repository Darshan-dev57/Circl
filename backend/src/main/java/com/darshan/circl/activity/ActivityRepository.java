package com.darshan.circl.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    /** 1 = seats taken, 0 = not enough seats (or not open). Postgres re-checks the WHERE after a concurrent update. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Activity a
               set a.seatsTaken = a.seatsTaken + :seats, a.version = a.version + 1
             where a.id = :id
               and a.status = com.darshan.circl.activity.ActivityStatus.OPEN
               and a.seatsTaken + :seats <= a.capacity
            """)
    int tryTakeSeats(@Param("id") UUID id, @Param("seats") int seats);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activity a where a.id = :id")
    Optional<Activity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select a.hostId as hostId, a.status as status, a.startsAt as startsAt, a.minReliability as minReliability
              from Activity a where a.id = :id
            """)
    Optional<ActivityGate> findGateById(@Param("id") UUID id);

    @Query(value = """
            SELECT a.id, a.title, a.category, a.starts_at AS startsAt, a.latitude AS lat, a.longitude AS lng,
                   a.capacity, a.seats_taken AS seatsTaken,
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
