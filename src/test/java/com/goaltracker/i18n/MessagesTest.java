package com.goaltracker.i18n;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@Test(groups = "api")
public class MessagesTest {

    private final Messages messages = new Messages();

    @Test
    public void fallsBackToEnglish() {
        assertEquals(messages.resolveLanguage(null), "en");
        assertEquals(messages.resolveLanguage(""), "en");
        assertEquals(messages.resolveLanguage("de-DE"), "en");
    }

    @Test
    public void resolvesLanguageWithRegion() {
        assertEquals(messages.resolveLanguage("ru-RU,ru;q=0.9,en;q=0.8"), "ru");
        assertEquals(messages.resolveLanguage("en-US"), "en");
    }

    @Test
    public void qualityValueWinsOverHeaderOrder() {
        assertEquals(messages.resolveLanguage("en;q=0.2, ru;q=0.9"), "ru");
        assertEquals(messages.resolveLanguage("ru;q=0.1, en;q=0.8"), "en");
    }

    @Test
    public void translatesAndInterpolates() {
        assertEquals(
                messages.t("email_already_registered", "ru"),
                "Электронная почта уже зарегистрирована"
        );
        assertEquals(
                messages.t("ai_provider_http_error", "ru", "status", 429),
                "Запрос к AI-провайдеру завершился со статусом 429."
        );
        assertEquals(messages.t("unknown_key", "en"), "unknown_key");
    }
}
