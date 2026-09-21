package com.darshan.circl.availability.dto;

import com.darshan.circl.activity.Category;

import java.time.Instant;
import java.util.UUID;

/** deliberately no coordinates and no exact distance */
public record NearbyPerson(UUID userId, String name, Category category, Instant freeUntil, String distance) {
}
