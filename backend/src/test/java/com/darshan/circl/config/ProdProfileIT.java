package com.darshan.circl.config;

import com.darshan.circl.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the real prod profile, with secrets passed the way a host passes environment variables. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CIRCL_JWT_SECRET=prod-profile-test-jwt-secret-0123456789abcdef",
        "CIRCL_CHECKIN_SECRET=prod-profile-test-checkin-secret-0123456789",
        "CORS_ORIGINS=https://circl.example.com",
        "DB_URL=jdbc:postgresql://replaced-by-testcontainers/circl", "DB_USER=circl", "DB_PASSWORD=not-the-compose-one",
        "REDIS_HOST=replaced-by-testcontainers", "KAFKA_BOOTSTRAP_SERVERS=replaced-by-testcontainers:9092",
        "MANAGEMENT_PORT=0"
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ProdProfileIT {

    @Autowired
    MockMvc mvc;

    @Test
    void corsOnlyAllowsTheConfiguredFrontend() throws Exception {
        mvc.perform(options("/api/v1/activities").header("Origin", "https://circl.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://circl.example.com"));
        mvc.perform(options("/api/v1/activities").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/api/v1/activities").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthChecksAreOnThePublicPortAndActuatorIsNot() throws Exception {
        mvc.perform(get("/livez")).andExpect(status().isOk());
        mvc.perform(get("/readyz")).andExpect(status().isOk());
        // actuator lives on the management port in prod, so the public port has none of it
        mvc.perform(get("/actuator/prometheus")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/heapdump")).andExpect(status().is4xxClientError());
    }
}
