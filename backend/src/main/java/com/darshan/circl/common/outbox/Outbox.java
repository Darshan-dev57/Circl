package com.darshan.circl.common.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes an event row in the caller's transaction, so the event exists
 * exactly when the change it describes was committed.
 */
@Component
public class Outbox {

    private final OutboxRepository repository;
    private final Clock clock;

    public Outbox(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID aggregateId, String type, Map<String, Object> payload) {
        repository.save(new OutboxEvent(aggregateId, type, payload, Instant.now(clock)));
    }
}
