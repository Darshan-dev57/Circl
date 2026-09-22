package com.darshan.circl.attendance;

import com.darshan.circl.common.outbox.Outbox;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Two hours before the start, asks everyone who has not confirmed yet whether they are still coming. */
@Component
public class ReconfirmReminderJob {

    private static final Duration BEFORE = Duration.ofHours(2);

    private final JdbcTemplate jdbc;
    private final Outbox outbox;
    private final Clock clock;

    public ReconfirmReminderJob(JdbcTemplate jdbc, Outbox outbox, Clock clock) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1M")
    @SchedulerLock(name = "reconfirm-reminders", lockAtMostFor = "PT5M")
    public void run() {
        sendDue();
    }

    @Transactional
    public int sendDue() {
        Instant now = Instant.now(clock);
        List<Map<String, Object>> due = jdbc.queryForList("""
                UPDATE participants p SET reminded_at = ?
                  FROM activities a
                 WHERE a.id = p.activity_id
                   AND p.status = 'JOINED' AND p.attendance_status = 'RSVP' AND p.reminded_at IS NULL
                   AND a.status = 'OPEN' AND a.starts_at > ? AND a.starts_at <= ?
                RETURNING p.activity_id, p.user_id
                """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plus(BEFORE)));
        for (Map<String, Object> row : due) {
            UUID activityId = (UUID) row.get("activity_id");
            outbox.append(activityId, "ReconfirmRequested", Map.of("activityId", activityId, "userId", row.get("user_id")));
        }
        return due.size();
    }
}
