package com.darshan.circl.participation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    /** one query with a join (no N+1), keyset paging on (starts_at, id) */
    @Query(value = """
            SELECT a.id AS activityId, a.title, a.category, a.starts_at AS startsAt, a.status AS activityStatus,
                   p.id AS participantId, p.party_size AS partySize, p.attendance_status AS attendanceStatus
              FROM participants p
              JOIN activities a ON a.id = p.activity_id
             WHERE p.user_id = :userId AND p.status = 'JOINED' AND a.starts_at > :now
               AND (a.starts_at, a.id) > (:afterStartsAt, :afterId)
             ORDER BY a.starts_at, a.id
             LIMIT :size
            """, nativeQuery = true)
    List<MyActivityRow> findUpcoming(@Param("userId") UUID userId, @Param("now") Instant now,
                                     @Param("afterStartsAt") Instant afterStartsAt, @Param("afterId") UUID afterId,
                                     @Param("size") int size);

    @Query(value = """
            SELECT a.id AS activityId, a.title, a.category, a.starts_at AS startsAt, a.status AS activityStatus,
                   p.id AS participantId, p.party_size AS partySize, p.attendance_status AS attendanceStatus
              FROM participants p
              JOIN activities a ON a.id = p.activity_id
             WHERE p.user_id = :userId AND p.status = 'JOINED' AND a.starts_at <= :now
               AND (a.starts_at, a.id) < (:beforeStartsAt, :beforeId)
             ORDER BY a.starts_at DESC, a.id DESC
             LIMIT :size
            """, nativeQuery = true)
    List<MyActivityRow> findPast(@Param("userId") UUID userId, @Param("now") Instant now,
                                 @Param("beforeStartsAt") Instant beforeStartsAt, @Param("beforeId") UUID beforeId,
                                 @Param("size") int size);

    Optional<Participant> findByActivityIdAndUserId(UUID activityId, UUID userId);

    long countByActivityIdAndStatus(UUID activityId, ParticipantStatus status);

    List<Participant> findByActivityIdAndStatus(UUID activityId, ParticipantStatus status);
}
