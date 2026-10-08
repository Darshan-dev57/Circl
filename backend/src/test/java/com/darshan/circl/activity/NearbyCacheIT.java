package com.darshan.circl.activity;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class NearbyCacheIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CacheManager caches;

    Users.TestUser host;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM activities");
        caches.getCache("nearby").clear();
        host = Users.host(mvc);
        Activities.create(mvc, host, 5); // at Koramangala
    }

    private void expectCount(double lat, double lng, int n) throws Exception {
        mvc.perform(get("/api/v1/activities/nearby").param("lat", "" + lat).param("lng", "" + lng))
                .andExpect(jsonPath("$", hasSize(n)));
    }

    @Test
    void secondCallIsServedFromTheCache() throws Exception {
        expectCount(12.9352, 77.6245, 1);
        // change the table behind the app's back: a cached answer does not notice
        jdbc.update("DELETE FROM activities");
        expectCount(12.9352, 77.6245, 1);
        // a point in another ~110 m cell is a different key and goes to the database
        expectCount(12.9372, 77.6245, 0);
    }

    @Test
    void creatingAnActivityEvictsTheCache() throws Exception {
        expectCount(12.9352, 77.6245, 1);
        Activities.create(mvc, host, 6);
        expectCount(12.9352, 77.6245, 2);
    }

    @Test
    void cancellingEvictsToo() throws Exception {
        expectCount(12.9352, 77.6245, 1);
        UUID id = jdbc.queryForObject("SELECT id FROM activities LIMIT 1", UUID.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/activities/{id}", id)
                .header("Authorization", host.bearer()));
        expectCount(12.9352, 77.6245, 0);
    }
}
