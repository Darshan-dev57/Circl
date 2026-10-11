package com.darshan.circl;

import com.darshan.circl.common.outbox.OutboxRelay;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The metrics and request ids someone on call would look at. */
@SpringBootTest
// tests switch metrics export off unless asked; these tests read the Prometheus output
@AutoConfigureObservability(tracing = false)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ObservabilityIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    OutboxRelay relay;

    private String metrics() throws Exception {
        return mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void requestIdIsEchoedOrCreated() throws Exception {
        mvc.perform(get("/api/v1/activities").header("X-Request-Id", "trace-me-123"))
                .andExpect(header().string("X-Request-Id", "trace-me-123"));
        mvc.perform(get("/api/v1/activities").header("X-Request-Id", "bad id with spaces"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void prometheusShowsLatencyPoolOutboxBookingsKafkaAndJobs() throws Exception {
        TestUser host = Users.host(mvc);
        UUID activity = Activities.create(mvc, host, 2);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/activities/{id}/join", activity).header("Authorization", Users.participant(mvc).bearer())
                    .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/activities/{id}/join", activity).header("Authorization", host.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().is4xxClientError());
        relay.relayBatch(500);

        // the scheduled jobs and the Kafka listeners report after their first run
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(metrics())
                .contains("tasks_scheduled_execution_seconds_count")
                .contains("spring_kafka_listener_seconds_count"));

        String m = metrics();
        assertThat(m).contains("http_server_requests_seconds_count")      // latency and errors per endpoint
                .contains("hikaricp_connections_active")                     // database pool
                .contains("hikaricp_connections_pending")
                .contains("circl_outbox_pending")                            // outbox backlog
                .contains("circl_outbox_oldest_age_seconds")
                .contains("circl_outbox_retrying")
                .contains("circl_outbox_parked")
                .contains("circl_bookings_total{outcome=\"joined\",reason=\"none\"}")
                .contains("circl_bookings_total{outcome=\"waitlisted\",reason=\"none\"}")
                .contains("circl_bookings_total{outcome=\"failed\",reason=\"RuleViolationException\"}")
                .contains("kafka_consumer_fetch_manager_records_lag_max");   // consumer lag
    }
}
