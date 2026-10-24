package com.fourgtss.mnp.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Injects the calling {@link com.fourgtss.mnp.model.Operator}, identified from the
 * {@code organization} header, into a controller method parameter.
 * Hidden from the API docs, which show the header itself instead.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(hidden = true)
public @interface AuthenticatedOperator {
}
