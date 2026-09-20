package com.darshan.circl.participation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByActivityIdAndUserId(UUID activityId, UUID userId);

    long countByActivityIdAndStatus(UUID activityId, ParticipantStatus status);

    List<Participant> findByActivityIdAndStatus(UUID activityId, ParticipantStatus status);
}
