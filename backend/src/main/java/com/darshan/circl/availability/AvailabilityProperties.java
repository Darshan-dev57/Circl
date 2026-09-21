package com.darshan.circl.availability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("circl.availability")
public record AvailabilityProperties(
        int maxMinutes,
        double maxRadiusKm,
        int minReliabilityToSearch,
        int searchesPerWindow,
        Duration searchWindow,
        double maxJumpKm
) {
}
