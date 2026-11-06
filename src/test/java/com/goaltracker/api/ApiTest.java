package com.goaltracker.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.goaltracker.ai.GoalAnalysis;
import com.goaltracker.ai.GoalAnalyzer;
import com.goaltracker.dto.GoalAnalysisResponse;
import com.goaltracker.dto.GoalResponse;
import com.goaltracker.dto.TaskResponse;
import com.goaltracker.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiTest.FakeAiConfig.class)
@Test(groups = "api")
public class ApiTest extends AbstractTestNGSpringContextTests {

    private static final Path DATABASE = Path.of("target", "test-data", "api.db");

    static {
        try {
            Files.deleteIfExists(DATABASE);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("app.database.path", DATABASE::toString);
        registry.add("app.ai.api-key", () -> "");
    }

    @Autowired
    private TestRestTemplate rest;

    // ==================== Health ====================

    @Test
    public void healthCheckRespondsOk() {
        ResponseEntity<Map> response = rest.getForEntity("/health", Map.class);

        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertNotNull(response.getBody());
        assertEquals(response.getBody().get("status"), "ok");
    }

    // ==================== Authentication ====================

    @Test
    public void registerAndLoginReturnTokens() {
        String email = uniqueEmail("login");

        ResponseEntity<TokenResponse> registered = post(
                "/api/auth/register",
                Map.of("email", email, "password", "secret"),
                null,
                null,
                TokenResponse.class
        );
        assertEquals(registered.getStatusCode(), HttpStatus.OK);
        assertNotNull(registered.getBody());
        assertEquals(registered.getBody().tokenType(), "bearer");
        assertTrue(registered.getBody().accessToken().length() > 20);

        ResponseEntity<TokenResponse> loggedIn = post(
                "/api/auth/login",
                Map.of("email", email, "password", "secret"),
                null,
                null,
                TokenResponse.class
        );
        assertEquals(loggedIn.getStatusCode(), HttpStatus.OK);
        assertNotNull(loggedIn.getBody());
        assertTrue(loggedIn.getBody().accessToken().length() > 20);

        ResponseEntity<ApiError> wrongPassword = post(
                "/api/auth/login",
                Map.of("email", email, "password", "wrong-password"),
                null,
                null,
                ApiError.class
        );
        assertEquals(wrongPassword.getStatusCode(), HttpStatus.UNAUTHORIZED);
        assertEquals(wrongPassword.getBody().detail(), "Invalid credentials");
    }

    @Test
    public void duplicateEmailIsRejected() {
        String email = uniqueEmail("duplicate");
        newUser(email);

        ResponseEntity<ApiError> duplicate = post(
                "/api/auth/register",
                Map.of("email", email, "password", "secret"),
                null,
                null,
                ApiError.class
        );

        assertEquals(duplicate.getStatusCode(), HttpStatus.BAD_REQUEST);
        assertEquals(duplicate.getBody().detail(), "Email already registered");
    }

    @Test
    public void missingPasswordIsRejectedAsUnprocessable() {
        ResponseEntity<ApiError> response = post(
                "/api/auth/register",
                Map.of("email", uniqueEmail("nopassword")),
                null,
                null,
                ApiError.class
        );

        assertEquals(response.getStatusCode(), HttpStatus.UNPROCESSABLE_ENTITY);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().detail());
    }

    // ==================== Goals ====================

    @Test
    public void createAndGetGoal() {
        String token = newUser();

        ResponseEntity<GoalResponse> created = post(
                "/api/goals",
                Map.of("title", "Тестовая цель", "description", "Описание тестовой цели"),
                token,
                null,
                GoalResponse.class
        );
        assertEquals(created.getStatusCode(), HttpStatus.OK);
        GoalResponse goal = created.getBody();
        assertNotNull(goal);
        assertTrue(goal.id() > 0);
        assertEquals(goal.title(), "Тестовая цель");
        assertEquals(goal.description(), "Описание тестовой цели");
        assertFalse(goal.isCompleted());
        assertTrue(goal.userId() > 0);
        assertNotNull(goal.tasks());
        assertTrue(goal.tasks().isEmpty());

        ResponseEntity<GoalResponse[]> listed = get(
                "/api/goals",
                token,
                null,
                GoalResponse[].class
        );
        assertEquals(listed.getStatusCode(), HttpStatus.OK);
        assertNotNull(listed.getBody());
        assertEquals(listed.getBody().length, 1);
        assertEquals(listed.getBody()[0].title(), "Тестовая цель");
    }

