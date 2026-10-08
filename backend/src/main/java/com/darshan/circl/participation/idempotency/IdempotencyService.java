package com.darshan.circl.participation.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.darshan.circl.common.error.ConflictException;
import com.darshan.circl.common.error.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency-Key support. The key row is inserted in the SAME transaction as the
 * work, so either both commit (and a retry gets the saved response) or neither does.
 */
@Service
public class IdempotencyService {

    public record Saved<T>(int status, T body) {
    }

    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    /** empty = key is ours, go do the work; present = replay this response */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Optional<Saved<T>> begin(UUID userId, String key, String requestHash, Class<T> type) {
        Instant now = Instant.now(clock);
        if (repository.tryClaim(userId, key, requestHash, now, now.plus(TTL)) == 1) {
            return Optional.empty();
        }
        IdempotencyRecord existing = repository.findById(new IdempotencyId(userId, key))
                .orElseThrow(() -> new ConflictException("idempotency-in-progress", "Request with this key is still running"));
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new RuleViolationException("idempotency-key-reused",
                    "This Idempotency-Key was already used with a different request");
        }
        if (!existing.isCompleted()) {
            throw new ConflictException("idempotency-in-progress", "Request with this key is still running");
        }
        try {
            return Optional.of(new Saved<>(existing.getResponseStatus(), json.readValue(existing.getResponseBody(), type)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is unreadable", e);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(UUID userId, String key, int status, Object body) {
        IdempotencyRecord record = repository.findById(new IdempotencyId(userId, key)).orElseThrow();
        try {
            record.complete(status, json.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
