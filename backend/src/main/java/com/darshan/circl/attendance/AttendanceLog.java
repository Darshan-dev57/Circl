package com.darshan.circl.attendance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class AttendanceLog {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AttendanceLog(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID participantId, AttendanceStatus from, AttendanceStatus to, Actor actor, String note) {
        jdbc.update("""
                INSERT INTO attendance_events (participant_id, from_status, to_status, actor, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, participantId, from.name(), to.name(), actor.name(), note, Timestamp.from(Instant.now(clock)));
    }
}
