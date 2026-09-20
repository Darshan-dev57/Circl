package com.darshan.circl.participation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JoinResponse(
        UUID activityId,
        JoinOutcome status,
        Long waitlistPosition,
        int seatsLeft
) {
    public enum JoinOutcome { JOINED, WAITLISTED }
}
