package com.fourgtss.mnp.common;

/** The caller can see the resource but isn't allowed to perform the action (mapped to {@code 403}). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
