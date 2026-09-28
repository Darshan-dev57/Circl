package com.darshan.circl.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // one broker locally, so one replica; events are keyed by activity id so 3 partitions keep each activity in order
    @Bean
    NewTopic activityEventsTopic(@Value("${circl.events.topic}") String topic,
                                 @Value("${circl.events.partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }
}
