package com.darshan.circl.common.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** What goes on the Kafka topic for one outbox row. eventId is the outbox row id, so consumers can dedupe on it. */
public record ActivityEvent(UUID eventId, UUID activityId, String type, Map<String, Object> payload, Instant createdAt) {

    public static ActivityEvent from(OutboxEvent row) {
        return new ActivityEvent(row.getId(), row.getAggregateId(), row.getType(), row.getPayload(), row.getCreatedAt());
    }
}
