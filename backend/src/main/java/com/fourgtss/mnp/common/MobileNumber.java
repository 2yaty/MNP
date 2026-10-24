package com.fourgtss.mnp.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

/** An 11-digit mobile number starting with {@code 01}, such as {@code 01012345678}. {@code null} is valid. */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = "01\\d{9}")
@ReportAsSingleViolation
public @interface MobileNumber {

    String message() default "must be an 11-digit mobile number starting with 01";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
