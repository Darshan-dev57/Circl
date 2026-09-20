package com.nearmeet.activity;

import com.nearmeet.TestcontainersConfig;
import com.nearmeet.support.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ActivityApiIT {

    @Autowired
    MockMvc mvc;

    Users.TestUser host;

    @BeforeEach
    void signUpHost() throws Exception {
        host = Users.host(mvc);
    }

    private String body(String title, int capacity, String description) {
        String startsAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        return """
                {"title":"%s","category":"CRICKET","description":"%s","startsAt":"%s",
                 "capacity":%d,"lat":12.9763,"lng":77.5929}
                """.formatted(title, description, startsAt, capacity);
    }

    @Test
    void createReturns201WithLocationAndCleanFields() throws Exception {
        mvc.perform(post("/api/v1/activities").header("Authorization", host.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("  Cricket   at Cubbon  ", 10, "<script>alert(1)</script>bring a bat")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/activities/")))
                .andExpect(jsonPath("$.title").value("Cricket at Cubbon"))
                .andExpect(jsonPath("$.description").value("bring a bat"))
                .andExpect(jsonPath("$.seatsLeft").value(10))
                .andExpect(jsonPath("$.startsAt", not(containsString("E9"))));
    }

    @Test
    void everyBadFieldIsReportedAtOnce() throws Exception {
        String bad = """
                {"title":"","category":"CRICKET","startsAt":"2020-01-01T10:00:00Z","capacity":1,"lat":120,"lng":77}
                """;
        mvc.perform(post("/api/v1/activities").header("Authorization", host.bearer()).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(4)));
    }

    @Test
    void startMoreThan30DaysAheadIsRejected() throws Exception {
        String far = Instant.now().plus(45, ChronoUnit.DAYS).toString();
        String req = """
                {"title":"Trek","category":"TREK","startsAt":"%s","capacity":5,"lat":12.9,"lng":77.5}
                """.formatted(far);
        mvc.perform(post("/api/v1/activities").header("Authorization", host.bearer()).contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startsAt"));
    }

    @Test
    void typoInFieldNameIsA400() throws Exception {
        String req = body("Coffee", 4, "").replace("\"capacity\"", "\"capcity\"");
        mvc.perform(post("/api/v1/activities").header("Authorization", host.bearer()).contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIdIs404ProblemJson() throws Exception {
        mvc.perform(get("/api/v1/activities/7b0f2d4e-9a55-4c87-8f43-2b1f8f0c3a11"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void badUuidIs400() throws Exception {
        mvc.perform(get("/api/v1/activities/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void deleteOnCollectionIs405() throws Exception {
        mvc.perform(delete("/api/v1/activities").header("Authorization", host.bearer())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listIsPaged() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/activities").header("Authorization", host.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(body("Study " + i, 5, ""))).andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/activities").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void patchUpdatesOnlyGivenFields() throws Exception {
        String location = mvc.perform(post("/api/v1/activities").header("Authorization", host.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Badminton", 6, "doubles")))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(patch(location).header("Authorization", host.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(8))
                .andExpect(jsonPath("$.title").value("Badminton"));
        mvc.perform(delete(location).header("Authorization", host.bearer())).andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
