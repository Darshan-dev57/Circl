package com.darshan.circl.participation;

import com.darshan.circl.activity.Activity;
import com.darshan.circl.activity.ActivityCapacityIncreased;
import com.darshan.circl.activity.ActivityRepository;
import com.darshan.circl.common.error.ConflictException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.outbox.Outbox;
import com.darshan.circl.participation.ledger.CapacityLedger;
import com.darshan.circl.participation.ledger.LedgerReason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Hands freed seats to the waitlist. A freed seat goes to the first party in line that
 * fits: a party of 3 is never offered 2 seats, it keeps its place for the next free-up.
 * An offered party has its seats held for the claim window.
 */
@Component
public class WaitlistOffers {

    private final ActivityRepository activities;
    private final WaitlistRepository waitlist;
    private final ParticipantRepository participants;
    private final CapacityLedger ledger;
    private final Outbox outbox;
    private final Duration claimWindow;
    private final Clock clock;

    public WaitlistOffers(ActivityRepository activities, WaitlistRepository waitlist, ParticipantRepository participants,
                          CapacityLedger ledger, Outbox outbox,
                          @Value("${circl.waitlist.claim-window:PT15M}") Duration claimWindow, Clock clock) {
        this.activities = activities;
        this.waitlist = waitlist;
        this.participants = participants;
        this.ledger = ledger;
        this.outbox = outbox;
        this.claimWindow = claimWindow;
        this.clock = clock;
    }

    /** caller must hold the activity row lock */
    @Transactional(propagation = Propagation.MANDATORY)
    public void offerFreedSeats(Activity activity) {
        Instant now = Instant.now(clock);
        if (!activity.isOpen() || !activity.getStartsAt().isAfter(now)) {
            return;
        }
        for (WaitlistEntry next : waitlist.findByActivityIdAndStatusOrderByPosition(activity.getId(), WaitlistStatus.WAITING)) {
            if (activity.seatsLeft() == 0) {
                return;
            }
            if (next.getPartySize() > activity.seatsLeft()) {
                continue;
            }
            next.offer(now, claimWindow);
            activity.setSeatsTaken(activity.getSeatsTaken() + next.getPartySize());
            ledger.record(activity.getId(), next.getPartySize(), LedgerReason.OFFER, next.getId());
            outbox.append(activity.getId(), "WaitlistOffered", Map.of(
                    "activityId", activity.getId(), "userId", next.getUserId(), "offerId", next.getId(),
                    "partySize", next.getPartySize(), "claimDeadline", next.getClaimDeadline().toString()));
        }
    }

    @EventListener
    public void onCapacityIncreased(ActivityCapacityIncreased event) {
        offerFreedSeats(event.activity());
    }

    @Transactional
    public Participant claim(UUID offerId, UUID userId) {
        Instant now = Instant.now(clock);
        WaitlistEntry offer = waitlist.findById(offerId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Offer", offerId));
        if (waitlist.claim(offerId, userId, now) == 0) {
            throw new ConflictException("offer-expired", "This offer is no longer open");
        }
        Participant participant = participants.findByActivityIdAndUserId(offer.getActivityId(), userId).orElse(null);
        if (participant == null) {
            participant = new Participant(offer.getActivityId(), userId, offer.getPartySize(), now);
        } else {
            participant.rejoin(offer.getPartySize(), now);
        }
        participant = participants.saveAndFlush(participant);
        // the seats were already counted when the offer was made
        ledger.record(offer.getActivityId(), 0, LedgerReason.CLAIM, offerId);
        outbox.append(offer.getActivityId(), "WaitlistClaimed",
                Map.of("activityId", offer.getActivityId(), "userId", userId, "offerId", offerId));
        return participant;
    }

    @Transactional
    public void decline(UUID offerId, UUID userId) {
        WaitlistEntry offer = waitlist.findById(offerId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Offer", offerId));
        if (!release(offer.getActivityId(), offerId, WaitlistStatus.DECLINED)) {
            throw new ConflictException("offer-expired", "This offer is no longer open");
        }
    }

    /** returns false if the offer was already claimed, declined or expired */
    @Transactional
    public boolean expire(UUID activityId, UUID offerId) {
        return release(activityId, offerId, WaitlistStatus.EXPIRED);
    }

    private boolean release(UUID activityId, UUID offerId, WaitlistStatus to) {
        Activity activity = activities.findByIdForUpdate(activityId)
                .orElseThrow(() -> new NotFoundException("Activity", activityId));
        WaitlistEntry offer = waitlist.findById(offerId).orElseThrow();
        int seats = offer.getPartySize();
        UUID user = offer.getUserId();
        if (waitlist.closeOffer(offerId, to) == 0) {
            return false;
        }
        // closeOffer cleared the persistence context, so re-read the locked row
        activity = activities.findByIdForUpdate(activityId).orElseThrow();
        activity.setSeatsTaken(activity.getSeatsTaken() - seats);
        ledger.record(activityId, -seats, to == WaitlistStatus.EXPIRED ? LedgerReason.EXPIRE : LedgerReason.DECLINE, offerId);
        outbox.append(activityId, to == WaitlistStatus.EXPIRED ? "WaitlistOfferExpired" : "WaitlistOfferDeclined",
                Map.of("activityId", activityId, "userId", user, "offerId", offerId));
        offerFreedSeats(activity);
        return true;
    }
}
