package com.darshan.circl.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NoShowAppealRepository extends JpaRepository<NoShowAppeal, UUID> {

    Optional<NoShowAppeal> findByParticipantId(UUID participantId);

    boolean existsByParticipantId(UUID participantId);
}
