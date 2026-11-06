package com.goaltracker.ai;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goaltracker.config.AiProperties;
import com.goaltracker.domain.Goal;
import com.goaltracker.i18n.Messages;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(groups = "api")
public class OpenRouterGoalAnalyzerTest {

    private final Messages messages = new Messages();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Goal goal = new Goal(1, "Подготовиться к марафону", "Пробежать 42 км", false, 1);

    private HttpServer server;

    @AfterMethod(alwaysRun = true)
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    public void sendsPromptAndParsesAnalysis() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        String body = providerResponse("  Нужно развить выносливость.  ", List.of("  План  ", "Пробежка"));
        String url = startServer(200, body, authorization, requestBody);

        GoalAnalysis analysis = analyzer(url, "test-key").analyze(goal, "en");

        assertEquals(analysis.analysis(), "Нужно развить выносливость.");
        assertEquals(analysis.proposedTasks(), List.of("План", "Пробежка"));
        assertEquals(authorization.get(), "Bearer test-key");
        assertTrue(requestBody.get().contains("\"model\":\"model-x\""));
        assertTrue(requestBody.get().contains("Подготовиться к марафону"));
        assertTrue(requestBody.get().contains("\"response_format\":{\"type\":\"json_object\"}"));
    }

    @Test
    public void reportsMissingApiKeyAsServiceUnavailable() {
        AiException exception = expectAiException(
                () -> analyzer("http://127.0.0.1:1/unused", "").analyze(goal, "en")
        );

        assertEquals(exception.getStatus(), 503);
        assertEquals(exception.getMessage(), "AI analysis is not configured. Set OPENROUTER_API_KEY.");
    }

    @Test
    public void mapsProviderHttpError() throws Exception {
        String url = startServer(
                429,
                objectMapper.writeValueAsString(
                        Map.of("error", Map.of("message", "rate limited"))
                ),
                null,
                null
        );

        AiException exception = expectAiException(
                () -> analyzer(url, "test-key").analyze(goal, "en")
        );

        assertEquals(exception.getStatus(), 502);
        assertTrue(exception.getMessage().contains("429"));
        assertTrue(exception.getMessage().contains("rate limited"));
    }

    @Test
    public void mapsUnreachableProvider() {
        AiException exception = expectAiException(
                () -> analyzer("http://127.0.0.1:1/chat", "test-key").analyze(goal, "en")
        );

        assertEquals(exception.getStatus(), 502);
        assertEquals(exception.getMessage(), "AI provider could not be reached.");
    }

    @Test
    public void rejectsInvalidProviderJson() throws Exception {
        String url = startServer(200, "this is not json", null, null);

        AiException exception = expectAiException(
                () -> analyzer(url, "test-key").analyze(goal, "en")
        );

        assertEquals(exception.getStatus(), 502);
        assertEquals(exception.getMessage(), "AI provider returned an invalid response.");
    }

    @Test
    public void rejectsInvalidGoalAnalysis() throws Exception {
        String content = objectMapper.writeValueAsString(Map.of("analysis", "Нет списка задач"));
        String url = startServer(200, wrapper(content), null, null);

        AiException exception = expectAiException(
                () -> analyzer(url, "test-key").analyze(goal, "en")
        );

        assertEquals(exception.getStatus(), 502);
        assertEquals(exception.getMessage(), "AI provider returned an invalid goal analysis.");
    }

    @Test
    public void localizesProviderErrors() throws Exception {
        String url = startServer(500, "{}", null, null);

        AiException exception = expectAiException(
                () -> analyzer(url, "test-key").analyze(goal, "ru")
        );

        assertEquals(exception.getStatus(), 502);
        assertTrue(exception.getMessage().startsWith("Запрос к AI-провайдеру"));
    }

    private static AiException expectAiException(Runnable action) {
        try {
            action.run();
        } catch (AiException exception) {
            return exception;
        }
        fail("Expected an AiException to be thrown");
        return null;
    }

    private OpenRouterGoalAnalyzer analyzer(String url, String apiKey) {
        return new OpenRouterGoalAnalyzer(
                new AiProperties(apiKey, "model-x", url, "http://localhost", "Test App"),
                messages,
                objectMapper
        );
    }

    private String providerResponse(String analysis, List<String> tasks)
            throws JsonProcessingException {
        String content = objectMapper.writeValueAsString(
                Map.of("analysis", analysis, "proposed_tasks", tasks)
        );
        return wrapper(content);
    }

    private String wrapper(String content) throws JsonProcessingException {
        return objectMapper.writeValueAsString(
                Map.of("choices", List.of(Map.of("message", Map.of("content", content))))
        );
    }

    private String startServer(
            int status,
            String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody
    ) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            if (authorization != null) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            }
            if (requestBody != null) {
                requestBody.set(new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                ));
            }

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/chat";
    }
}
