package com.darshan.circl.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    public record Notification(UUID id, String type, String message, UUID activityId, Instant createdAt, Instant readAt) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void notify(UUID userId, String type, String message, UUID activityId, UUID eventId) {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, message, activity_id, event_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, user_id) DO NOTHING
                """, userId, type, message, activityId, eventId, Timestamp.from(Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(UUID userId, int limit) {
        return jdbc.query("""
                SELECT id, type, message, activity_id, created_at, read_at FROM notifications
                 WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
                """, (rs, i) -> new Notification(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getObject(4, UUID.class), rs.getTimestamp(5).toInstant(),
                rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()), userId, limit);
    }

    @Transactional
    public boolean markRead(UUID userId, UUID notificationId) {
        return jdbc.update("UPDATE notifications SET read_at = ? WHERE id = ? AND user_id = ? AND read_at IS NULL",
                Timestamp.from(Instant.now(clock)), notificationId, userId) == 1;
    }
}
