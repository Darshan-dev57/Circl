package com.darshan.circl.participation.dto;

import java.time.Instant;
import java.util.UUID;

public record MyActivity(UUID activityId, String title, String category, Instant startsAt, String activityStatus,
                         UUID participantId, int partySize, String attendanceStatus) {
}
