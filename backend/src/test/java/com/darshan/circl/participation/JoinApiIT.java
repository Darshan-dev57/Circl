package com.darshan.circl.participation;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class JoinApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    TestUser host;
    UUID activityId;

    @BeforeEach
    void setUp() throws Exception {
        host = Users.host(mvc);
        activityId = Activities.create(mvc, host, 2);
    }

    private ResultActions join(TestUser user, String key) throws Exception {
        return mvc.perform(post("/api/v1/activities/{id}/join", activityId)
                .header("Authorization", user.bearer())
                .header("Idempotency-Key", key));
    }

    @Test
    void joinTakesASeat() throws Exception {
        TestUser alice = Users.participant(mvc);
        join(alice, "k-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.seatsLeft").value(1));
        mvc.perform(get("/api/v1/activities/{id}", activityId))
                .andExpect(jsonPath("$.seatsTaken").value(1));
    }

    @Test
    void sameKeyTwiceReturnsTheSavedResponseAndJoinsOnce() throws Exception {
        TestUser alice = Users.participant(mvc);
        join(alice, "retry-me").andExpect(header().string("Idempotent-Replayed", "false"));
        join(alice, "retry-me")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.seatsLeft").value(1));

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM participants WHERE activity_id = ?",
                Integer.class, activityId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void sameKeyForADifferentActivityIs422() throws Exception {
        TestUser alice = Users.participant(mvc);
        join(alice, "one-key").andExpect(status().isOk());
        UUID other = Activities.create(mvc, host, 5);
        mvc.perform(post("/api/v1/activities/{id}/join", other)
                        .header("Authorization", alice.bearer()).header("Idempotency-Key", "one-key"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void joiningTwiceWithANewKeyIs409() throws Exception {
        TestUser alice = Users.participant(mvc);
        join(alice, "a").andExpect(status().isOk());
        join(alice, "b").andExpect(status().isConflict());
    }

    @Test
    void keyIsRequiredAndSoIsLogin() throws Exception {
        TestUser alice = Users.participant(mvc);
        mvc.perform(post("/api/v1/activities/{id}/join", activityId).header("Authorization", alice.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/activities/{id}/join", activityId).header("Idempotency-Key", "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hostCannotJoinOwnActivity() throws Exception {
        join(host, "h").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void whenFullYouGoOnTheWaitlistInOrder() throws Exception {
        join(Users.participant(mvc), "1");
        join(Users.participant(mvc), "2");
        join(Users.participant(mvc), "3")
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.waitlistPosition").value(1));
        join(Users.participant(mvc), "4")
                .andExpect(jsonPath("$.waitlistPosition").value(2));
    }

    @Test
    void leavingFreesTheSeatForTheFirstWaitlistedUser() throws Exception {
        TestUser a = Users.participant(mvc);
        TestUser b = Users.participant(mvc);
        TestUser c = Users.participant(mvc);
        join(a, "1");
        join(b, "2");
        join(c, "3").andExpect(jsonPath("$.status").value("WAITLISTED"));

        mvc.perform(delete("/api/v1/activities/{id}/participants/me", activityId).header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());

        String cStatus = jdbc.queryForObject("SELECT status FROM participants WHERE activity_id = ? AND user_id = ?",
                String.class, activityId, c.id());
        assertThat(cStatus).isEqualTo("JOINED");
        Integer taken = jdbc.queryForObject("SELECT seats_taken FROM activities WHERE id = ?", Integer.class, activityId);
        assertThat(taken).isEqualTo(2);

        // a can come back, now as waitlisted
        join(a, "again").andExpect(jsonPath("$.status").value("WAITLISTED"));
    }

    @Test
    void leavingWhenNotJoinedIs404() throws Exception {
        mvc.perform(delete("/api/v1/activities/{id}/participants/me", activityId)
                        .header("Authorization", Users.participant(mvc).bearer()))
                .andExpect(status().isNotFound());
    }
}
