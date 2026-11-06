package com.goaltracker.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoalAnalysisResponse(
        String analysis,
        @JsonProperty("proposed_tasks") List<String> proposedTasks
) {
}
