package com.darshan.circl.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbox health for Prometheus, read from the table when metrics are scraped:
 * how many events wait, how old the oldest one is, how many are failing and how many were parked.
 * A growing backlog or age means Kafka is down or the relay stopped.
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        Gauge.builder("circl.outbox.pending", () -> count(jdbc,
                        "SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND failed_at IS NULL"))
                .description("events waiting to be published").register(registry);
        Gauge.builder("circl.outbox.oldest.age", () -> count(jdbc, """
                        SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)
                          FROM outbox_events WHERE published_at IS NULL AND failed_at IS NULL"""))
                .baseUnit("seconds").description("age of the oldest unpublished event").register(registry);
        Gauge.builder("circl.outbox.retrying", () -> count(jdbc,
                        "SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND failed_at IS NULL AND attempts > 0"))
                .description("events whose last publish attempt failed").register(registry);
        Gauge.builder("circl.outbox.parked", () -> count(jdbc,
                        "SELECT count(*) FROM outbox_events WHERE failed_at IS NOT NULL"))
                .description("events given up on after 5 attempts").register(registry);
    }

    private static double count(JdbcTemplate jdbc, String sql) {
        Number n = jdbc.queryForObject(sql, Number.class);
        return n == null ? 0 : n.doubleValue();
    }
}
