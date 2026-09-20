package com.nearmeet.activity.dto;

import com.nearmeet.activity.ActivityStatus;
import com.nearmeet.activity.Category;

import java.time.Instant;
import java.util.UUID;

public record ActivityDetail(
        UUID id,
        UUID hostId,
        String title,
        Category category,
        String description,
        double lat,
        double lng,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int seatsTaken,
        int seatsLeft,
        ActivityStatus status,
        Instant createdAt
) {
}
