package com.goaltracker.i18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Localized API messages with the same resolution rules as the Python backend:
 * the Accept-Language header is parsed with quality values and falls back to English.
 */
@Component
public class Messages {

    public static final String DEFAULT_LANGUAGE = "en";

    private static final Map<String, Map<String, String>> MESSAGES = buildMessages();

    private static Map<String, Map<String, String>> buildMessages() {
        Map<String, String> en = new LinkedHashMap<>();
        en.put("email_already_registered", "Email already registered");
        en.put("invalid_credentials", "Invalid credentials");
        en.put("could_not_validate_credentials", "Could not validate credentials");
        en.put("goal_not_found", "Goal not found");
        en.put("task_not_found", "Task not found");
        en.put("not_authorized", "Not authorized");
        en.put("invalid_request_body", "Invalid request body");
        en.put("method_not_allowed", "Method not allowed");
        en.put("internal_server_error", "Internal server error");
        en.put("ai_not_configured", "AI analysis is not configured. Set OPENROUTER_API_KEY.");
        en.put("ai_provider_http_error", "AI provider request failed with status {status}.");
        en.put("ai_provider_unreachable", "AI provider could not be reached.");
        en.put("ai_provider_invalid_response", "AI provider returned an invalid response.");
        en.put("ai_provider_invalid_analysis", "AI provider returned an invalid goal analysis.");

        Map<String, String> ru = new LinkedHashMap<>();
        ru.put("email_already_registered", "Электронная почта уже зарегистрирована");
        ru.put("invalid_credentials", "Неверные учётные данные");
        ru.put("could_not_validate_credentials", "Не удалось проверить учётные данные");
        ru.put("goal_not_found", "Цель не найдена");
        ru.put("task_not_found", "Задача не найдена");
        ru.put("not_authorized", "Доступ запрещён");
        ru.put("invalid_request_body", "Некорректное тело запроса");
        ru.put("method_not_allowed", "Метод не поддерживается");
        ru.put("internal_server_error", "Внутренняя ошибка сервера");
        ru.put("ai_not_configured", "AI-анализ не настроен. Укажите OPENROUTER_API_KEY.");
        ru.put("ai_provider_http_error", "Запрос к AI-провайдеру завершился со статусом {status}.");
        ru.put("ai_provider_unreachable", "Не удалось связаться с AI-провайдером.");
        ru.put("ai_provider_invalid_response", "AI-провайдер вернул некорректный ответ.");
        ru.put("ai_provider_invalid_analysis", "AI-провайдер вернул некорректный анализ цели.");

        Map<String, Map<String, String>> messages = new LinkedHashMap<>();
        messages.put("en", Map.copyOf(en));
        messages.put("ru", Map.copyOf(ru));
        return Map.copyOf(messages);
    }

    public String resolveLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        List<Preference> preferences = new ArrayList<>();
        String[] parts = acceptLanguage.split(",");
        for (int index = 0; index < parts.length; index++) {
            String piece = parts[index].trim();
            if (piece.isEmpty()) {
                continue;
            }
            String[] parameters = piece.split(";");
            String language = parameters[0].trim().toLowerCase();
            double quality = 1.0;
            for (int paramIndex = 1; paramIndex < parameters.length; paramIndex++) {
                String parameter = parameters[paramIndex].trim();
                if (parameter.startsWith("q=")) {
                    try {
                        quality = Double.parseDouble(parameter.substring(2));
                    } catch (NumberFormatException exception) {
                        quality = 0.0;
                    }
                }
            }
            preferences.add(new Preference(quality, index, language));
        }

        preferences.sort(Comparator
                .comparingDouble(Preference::quality).reversed()
                .thenComparingInt(Preference::index));

        for (Preference preference : preferences) {
            String base = preference.language().split("-")[0];
            if (MESSAGES.containsKey(base)) {
                return base;
            }
        }
        return DEFAULT_LANGUAGE;
    }

    public String t(String key, String language) {
        Map<String, String> dictionary = MESSAGES.getOrDefault(language, MESSAGES.get(DEFAULT_LANGUAGE));
        String template = dictionary.get(key);
        if (template == null) {
            template = MESSAGES.get(DEFAULT_LANGUAGE).getOrDefault(key, key);
        }
        return template;
    }

    public String t(String key, String language, String placeholder, Object value) {
        return t(key, language).replace("{" + placeholder + "}", String.valueOf(value));
    }

    private record Preference(double quality, int index, String language) {
    }
}
