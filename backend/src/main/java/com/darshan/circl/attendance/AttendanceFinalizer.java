package com.darshan.circl.attendance;

import com.darshan.circl.activity.Activity;
import com.darshan.circl.activity.ActivityRepository;
import com.darshan.circl.participation.Participant;
import com.darshan.circl.participation.ParticipantRepository;
import com.darshan.circl.participation.ParticipantStatus;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Some time after an activity ends: checked-in people become ATTENDED, everyone else who
 * was still booked becomes NO_SHOW. Scores are not touched here; that waits for the appeal window.
 */
@Component
public class AttendanceFinalizer {

    private static final Logger log = LoggerFactory.getLogger(AttendanceFinalizer.class);

    private final JdbcTemplate jdbc;
    private final ActivityRepository activities;
    private final ParticipantRepository participants;
    private final AttendanceLog attendanceLog;
    private final TransactionTemplate tx;
    private final Duration afterEnd;
    private final Clock clock;

    public AttendanceFinalizer(JdbcTemplate jdbc, ActivityRepository activities, ParticipantRepository participants,
                               AttendanceLog attendanceLog, PlatformTransactionManager txManager,
                               @Value("${circl.attendance.finalize-after-end:PT2H}") Duration afterEnd, Clock clock) {
        this.jdbc = jdbc;
        this.activities = activities;
        this.participants = participants;
        this.attendanceLog = attendanceLog;
        this.tx = new TransactionTemplate(txManager);
        this.afterEnd = afterEnd;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${circl.attendance.finalize-every:PT5M}")
    @SchedulerLock(name = "attendance-finalizer", lockAtMostFor = "PT10M")
    public void run() {
        finalizeDue();
    }

    public int finalizeDue() {
        Instant cutoff = Instant.now(clock).minus(afterEnd);
        List<UUID> due = jdbc.queryForList("""
                SELECT id FROM activities
                 WHERE attendance_finalized = false AND status = 'OPEN'
                   AND starts_at + make_interval(mins => duration_minutes) <= ?
                 ORDER BY starts_at LIMIT 200
                """, UUID.class, Timestamp.from(cutoff));
        int done = 0;
        for (UUID id : due) {
            Boolean ok = tx.execute(s -> finalizeOne(id));
            if (Boolean.TRUE.equals(ok)) {
                done++;
            }
        }
        if (done > 0) {
            log.info("Finalized attendance for {} activities", done);
        }
        return done;
    }

    private boolean finalizeOne(UUID activityId) {
        Activity activity = activities.findByIdForUpdate(activityId).orElse(null);
        if (activity == null || activity.isAttendanceFinalized()) {
            return false;
        }
        Instant now = Instant.now(clock);
        for (Participant p : participants.findByActivityIdAndStatus(activityId, ParticipantStatus.JOINED)) {
            AttendanceStatus from = p.getAttendanceStatus();
            switch (from) {
                case CHECKED_IN -> {
                    p.moveTo(AttendanceStatus.ATTENDED);
                    attendanceLog.record(p.getId(), from, AttendanceStatus.ATTENDED, Actor.SYSTEM, null);
                }
                case RSVP, RECONFIRMED -> {
                    p.markNoShow(now);
                    attendanceLog.record(p.getId(), from, AttendanceStatus.NO_SHOW, Actor.SYSTEM, null);
                }
                default -> {
                    // already ATTENDED by the host, or CANCELLED
                }
            }
        }
        activity.markAttendanceFinalized();
        return true;
    }
}
