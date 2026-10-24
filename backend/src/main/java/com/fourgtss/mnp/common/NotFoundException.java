package com.fourgtss.mnp.common;

/** The resource doesn't exist, or the caller isn't allowed to see it (mapped to {@code 404}). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
