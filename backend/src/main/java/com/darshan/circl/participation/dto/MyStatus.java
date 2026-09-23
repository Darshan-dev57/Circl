package com.darshan.circl.participation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/** Where the current user stands on one activity: JOINED, WAITLISTED, OFFERED or NONE. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyStatus(String status, Integer partySize, Long waitlistPosition, UUID offerId, Instant claimDeadline,
                       String attendanceStatus) {

    public static MyStatus none() {
        return new MyStatus("NONE", null, null, null, null, null);
    }
}
