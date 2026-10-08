package com.darshan.circl.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class StartsWithinDaysValidator implements ConstraintValidator<StartsWithinDays, Instant> {

    private final Clock clock;
    private int days;

    public StartsWithinDaysValidator() {
        this(Clock.systemUTC());
    }

    StartsWithinDaysValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void initialize(StartsWithinDays annotation) {
        this.days = annotation.value();
    }

    @Override
    public boolean isValid(Instant value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull decides about null
        }
        return !value.isAfter(Instant.now(clock).plus(Duration.ofDays(days)));
    }
}
