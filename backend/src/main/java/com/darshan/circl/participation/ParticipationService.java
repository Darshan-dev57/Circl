package com.darshan.circl.participation;

import com.darshan.circl.activity.Activity;
import com.darshan.circl.activity.ActivityGate;
import com.darshan.circl.activity.ActivityRepository;
import com.darshan.circl.activity.ActivityStatus;
import com.darshan.circl.common.error.ConflictException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.error.RuleViolationException;
import com.darshan.circl.common.outbox.Outbox;
import com.darshan.circl.identity.TokenService;
import com.darshan.circl.participation.dto.JoinResponse;
import com.darshan.circl.participation.dto.JoinResponse.JoinOutcome;
import com.darshan.circl.participation.engine.JoinStrategy;
import com.darshan.circl.participation.engine.SeatAllocator;
import com.darshan.circl.participation.idempotency.IdempotencyService;
import com.darshan.circl.participation.ledger.CapacityLedger;
import com.darshan.circl.participation.ledger.LedgerReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ParticipationService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationService.class);
    private static final int MAX_ATTEMPTS = 10;

    public record JoinResult(JoinResponse response, boolean replayed, int attempts) {
    }

    private final ActivityRepository activities;
    private final ParticipantRepository participants;
    private final WaitlistRepository waitlist;
    private final IdempotencyService idempotency;
    private final CapacityLedger ledger;
    private final WaitlistOffers offers;
    private final Outbox outbox;
    private final Map<JoinStrategy, SeatAllocator> allocators = new EnumMap<>(JoinStrategy.class);
    private final JoinStrategy defaultStrategy;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ParticipationService(ActivityRepository activities, ParticipantRepository participants,
                                WaitlistRepository waitlist, IdempotencyService idempotency,
                                CapacityLedger ledger, WaitlistOffers offers, Outbox outbox,
                                List<SeatAllocator> allocators,
                                @Value("${circl.join.strategy:CONDITIONAL_UPDATE}") JoinStrategy defaultStrategy,
                                PlatformTransactionManager txManager, Clock clock) {
        this.activities = activities;
        this.participants = participants;
        this.waitlist = waitlist;
        this.idempotency = idempotency;
        this.ledger = ledger;
        this.offers = offers;
        this.outbox = outbox;
        allocators.forEach(a -> this.allocators.put(a.strategy(), a));
        this.defaultStrategy = defaultStrategy;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public JoinResult join(UUID activityId, UUID userId, int partySize, String idempotencyKey) {
        return join(activityId, userId, partySize, idempotencyKey, defaultStrategy);
    }

    /**
     * Each attempt is its own transaction. Only the optimistic strategy ever needs
     * a second attempt (someone bumped the activity version between our read and write).
     */
    public JoinResult join(UUID activityId, UUID userId, int partySize, String idempotencyKey, JoinStrategy strategy) {
        SeatAllocator allocator = allocators.get(strategy);
        String requestHash = TokenService.sha256(activityId + "|" + partySize);
        for (int attempt = 1; ; attempt++) {
            try {
                int attemptNo = attempt;
                return tx.execute(status -> joinOnce(activityId, userId, partySize, idempotencyKey, requestHash, allocator, attemptNo));
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Join on {} gave up after {} optimistic conflicts", activityId, attempt);
                    throw new ConflictException("join-contended", "Too many people are joining right now, please retry");
                }
                backoff(attempt);
            } catch (DataIntegrityViolationException e) {
                // two parallel requests for the same user with different keys: the unique constraint picks one
                if (isDuplicateJoin(e)) {
                    throw new ConflictException("already-joined", "You already joined or are on the waitlist");
                }
                throw e;
            }
        }
    }

    private JoinResult joinOnce(UUID activityId, UUID userId, int partySize, String key, String requestHash,
                                SeatAllocator allocator, int attempt) {
        var replay = idempotency.begin(userId, key, requestHash, JoinResponse.class);
        if (replay.isPresent()) {
            return new JoinResult(replay.get().body(), true, attempt);
        }

        Instant now = Instant.now(clock);
        ActivityGate gate = activities.findGateById(activityId)
                .orElseThrow(() -> new NotFoundException("Activity", activityId));
        if (gate.getStatus() != ActivityStatus.OPEN || !gate.getStartsAt().isAfter(now)) {
            throw new RuleViolationException("activity-closed", "This activity is not open for joining");
        }
        if (gate.getHostId().equals(userId)) {
            throw new RuleViolationException("host-cannot-join", "You are hosting this activity");
        }

        Participant existing = participants.findByActivityIdAndUserId(activityId, userId).orElse(null);
        if (existing != null && existing.isJoined()) {
            throw new ConflictException("already-joined", "You already joined this activity");
        }
        var queued = waitlist.findByActivityIdAndUserId(activityId, userId);
        if (queued.isPresent()) {
            if (queued.get().getStatus().isActive()) {
                throw new ConflictException("already-waitlisted", "You are already on the waitlist");
            }
            waitlist.delete(queued.get()); // an old expired or declined offer
            waitlist.flush();
        }

        JoinResponse response;
        if (allocator.tryTakeSeats(activityId, partySize)) {
            Participant p;
            if (existing == null) {
                p = participants.saveAndFlush(new Participant(activityId, userId, partySize, now));
            } else {
                existing.rejoin(partySize, now);
                p = participants.saveAndFlush(existing);
            }
            ledger.record(activityId, partySize, LedgerReason.JOIN, p.getId());
            outbox.append(activityId, "ParticipantJoined",
                    Map.of("activityId", activityId, "userId", userId, "partySize", partySize));
            response = new JoinResponse(activityId, JoinOutcome.JOINED, partySize, null, seatsLeft(activityId));
        } else {
            WaitlistEntry entry = waitlist.saveAndFlush(new WaitlistEntry(activityId, userId, partySize, now));
            long rank = waitlist.rankOf(activityId, entry.getPosition());
            outbox.append(activityId, "ParticipantWaitlisted",
                    Map.of("activityId", activityId, "userId", userId, "partySize", partySize));
            response = new JoinResponse(activityId, JoinOutcome.WAITLISTED, partySize, rank, seatsLeft(activityId));
        }

        idempotency.complete(userId, key, 200, response);
        return new JoinResult(response, false, attempt);
    }

    /**
     * Leaving frees the seats and offers them to the waitlist in the same transaction,
     * with the activity row locked, so one freed seat can never be offered twice.
     */
    @Transactional
    public void leave(UUID activityId, UUID userId) {
        Instant now = Instant.now(clock);
        Activity activity = activities.findByIdForUpdate(activityId)
                .orElseThrow(() -> new NotFoundException("Activity", activityId));

        var queued = waitlist.findByActivityIdAndUserId(activityId, userId)
                .filter(w -> w.getStatus().isActive());
        if (queued.isPresent()) {
            WaitlistEntry entry = queued.get();
            if (entry.getStatus() == WaitlistStatus.OFFERED) {
                offers.decline(entry.getId(), userId);
            } else {
                waitlist.delete(entry);
                outbox.append(activityId, "WaitlistLeft", Map.of("activityId", activityId, "userId", userId));
            }
            return;
        }

        Participant participant = participants.findByActivityIdAndUserId(activityId, userId)
                .filter(Participant::isJoined)
                .orElseThrow(() -> new NotFoundException("Participation of user", userId));
        participant.leave(now);
        activity.setSeatsTaken(activity.getSeatsTaken() - participant.getPartySize());
        ledger.record(activityId, -participant.getPartySize(), LedgerReason.CANCEL, participant.getId());
        outbox.append(activityId, "ParticipantLeft", Map.of("activityId", activityId, "userId", userId));
        offers.offerFreedSeats(activity);
    }

    private int seatsLeft(UUID activityId) {
        return activities.findById(activityId).map(Activity::seatsLeft).orElse(0);
    }

    private static boolean isDuplicateJoin(DataIntegrityViolationException e) {
        String msg = String.valueOf(e.getMostSpecificCause().getMessage());
        return msg.contains("participants_activity_user_unique") || msg.contains("waitlist_activity_user_unique");
    }

    private static void backoff(int attempt) {
        long maxMs = Math.min(50, 5L * attempt);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, maxMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
