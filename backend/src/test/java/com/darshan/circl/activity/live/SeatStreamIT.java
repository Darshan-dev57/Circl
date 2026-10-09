package com.darshan.circl.activity.live;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.common.outbox.OutboxRelay;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class SeatStreamIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    OutboxRelay relay;

    @Autowired
    SeatStream seats;

    private MockHttpServletResponse listen(UUID activity) throws Exception {
        return mvc.perform(get("/api/v1/activities/{id}/seats", activity))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
    }

    // the update travels through a redis channel, so give it a moment
    private static void waitFor(MockHttpServletResponse stream, String text) throws Exception {
        for (int i = 0; i < 100 && !stream.getContentAsString().contains(text); i++) {
            Thread.sleep(100);
        }
        assertThat(stream.getContentAsString()).contains(text);
    }

    @Test
    void sendsTheCurrentCountThenEveryChange() throws Exception {
        TestUser host = Users.host(mvc);
        TestUser a = Users.participant(mvc);
        UUID activity = Activities.create(mvc, host, 4);

        MockHttpServletResponse stream = listen(activity);
        assertThat(stream.getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        waitFor(stream, "\"seatsTaken\":0");

        mvc.perform(post("/api/v1/activities/{id}/join", activity).header("Authorization", a.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        relay.relayBatch(500);
        waitFor(stream, "\"seatsTaken\":1,\"seatsLeft\":3");

        mvc.perform(patch("/api/v1/activities/{id}", activity).header("Authorization", host.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":6}")).andExpect(status().isOk());
        relay.relayBatch(500);
        waitFor(stream, "\"capacity\":6,\"seatsTaken\":1,\"seatsLeft\":5");

        mvc.perform(delete("/api/v1/activities/{id}", activity).header("Authorization", host.bearer()));
        relay.relayBatch(500);
        waitFor(stream, "\"status\":\"CANCELLED\"");
    }

    @Test
    void onlyListenersOfThatActivityHearIt() throws Exception {
        TestUser host = Users.host(mvc);
        UUID watched = Activities.create(mvc, host, 3);
        UUID other = Activities.create(mvc, host, 3);
        MockHttpServletResponse stream = listen(watched);
        waitFor(stream, "event:seats");
        int before = stream.getContentAsString().length();

        mvc.perform(post("/api/v1/activities/{id}/join", other).header("Authorization", Users.participant(mvc).bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        relay.relayBatch(500);
        Thread.sleep(300);
        assertThat(stream.getContentAsString()).hasSize(before);
        assertThat(seats.listenerCount(watched)).isEqualTo(1);
    }

    @Test
    void unknownActivityIs404() throws Exception {
        mvc.perform(get("/api/v1/activities/{id}/seats", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
