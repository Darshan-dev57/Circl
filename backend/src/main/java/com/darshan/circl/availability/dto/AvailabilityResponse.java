package com.darshan.circl.availability.dto;

import com.darshan.circl.activity.Category;

import java.time.Instant;

public record AvailabilityResponse(Category category, Instant freeUntil) {
}
