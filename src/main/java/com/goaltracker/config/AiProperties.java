package com.goaltracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the OpenRouter integration. Defaults match the Python backend.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String apiKey,
        String model,
        String apiUrl,
        String siteUrl,
        String appName
) {

    public AiProperties {
        apiKey = apiKey == null ? "" : apiKey;
        model = (model == null || model.isBlank())
                ? "deepseek/deepseek-v4.1-flash"
                : model;
        apiUrl = (apiUrl == null || apiUrl.isBlank())
                ? "https://openrouter.ai/api/v1/chat/completions"
                : apiUrl;
        siteUrl = (siteUrl == null || siteUrl.isBlank()) ? "http://localhost" : siteUrl;
        appName = (appName == null || appName.isBlank()) ? "My Goal App" : appName;
    }
}
