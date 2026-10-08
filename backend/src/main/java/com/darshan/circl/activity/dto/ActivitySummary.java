package com.darshan.circl.activity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.darshan.circl.activity.Category;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivitySummary(
        UUID id,
        String title,
        Category category,
        Instant startsAt,
        double lat,
        double lng,
        int capacity,
        int seatsLeft,
        Double distanceM
) {
}
