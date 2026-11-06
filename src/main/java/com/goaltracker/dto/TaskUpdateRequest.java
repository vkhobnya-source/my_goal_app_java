package com.goaltracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskUpdateRequest(
        @JsonProperty("is_completed") Boolean isCompleted,
        String title
) {
}
