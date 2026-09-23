package com.darshan.circl.participation;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class MyActivitiesIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void upcomingIsKeysetPagedAndPastIsSeparate() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser me = Users.participant(mvc);
        UUID[] ids = new UUID[4];
        for (int i = 0; i < 4; i++) {
            ids[i] = Activities.create(mvc, host, 5);
            jdbc.update("UPDATE activities SET starts_at = now() + make_interval(hours => ?) WHERE id = ?", i + 1, ids[i]);
            mvc.perform(post("/api/v1/activities/{id}/join", ids[i]).header("Authorization", me.bearer())
                    .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        }
        jdbc.update("UPDATE activities SET starts_at = now() - interval '1 day' WHERE id = ?", ids[3]);

        String first = mvc.perform(get("/api/v1/me/activities").param("size", "2").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].activityId").value(ids[0].toString()))
                .andExpect(jsonPath("$.items[1].activityId").value(ids[1].toString()))
                .andExpect(jsonPath("$.nextCursor").exists())
                .andReturn().getResponse().getContentAsString();
        String cursor = new ObjectMapper().readTree(first).get("nextCursor").asText();

        mvc.perform(get("/api/v1/me/activities").param("size", "2").param("cursor", cursor).header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].activityId").value(ids[2].toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mvc.perform(get("/api/v1/me/activities").param("when", "past").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].attendanceStatus").value("RSVP"));

        mvc.perform(get("/api/v1/me/activities").param("cursor", "%%%").header("Authorization", me.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheHostSeesTheParticipantList() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser me = Users.participant(mvc);
        UUID id = Activities.create(mvc, host, 5);
        mvc.perform(post("/api/v1/activities/{id}/join", id).header("Authorization", me.bearer())
                .header("Idempotency-Key", "k")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/activities/{id}/participants", id).header("Authorization", host.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(me.id().toString()));
        mvc.perform(get("/api/v1/activities/{id}/participants", id).header("Authorization", me.bearer()))
                .andExpect(status().isForbidden());
    }

    private void join(TestUser u, UUID activity, int party) throws Exception {
        mvc.perform(post("/api/v1/activities/{id}/join", activity).header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json").content("{\"partySize\":" + party + "}")).andExpect(status().isOk());
    }

    @Test
    void myStatusOnAnActivity() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser a = Users.participant(mvc), b = Users.participant(mvc), c = Users.participant(mvc), d = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 2);
        join(a, activity, 2);
        join(b, activity, 1);
        join(c, activity, 1);

        mvc.perform(get("/api/v1/activities/{id}/participants/me", activity).header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.partySize").value(2))
                .andExpect(jsonPath("$.attendanceStatus").value("RSVP"));
        mvc.perform(get("/api/v1/activities/{id}/participants/me", activity).header("Authorization", c.bearer()))
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.waitlistPosition").value(2));
        mvc.perform(get("/api/v1/activities/{id}/participants/me", activity).header("Authorization", d.bearer()))
                .andExpect(jsonPath("$.status").value("NONE"))
                .andExpect(jsonPath("$.partySize").doesNotExist());

        mvc.perform(delete("/api/v1/activities/{id}/participants/me", activity).header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/activities/{id}/participants/me", activity).header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.status").value("OFFERED"))
                .andExpect(jsonPath("$.offerId").exists())
                .andExpect(jsonPath("$.claimDeadline").exists());
        mvc.perform(get("/api/v1/activities/{id}/participants/me", UUID.randomUUID()).header("Authorization", b.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hostingListsOnlyMyOwnActivities() throws Exception {
        TestUser host = Users.host(mvc), other = Users.host(mvc);
        UUID mine = Activities.create(mvc, host, 4);
        Activities.create(mvc, other, 4);
        mvc.perform(get("/api/v1/me/hosting").header("Authorization", host.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(mine.toString()))
                .andExpect(jsonPath("$[0].lat").exists());
    }
}