    @Test
    public void goalsListIncludesTasksForProgressBars() {
        String token = newUser();
        GoalResponse goal = createGoal(token, "Цель с задачей", "Описание");

        ResponseEntity<TaskResponse> createdTask = post(
                "/api/goals/" + goal.id() + "/tasks",
                Map.of("title", "Первая задача"),
                token,
                null,
                TaskResponse.class
        );
        assertEquals(createdTask.getStatusCode(), HttpStatus.OK);

        ResponseEntity<GoalResponse[]> listed = get(
                "/api/goals",
                token,
                null,
                GoalResponse[].class
        );

        assertEquals(listed.getStatusCode(), HttpStatus.OK);
        assertNotNull(listed.getBody());
        assertEquals(listed.getBody()[0].tasks().size(), 1);
        assertEquals(listed.getBody()[0].tasks().getFirst().title(), "Первая задача");
    }

    @Test
    public void updateGoalKeepsOmittedFields() {
        String token = newUser();
        GoalResponse goal = createGoal(token, "Старое имя", "Старое описание");

        ResponseEntity<GoalResponse> updated = put(
                "/api/goals/" + goal.id(),
                Map.of("title", "Новое имя", "description", "Новое описание"),
                token,
                GoalResponse.class
        );
        assertEquals(updated.getStatusCode(), HttpStatus.OK);
        assertEquals(updated.getBody().title(), "Новое имя");
        assertEquals(updated.getBody().description(), "Новое описание");

        ResponseEntity<GoalResponse> partial = put(
                "/api/goals/" + goal.id(),
                Map.of("description", "Только описание"),
                token,
                GoalResponse.class
        );
        assertEquals(partial.getStatusCode(), HttpStatus.OK);
        assertEquals(partial.getBody().title(), "Новое имя");
        assertEquals(partial.getBody().description(), "Только описание");
    }

    @Test
    public void createGoalWithoutTitleIsRejected() {
        String token = newUser();

        ResponseEntity<ApiError> response = post(
                "/api/goals",
                Map.of("description", "Описание без названия"),
                token,
                null,
                ApiError.class
        );

        assertEquals(response.getStatusCode(), HttpStatus.UNPROCESSABLE_ENTITY);
        assertNotNull(response.getBody());
    }

    // ==================== Tasks ====================

    @Test
    public void createTaskForGoal() {
        String token = newUser();
        GoalResponse goal = createGoal(token, "Цель для задач", null);

        ResponseEntity<TaskResponse> response = post(
                "/api/goals/" + goal.id() + "/tasks",
                Map.of("title", "Подзадача 1"),
                token,
                null,
                TaskResponse.class
        );

        assertEquals(response.getStatusCode(), HttpStatus.OK);
        TaskResponse task = response.getBody();
        assertNotNull(task);
        assertTrue(task.id() > 0);
        assertEquals(task.title(), "Подзадача 1");
        assertEquals(task.goalId(), goal.id());
        assertFalse(task.isCompleted());

        ResponseEntity<TaskResponse[]> tasks = get(
                "/api/goals/" + goal.id() + "/tasks",
                token,
                null,
                TaskResponse[].class
        );
        assertEquals(tasks.getStatusCode(), HttpStatus.OK);
        assertNotNull(tasks.getBody());
        assertEquals(tasks.getBody().length, 1);
    }

    @Test
    public void patchTaskUpdatesCompletionAndTitle() {
        String token = newUser();
        GoalResponse goal = createGoal(token, "Цель для обновления задачи", null);
        TaskResponse task = createTask(token, goal.id(), "Исходное имя");

        ResponseEntity<TaskResponse> completed = patch(
                "/api/tasks/" + task.id(),
                Map.of("is_completed", true),
                token,
                TaskResponse.class
        );
        assertEquals(completed.getStatusCode(), HttpStatus.OK);
        assertTrue(completed.getBody().isCompleted());
        assertEquals(completed.getBody().title(), "Исходное имя");

        ResponseEntity<TaskResponse> renamed = patch(
                "/api/tasks/" + task.id(),
                Map.of("title", "Новое имя"),
                token,
                TaskResponse.class
        );
        assertEquals(renamed.getStatusCode(), HttpStatus.OK);
        assertTrue(renamed.getBody().isCompleted());
        assertEquals(renamed.getBody().title(), "Новое имя");

        ResponseEntity<TaskResponse> put = put(
                "/api/tasks/" + task.id(),
                Map.of("is_completed", false),
                token,
                TaskResponse.class
        );
        assertEquals(put.getStatusCode(), HttpStatus.OK);
        assertFalse(put.getBody().isCompleted());
    }

