package com.goaltracker.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goaltracker.config.AiProperties;
import com.goaltracker.domain.Goal;
import com.goaltracker.i18n.Messages;
import org.springframework.stereotype.Service;

/**
 * Calls the OpenRouter chat completions API and validates the JSON answer.
 * The request shape and prompt match the Python backend.
 */
@Service
public class OpenRouterGoalAnalyzer implements GoalAnalyzer {

    private static final String SYSTEM_PROMPT = "You are a practical goal-planning assistant.";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final AiProperties properties;
    private final Messages messages;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenRouterGoalAnalyzer(
            AiProperties properties,
            Messages messages,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public GoalAnalysis analyze(Goal goal, String language) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiException(503, messages.t("ai_not_configured", language));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.apiUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + properties.apiKey().trim())
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", properties.siteUrl())
                .header("X-Title", properties.appName())
                .POST(HttpRequest.BodyPublishers.ofString(payload(goal), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            throw new AiException(
                    502,
                    messages.t("ai_provider_invalid_response", language)
            );
        } catch (IOException exception) {
            throw new AiException(
                    502,
                    messages.t("ai_provider_unreachable", language)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiException(
                    502,
                    messages.t("ai_provider_unreachable", language)
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = messages.t(
                    "ai_provider_http_error",
                    language,
                    "status",
                    response.statusCode()
            );
            String providerDetail = providerDetail(response.body());
            if (!providerDetail.isBlank()) {
                message = message + " " + providerDetail;
            }
            throw new AiException(502, message);
        }

        return parseResponse(response.body(), language);
    }

    private String payload(Goal goal) {
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt(goal));

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_object");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.model());
        payload.put("messages", List.of(systemMessage, userMessage));
        payload.put("response_format", responseFormat);
        payload.put("temperature", 0.2);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the AI request", exception);
        }
    }

    private String prompt(Goal goal) {
        String description = goal.description() == null || goal.description().isEmpty()
                ? "No description provided"
                : goal.description();

        return "Analyze this personal goal and propose practical, ordered tasks to reach it. "
                + "Return strict JSON with exactly two fields: "
                + "\"analysis\" (a concise explanation) and "
                + "\"proposed_tasks\" (an array of 3 to 7 short task strings). "
                + "Do not include markdown.\n\n"
                + "Goal: " + goal.title() + "\n"
                + "Description: " + description;
    }

    private GoalAnalysis parseResponse(String body, String language) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new AiException(
                    502,
                    messages.t("ai_provider_invalid_response", language)
            );
        }

        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (!contentNode.isTextual()) {
            throw new AiException(
                    502,
                    messages.t("ai_provider_invalid_response", language)
            );
        }

        JsonNode analysisRoot;
        try {
            analysisRoot = objectMapper.readTree(contentNode.asText());
        } catch (JsonProcessingException exception) {
            throw invalidAnalysis(language);
        }

        JsonNode analysisNode = analysisRoot.path("analysis");
        JsonNode proposedNode = analysisRoot.path("proposed_tasks");
        if (!analysisNode.isTextual() || !proposedNode.isArray() || proposedNode.isEmpty()) {
            throw invalidAnalysis(language);
        }

        List<String> proposedTasks = new ArrayList<>();
        for (JsonNode taskNode : proposedNode) {
            if (!taskNode.isTextual() || taskNode.asText().isBlank()) {
                throw invalidAnalysis(language);
            }
            proposedTasks.add(taskNode.asText().strip());
        }

        return new GoalAnalysis(analysisNode.asText().strip(), List.copyOf(proposedTasks));
    }

    private AiException invalidAnalysis(String language) {
        return new AiException(
                502,
                messages.t("ai_provider_invalid_analysis", language)
        );
    }

    private String providerDetail(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("error").path("message").asText("");
        } catch (JsonProcessingException exception) {
            return "";
        }
    }
}
