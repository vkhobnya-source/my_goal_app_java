package com.goaltracker.domain;

public record Goal(long id, String title, String description, boolean isCompleted, long userId) {
}
