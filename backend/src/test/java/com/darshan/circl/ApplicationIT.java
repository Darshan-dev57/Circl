package com.darshan.circl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ApplicationIT {

    @Autowired
    TestRestTemplate http;

    @Test
    void healthIsUp() {
        String body = http.getForObject("/actuator/health", String.class);
        assertThat(body).contains("\"status\":\"UP\"");
    }
}
