package com.darshan.circl.participation.dto;

import java.time.Instant;
import java.util.UUID;

public record ParticipantView(UUID participantId, UUID userId, String name, int partySize, String attendanceStatus,
                              boolean late, Instant joinedAt) {
}
