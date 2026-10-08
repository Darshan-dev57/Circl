package com.darshan.circl.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public final class Activities {

    private Activities() {
    }

    public static UUID create(MockMvc mvc, Users.TestUser host, int capacity) throws Exception {
        String body = """
                {"title":"Game night","category":"STUDY","startsAt":"%s","capacity":%d,"lat":12.9352,"lng":77.6245}
                """.formatted(Instant.now().plus(1, ChronoUnit.DAYS), capacity);
        String res = mvc.perform(post("/api/v1/activities").header("Authorization", host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(new ObjectMapper().readTree(res).get("id").asText());
    }
}
