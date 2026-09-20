package com.nearmeet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    /** injected everywhere "now" matters, so tests can move time */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
