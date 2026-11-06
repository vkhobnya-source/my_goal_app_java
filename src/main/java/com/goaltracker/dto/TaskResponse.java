package com.goaltracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.goaltracker.domain.Task;

public record TaskResponse(
        long id,
        String title,
        @JsonProperty("is_completed") boolean isCompleted,
        @JsonProperty("goal_id") long goalId
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.id(), task.title(), task.isCompleted(), task.goalId());
    }
}
