package com.darshan.circl.common.outbox;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls the outbox and publishes each event to Kafka, keyed by activity id so one activity's events
 * land on one partition in order. A row is marked published only after the broker acks it. If the
 * broker is down the row stays, and is retried later with a growing delay (parked after 5 attempts).
 *
 * The app crashing between the ack and the commit means the event goes out twice, which is why
 * the consumers are idempotent.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final OutboxRepository events;
    private final KafkaTemplate<String, ActivityEvent> kafka;
    private final String topic;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OutboxRelay(JdbcTemplate jdbc, OutboxRepository events, KafkaTemplate<String, ActivityEvent> kafka,
                       @Value("${circl.events.topic}") String topic, PlatformTransactionManager txManager, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.kafka = kafka;
        this.topic = topic;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    // one relay at a time across instances, otherwise two of them could send one activity's events out of order
    @Scheduled(fixedDelayString = "${circl.outbox.poll:PT2S}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT1M")
    public void run() {
        relayBatch(100);
    }

    /** returns how many events were published */
    public int relayBatch(int limit) {
        int published = 0;
        for (int i = 0; i < limit; i++) {
            UUID[] current = new UUID[1];
            try {
                Boolean sent = tx.execute(status -> relayOne(current));
                if (!Boolean.TRUE.equals(sent)) {
                    break;
                }
                published++;
            } catch (RuntimeException e) {
                if (current[0] == null) {
                    // failed before picking an event (database trouble), try again on the next poll
                    log.warn("Outbox poll failed: {}", e.getMessage());
                    break;
                }
                log.warn("Outbox event {} not published: {}", current[0], e.getMessage());
                recordFailure(current[0], e);
                // the broker is probably down, so the rest would fail the same way
                break;
            }
        }
        return published;
    }

    /** false = nothing left to do */
    private boolean relayOne(UUID[] current) {
        List<UUID> next = jdbc.queryForList("""
                SELECT id FROM outbox_events
                 WHERE published_at IS NULL AND failed_at IS NULL
                   AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                 ORDER BY created_at
                 LIMIT 1
                   FOR UPDATE SKIP LOCKED
                """, UUID.class, Timestamp.from(Instant.now(clock)));
        if (next.isEmpty()) {
            return false;
        }
        current[0] = next.get(0);
        OutboxEvent event = events.findById(next.get(0)).orElseThrow();
        send(event);
        event.markPublished(Instant.now(clock));
        return true;
    }

    private void send(OutboxEvent event) {
        try {
            kafka.send(topic, event.getAggregateId().toString(), ActivityEvent.from(event)).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while publishing", e);
        } catch (ExecutionException | TimeoutException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("kafka send failed: " + cause.getMessage(), e);
        }
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
