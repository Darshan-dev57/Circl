package com.darshan.circl.notification;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.attendance.ReconfirmReminderJob;
import com.darshan.circl.common.outbox.OutboxRelay;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    private void drainOutbox() {
        relay.relayBatch(500);
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

        mvc.perform(get("/api/v1/me/notifications").header("Authorization", host.bearer()))
                .andExpect(jsonPath("$[*].type", hasItem("PARTICIPANT_JOINED")))
                .andExpect(jsonPath("$[*].type", hasItem("PARTICIPANT_LEFT")));
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", c.bearer()))
                .andExpect(jsonPath("$[0].type").value("WAITLIST_OFFER"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.startsWith("A seat opened up for Game night")));
    }

    @Test
    void cancellingTellsEveryParticipant() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser a = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(a, activity);
        mvc.perform(delete("/api/v1/activities/{id}", activity).header("Authorization", host.bearer()));
        drainOutbox();
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$[0].type").value("ACTIVITY_CANCELLED"));
    }

    @Test
    void reconfirmReminderGoesOutOnceTwoHoursBefore() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser a = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(a, activity);
        jdbc.update("UPDATE activities SET starts_at = now() + interval '90 minutes' WHERE id = ?", activity);

        assertThat(reminders.sendDue()).isGreaterThanOrEqualTo(1);
        assertThat(reminders.sendDue()).isZero();
        drainOutbox();
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$[0].type").value("RECONFIRM"));
    }

    @Test
    void markRead() throws Exception {
        TestUser host = Users.host(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(Users.participant(mvc), activity);
        drainOutbox();
        UUID id = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ? LIMIT 1", UUID.class, host.id());
        mvc.perform(post("/api/v1/me/notifications/{id}/read", id).header("Authorization", host.bearer()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT read_at IS NOT NULL FROM notifications WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void aBrokenEventIsParkedAfterFiveTriesAndDoesNotBlockOthers() throws Exception {
        UUID broken = jdbc.queryForObject("""
                INSERT INTO outbox_events (aggregate_id, type, payload, created_at)
                VALUES (gen_random_uuid(), 'ParticipantJoined', '{"userId":"00000000-0000-0000-0000-000000000000"}', now() - interval '1 hour')
                RETURNING id
                """, UUID.class);
        for (int i = 0; i < 10; i++) {
            jdbc.update("UPDATE outbox_events SET next_attempt_at = NULL WHERE id = ?", broken); // skip the backoff wait
            drainOutbox();
            Boolean parked = jdbc.queryForObject("SELECT failed_at IS NOT NULL FROM outbox_events WHERE id = ?", Boolean.class, broken);
            if (Boolean.TRUE.equals(parked)) {
                break;
            }
        }
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE id = ?", Integer.class, broken)).isEqualTo(5);

        TestUser host = Users.host(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(Users.participant(mvc), activity);
        drainOutbox();
        // other test classes share this database, so only look at the new activity's events
        Integer pending = jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events
                 WHERE aggregate_id = ? AND published_at IS NULL
                """, Integer.class, activity);
        assertThat(pending).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Integer.class, host.id()))
                .isEqualTo(1);
    }
}
