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
}
