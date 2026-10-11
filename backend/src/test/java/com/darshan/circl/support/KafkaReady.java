package com.darshan.circl.support;

import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

public final class KafkaReady {

    private KafkaReady() {
    }

    /**
     * On a slow CI runner the listeners can still be joining their consumer group when the first
     * test starts, and the events of that test then arrive after the test gave up. Wait until every
     * listener owns its partitions.
     */
    public static void waitForConsumers(KafkaListenerEndpointRegistry listeners) {
        await().atMost(Duration.ofSeconds(60)).until(() -> listeners.getListenerContainers().stream()
                .allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));
    }
}
