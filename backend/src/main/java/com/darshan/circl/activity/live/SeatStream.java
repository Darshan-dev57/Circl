package com.darshan.circl.activity.live;

import com.darshan.circl.activity.ActivityStatus;
import com.darshan.circl.common.error.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes seat counts to browsers with Server-Sent Events.
 * <p>
 * The outbox relay calls {@link #publish} after seats change. That goes through a Redis channel so every
 * app instance hears it and forwards it to the browsers connected to that instance.
 */
@Component
public class SeatStream {

    public static final String CHANNEL = "circl:seats";
    private static final Logger log = LoggerFactory.getLogger(SeatStream.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private static final int MAX_LISTENERS_PER_ACTIVITY = 500;

    /** outbox event types after which the seat numbers may be different */
    public static final Set<String> SEAT_EVENTS = Set.of("ParticipantJoined", "ParticipantLeft", "WaitlistOffered",
            "WaitlistClaimed", "WaitlistOfferExpired", "WaitlistOfferDeclined", "ActivityCancelled", "CapacityChanged");

    private final Map<UUID, List<SseEmitter>> listeners = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public SeatStream(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper json) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.json = json;
    }

    public SseEmitter subscribe(UUID activityId) {
        SeatUpdate current = snapshot(activityId);
        List<SseEmitter> list = listeners.computeIfAbsent(activityId, id -> new CopyOnWriteArrayList<>());
        if (list.size() >= MAX_LISTENERS_PER_ACTIVITY) {
            // the page falls back to polling the activity
            SseEmitter full = new SseEmitter(0L);
            full.completeWithError(new IllegalStateException("too many listeners"));
            return full;
        }
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(emitter, current);
        return emitter;
    }

    /** Called after the change is committed. Reads the fresh numbers and tells every instance. */
    public void publish(Collection<UUID> activityIds) {
        for (UUID id : activityIds) {
            try {
                redis.convertAndSend(CHANNEL, json.writeValueAsString(snapshot(id)));
            } catch (NotFoundException e) {
                // activity was deleted, nobody to tell
            } catch (JsonProcessingException | RuntimeException e) {
                // a missed push is fine, the next change sends the full numbers again
                log.warn("Could not publish seats for {}: {}", id, e.getMessage());
            }
        }
    }

    /** Message from the Redis channel: forward it to the browsers on this instance. */
    public void onMessage(String message) {
        try {
            SeatUpdate update = json.readValue(message, SeatUpdate.class);
            for (SseEmitter emitter : listeners.getOrDefault(update.activityId(), List.of())) {
                send(emitter, update);
            }
        } catch (IOException e) {
            log.warn("Bad seat message: {}", e.getMessage());
        }
    }

    // proxies close connections that stay silent, so send a comment line now and then
    @Scheduled(fixedDelayString = "PT25S")
    public void heartbeat() {
        listeners.values().forEach(list -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }));
        listeners.values().removeIf(List::isEmpty);
    }

    int listenerCount(UUID activityId) {
        return listeners.getOrDefault(activityId, List.of()).size();
    }

    private void send(SseEmitter emitter, SeatUpdate update) {
        try {
            emitter.send(SseEmitter.event().name("seats").data(update));
        } catch (IOException | IllegalStateException e) {
            // browser went away; onError/onCompletion removes it
            emitter.completeWithError(e);
        }
    }

    private SeatUpdate snapshot(UUID activityId) {
        try {
            return jdbc.queryForObject("""
                    SELECT capacity, seats_taken, status FROM activities WHERE id = ?
                    """, (rs, i) -> {
                int capacity = rs.getInt("capacity");
                int taken = rs.getInt("seats_taken");
                return new SeatUpdate(activityId, capacity, taken, Math.max(0, capacity - taken),
                        ActivityStatus.valueOf(rs.getString("status")));
            }, activityId);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("Activity", activityId);
        }
    }
}
