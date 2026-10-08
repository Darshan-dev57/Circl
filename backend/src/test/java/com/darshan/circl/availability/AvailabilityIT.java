package com.darshan.circl.availability;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AvailabilityIT {

    // a spot in Cubbon Park, deliberately with many decimals
    static final double LAT = 12.97632;
    static final double LNG = 77.59291;

    @Autowired
    MockMvc mvc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AvailabilityService service;

    TestUser ravi;
    TestUser meera;

    @BeforeEach
    void setUp() throws Exception {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        ravi = Users.participant(mvc);
        meera = Users.participant(mvc);
    }

    private ResultActions postFree(TestUser u, double lat, double lng, int minutes) throws Exception {
        return mvc.perform(post("/api/v1/availability").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"CRICKET\",\"minutes\":%d,\"lat\":%s,\"lng\":%s}".formatted(minutes, lat, lng)));
    }

    private ResultActions search(TestUser u, double lat, double lng) throws Exception {
        return mvc.perform(get("/api/v1/availability/nearby").header("Authorization", u.bearer())
                .param("lat", "" + lat).param("lng", "" + lng).param("category", "CRICKET"));
    }

    @Test
    void someoneFreeNearbyShowsUpWithABucketOnly() throws Exception {
        postFree(ravi, LAT, LNG, 60).andExpect(status().isOk()).andExpect(jsonPath("$.freeUntil").exists());

        String body = search(meera, 12.9716, 77.5946)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(ravi.id().toString()))
                .andExpect(jsonPath("$[0].distance").value("~1 km"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("lat").doesNotContain("lng").doesNotContain("12.97").doesNotContain("77.59");
    }

    @Test
    void exactLocationIsNeverStored() throws Exception {
        postFree(ravi, LAT, LNG, 60);
        List<Point> pos = redis.opsForGeo().position("avail:geo:CRICKET", ravi.id().toString());
        assertThat(pos).hasSize(1);
        // Redis keeps ~6 decimals internally, but the point is the snapped grid cell, not the real spot
        assertThat(pos.get(0).getY()).isCloseTo(12.98, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(pos.get(0).getX()).isCloseTo(77.59, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void threeSearchPointsCannotPinSomeoneDownBelowAKilometre() throws Exception {
        postFree(ravi, LAT, LNG, 60);
        // standing on the real spot or 400 m away gives the same answer, so moving around reveals nothing finer
        search(meera, LAT, LNG).andExpect(jsonPath("$[0].distance").value("<1 km"));
        search(meera, LAT + 0.0035, LNG).andExpect(jsonPath("$[0].distance").value("<1 km"));
        search(meera, LAT - 0.0005, LNG - 0.0040).andExpect(jsonPath("$[0].distance").value("<1 km"));
        double gridError = AvailabilityService.haversineKm(LAT, LNG, 12.98, 77.59);
        assertThat(gridError).isGreaterThan(0.3); // the best anyone can learn is the grid cell
    }

    @Test
    void repostReplacesAndStopRemoves() throws Exception {
        postFree(ravi, LAT, LNG, 60);
        postFree(ravi, LAT, LNG, 30);
        search(meera, LAT, LNG).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/v1/availability/me").header("Authorization", ravi.bearer())).andExpect(status().isNoContent());
        search(meera, LAT, LNG).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void expiredPostsDisappearWithoutAManualDelete() throws Exception {
        postFree(ravi, LAT, LNG, 15);
        redis.delete("avail:" + ravi.id()); // what the TTL does after 15 minutes
        redis.opsForZSet().add(AvailabilityService.EXPIRY_KEY, ravi.id().toString(), 1);
        assertThat(service.cleanupExpired()).isEqualTo(1);
        assertThat(redis.opsForGeo().position("avail:geo:CRICKET", ravi.id().toString())).containsOnlyNulls();
        search(meera, LAT, LNG).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void eleventhSearchInFiveMinutesIs429() throws Exception {
        for (int i = 0; i < 10; i++) {
            search(meera, LAT, LNG).andExpect(status().isOk());
        }
        search(meera, LAT, LNG)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void jumpingAcrossTheCityBetweenSearchesIsRejected() throws Exception {
        search(meera, LAT, LNG).andExpect(status().isOk());
        search(meera, 12.9698, 77.7500).andExpect(status().isUnprocessableEntity()); // Whitefield, ~17 km away
    }

    @Test
    void lowReliabilityCannotSearch() throws Exception {
        jdbc.update("UPDATE users SET reliability_score = 30 WHERE id = ?", meera.id());
        search(meera, LAT, LNG).andExpect(status().isForbidden());
    }

    @Test
    void validation() throws Exception {
        postFree(ravi, LAT, LNG, 500).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/availability/nearby").header("Authorization", meera.bearer())
                        .param("lat", "" + LAT).param("lng", "" + LNG).param("category", "CRICKET").param("radiusKm", "20"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/availability/nearby").param("lat", "1").param("lng", "1").param("category", "CRICKET"))
                .andExpect(status().isUnauthorized());
    }
}
