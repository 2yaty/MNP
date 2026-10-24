package com.fourgtss.mnp.common;

/** The request conflicts with the current state, such as an existing pending request (mapped to {@code 409}). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
