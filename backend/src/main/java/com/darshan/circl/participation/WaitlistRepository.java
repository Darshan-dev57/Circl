package com.darshan.circl.participation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    Optional<WaitlistEntry> findByActivityIdAndUserId(UUID activityId, UUID userId);

    List<WaitlistEntry> findByActivityIdOrderByPosition(UUID activityId);

    @Query("select count(w) from WaitlistEntry w where w.activityId = :activityId and w.position <= :position")
    long rankOf(@Param("activityId") UUID activityId, @Param("position") long position);

    long countByActivityId(UUID activityId);
}
