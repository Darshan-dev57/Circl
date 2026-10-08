package com.darshan.circl.activity;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class NearbySearchIT {

    // Cubbon Park, Bengaluru
    static final double LAT = 12.9763;
    static final double LNG = 77.5929;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    Users.TestUser host;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM activities");
        host = Users.host(mvc);
        create("Cricket in Cubbon", "CRICKET", 12.9763, 77.5929);      // 0 km
        create("Coffee on Church St", "COFFEE", 12.9750, 77.6050);     // ~1.3 km
        create("Badminton Indiranagar", "BADMINTON", 12.9784, 77.6408); // ~5.2 km
        create("Trek from Whitefield", "TREK", 12.9698, 77.7500);      // ~17 km
    }

    private void create(String title, String category, double lat, double lng) throws Exception {
        String body = """
                {"title":"%s","category":"%s","startsAt":"%s","capacity":6,"lat":%s,"lng":%s}
                """.formatted(title, category, Instant.now().plus(1, ChronoUnit.DAYS), lat, lng);
        mvc.perform(post("/api/v1/activities").header("Authorization", host.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    @Test
    void returnsOnlyActivitiesInsideTheRadiusSortedByDistance() throws Exception {
        mvc.perform(get("/api/v1/activities/nearby")
                        .param("lat", "" + LAT).param("lng", "" + LNG).param("radiusKm", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Cricket in Cubbon"))
                .andExpect(jsonPath("$[0].distanceM", closeTo(0.0, 1.0)))
                .andExpect(jsonPath("$[1].title").value("Coffee on Church St"))
                .andExpect(jsonPath("$[1].distanceM", closeTo(1300.0, 150.0)));
    }

    @Test
    void biggerRadiusAndCategoryFilter() throws Exception {
        mvc.perform(get("/api/v1/activities/nearby")
                        .param("lat", "" + LAT).param("lng", "" + LNG).param("radiusKm", "20"))
                .andExpect(jsonPath("$", hasSize(4)));
        mvc.perform(get("/api/v1/activities/nearby")
                        .param("lat", "" + LAT).param("lng", "" + LNG).param("radiusKm", "20")
                        .param("category", "BADMINTON"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].category", everyItem(is("BADMINTON"))));
    }

    @Test
    void cancelledAndPastActivitiesAreHidden() throws Exception {
        jdbc.update("UPDATE activities SET status = 'CANCELLED' WHERE title = 'Cricket in Cubbon'");
        jdbc.update("UPDATE activities SET starts_at = now() - interval '1 hour' WHERE title = 'Coffee on Church St'");
        mvc.perform(get("/api/v1/activities/nearby")
                        .param("lat", "" + LAT).param("lng", "" + LNG).param("radiusKm", "3"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void radiusIsBounded() throws Exception {
        mvc.perform(get("/api/v1/activities/nearby")
                        .param("lat", "" + LAT).param("lng", "" + LNG).param("radiusKm", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usesTheGistIndex() {
        // tiny table: tell the planner to avoid a seq scan so we can see the index is usable
        String plan = jdbc.execute((java.sql.Connection c) -> {
            try (var st = c.createStatement()) {
                st.execute("SET enable_seqscan = off");
                var rs = st.executeQuery("""
                        EXPLAIN SELECT id FROM activities
                        WHERE ST_DWithin(location, ST_MakePoint(77.5929, 12.9763)::geography, 3000)
                        """);
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString(1)).append('\n');
                }
                st.execute("RESET enable_seqscan");
                return sb.toString();
            }
        });
        org.assertj.core.api.Assertions.assertThat(plan).contains("idx_activities_location");
    }
}
