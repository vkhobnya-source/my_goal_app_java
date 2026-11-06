package com.goaltracker.api;

import org.springframework.http.HttpStatus;

/**
 * Operational API error. The handler renders it as {"detail": "..."} like FastAPI.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String wwwAuthenticate;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, String wwwAuthenticate) {
        super(message);
        this.status = status;
        this.wwwAuthenticate = wwwAuthenticate;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getWwwAuthenticate() {
        return wwwAuthenticate;
    }
}
