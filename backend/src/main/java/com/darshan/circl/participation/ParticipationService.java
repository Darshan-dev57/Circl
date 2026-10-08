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
    private final Outbox outbox;
    private final Map<JoinStrategy, SeatAllocator> allocators = new EnumMap<>(JoinStrategy.class);
    private final JoinStrategy defaultStrategy;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ParticipationService(ActivityRepository activities, ParticipantRepository participants,
                                WaitlistRepository waitlist, IdempotencyService idempotency, Outbox outbox,
                                List<SeatAllocator> allocators,
                                @Value("${circl.join.strategy:CONDITIONAL_UPDATE}") JoinStrategy defaultStrategy,
                                PlatformTransactionManager txManager, Clock clock) {
        this.activities = activities;
        this.participants = participants;
        this.waitlist = waitlist;
        this.idempotency = idempotency;
        this.outbox = outbox;
        allocators.forEach(a -> this.allocators.put(a.strategy(), a));
        this.defaultStrategy = defaultStrategy;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public JoinResult join(UUID activityId, UUID userId, String idempotencyKey) {
        return join(activityId, userId, idempotencyKey, defaultStrategy);
    }

    /**
     * Each attempt is its own transaction. Only the optimistic strategy ever needs
     * a second attempt (someone bumped the activity version between our read and write).
     */
    public JoinResult join(UUID activityId, UUID userId, String idempotencyKey, JoinStrategy strategy) {
        SeatAllocator allocator = allocators.get(strategy);
        String requestHash = TokenService.sha256(activityId.toString());
        for (int attempt = 1; ; attempt++) {
            try {
                int attemptNo = attempt;
                return tx.execute(status -> joinOnce(activityId, userId, idempotencyKey, requestHash, allocator, attemptNo));
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

    private JoinResult joinOnce(UUID activityId, UUID userId, String key, String requestHash,
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
        if (waitlist.findByActivityIdAndUserId(activityId, userId).isPresent()) {
            throw new ConflictException("already-waitlisted", "You are already on the waitlist");
        }

        JoinResponse response;
        if (allocator.tryTakeSeats(activityId, 1)) {
            if (existing == null) {
                participants.saveAndFlush(new Participant(activityId, userId, now));
            } else {
                existing.rejoin(now);
                participants.saveAndFlush(existing);
            }
            outbox.append(activityId, "ParticipantJoined", Map.of("activityId", activityId, "userId", userId));
            response = new JoinResponse(activityId, JoinOutcome.JOINED, null, seatsLeft(activityId));
        } else {
            WaitlistEntry entry = waitlist.saveAndFlush(new WaitlistEntry(activityId, userId, now));
            long rank = waitlist.rankOf(activityId, entry.getPosition());
            outbox.append(activityId, "ParticipantWaitlisted", Map.of("activityId", activityId, "userId", userId));
            response = new JoinResponse(activityId, JoinOutcome.WAITLISTED, rank, 0);
        }

        idempotency.complete(userId, key, 200, response);
        return new JoinResult(response, false, attempt);
    }

    /**
     * Leaving frees the seat and hands it to the first person on the waitlist, all in one
     * transaction with the activity row locked, so a freed seat cannot be handed out twice.
     */
    @Transactional
    public void leave(UUID activityId, UUID userId) {
        Instant now = Instant.now(clock);
        Activity activity = activities.findByIdForUpdate(activityId)
                .orElseThrow(() -> new NotFoundException("Activity", activityId));

        var queued = waitlist.findByActivityIdAndUserId(activityId, userId);
        if (queued.isPresent()) {
            waitlist.delete(queued.get());
            outbox.append(activityId, "WaitlistLeft", Map.of("activityId", activityId, "userId", userId));
            return;
        }

        Participant participant = participants.findByActivityIdAndUserId(activityId, userId)
                .filter(Participant::isJoined)
                .orElseThrow(() -> new NotFoundException("Participation of user", userId));
        participant.leave(now);
        activity.setSeatsTaken(activity.getSeatsTaken() - 1);
        outbox.append(activityId, "ParticipantLeft", Map.of("activityId", activityId, "userId", userId));

        if (activity.isOpen() && activity.getStartsAt().isAfter(now)) {
            promoteFromWaitlist(activity, now);
        }
    }

    private void promoteFromWaitlist(Activity activity, Instant now) {
        for (WaitlistEntry next : waitlist.findByActivityIdOrderByPosition(activity.getId())) {
            if (activity.seatsLeft() < 1) {
                return;
            }
            Participant p = participants.findByActivityIdAndUserId(activity.getId(), next.getUserId())
                    .orElse(null);
            if (p == null) {
                participants.save(new Participant(activity.getId(), next.getUserId(), now));
            } else {
                p.rejoin(now);
            }
            waitlist.delete(next);
            activity.setSeatsTaken(activity.getSeatsTaken() + 1);
            outbox.append(activity.getId(), "WaitlistPromoted",
                    Map.of("activityId", activity.getId(), "userId", next.getUserId()));
        }
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
