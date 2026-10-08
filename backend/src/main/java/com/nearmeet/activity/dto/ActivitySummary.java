package com.nearmeet.activity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nearmeet.activity.Category;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivitySummary(
        UUID id,
        String title,
        Category category,
        Instant startsAt,
        int capacity,
        int seatsLeft,
        Double distanceM
) {
}
