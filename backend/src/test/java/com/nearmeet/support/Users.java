package com.nearmeet.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Signs up throwaway users through the real API and hands back their tokens. */
public final class Users {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Users() {
    }

    public record TestUser(UUID id, String email, String accessToken, String refreshToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public static TestUser signup(MockMvc mvc, String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@test.dev";
        String body = """
                {"email":"%s","password":"correct-horse-1","name":"Test %s","role":"%s"}
                """.formatted(email, role, role);
        String res = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = JSON.readTree(res);
        return new TestUser(UUID.fromString(node.at("/user/id").asText()), email,
                node.get("accessToken").asText(), node.get("refreshToken").asText());
    }

    public static TestUser host(MockMvc mvc) throws Exception {
        return signup(mvc, "HOST");
    }

    public static TestUser participant(MockMvc mvc) throws Exception {
        return signup(mvc, "PARTICIPANT");
    }
}
