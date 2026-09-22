package com.darshan.circl.notification;

import com.darshan.circl.common.outbox.OutboxEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turns outbox events into in-app notifications for the people who care about them. */
@Component
public class EventNotifier {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    public EventNotifier(NotificationService notifications, JdbcTemplate jdbc) {
        this.notifications = notifications;
        this.jdbc = jdbc;
    }

    public void handle(OutboxEvent event) {
        Map<String, Object> data = event.getPayload();
        UUID activityId = event.getAggregateId();
        switch (event.getType()) {
            case "ParticipantJoined" -> toHost(event, activityId, "PARTICIPANT_JOINED",
                    nameOf(data.get("userId")) + " joined " + titleOf(activityId));
            case "ParticipantLeft" -> toHost(event, activityId, "PARTICIPANT_LEFT",
                    nameOf(data.get("userId")) + " left " + titleOf(activityId));
            case "WaitlistOffered" -> {
                int party = ((Number) data.get("partySize")).intValue();
                String seats = party == 1 ? "A seat" : party + " seats";
                String deadline = TIME.format(Instant.parse((String) data.get("claimDeadline")));
                notifications.notify(uuid(data.get("userId")), "WAITLIST_OFFER",
                        seats + " opened up for " + titleOf(activityId) + ". Claim before " + deadline + ".",
                        activityId, event.getId());
            }
            case "WaitlistOfferExpired" -> notifications.notify(uuid(data.get("userId")), "OFFER_EXPIRED",
                    "Your offer for " + titleOf(activityId) + " expired and went to the next person.", activityId, event.getId());
            case "ReconfirmRequested" -> notifications.notify(uuid(data.get("userId")), "RECONFIRM",
                    titleOf(activityId) + " starts in 2 hours. Still coming? Tap confirm.", activityId, event.getId());
            case "ActivityCancelled" -> {
                String message = titleOf(activityId) + " was cancelled by the host.";
                for (UUID user : participantsOf(activityId)) {
                    notifications.notify(user, "ACTIVITY_CANCELLED", message, activityId, event.getId());
                }
            }
            default -> {
                // nothing to tell anyone
            }
        }
    }

    private void toHost(OutboxEvent event, UUID activityId, String type, String message) {
        UUID host = jdbc.queryForObject("SELECT host_id FROM activities WHERE id = ?", UUID.class, activityId);
        notifications.notify(host, type, message, activityId, event.getId());
    }

    private String titleOf(UUID activityId) {
        return jdbc.queryForObject("SELECT title FROM activities WHERE id = ?", String.class, activityId);
    }

    private String nameOf(Object userId) {
        return jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, uuid(userId));
    }

    private List<UUID> participantsOf(UUID activityId) {
        return jdbc.queryForList("SELECT user_id FROM participants WHERE activity_id = ? AND status = 'JOINED'",
                UUID.class, activityId);
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID u ? u : UUID.fromString(String.valueOf(value));
    }
}
