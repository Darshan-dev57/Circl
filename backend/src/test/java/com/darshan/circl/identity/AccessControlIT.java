package com.darshan.circl.identity;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may call what, and what an error response is allowed to say. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AccessControlIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    private void join(TestUser u, UUID activity) throws Exception {
        mvc.perform(post("/api/v1/activities/{id}/join", activity).header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
    }

    @Test
    void anotherHostCannotCancelMyActivity() throws Exception {
        TestUser me = Users.host(mvc);
        TestUser other = Users.host(mvc);
        UUID activity = Activities.create(mvc, me, 4);

        mvc.perform(delete("/api/v1/activities/{id}", activity).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM activities WHERE id = ?", String.class, activity))
                .isEqualTo("OPEN");
    }

    @Test
    void aParticipantCannotUseHostOnlyEndpoints() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser guest = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 4);
        join(guest, activity);
        String bearer = guest.bearer();

        mvc.perform(patch("/api/v1/activities/{id}", activity).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":40}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/activities/{id}", activity).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/activities/{id}/participants", activity).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/activities/{id}/checkin-code", activity).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/activities/{id}/attendance/unreliable", activity).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/activities/{id}/attendance/{user}/evidence", activity, guest.id())
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"scanner broke\",\"markAttended\":true}")).andExpect(status().isForbidden());
        // "activities I host" only ever lists the caller's own, so a participant gets nothing back
        mvc.perform(get("/api/v1/me/hosting").header("Authorization", bearer))
                .andExpect(content().string(not(containsString(activity.toString()))));
        assertThat(jdbc.queryForObject("SELECT capacity FROM activities WHERE id = ?", Integer.class, activity))
                .isEqualTo(4);
    }

    @Test
    void usersCannotReadOrChangeEachOthersPrivateData() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser alice = Users.participant(mvc);
        TestUser bob = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 4);
        join(alice, activity);
        UUID note = jdbc.queryForObject("""
                INSERT INTO notifications (user_id, type, message, activity_id) VALUES (?, 'TEST', 'only for alice', ?)
                RETURNING id
                """, UUID.class, alice.id(), activity);

        // bob only ever sees his own profile, notifications and status
        mvc.perform(get("/api/v1/me").header("Authorization", bob.bearer()))
                .andExpect(jsonPath("$.email").value(bob.email()));
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", bob.bearer()))
                .andExpect(content().string(not(containsString("only for alice"))));
        // the answer is the same as for a real id, so it does not even reveal that the id exists
        mvc.perform(post("/api/v1/me/notifications/{id}/read", note).header("Authorization", bob.bearer()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT read_at IS NULL FROM notifications WHERE id = ?", Boolean.class, note))
                .isTrue();
        mvc.perform(get("/api/v1/activities/{id}/participants/me", activity).header("Authorization", bob.bearer()))
                .andExpect(content().string(not(containsString("JOINED"))));

        // bob cannot appeal a no-show that belongs to alice
        UUID aliceParticipant = jdbc.queryForObject("SELECT id FROM participants WHERE activity_id = ? AND user_id = ?",
                UUID.class, activity, alice.id());
        int status = mvc.perform(post("/api/v1/me/attendance/{id}/appeal", aliceParticipant).header("Authorization", bob.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"not me\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isIn(403, 404);

        // public activity pages never show emails or password hashes
        mvc.perform(get("/api/v1/activities/{id}", activity))
                .andExpect(content().string(not(containsString("@test.dev"))))
                .andExpect(content().string(not(containsString("$2a$"))));
        // even the host's participant list shows names, not emails
        mvc.perform(get("/api/v1/activities/{id}/participants", activity).header("Authorization", host.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(alice.email()))));
    }

    @Test
    void expiredTamperedAndForeignTokensAreRejected() throws Exception {
        TestUser u = Users.participant(mvc);
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        JwtClaimsSet expired = JwtClaimsSet.builder().issuer("circl").subject(u.id().toString())
                .claim("roles", List.of("PARTICIPANT")).issuedAt(past).expiresAt(past.plus(15, ChronoUnit.MINUTES)).build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), expired))
                .getTokenValue();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("Jwt expired"))));

        JwtClaimsSet valid = JwtClaimsSet.builder().issuer("circl").subject(u.id().toString())
                .claim("roles", List.of("HOST")).issuedAt(Instant.now()).expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES)).build();
        JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "some-other-secret-that-is-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = otherKey.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), valid))
                .getTokenValue();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());

        // change one character of the payload of a real token: the signature no longer matches
        String[] parts = u.accessToken().split("\\.");
        char last = parts[1].charAt(parts[1].length() - 1);
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 1) + (last == 'A' ? 'B' : 'A') + "." + parts[2];
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/me").header("Authorization", u.bearer())).andExpect(status().isOk());
    }

    @Test
    void validationErrorsDoNotEchoSecretsOrInternals() throws Exception {
        String password = "my-real-password-123";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"" + password + "\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(content().string(not(containsString(password))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.springframework"))));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\": broken json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("jackson"))));

        // wrong password and unknown email look the same, so emails cannot be probed
        TestUser u = Users.participant(mvc);
        String wrong = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.email() + "\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@test.dev\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(strip(wrong)).isEqualTo(strip(unknown));
    }

    private static String strip(String problem) {
        return problem.replaceAll("\"instance\":\"[^\"]*\"", "");
    }
}
