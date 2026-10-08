package com.darshan.circl.activity.dto;

import com.darshan.circl.activity.Category;
import com.darshan.circl.common.validation.StartsWithinDays;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateActivityRequest(
        @NotBlank @Size(max = 80) String title,
        @NotNull Category category,
        @Size(max = 1000) String description,
        @NotNull @Future @StartsWithinDays(30) Instant startsAt,
        @Min(15) @Max(720) Integer durationMinutes,
        @NotNull @Min(2) @Max(50) Integer capacity,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @Min(0) @Max(100) Integer minReliability
) {
}
