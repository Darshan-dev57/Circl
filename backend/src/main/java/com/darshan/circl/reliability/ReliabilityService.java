package com.darshan.circl.reliability;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * score = (checked_in + 1) / (committed + 2) * 100, so a new user starts at 50 and one
 * bad day does not sink anyone. A no-show only counts once its appeal window has passed,
 * the appeal (if any) was rejected, and check-in was working that day. score_applied
 * makes the job safe to run any number of times.
 */
@Service
public class ReliabilityService {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityService.class);

    public record Breakdown(int committed, int checkedIn, int noShows, int score) {
    }

    private final JdbcTemplate jdbc;
    private final Duration appealWindow;
    private final Clock clock;

    public ReliabilityService(JdbcTemplate jdbc,
                              @Value("${circl.attendance.appeal-window:PT48H}") Duration appealWindow, Clock clock) {
        this.jdbc = jdbc;
        this.appealWindow = appealWindow;
        this.clock = clock;
    }

    public static int score(int checkedIn, int committed) {
        return (int) Math.round((checkedIn + 1) * 100.0 / (committed + 2));
    }

    @Scheduled(cron = "${circl.reliability.cron:0 30 3 * * *}", zone = "Asia/Kolkata")
    @SchedulerLock(name = "reliability-score", lockAtMostFor = "PT30M")
    public void nightly() {
        applyDueScores();
    }

    @Transactional
    public int applyDueScores() {
        Instant now = Instant.now(clock);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.user_id, p.attendance_status, p.no_show_at, a.attendance_unreliable, ap.status AS appeal
                  FROM participants p
                  JOIN activities a ON a.id = p.activity_id
                  LEFT JOIN no_show_appeals ap ON ap.participant_id = p.id
                 WHERE p.score_applied = false
                   AND p.status = 'JOINED'
                   AND a.attendance_finalized = true
                   AND p.attendance_status IN ('ATTENDED', 'NO_SHOW')
                 ORDER BY p.user_id
                 LIMIT 5000
                   FOR UPDATE OF p SKIP LOCKED
                """);
        Set<UUID> touched = new HashSet<>();
        int applied = 0;
        for (Map<String, Object> r : rows) {
            UUID participantId = (UUID) r.get("id");
            UUID userId = (UUID) r.get("user_id");
            String status = (String) r.get("attendance_status");
            String appeal = (String) r.get("appeal");
            boolean unreliableDay = Boolean.TRUE.equals(r.get("attendance_unreliable"));

            int committed = 0, checkedIn = 0, noShows = 0;
            if ("ATTENDED".equals(status)) {
                committed = 1;
                checkedIn = 1;
            } else {
                Instant noShowAt = ((Timestamp) r.get("no_show_at")).toInstant();
                boolean windowOpen = now.isBefore(noShowAt.plus(appealWindow));
                if (windowOpen || "OPEN".equals(appeal)) {
                    continue; // decide later
                }
                if (!unreliableDay && !"ACCEPTED".equals(appeal)) {
                    committed = 1;
                    noShows = 1;
                }
            }
            if (committed > 0) {
                jdbc.update("""
                        INSERT INTO reliability (user_id, committed, checked_in, no_shows, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                           SET committed = reliability.committed + EXCLUDED.committed,
                               checked_in = reliability.checked_in + EXCLUDED.checked_in,
                               no_shows = reliability.no_shows + EXCLUDED.no_shows,
                               updated_at = EXCLUDED.updated_at
                        """, userId, committed, checkedIn, noShows, Timestamp.from(now));
                touched.add(userId);
            }
            jdbc.update("UPDATE participants SET score_applied = true WHERE id = ?", participantId);
            applied++;
        }
        for (UUID userId : touched) {
            Map<String, Object> rel = jdbc.queryForMap("SELECT committed, checked_in FROM reliability WHERE user_id = ?", userId);
            int score = score((Integer) rel.get("checked_in"), (Integer) rel.get("committed"));
            jdbc.update("UPDATE reliability SET score = ? WHERE user_id = ?", score, userId);
            jdbc.update("UPDATE users SET reliability_score = ? WHERE id = ?", score, userId);
        }
        if (applied > 0) {
            log.info("Applied {} attendance results to {} users' reliability", applied, touched.size());
        }
        return applied;
    }

    @Transactional(readOnly = true)
    public Breakdown breakdown(UUID userId) {
        List<Breakdown> list = jdbc.query("SELECT committed, checked_in, no_shows, score FROM reliability WHERE user_id = ?",
                (rs, i) -> new Breakdown(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)), userId);
        return list.isEmpty() ? new Breakdown(0, 0, 0, score(0, 0)) : list.get(0);
    }
}
