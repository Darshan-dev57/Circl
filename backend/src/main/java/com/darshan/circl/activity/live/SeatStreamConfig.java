package com.darshan.circl.activity.live;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
class SeatStreamConfig {

    @Bean
    RedisMessageListenerContainer seatListener(RedisConnectionFactory redis, SeatStream seats) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redis);
        container.addMessageListener((message, pattern) ->
                seats.onMessage(new String(message.getBody(), StandardCharsets.UTF_8)), new ChannelTopic(SeatStream.CHANNEL));
        return container;
    }
}
