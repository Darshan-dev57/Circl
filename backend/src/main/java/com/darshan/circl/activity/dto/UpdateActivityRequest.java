package com.darshan.circl.activity.dto;

import com.darshan.circl.common.validation.StartsWithinDays;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** PATCH body: every field is optional, null means "keep what is there". */
public record UpdateActivityRequest(
        // null keeps the old title, but only spaces would be saved as "" ((?s) lets . match new lines too)
        @Size(max = 80) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String title,
        @Size(max = 1000) String description,
        @Future @StartsWithinDays(30) Instant startsAt,
        @Min(15) @Max(720) Integer durationMinutes,
        @Min(2) @Max(50) Integer capacity,
        @Min(0) @Max(100) Integer minReliability
) {
}
