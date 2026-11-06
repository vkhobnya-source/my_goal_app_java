package com.goaltracker.ai;

/**
 * Failure while talking to the AI provider. The HTTP status is rendered by
 * the API exception handler.
 */
public class AiException extends RuntimeException {

    private final int status;

    public AiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
