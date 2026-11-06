package com.goaltracker.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Test(groups = "e2e")
public class PlaywrightTest extends AbstractTestNGSpringContextTests {

    private static final Path DATABASE = Path.of("target", "test-data", "e2e.db");

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeClass(alwaysRun = true)
    public void launchBrowser() {
        playwright = Playwright.create();
        boolean headless = !Boolean.parseBoolean(
                System.getProperty("playwright.headed", "false")
        );
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless)
        );
    }

    @AfterClass(alwaysRun = true)
    public void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void openPage() {
        context = browser.newContext();
        page = context.newPage();
        page.setDefaultTimeout(15_000);
        page.navigate("http://127.0.0.1:" + port + "/");
        registerFreshUser();
    }

    @AfterMethod(alwaysRun = true)
    public void closePage() {
        if (context != null) {
            context.close();
        }
    }

    // ==================== Scenarios ====================

    @Test
    public void goalTaskProgressAndDeletion() {
        String goalName = "Playwright goal " + System.nanoTime();
        String taskName = "Playwright task " + System.nanoTime();

        assertEquals(page.title(), "Goals and Tasks Tracker");
        Locator goal = createGoal(goalName, "Created by Playwright");
        select(goal);

        Locator task = createTask(taskName);
        awaitText(goal.locator(".progress-text"), "0%");
        task.locator("input[type='checkbox']").check();
        awaitText(goal.locator(".progress-text"), "100%");

        task.locator(".task-delete-btn").click();
        awaitCount(page.locator(".task-item").filter(hasText(taskName)), 0);

        goal.locator(".delete-btn").click();
        awaitCount(page.locator(".goal-item").filter(hasText(goalName)), 0);
        awaitHidden(page.locator("#tasks-container"));
    }

    @Test
    public void aiSuggestionsCanBeAddedAsTasks() throws JsonProcessingException {
        String goalName = "Playwright AI goal " + System.nanoTime();
        String suggestedTask = "Create a weekly action plan";

        Locator goal = createGoal(goalName, null);
        select(goal);

        String body = objectMapper.writeValueAsString(Map.of(
                "analysis", "Break the goal into practical steps.",
                "proposed_tasks", List.of(suggestedTask, "Review progress")
        ));
        page.route("**/api/goals/*/analyze", route -> route.fulfill(
                new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody(body)
        ));

        page.locator("#analyze-goal-button").click();

        Locator analysis = page.locator("#goal-analysis");
        awaitVisible(analysis);
        awaitText(analysis, "Break the goal into practical steps.");

        Locator suggested = analysis.locator(".suggested-tasks li")
                .filter(hasText(suggestedTask));
        awaitVisible(suggested);
        suggested.locator("button").click();
        awaitText(suggested.locator("button"), "Added");
        awaitVisible(page.locator(".task-item").filter(hasText(suggestedTask)));

        goal.locator(".delete-btn").click();
    }

    @Test
    public void userContentIsRenderedAsText() throws JsonProcessingException {
        String title = "<img src=x onerror=\"window.__goalXss=1\">Goal";
        String description = "<script>window.__descriptionXss=1</script>Description";
        String analysis = "<img src=x onerror=\"window.__analysisXss=1\">Analysis";

        page.locator("#goal-title").fill(title);
        page.locator("#goal-desc").fill(description);
        page.locator("#goal-form button").click();

        Locator goal = page.locator(".goal-item").first();
        awaitVisible(goal);
        assertEquals(goal.locator("h3").textContent(), title);
        assertEquals(goal.locator("p").textContent(), description);
        assertEquals(goal.locator("img").count(), 0);
        assertEquals(goal.locator("script").count(), 0);
        assertNull(page.evaluate("() => window.__goalXss ?? null"));
        assertNull(page.evaluate("() => window.__descriptionXss ?? null"));

        select(goal);

        String body = objectMapper.writeValueAsString(Map.of(
                "analysis", analysis,
                "proposed_tasks", List.of("<b>Task</b>")
        ));
        page.route("**/api/goals/*/analyze", route -> route.fulfill(
                new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody(body)
        ));

        page.locator("#analyze-goal-button").click();

        Locator result = page.locator("#goal-analysis");
        awaitVisible(result);
        awaitText(result.locator("strong"), analysis);
        assertEquals(result.locator("img").count(), 0);
        assertNull(page.evaluate("() => window.__analysisXss ?? null"));

        Locator suggested = result.locator(".suggested-tasks li").first();
        awaitText(suggested, "<b>Task</b>");
        assertEquals(suggested.locator("b").count(), 0);
    }

    @Test
    public void frontendFlow() {
        String goalName = "Playwright flow goal " + System.nanoTime();
        String taskName = "Playwright flow task " + System.nanoTime();

        assertEquals(page.title(), "Goals and Tasks Tracker");

        Locator goal = createGoal(goalName, "Created by Playwright");
        select(goal);
        createTask(taskName);
    }

    @Test
    public void goalProgressBarUpdates() {
        String goalName = "Playwright progress goal " + System.nanoTime();
        String taskName = "Playwright progress task " + System.nanoTime();

        Locator goal = createGoal(goalName, null);
        select(goal);
        awaitText(goal.locator(".progress-text"), "0%");

        Locator task = createTask(taskName);
        task.locator("input[type='checkbox']").check();
        awaitText(goal.locator(".progress-text"), "100%");
    }

    @Test
    public void deleteGoalAndTask() {
        String goalName = "Playwright delete goal " + System.nanoTime();
        String taskName = "Playwright delete task " + System.nanoTime();

        Locator goal = createGoal(goalName, null);
        select(goal);

        Locator task = createTask(taskName);
        task.locator(".task-delete-btn").click();
        awaitCount(page.locator(".task-item").filter(hasText(taskName)), 0);

        goal.locator(".delete-btn").click();
        awaitCount(page.locator(".goal-item").filter(hasText(goalName)), 0);
        awaitHidden(page.locator("#tasks-container"));
    }

    // ==================== Helpers ====================

    private void registerFreshUser() {
        page.locator("#toggle-auth-btn").click();
        page.locator("#auth-email").fill("e2e-" + System.nanoTime() + "@example.com");
        page.locator("#auth-password").fill("password123");
        page.locator("#auth-submit-btn").click();
        awaitVisible(page.locator("#app-screen"));
    }

    private Locator createGoal(String title, String description) {
        page.locator("#goal-title").fill(title);
        if (description != null) {
            page.locator("#goal-desc").fill(description);
        }
        page.locator("#goal-form button").click();

        Locator goal = page.locator(".goal-item").filter(hasText(title));
        awaitVisible(goal);
        return goal;
    }

    private void select(Locator goal) {
        goal.locator(".goal-main").click();
        awaitVisible(page.locator("#tasks-container"));
    }

    private Locator createTask(String title) {
        page.locator("#task-title").fill(title);
        page.locator("#task-form button").click();

        Locator task = page.locator(".task-item").filter(hasText(title));
        awaitVisible(task);
        return task;
    }

    private static Locator.FilterOptions hasText(String text) {
        return new Locator.FilterOptions().setHasText(text);
    }

    private void awaitVisible(Locator locator) {
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(15_000));
    }

    private void awaitHidden(Locator locator) {
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(15_000));
    }

    private void awaitCount(Locator locator, int expected) {
        awaitCondition(() -> locator.count() == expected);
    }

    private void awaitText(Locator locator, String expected) {
        awaitCondition(() -> {
            try {
                String text = locator.textContent();
                return text != null && text.contains(expected);
            } catch (RuntimeException exception) {
                return false;
            }
        });
    }

    private void awaitCondition(BooleanSupplier condition) {
        page.waitForCondition(condition);
        assertTrue(condition.getAsBoolean());
    }
}
