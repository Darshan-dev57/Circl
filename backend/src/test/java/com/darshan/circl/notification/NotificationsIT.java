package com.darshan.circl.notification;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.attendance.ReconfirmReminderJob;
import com.darshan.circl.common.outbox.ActivityEvent;
import com.darshan.circl.common.outbox.OutboxRelay;
import com.darshan.circl.common.outbox.OutboxRepository;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class NotificationsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxRelay relay;

    @Autowired
    ReconfirmReminderJob reminders;

    private void join(TestUser u, UUID activity) throws Exception {
        mvc.perform(post("/api/v1/activities/{id}/join", activity).header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
    }

    private void join(TestUser u, UUID activity, int partySize) throws Exception {
        mvc.perform(post("/api/v1/activities/{id}/join", activity).header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"partySize\":" + partySize + "}")).andExpect(status().isOk());
    }

    @Autowired
    OutboxRepository outbox;

    @Autowired
    PlatformTransactionManager txManager;

    private void drainOutbox() {
        relay.relayBatch(500);
    }

    // the consumer runs on its own thread, so give it a moment
    private static void eventually(ThrowingRunnable check) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(check::run);
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void hostHearsAboutJoinsAndTheNextInLineHearsAboutTheOffer() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser a = Users.participant(mvc), b = Users.participant(mvc), c = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 2);
        join(a, activity);
        join(b, activity);
        join(c, activity); // waitlisted
        mvc.perform(delete("/api/v1/activities/{id}/participants/me", activity).header("Authorization", a.bearer()));
        drainOutbox();

        eventually(() -> mvc.perform(get("/api/v1/me/notifications").header("Authorization", host.bearer()))
                .andExpect(jsonPath("$[*].type", hasItem("PARTICIPANT_JOINED")))
                .andExpect(jsonPath("$[*].type", hasItem("PARTICIPANT_LEFT"))));
        eventually(() -> mvc.perform(get("/api/v1/me/notifications").header("Authorization", c.bearer()))
                .andExpect(jsonPath("$[0].type").value("WAITLIST_OFFER"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.startsWith("A seat opened up for Game night"))));
    }

    @Test
    void hostHearsWhenSomeoneJoinsFromTheWaitlist() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser a = Users.participant(mvc), b = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 2);
        join(a, activity);
        join(b, activity, 2); // waitlisted, a party of two does not fit
        mvc.perform(delete("/api/v1/activities/{id}/participants/me", activity).header("Authorization", a.bearer()));
        UUID offer = jdbc.queryForObject("SELECT id FROM waitlist WHERE activity_id = ? AND status = 'OFFERED'",
                UUID.class, activity);
        mvc.perform(post("/api/v1/waitlist/offers/{id}/claim", offer).header("Authorization", b.bearer()))
                .andExpect(status().isOk());
        drainOutbox();

        eventually(() -> mvc.perform(get("/api/v1/me/notifications").header("Authorization", host.bearer()))
                .andExpect(jsonPath("$[0].type").value("PARTICIPANT_JOINED"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.endsWith("from the waitlist"))));
    }

    @Test
    void cancellingTellsEveryParticipant() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser a = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(a, activity);
        mvc.perform(delete("/api/v1/activities/{id}", activity).header("Authorization", host.bearer()));
        drainOutbox();
        eventually(() -> mvc.perform(get("/api/v1/me/notifications").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$[0].type").value("ACTIVITY_CANCELLED")));
    }

    @Test
    void reconfirmReminderGoesOutOnceTwoHoursBefore() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser a = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(a, activity);
        jdbc.update("UPDATE activities SET starts_at = now() + interval '90 minutes' WHERE id = ?", activity);

        reminders.run(); // the way the scheduler calls it
        assertThat(reminders.sendDue()).isZero();
        drainOutbox();
        eventually(() -> mvc.perform(get("/api/v1/me/notifications").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$[0].type").value("RECONFIRM")));
    }

    @Test
    void markRead() throws Exception {
        TestUser host = Users.host(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(Users.participant(mvc), activity);
        drainOutbox();
        eventually(() -> assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?",
                Integer.class, host.id())).isEqualTo(1));
        UUID id = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ? LIMIT 1", UUID.class, host.id());
        mvc.perform(post("/api/v1/me/notifications/{id}/read", id).header("Authorization", host.bearer()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT read_at IS NOT NULL FROM notifications WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void whenKafkaIsDownTheRowStaysAndGoesOutLater() throws Exception {
        holdScheduledRelay();
        drainOutbox();
        TestUser host = Users.host(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(Users.participant(mvc), activity);

        // nothing listens on port 1, so every send fails fast
        KafkaTemplate<String, ActivityEvent> deadBroker = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(Map.of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                        ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000,
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)));
        OutboxRelay brokenRelay = new OutboxRelay(jdbc, outbox, deadBroker, "circl.activity-events", txManager,
                Clock.systemUTC());

        assertThat(brokenRelay.relayBatch(500)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT attempts FROM outbox_events WHERE aggregate_id = ? AND type = 'ParticipantJoined'
                """, Integer.class, activity)).isEqualTo(1);

        // broker is back: skip the backoff wait and relay again
        jdbc.update("UPDATE outbox_events SET next_attempt_at = NULL WHERE aggregate_id = ?", activity);
        drainOutbox();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL
                """, Integer.class, activity)).isZero();
        eventually(() -> assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?",
                Integer.class, host.id())).isEqualTo(1));
        deadBroker.destroy();
        jdbc.update("UPDATE shedlock SET lock_until = now() WHERE name = 'outbox-relay' AND locked_by = 'test'");
    }

    // takes the scheduler's lock (the same way ShedLock does) so the background relay stays out of this test
    private void holdScheduledRelay() {
        jdbc.update("""
                INSERT INTO shedlock (name, lock_until, locked_at, locked_by) VALUES ('outbox-relay', now(), now(), 'test')
                ON CONFLICT (name) DO NOTHING
                """);
        await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.update("""
                UPDATE shedlock SET lock_until = now() + interval '2 minutes', locked_at = now(), locked_by = 'test'
                 WHERE name = 'outbox-relay' AND lock_until <= now()
                """) == 1);
    }
}