    @Test
    public void deleteTaskRemovesIt() {
        String token = newUser();
        GoalResponse goal = createGoal(token, "Цель для удаления задачи", null);
        TaskResponse task = createTask(token, goal.id(), "Задача для удаления");

        ResponseEntity<TaskResponse> deleted = exchange(
                HttpMethod.DELETE,
                "/api/tasks/" + task.id(),
                null,
                token,
                null,
                TaskResponse.class
        );
        assertEquals(deleted.getStatusCode(), HttpStatus.OK);
        assertEquals(deleted.getBody().id(), task.id());

        ResponseEntity<TaskResponse[]> tasks = get(
                "/api/goals/" + goal.id() + "/tasks",
                token,
                null,
                TaskResponse[].class
        );
        assertEquals(tasks.getBody().length, 0);
    }

    @Test
    public void deleteGoalCascadesToTasks() {
        String token = newUser();
        GoalResponse goal = createGoal(token, "Цель с задачами для удаления", null);
        TaskResponse task = createTask(token, goal.id(), "Задача удалится вместе с целью");

        ResponseEntity<GoalResponse> deleted = exchange(
                HttpMethod.DELETE,
                "/api/goals/" + goal.id(),
                null,
                token,
                null,
                GoalResponse.class
        );
        assertEquals(deleted.getStatusCode(), HttpStatus.OK);
        assertEquals(deleted.getBody().id(), goal.id());

        ResponseEntity<GoalResponse[]> goals = get(
                "/api/goals",
                token,
                null,
                GoalResponse[].class
        );
        assertEquals(goals.getBody().length, 0);

        ResponseEntity<ApiError> tasks = get(
                "/api/goals/" + goal.id() + "/tasks",
                token,
                null,
                ApiError.class
        );
        assertEquals(tasks.getStatusCode(), HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> deletedTask = exchange(
                HttpMethod.DELETE,
                "/api/tasks/" + task.id(),
                null,
                token,
                null,
                ApiError.class
        );
        assertEquals(deletedTask.getStatusCode(), HttpStatus.NOT_FOUND);
    }

    // ==================== AI analysis ====================

    @Test
    public void analyzeGoalReturnsProposedTasks() {
        String token = newUser();
        GoalResponse goal = createGoal(
                token,
                "Подготовиться к марафону",
                "Пробежать 42 км"
        );

        ResponseEntity<GoalAnalysisResponse> response = post(
                "/api/goals/" + goal.id() + "/analyze",
                null,
                token,
                null,
                GoalAnalysisResponse.class
        );

        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertNotNull(response.getBody());
        assertEquals(
                response.getBody().analysis(),
                "Нужно постепенно развить выносливость."
        );
        assertEquals(
                response.getBody().proposedTasks(),
                List.of("Составить план тренировок", "Провести легкую пробежку")
        );
    }

    // ==================== Authorization ====================

    @Test
    public void goalsEndpointsRequireAuthentication() {
        ResponseEntity<ApiError> list = get(
                "/api/goals",
                null,
                null,
                ApiError.class
        );
        assertEquals(list.getStatusCode(), HttpStatus.UNAUTHORIZED);
        assertEquals(list.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), "Bearer");

        ResponseEntity<ApiError> create = post(
                "/api/goals",
                Map.of("title", "No auth"),
                null,
                null,
                ApiError.class
        );
        assertEquals(create.getStatusCode(), HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void invalidTokenIsRejected() {
        ResponseEntity<ApiError> response = get(
                "/api/goals",
                "not-a-valid-token",
                null,
                ApiError.class
        );

        assertEquals(response.getStatusCode(), HttpStatus.UNAUTHORIZED);
        assertEquals(response.getBody().detail(), "Could not validate credentials");
    }

    @Test
    public void goalsAreIsolatedBetweenUsers() {
        String first = newUser();
        String second = newUser();
        createGoal(first, "First goal", null);

        ResponseEntity<GoalResponse[]> firstGoals = get(
                "/api/goals",
                first,
                null,
                GoalResponse[].class
        );
        ResponseEntity<GoalResponse[]> secondGoals = get(
                "/api/goals",
                second,
                null,
                GoalResponse[].class
        );

        assertEquals(firstGoals.getBody().length, 1);
        assertEquals(secondGoals.getBody().length, 0);
    }

    @Test
    public void foreignGoalIsNotAccessible() {
        String owner = newUser();
        String intruder = newUser();
        GoalResponse goal = createGoal(owner, "Private goal", null);

        ResponseEntity<GoalResponse[]> intruderGoals = get(
                "/api/goals",
                intruder,
                null,
                GoalResponse[].class
        );
        assertEquals(intruderGoals.getBody().length, 0);

        assertEquals(get(
                "/api/goals/" + goal.id() + "/tasks",
                intruder,
                null,
                ApiError.class
        ).getStatusCode(), HttpStatus.NOT_FOUND);

        assertEquals(put(
                "/api/goals/" + goal.id(),
                Map.of("title", "Hacked"),
                intruder,
                ApiError.class
        ).getStatusCode(), HttpStatus.NOT_FOUND);

        assertEquals(post(
                "/api/goals/" + goal.id() + "/analyze",
                null,
                intruder,
                null,
                ApiError.class
        ).getStatusCode(), HttpStatus.NOT_FOUND);

        assertEquals(exchange(
                HttpMethod.DELETE,
                "/api/goals/" + goal.id(),
                null,
                intruder,
                null,
                ApiError.class
        ).getStatusCode(), HttpStatus.NOT_FOUND);

        ResponseEntity<GoalResponse[]> ownerGoals = get(
                "/api/goals",
                owner,
                null,
                GoalResponse[].class
        );
        assertEquals(ownerGoals.getBody().length, 1);
        assertEquals(ownerGoals.getBody()[0].title(), "Private goal");
    }

    @Test
    public void errorMessagesAreLocalized() {
        String email = uniqueEmail("localized");
        newUser(email);

        ResponseEntity<ApiError> duplicate = post(
                "/api/auth/register",
                Map.of("email", email, "password", "secret"),
                null,
                "ru",
                ApiError.class
        );
        assertEquals(duplicate.getStatusCode(), HttpStatus.BAD_REQUEST);
        assertEquals(
                duplicate.getBody().detail(),
                "Электронная почта уже зарегистрирована"
        );

        ResponseEntity<ApiError> invalidToken = get(
                "/api/goals",
                "nope",
                "ru",
                ApiError.class
        );
        assertEquals(invalidToken.getStatusCode(), HttpStatus.UNAUTHORIZED);
        assertEquals(
                invalidToken.getBody().detail(),
                "Не удалось проверить учётные данные"
        );

        ResponseEntity<ApiError> qualityBeatsOrder = post(
                "/api/auth/register",
                Map.of("email", email, "password", "secret"),
                null,
                "en;q=0.2, ru;q=0.9",
                ApiError.class
        );
        assertEquals(
                qualityBeatsOrder.getBody().detail(),
                "Электронная почта уже зарегистрирована"
        );
    }

    // ==================== Helpers ====================

    private String newUser() {
        return newUser(uniqueEmail("user"));
    }

    private String newUser(String email) {
        ResponseEntity<TokenResponse> response = post(
                "/api/auth/register",
                Map.of("email", email, "password", "secret"),
                null,
                null,
                TokenResponse.class
        );
        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertNotNull(response.getBody());
        return response.getBody().accessToken();
    }

    private GoalResponse createGoal(String token, String title, String description) {
        Map<String, Object> body = description == null
                ? Map.of("title", title)
                : Map.of("title", title, "description", description);

        ResponseEntity<GoalResponse> response = post(
                "/api/goals",
                body,
                token,
                null,
                GoalResponse.class
        );
        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private TaskResponse createTask(String token, long goalId, String title) {
        ResponseEntity<TaskResponse> response = post(
                "/api/goals/" + goalId + "/tasks",
                Map.of("title", title),
                token,
                null,
                TaskResponse.class
        );
        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private <T> ResponseEntity<T> get(String path, String token, String language, Class<T> type) {
        return exchange(HttpMethod.GET, path, null, token, language, type);
    }

    private <T> ResponseEntity<T> post(
            String path,
            Object body,
            String token,
            String language,
            Class<T> type
    ) {
        return exchange(HttpMethod.POST, path, body, token, language, type);
    }

    private <T> ResponseEntity<T> put(String path, Object body, String token, Class<T> type) {
        return exchange(HttpMethod.PUT, path, body, token, null, type);
    }

    private <T> ResponseEntity<T> patch(String path, Object body, String token, Class<T> type) {
        return exchange(HttpMethod.PATCH, path, body, token, null, type);
    }

    private <T> ResponseEntity<T> exchange(
            HttpMethod method,
            String path,
            Object body,
            String token,
            String language,
            Class<T> type
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (language != null) {
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, language);
        }

        HttpEntity<Object> entity = body == null
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(body, headers);

        return rest.exchange(path, method, entity, type);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    @TestConfiguration
    static class FakeAiConfig {

        @Bean
        @Primary
        GoalAnalyzer fakeGoalAnalyzer() {
            return (goal, language) -> new GoalAnalysis(
                    "Нужно постепенно развить выносливость.",
                    List.of("Составить план тренировок", "Провести легкую пробежку")
            );
        }
    }
}
