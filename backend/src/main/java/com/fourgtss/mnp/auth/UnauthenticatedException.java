package com.fourgtss.mnp.auth;

/** The {@code organization} header is missing or doesn't name a known operator. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
