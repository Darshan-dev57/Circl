package com.darshan.circl.participation;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import org.junit.jupiter.api.AfterEach;
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
class WaitlistOfferIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OfferExpiryJob expiryJob;

    TestUser host;
    UUID activityId;

    @BeforeEach
    void setUp() throws Exception {
        host = Users.host(mvc);
        activityId = Activities.create(mvc, host, 4);
    }

    @AfterEach
    void ledgerAlwaysMatchesSeatsTaken() {
        Integer taken = jdbc.queryForObject("SELECT seats_taken FROM activities WHERE id = ?", Integer.class, activityId);
        Integer ledger = jdbc.queryForObject("SELECT COALESCE(SUM(delta), 0) FROM capacity_ledger WHERE activity_id = ?",
                Integer.class, activityId);
        assertThat(ledger).isEqualTo(taken);
    }

    private ResultActions join(TestUser u, int party) throws Exception {
        return mvc.perform(post("/api/v1/activities/{id}/join", activityId)
                .header("Authorization", u.bearer()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"partySize\":" + party + "}"));
    }

    private void leave(TestUser u) throws Exception {
        mvc.perform(delete("/api/v1/activities/{id}/participants/me", activityId).header("Authorization", u.bearer()))
                .andExpect(status().isNoContent());
    }

    private String waitlistStatus(TestUser u) {
        return jdbc.queryForObject("SELECT status FROM waitlist WHERE activity_id = ? AND user_id = ?",
                String.class, activityId, u.id());
    }

    private UUID offerId(TestUser u) {
        return jdbc.queryForObject("SELECT id FROM waitlist WHERE activity_id = ? AND user_id = ?",
                UUID.class, activityId, u.id());
    }

    private int seatsTaken() {
        return jdbc.queryForObject("SELECT seats_taken FROM activities WHERE id = ?", Integer.class, activityId);
    }

    @Test
    void partyThatDoesNotFitKeepsItsPlaceAndTheNextOneGetsTheSeat() throws Exception {
        TestUser a = Users.participant(mvc), b = Users.participant(mvc);
        TestUser bigParty = Users.participant(mvc), solo = Users.participant(mvc);
        join(a, 2).andExpect(jsonPath("$.status").value("JOINED"));
        join(b, 2).andExpect(jsonPath("$.status").value("JOINED"));
        join(bigParty, 3).andExpect(jsonPath("$.status").value("WAITLISTED")).andExpect(jsonPath("$.waitlistPosition").value(1));
        join(solo, 1).andExpect(jsonPath("$.waitlistPosition").value(2));

        leave(a); // 2 seats free: the party of 3 does not fit, the solo user does
        assertThat(waitlistStatus(bigParty)).isEqualTo("WAITING");
        assertThat(waitlistStatus(solo)).isEqualTo("OFFERED");
        assertThat(seatsTaken()).isEqualTo(3);

        leave(b); // now 3 free
        assertThat(waitlistStatus(bigParty)).isEqualTo("OFFERED");
        assertThat(seatsTaken()).isEqualTo(4);
    }

    @Test
    void claimTurnsTheOfferIntoASeatForTheWholeParty() throws Exception {
        TestUser a = Users.participant(mvc), b = Users.participant(mvc), c = Users.participant(mvc);
        join(a, 2);
        join(b, 2);
        join(c, 2);
        leave(a);

        mvc.perform(get("/api/v1/me/offers").header("Authorization", c.bearer()))
                .andExpect(jsonPath("$[0].partySize").value(2))
                .andExpect(jsonPath("$[0].claimDeadline").exists());

        mvc.perform(post("/api/v1/waitlist/offers/{id}/claim", offerId(c)).header("Authorization", c.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JOINED"));
        Integer party = jdbc.queryForObject("SELECT party_size FROM participants WHERE activity_id = ? AND user_id = ? AND status = 'JOINED'",
                Integer.class, activityId, c.id());
        assertThat(party).isEqualTo(2);
        assertThat(seatsTaken()).isEqualTo(4);

        // claiming twice does nothing
        mvc.perform(post("/api/v1/waitlist/offers/{id}/claim", offerId(c)).header("Authorization", c.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void someoneElseCannotClaimMyOffer() throws Exception {
        TestUser a = Users.participant(mvc), b = Users.participant(mvc), c = Users.participant(mvc);
        join(a, 2);
        join(b, 2);
        join(c, 1);
        leave(a);
        mvc.perform(post("/api/v1/waitlist/offers/{id}/claim", offerId(c)).header("Authorization", b.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anExpiredOfferCannotBeClaimedAndMovesToTheNextParty() throws Exception {
        TestUser a = Users.participant(mvc), b = Users.participant(mvc);
        TestUser slow = Users.participant(mvc), next = Users.participant(mvc);
        join(a, 2);
        join(b, 2);
        join(slow, 1);
        join(next, 1);
        leave(a); // both waitlisted users get offers (2 seats free)
        assertThat(waitlistStatus(slow)).isEqualTo("OFFERED");
        assertThat(waitlistStatus(next)).isEqualTo("OFFERED");

        TestUser third = Users.participant(mvc);
        join(third, 1).andExpect(jsonPath("$.status").value("WAITLISTED"));

        jdbc.update("UPDATE waitlist SET claim_deadline = now() - interval '1 minute' WHERE activity_id = ? AND user_id = ?",
                activityId, slow.id());
        mvc.perform(post("/api/v1/waitlist/offers/{id}/claim", offerId(slow)).header("Authorization", slow.bearer()))
                .andExpect(status().isConflict());

        assertThat(expiryJob.expireDueOffers()).isEqualTo(1);
        assertThat(waitlistStatus(slow)).isEqualTo("EXPIRED");
        assertThat(waitlistStatus(third)).isEqualTo("OFFERED");
        assertThat(seatsTaken()).isEqualTo(4);
        assertThat(expiryJob.expireDueOffers()).isZero();
    }

    @Test
    void declineHandsTheSeatToTheNextParty() throws Exception {
        TestUser a = Users.participant(mvc), b = Users.participant(mvc);
        TestUser first = Users.participant(mvc), second = Users.participant(mvc);
        join(a, 2);
        join(b, 2);
        join(first, 2);
        join(second, 2);
        leave(a);
        assertThat(waitlistStatus(first)).isEqualTo("OFFERED");

        mvc.perform(post("/api/v1/waitlist/offers/{id}/decline", offerId(first)).header("Authorization", first.bearer()))
                .andExpect(status().isNoContent());
        assertThat(waitlistStatus(first)).isEqualTo("DECLINED");
        assertThat(waitlistStatus(second)).isEqualTo("OFFERED");
    }

    @Test
    void raisingCapacityOffersTheNewSeats() throws Exception {
        TestUser a = Users.participant(mvc), b = Users.participant(mvc), c = Users.participant(mvc);
        join(a, 2);
        join(b, 2);
        join(c, 2);
        mvc.perform(patch("/api/v1/activities/{id}", activityId).header("Authorization", host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":6}"))
                .andExpect(status().isOk());
        assertThat(waitlistStatus(c)).isEqualTo("OFFERED");
        assertThat(seatsTaken()).isEqualTo(6);
    }

    @Test
    void partySizeIsLimited() throws Exception {
        join(Users.participant(mvc), 5).andExpect(status().isBadRequest());
    }
}
