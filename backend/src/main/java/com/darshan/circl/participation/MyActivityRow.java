package com.darshan.circl.participation;

import java.time.Instant;
import java.util.UUID;

public interface MyActivityRow {
    UUID getActivityId();
    String getTitle();
    String getCategory();
    Instant getStartsAt();
    String getActivityStatus();
    UUID getParticipantId();
    int getPartySize();
    String getAttendanceStatus();
}
