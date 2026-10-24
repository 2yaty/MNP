package com.fourgtss.mnp.common;

/** A well-formed request that breaks a business rule (mapped to {@code 422}). */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
