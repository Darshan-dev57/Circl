package com.darshan.circl.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The instant must not be further in the future than the given number of days. */
@Documented
@Constraint(validatedBy = StartsWithinDaysValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StartsWithinDays {

    int value();

    String message() default "must be within {value} days from now";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
