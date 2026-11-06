package com.goaltracker.dto;

/**
 * Request body for both register and login. Fields are required but may be
 * empty strings; this mirrors the Python/Pydantic contract.
 */
public record AuthRequest(String email, String password) {
}
