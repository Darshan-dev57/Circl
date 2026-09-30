package com.darshan.circl.config;

import com.darshan.circl.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
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
        "REDIS_HOST=replaced-by-testcontainers", "KAFKA_BOOTSTRAP_SERVERS=replaced-by-testcontainers:9092"
})
@ActiveProfiles("prod")
// tests switch metrics export off unless asked; these tests read the Prometheus output
@AutoConfigureObservability(tracing = false)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ProdProfileIT {

    @Autowired
    MockMvc mvc;

    // a fixed free port, because port 0 for both would put actuator back on the public port
    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            registry.add("MANAGEMENT_PORT", socket::getLocalPort);
        }
    }

    @LocalManagementPort
    int managementPort;

    @Test
    void metricsAreOnlyOnTheManagementPort() throws Exception {
        HttpResponse<String> res = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + managementPort + "/actuator/prometheus")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("circl_outbox_pending");
    }

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
