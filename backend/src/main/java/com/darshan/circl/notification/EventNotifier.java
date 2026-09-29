package com.darshan.circl.notification;

import com.darshan.circl.common.outbox.ActivityEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns activity events into in-app notifications for the people who care about them.
 * Safe to call twice for the same event: notifications are unique per (event, user).
 */
@Component
public class EventNotifier {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    public EventNotifier(NotificationService notifications, JdbcTemplate jdbc) {
        this.notifications = notifications;
        this.jdbc = jdbc;
    }

    public void handle(ActivityEvent event) {
        Map<String, Object> data = event.payload();
        UUID activityId = event.activityId();
        switch (event.type()) {
            case "ParticipantJoined" -> toHost(event, activityId, "PARTICIPANT_JOINED",
                    nameOf(data.get("userId")) + " joined " + titleOf(activityId));
            case "WaitlistClaimed" -> toHost(event, activityId, "PARTICIPANT_JOINED",
                    nameOf(data.get("userId")) + " joined " + titleOf(activityId) + " from the waitlist");
            case "ParticipantLeft" -> toHost(event, activityId, "PARTICIPANT_LEFT",
                    nameOf(data.get("userId")) + " left " + titleOf(activityId));
            case "WaitlistOffered" -> {
                int party = ((Number) data.get("partySize")).intValue();
                String seats = party == 1 ? "A seat" : party + " seats";
                String deadline = TIME.format(Instant.parse((String) data.get("claimDeadline")));
                notifications.notify(uuid(data.get("userId")), "WAITLIST_OFFER",
                        seats + " opened up for " + titleOf(activityId) + ". Claim before " + deadline + ".",
                        activityId, event.eventId());
            }
            case "WaitlistOfferExpired" -> notifications.notify(uuid(data.get("userId")), "OFFER_EXPIRED",
                    "Your offer for " + titleOf(activityId) + " expired and went to the next person.", activityId, event.eventId());
            case "ReconfirmRequested" -> notifications.notify(uuid(data.get("userId")), "RECONFIRM",
                    titleOf(activityId) + " starts at " + TIME.format(startOf(activityId)) + ". Still coming? Tap confirm.",
                    activityId, event.eventId());
            case "ActivityCancelled" -> {
                String message = titleOf(activityId) + " was cancelled by the host.";
                for (UUID user : participantsOf(activityId)) {
                    notifications.notify(user, "ACTIVITY_CANCELLED", message, activityId, event.eventId());
                }
            }
            default -> {
                // nothing to tell anyone
            }
        }
    }

    private void toHost(ActivityEvent event, UUID activityId, String type, String message) {
        UUID host = jdbc.queryForObject("SELECT host_id FROM activities WHERE id = ?", UUID.class, activityId);
        notifications.notify(host, type, message, activityId, event.eventId());
    }

    private String titleOf(UUID activityId) {
        return jdbc.queryForObject("SELECT title FROM activities WHERE id = ?", String.class, activityId);
    }

    private Instant startOf(UUID activityId) {
        return jdbc.queryForObject("SELECT starts_at FROM activities WHERE id = ?", java.sql.Timestamp.class, activityId)
                .toInstant();
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
