package com.goaltracker.domain;

public record User(long id, String email, String hashedPassword) {
}
