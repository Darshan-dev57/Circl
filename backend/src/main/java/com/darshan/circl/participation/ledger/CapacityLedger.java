package com.darshan.circl.participation.ledger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class CapacityLedger {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CapacityLedger(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID activityId, int delta, LedgerReason reason, UUID refId) {
        jdbc.update("INSERT INTO capacity_ledger (activity_id, delta, reason, ref_id, created_at) VALUES (?, ?, ?, ?, ?)",
                activityId, delta, reason.name(), refId, Timestamp.from(Instant.now(clock)));
    }

    public int balance(UUID activityId) {
        Integer sum = jdbc.queryForObject("SELECT COALESCE(SUM(delta), 0) FROM capacity_ledger WHERE activity_id = ?",
                Integer.class, activityId);
        return sum == null ? 0 : sum;
    }
}
