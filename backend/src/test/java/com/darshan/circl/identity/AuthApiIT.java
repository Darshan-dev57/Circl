package com.darshan.circl.identity;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    private String activity() {
        return """
                {"title":"Evening football","category":"FOOTBALL","startsAt":"%s","capacity":10,"lat":12.93,"lng":77.62}
                """.formatted(Instant.now().plus(1, ChronoUnit.DAYS));
    }

    @Test
    void signupNormalizesEmailAndStoresBcryptHash() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"  Darshan.Test@GMAIL.com ","password":"s3cret-pass","name":"Darshan"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("darshan.test@gmail.com"))
                .andExpect(jsonPath("$.user.role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.user.reliabilityScore").value(50));

        User saved = users.findByEmail("darshan.test@gmail.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2a$").doesNotContain("s3cret-pass");

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"darshan.test@gmail.com","password":"another-pass","name":"Copy"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIs401() throws Exception {
        Users.TestUser u = Users.participant(mvc);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.email() + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.email() + "\",\"password\":\"correct-horse-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void sixthWrongPasswordIsBlockedEvenWithTheRightOne() throws Exception {
        Users.TestUser u = Users.participant(mvc);
        String wrong = "{\"email\":\"" + u.email() + "\",\"password\":\"nope-nope\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.email() + "\",\"password\":\"correct-horse-1\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cannotSignUpAsAdmin() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"sneaky@test.dev","password":"s3cret-pass","name":"x","role":"ADMIN"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordLongerThan72BytesIsRefusedNotA500() throws Exception {
        // 30 Kannada letters are only 30 characters but 90 bytes, more than BCrypt can take
        String password = "ಕ".repeat(30);
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"kannada@test.dev","password":"%s","name":"Darshan"}
                        """.formatted(password)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void meNeedsAToken() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        Users.TestUser u = Users.participant(mvc);
        mvc.perform(get("/api/v1/me").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(u.email()));
    }

    @Test
    void creatingAnActivityNeedsAHost() throws Exception {
        mvc.perform(post("/api/v1/activities").contentType(MediaType.APPLICATION_JSON).content(activity()))
                .andExpect(status().isUnauthorized());

        Users.TestUser participant = Users.participant(mvc);
        mvc.perform(post("/api/v1/activities").header("Authorization", participant.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(activity()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherHostCannotEditMyActivity() throws Exception {
        Users.TestUser me = Users.host(mvc);
        Users.TestUser other = Users.host(mvc);
        String location = mvc.perform(post("/api/v1/activities").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(activity()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(patch(location).header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"mine now\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", startsWith("https://circl.dev/problems/")));
        mvc.perform(patch(location).header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"still mine\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenRotatesAndReuseIsRejected() throws Exception {
        Users.TestUser u = Users.participant(mvc);
        String first = "{\"refreshToken\":\"" + u.refreshToken() + "\"}";

        String rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = rotated.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");

        // the same old token a second time: rejected, and the whole family is revoked
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Users.TestUser u = Users.participant(mvc);
        String body = "{\"refreshToken\":\"" + u.refreshToken() + "\"}";
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserCanRefreshWithTheHttpOnlyCookie() throws Exception {
        Users.TestUser u = Users.participant(mvc);
        Cookie sent = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.email() + "\",\"password\":\"correct-horse-1\"}"))
                .andExpect(header().string("Set-Cookie", allOf(containsString("circl_refresh="),
                        containsString("HttpOnly"), containsString("SameSite=Strict"), containsString("Path=/api/v1/auth"))))
                .andReturn().getResponse().getCookie("circl_refresh");

        Cookie rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(sent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getCookie("circl_refresh");
        assertThat(rotated.getValue()).isNotEqualTo(sent.getValue());

        // the old cookie was rotated away, so sending it again is reuse
        mvc.perform(post("/api/v1/auth/refresh").cookie(sent)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout").cookie(rotated))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void refreshWithoutAnyTokenIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }
}
