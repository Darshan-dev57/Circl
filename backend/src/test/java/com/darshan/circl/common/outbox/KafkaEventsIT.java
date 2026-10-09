package com.darshan.circl.common.outbox;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.support.Activities;
import com.darshan.circl.support.Users;
import com.darshan.circl.support.Users.TestUser;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class KafkaEventsIT {

    private static final String TOPIC = "circl.activity-events";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxRelay relay;

    @Autowired
    KafkaTemplate<String, ActivityEvent> kafka;

    @Autowired
    ConsumerFactory<String, ActivityEvent> consumers;

    /** reads the whole topic from the start with a throwaway consumer group */
    private List<ConsumerRecord<String, ActivityEvent>> readAll(String topic, UUID eventId) {
        List<ConsumerRecord<String, ActivityEvent>> found = new ArrayList<>();
        try (Consumer<String, ActivityEvent> consumer = consumers.createConsumer("it-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(found::add);
                return found.stream().anyMatch(r -> r.value() != null && eventId.equals(r.value().eventId()));
            });
        }
        return found;
    }

    @Test
    void aRecordThatKeepsFailingGoesToTheDeadLetterTopic() {
        // no such activity, so the notification listener can never handle it
        ActivityEvent poison = new ActivityEvent(UUID.randomUUID(), UUID.randomUUID(), "ParticipantJoined",
                Map.of("userId", UUID.randomUUID().toString()), Instant.now());
        kafka.send(TOPIC, poison.activityId().toString(), poison);

        List<ConsumerRecord<String, ActivityEvent>> dead = readAll(TOPIC + "-dlt", poison.eventId());
        assertThat(dead).anyMatch(r -> poison.eventId().equals(r.value().eventId()));
    }
}
