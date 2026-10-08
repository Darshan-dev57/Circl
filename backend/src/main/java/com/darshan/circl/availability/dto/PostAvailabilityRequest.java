package com.darshan.circl.availability.dto;

import com.darshan.circl.activity.Category;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PostAvailabilityRequest(
        @NotNull Category category,
        @NotNull @Min(15) @Max(180) Integer minutes,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng
) {
}
