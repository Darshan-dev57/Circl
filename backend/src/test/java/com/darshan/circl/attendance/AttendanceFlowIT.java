package com.darshan.circl.attendance;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.reliability.ReliabilityService;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AttendanceFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AttendanceFinalizer finalizer;

    @Autowired
    ReliabilityService reliability;

    TestUser host;
    TestUser alice;
    UUID activityId;

    @BeforeEach
    void setUp() throws Exception {
        host = Users.host(mvc);
        alice = Users.participant(mvc);
        activityId = Activities.create(mvc, host, 5);
        mvc.perform(post("/api/v1/activities/{id}/join", activityId).header("Authorization", alice.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
    }

    private void startsInMinutes(int minutes) {
        jdbc.update("UPDATE activities SET starts_at = now() + make_interval(mins => ?) WHERE id = ?", minutes, activityId);
    }

    private String code() throws Exception {
        String res = mvc.perform(get("/api/v1/activities/{id}/checkin-code", activityId).header("Authorization", host.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(res).get("code").asText();
    }

    private ResultActions checkIn(TestUser u, String code) throws Exception {
        return mvc.perform(post("/api/v1/activities/{id}/checkin", activityId).header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private String attendance(TestUser u) {
        return jdbc.queryForObject("SELECT attendance_status FROM participants WHERE activity_id = ? AND user_id = ?",
                String.class, activityId, u.id());
    }

    private UUID participantId(TestUser u) {
        return jdbc.queryForObject("SELECT id FROM participants WHERE activity_id = ? AND user_id = ?",
                UUID.class, activityId, u.id());
    }

    private int score(TestUser u) {
        return jdbc.queryForObject("SELECT reliability_score FROM users WHERE id = ?", Integer.class, u.id());
    }

    private void endedHoursAgo(int hours) {
        jdbc.update("UPDATE activities SET starts_at = now() - make_interval(hours => ?) - interval '1 hour' WHERE id = ?",
                hours, activityId);
    }

    @Test
    void confirmThenCheckInOnTime() throws Exception {
        mvc.perform(post("/api/v1/activities/{id}/confirm", activityId).header("Authorization", alice.bearer()))
                .andExpect(status().isOk());
        assertThat(attendance(alice)).isEqualTo("RECONFIRMED");

        startsInMinutes(10);
        checkIn(alice, code())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceStatus").value("CHECKED_IN"))
                .andExpect(jsonPath("$.late").value(false));
        checkIn(alice, code()).andExpect(status().isConflict());
    }

    @Test
    void tenMinutesLateIsAttendedNotANoShow() throws Exception {
        startsInMinutes(-10);
        checkIn(alice, code()).andExpect(jsonPath("$.late").value(true));
        endedHoursAgo(3);
        assertThat(finalizer.finalizeDue()).isGreaterThanOrEqualTo(1);
        assertThat(attendance(alice)).isEqualTo("ATTENDED");
    }

    @Test
    void checkInWindowAndCodeAreEnforced() throws Exception {
        startsInMinutes(90);
        checkIn(alice, code()).andExpect(status().isUnprocessableEntity()); // too early
        startsInMinutes(-20);
        checkIn(alice, code()).andExpect(status().isUnprocessableEntity()); // grace is over
        startsInMinutes(5);
        checkIn(alice, "forged.code").andExpect(status().isUnprocessableEntity());
        checkIn(Users.participant(mvc), code()).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/activities/{id}/checkin-code", activityId).header("Authorization", alice.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotLeaveAfterTheStart() throws Exception {
        startsInMinutes(-5);
        mvc.perform(delete("/api/v1/activities/{id}/participants/me", activityId).header("Authorization", alice.bearer()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void noShowCountsOnlyAfterTheAppealWindowAndARejection() throws Exception {
        endedHoursAgo(3);
        finalizer.finalizeDue();
        assertThat(attendance(alice)).isEqualTo("NO_SHOW");

        reliability.applyDueScores();
        assertThat(score(alice)).isEqualTo(50); // window still open

        String appeal = mvc.perform(post("/api/v1/me/attendance/{pid}/appeal", participantId(alice))
                        .header("Authorization", alice.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"I was there, phone died\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        UUID appealId = UUID.fromString(new ObjectMapper().readTree(appeal).get("id").asText());

        mvc.perform(post("/api/v1/appeals/{id}/decision", appealId).header("Authorization", alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/appeals/{id}/decision", appealId).header("Authorization", host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\",\"note\":\"not seen\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        jdbc.update("UPDATE participants SET no_show_at = now() - interval '49 hours' WHERE id = ?", participantId(alice));
        reliability.applyDueScores();
        assertThat(score(alice)).isEqualTo(33);

        reliability.applyDueScores(); // running it again changes nothing
        assertThat(score(alice)).isEqualTo(33);
        mvc.perform(get("/api/v1/me/reliability").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.noShows").value(1))
                .andExpect(jsonPath("$.committed").value(1));
    }

    @Test
    void acceptedAppealNeverChangesTheScore() throws Exception {
        endedHoursAgo(3);
        finalizer.finalizeDue();
        String appeal = mvc.perform(post("/api/v1/me/attendance/{pid}/appeal", participantId(alice))
                        .header("Authorization", alice.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"bus broke down\"}"))
                .andReturn().getResponse().getContentAsString();
        UUID appealId = UUID.fromString(new ObjectMapper().readTree(appeal).get("id").asText());
        mvc.perform(post("/api/v1/appeals/{id}/decision", appealId).header("Authorization", host.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}")).andExpect(status().isOk());

        jdbc.update("UPDATE participants SET no_show_at = now() - interval '49 hours' WHERE id = ?", participantId(alice));
        reliability.applyDueScores();
        reliability.applyDueScores();
        assertThat(score(alice)).isEqualTo(50);
    }

    @Test
    void appealAfter48HoursIsRefused() throws Exception {
        endedHoursAgo(3);
        finalizer.finalizeDue();
        jdbc.update("UPDATE participants SET no_show_at = now() - interval '49 hours' WHERE id = ?", participantId(alice));
        mvc.perform(post("/api/v1/me/attendance/{pid}/appeal", participantId(alice))
                        .header("Authorization", alice.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"late appeal\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void hostCanFlipANoShowWhenTheScanFailed() throws Exception {
        endedHoursAgo(3);
        finalizer.finalizeDue();
        mvc.perform(post("/api/v1/activities/{id}/attendance/{uid}/evidence", activityId, alice.id())
                        .header("Authorization", host.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"she was there, QR failed\",\"markAttended\":true}"))
                .andExpect(jsonPath("$.attendanceStatus").value("ATTENDED"));
        reliability.applyDueScores();
        assertThat(score(alice)).isEqualTo(67);
        Integer events = jdbc.queryForObject("SELECT count(*) FROM attendance_events WHERE participant_id = ? AND actor = 'HOST'",
                Integer.class, participantId(alice));
        assertThat(events).isEqualTo(1);
    }

    @Test
    void noPenaltyWhenCheckInWasDownThatDay() throws Exception {
        endedHoursAgo(3);
        finalizer.finalizeDue();
        jdbc.update("UPDATE activities SET attendance_unreliable = true WHERE id = ?", activityId);
        jdbc.update("UPDATE participants SET no_show_at = now() - interval '49 hours' WHERE id = ?", participantId(alice));
        reliability.applyDueScores();
        assertThat(score(alice)).isEqualTo(50);
    }

    @Test
    void minReliabilityKeepsLowScoresOut() throws Exception {
        mvc.perform(patch("/api/v1/activities/{id}", activityId).header("Authorization", host.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"minReliability\":60}")).andExpect(status().isOk());
        TestUser bob = Users.participant(mvc);
        mvc.perform(post("/api/v1/activities/{id}/join", activityId).header("Authorization", bob.bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("This host asks for a reliability score of 60, yours is 50"));
    }
}
