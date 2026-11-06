package com.goaltracker.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.goaltracker.domain.Goal;
import com.goaltracker.domain.Task;

public record GoalResponse(
        long id,
        String title,
        String description,
        @JsonProperty("is_completed") boolean isCompleted,
        List<TaskResponse> tasks,
        @JsonProperty("user_id") long userId
) {

    public static GoalResponse from(Goal goal, List<Task> tasks) {
        List<TaskResponse> taskResponses = tasks.stream()
                .map(TaskResponse::from)
                .toList();
        return new GoalResponse(
                goal.id(),
                goal.title(),
                goal.description(),
                goal.isCompleted(),
                taskResponses,
                goal.userId()
        );
    }
}
