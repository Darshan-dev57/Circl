package com.darshan.circl.common.outbox;

import com.darshan.circl.activity.live.SeatStream;
import com.darshan.circl.notification.EventNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Polls the outbox and hands each event to the notifier. FOR UPDATE SKIP LOCKED lets several
 * app instances run this at once without picking the same rows. Each event is processed in its
 * own transaction; a failing event is retried later with a growing delay and parked after 5 attempts,
 * so it never blocks the events behind it.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final OutboxRepository events;
    private final EventNotifier notifier;
    private final SeatStream seats;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OutboxRelay(JdbcTemplate jdbc, OutboxRepository events, EventNotifier notifier,
                       SeatStream seats, PlatformTransactionManager txManager, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.notifier = notifier;
        this.seats = seats;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${circl.outbox.poll:PT2S}")
    public void run() {
        relayBatch(100);
    }

    /** returns how many events were published */
    public int relayBatch(int limit) {
        int published = 0;
        Set<UUID> seatsChanged = new LinkedHashSet<>();
        for (int i = 0; i < limit; i++) {
            UUID[] current = new UUID[1];
            try {
                OutboxEvent done = tx.execute(status -> relayOne(current));
                if (done == null) {
                    break;
                }
                published++;
                if (SeatStream.SEAT_EVENTS.contains(done.getType())) {
                    seatsChanged.add(done.getAggregateId());
                }
            } catch (RuntimeException e) {
                if (current[0] == null) {
                    // failed before picking an event (database trouble), try again on the next poll
                    log.warn("Outbox poll failed: {}", e.getMessage());
                    break;
                }
                // the event's transaction is rolled back by now, so its row lock is gone
                log.warn("Outbox event {} failed: {}", current[0], e.getMessage());
                recordFailure(current[0], e);
            }
        }
        // after the commits, so browsers never see a seat count that was rolled back
        seats.publish(seatsChanged);
        return published;
    }

    /** null = nothing left to do */
    private OutboxEvent relayOne(UUID[] current) {
        List<UUID> next = jdbc.queryForList("""
                SELECT id FROM outbox_events
                 WHERE published_at IS NULL AND failed_at IS NULL
                   AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                 ORDER BY created_at
                 LIMIT 1
                   FOR UPDATE SKIP LOCKED
                """, UUID.class, Timestamp.from(Instant.now(clock)));
        if (next.isEmpty()) {
            return null;
        }
        current[0] = next.get(0);
        OutboxEvent event = events.findById(next.get(0)).orElseThrow();
        notifier.handle(event);
        event.markPublished(Instant.now(clock));
        return event;
    }

    private void recordFailure(UUID id, RuntimeException e) {
        Timestamp now = Timestamp.from(Instant.now(clock));
        tx.executeWithoutResult(s -> jdbc.update("""
                UPDATE outbox_events
                   SET attempts = attempts + 1,
                       last_error = left(?, 500),
                       next_attempt_at = CAST(? AS timestamptz) + make_interval(secs => 5 * (attempts + 1)),
                       failed_at = CASE WHEN attempts + 1 >= ? THEN CAST(? AS timestamptz) END
                 WHERE id = ?
                """, String.valueOf(e.getMessage()), now, MAX_ATTEMPTS, now, id));
    }
}
