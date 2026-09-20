package com.darshan.circl.activity.dto;

import com.darshan.circl.activity.ActivityStatus;
import com.darshan.circl.activity.Category;

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
