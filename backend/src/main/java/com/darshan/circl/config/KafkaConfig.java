package com.darshan.circl.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    // one broker locally, so one replica; events are keyed by activity id so 3 partitions keep each activity in order
    @Bean
    NewTopic activityEventsTopic(@Value("${circl.events.topic}") String topic,
                                 @Value("${circl.events.partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    // the dead letter topic needs the same partitions, a failed record goes to the same partition number there
    @Bean
    NewTopic activityEventsDltTopic(@Value("${circl.events.topic}") String topic,
                                           @Value("${circl.events.partitions}") int partitions) {
        return TopicBuilder.name(topic + "-dlt").partitions(partitions).replicas(1).build();
    }

    /**
     * A listener that keeps failing on one record would block its partition forever.
     * Try 3 times a second apart, then park the record on circl.activity-events-dlt and move on.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 2));
    }
}
