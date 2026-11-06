package com.goaltracker.domain;

public record Task(long id, String title, boolean isCompleted, long goalId) {
}
