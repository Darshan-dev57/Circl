package com.darshan.circl.participation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    Optional<WaitlistEntry> findByActivityIdAndUserId(UUID activityId, UUID userId);

    List<WaitlistEntry> findByActivityIdAndStatusOrderByPosition(UUID activityId, WaitlistStatus status);

    List<WaitlistEntry> findByUserIdAndStatusIn(UUID userId, List<WaitlistStatus> statuses);

    @Query("""
            select count(w) from WaitlistEntry w
             where w.activityId = :activityId and w.status = com.darshan.circl.participation.WaitlistStatus.WAITING
               and w.position <= :position
            """)
    long rankOf(@Param("activityId") UUID activityId, @Param("position") long position);

    /** Moves an offer to a final state only if it is still open; 0 = someone else (claim or expiry) got there first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update WaitlistEntry w set w.status = :to
             where w.id = :id and w.status = com.darshan.circl.participation.WaitlistStatus.OFFERED
            """)
    int closeOffer(@Param("id") UUID id, @Param("to") WaitlistStatus to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update WaitlistEntry w set w.status = com.darshan.circl.participation.WaitlistStatus.CLAIMED
             where w.id = :id and w.userId = :userId
               and w.status = com.darshan.circl.participation.WaitlistStatus.OFFERED
               and w.claimDeadline > :now
            """)
    int claim(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            select w from WaitlistEntry w
             where w.status = com.darshan.circl.participation.WaitlistStatus.OFFERED and w.claimDeadline <= :now
             order by w.claimDeadline
            """)
    List<WaitlistEntry> findExpiredOffers(@Param("now") Instant now, org.springframework.data.domain.Pageable page);
}
