package com.darshan.circl.participation.dto;

import com.darshan.circl.participation.WaitlistEntry;
import com.darshan.circl.participation.WaitlistStatus;

import java.time.Instant;
import java.util.UUID;

public record OfferResponse(UUID offerId, UUID activityId, int partySize, WaitlistStatus status, Instant claimDeadline) {

    public static OfferResponse of(WaitlistEntry e) {
        return new OfferResponse(e.getId(), e.getActivityId(), e.getPartySize(), e.getStatus(), e.getClaimDeadline());
    }
}
