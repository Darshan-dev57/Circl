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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    OutboxRepository outbox;

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

    private void join(TestUser u, UUID activity) throws Exception {
        mvc.perform(post("/api/v1/activities/{id}/join", activity).header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
    }

    private int notificationsFor(TestUser u) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Integer.class, u.id());
    }

    @Test
    void outboxRowGoesThroughKafkaAndBecomesOneNotification() throws Exception {
        TestUser host = Users.host(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(Users.participant(mvc), activity);
        join(Users.participant(mvc), activity);
        relay.relayBatch(500);

        UUID lastEvent = jdbc.queryForObject("""
                SELECT id FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at DESC LIMIT 1
                """, UUID.class, activity);
        List<ConsumerRecord<String, ActivityEvent>> mine = readAll(TOPIC, lastEvent).stream()
                .filter(r -> r.value() != null && activity.equals(r.value().activityId()))
                .toList();

        // keyed by activity, so both joins sit on one partition in the order they happened
        assertThat(mine).hasSize(2);
        assertThat(mine).allMatch(r -> r.key().equals(activity.toString()));
        assertThat(mine.get(0).partition()).isEqualTo(mine.get(1).partition());
        assertThat(mine.get(0).offset()).isLessThan(mine.get(1).offset());

        await().atMost(Duration.ofSeconds(15)).until(() -> notificationsFor(host) == 2);
    }

    @Test
    void theSameEventDeliveredTwiceGivesOneNotification() throws Exception {
        TestUser host = Users.host(mvc);
        UUID activity = Activities.create(mvc, host, 3);
        join(Users.participant(mvc), activity);
        relay.relayBatch(500);
        await().atMost(Duration.ofSeconds(15)).until(() -> notificationsFor(host) == 1);

        // what a redelivery looks like: same event id, same key
        OutboxEvent row = outbox.findAll().stream()
                .filter(e -> e.getAggregateId().equals(activity))
                .findFirst().orElseThrow();
        kafka.send(TOPIC, activity.toString(), ActivityEvent.from(row)).get();
        kafka.send(TOPIC, activity.toString(), ActivityEvent.from(row)).get();

        // a new event behind the duplicates on the same partition; once it is handled, so are they
        join(Users.participant(mvc), activity);
        relay.relayBatch(500);
        await().atMost(Duration.ofSeconds(15)).until(() -> notificationsFor(host) == 2);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE event_id = ?", Integer.class, row.getId()))
                .isEqualTo(1);
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
