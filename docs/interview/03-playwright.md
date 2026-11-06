# Playwright Java для AQA-интервью

> В проекте Playwright 1.64.0 используется для 6 E2E-сценариев. Все примеры ниже —
> реальный код `src/test/java/com/goaltracker/e2e/PlaywrightTest.java`.

## 1. Что такое Playwright и как он устроен

Playwright — библиотека автоматизации браузеров от Microsoft. Один API для Chromium,
Firefox и WebKit.

Слои:

```
Java-тест
   │  вызовы API
   ▼
Playwright Java client  ──WebSocket/pipe──►  Playwright driver (Node.js)
                                                   │
                                                   ▼
                                        Chromium / Firefox / WebKit
```

- `Playwright.create()` запускает driver-процесс (Node.js внутри jar).
- Браузеры скачиваются отдельно в `%LOCALAPPDATA%\ms-playwright` (Windows) или
  `~/.cache/ms-playwright` (Linux). В CI — командой `install --with-deps chromium`.
- Клиент и браузер общаются по протоколу; поэтому тесты быстрее Selenium и не требуют
  отдельных драйверов (`chromedriver` и т.п.).

Иерархия объектов:

```
Playwright
└── BrowserType (chromium/firefox/webkit)
    └── Browser                 — процесс браузера
        └── BrowserContext      — изолированная сессия (cookies, storage, cache)
            └── Page            — вкладка
                └── Locator     — «ленивая» ссылка на элемент
```

**Изоляция:** контекст — это как инкогнито-профиль. Дешевле, чем запускать браузер,
но полностью разделяет состояние. Поэтому в проекте браузер один на класс, а контекст
новый на каждый тест.

## 2. Установка браузеров

```powershell
.\scripts\install-playwright.ps1
# внутри:
.\mvnw.cmd exec:java `
  -Dexec.mainClass=com.microsoft.playwright.CLI `
  -Dexec.classpathScope=test `
  '-Dexec.args=install --with-deps chromium'
```

`--with-deps` в Linux доставляет системные библиотеки (шрифты, libnss и т.д.).
В CI это отдельный шаг до запуска тестов.

## 3. Автоожидания и actionability

Перед действием (`click`, `fill`, `check`) Playwright проверяет элемент:

1. **attached** — есть в DOM;
2. **visible** — не `display:none`, ненулевой размер;
3. **stable** — не двигается (закончилась анимация);
4. **receives events** — не перекрыт другим элементом;
5. **enabled** — не `disabled`;
6. для `fill` — **editable**.

Всё это происходит автоматически с таймаутом. Поэтому `Thread.sleep` не нужен и вреден:
он либо замедляет, либо всё равно нестабилен.

Таймауты:

| Что | По умолчанию | Как изменить |
| --- | --- | --- |
| Действия и навигация | 30 с | `page.setDefaultTimeout(15_000)` |
| Web-first assertions | 5 с | `PlaywrightAssertions.setDefaultAssertionTimeout(15_000)` |
| Конкретный вызов | — | `click(new Page.ClickOptions().setTimeout(5_000))` |

В проекте:

```java
page.setDefaultTimeout(15_000);
```

**Вопрос:** Playwright ждёт загрузку страницы при `navigate`?
**Ответ:** да, по умолчанию ждёт событие `load` (`waitUntil = LOAD`). Можно
`DOMCONTENTLOADED` или `NETWORKIDLE`, но `NETWORKIDLE` на SPA может «зависать» из-за
постоянных запросов.

## 4. Локаторы

```java
page.locator("#goal-title");                       // CSS id
page.locator(".goal-item");                        // CSS class
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add")); // role
page.getByTestId("goal-card");                     // data-testid
page.getByText("Мои цели");                         // текст
page.locator(".goal-item").filter(hasText(goalName)); // фильтр по тексту
page.locator(".task-item").first();
page.locator(".task-item").nth(2);
```

Почему локаторы лучше `ElementHandle`:

- локатор ленивый: ищет элемент в момент вызова, а не один раз;
- после ре-рендера DOM ссылка не «протухает»;
- поддерживает цепочки и фильтры;
- автоожидание встроено.

Правила выбора:

1. `data-testid` / `getByTestId` — самый стабильный вариант.
2. ARIA-роли и текст — ближе к пользователю.
3. CSS — нормально, но избегать длинных цепочек `div > div:nth-child(3)`.
4. XPath — только если без него никак.

В проекте используются CSS-классы и `filter(hasText(...))`:

```java
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
```

`filter(hasText(...))` важен: мы не зависим от порядка целей на странице.

## 5. Проверки: TestNG Assert vs web-first assertions

Playwright даёт «веб-ожидающие» проверки:

```java
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

assertThat(page).hasTitle("Goals and Tasks Tracker");
assertThat(page.locator(".progress-text")).hasText("100%");
assertThat(page.locator(".goal-item")).hasCount(1);
assertThat(page.locator("#tasks-container")).isVisible();
assertThat(page.locator("input[type='checkbox']")).isChecked();
```

Они перепроверяют условие до таймаута (5 с по умолчанию) и не требуют `sleep`.

В проекте проверки сделаны через TestNG + собственное ожидание:

```java
private void awaitText(Locator locator, String expected) {
    awaitCondition(() -> {
        try {
            String text = locator.textContent();
            return text != null && text.contains(expected);
        } catch (RuntimeException exception) {
            return false;   // элемент мог исчезнуть между вызовами
        }
    });
}

private void awaitCondition(BooleanSupplier condition) {
    page.waitForCondition(condition);
    assertTrue(condition.getAsBoolean());
}
```

Почему так: единый стиль ассертов TestNG и явный контроль условия. Минус — больше
кода и хуже диагностика. На интервью честно скажите: «в проекте так, в продакшене я бы
использовал `assertThat(locator)`, потому что там авторетрай и понятные сообщения».

## 6. Формы и действия

```java
page.locator("#auth-email").fill("user@example.com");   // очищает и печатает
page.locator("#auth-password").fill("secret");
page.locator("#auth-submit-btn").click();
page.locator("input[type='checkbox']").check();         // check/uncheck
page.locator("#lang").selectOption("ru");               // select
page.locator("#title").press("Enter");                  // клавиши
page.locator("input[type=file]").setInputFiles(Paths.get("a.png"));
```

Особые случаи:

```java
// Диалог confirm/alert
page.onDialog(dialog -> dialog.accept());

// iframe
page.frameLocator("#frame").locator("#button").click();

// новая вкладка
Page popup = page.waitForPopup(() -> page.locator("#open").click());

// скачивание
Download download = page.waitForDownload(() -> page.locator("#export").click());
Path file = download.path();
```

## 7. Работа с сетью: моки и перехват

Это одна из главных причин выбирать Playwright.

```java
page.route("**/api/goals/*/analyze", route -> route.fulfill(
        new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("{\"analysis\":\"...\",\"proposed_tasks\":[\"A\"]}")
));
```

Что умеет `route`:

- `fulfill` — вернуть свой ответ, не ходя в сеть;
- `abort` — имитировать обрыв;
- `continue_` — пропустить, при необходимости изменив заголовки;
- `fetch` — сходить в сеть и модифицировать ответ.

Другие инструменты:

```java
Response response = page.waitForResponse(
        r -> r.url().contains("/api/goals") && r.status() == 200,
        () -> page.locator("#save").click()
);

page.onConsoleMessage(msg -> System.out.println(msg.type() + ": " + msg.text()));
page.onPageError(error -> fail("JS error: " + error));
page.onRequestFailed(request -> System.out.println("FAILED " + request.url()));
```

**Где ставить `page.route`?** До действия, которое вызывает запрос. В проекте — до
клика по кнопке анализа:

```java
page.route("**/api/goals/*/analyze", route -> route.fulfill(...));
page.locator("#analyze-goal-button").click();
```

**Вопрос:** зачем мокать AI в E2E, если есть настоящий сервис?
**Ответ:** E2E должен быть детерминированным и не зависеть от сети, денег и модели.
Мы проверяем, что фронтенд правильно показывает ответ и добавляет задачи. Сам клиент
OpenRouter проверяется отдельно — на локальном HTTP-сервере (юнит-уровень).

## 8. Аутентификация и `storageState`

В проекте каждый тест регистрирует нового пользователя через UI — максимальная
изоляция, но медленнее.

Альтернатива для больших сьютов:

```java
// один раз: залогинились и сохранили состояние
context.storageState(new BrowserContext.StorageStateOptions()
        .setPath(Paths.get("target/auth.json")));

// в каждом тесте: стартуем уже залогиненными
BrowserContext context = browser.newContext(
        new Browser.NewContextOptions()
                .setStorageStatePath(Paths.get("target/auth.json"))
);
```

Что хранится: cookies и `localStorage`/`sessionStorage`. Не храните так пароли и
токены в открытом виде — файл состояния считается секретом.

## 9. Отладка

| Инструмент | Как |
| --- | --- |
| Headed-режим | `.\test.ps1 -Suite e2e -Headed` (в проекте — системное свойство `playwright.headed`) |
| Пауза в коде | `page.pause()` |
| Инспектор | `PWDEBUG=1` перед запуском |
| Codegen | `-Dexec.args="codegen http://localhost:8080"` |
| Замедление | `new BrowserType.LaunchOptions().setSlowMo(500)` |
| Скриншот | `page.screenshot(new Page.ScreenshotOptions().setPath(...))` |
| Видео | `newContext(new Browser.NewContextOptions().setRecordVideoDir(...))` |
| Трассировка | см. ниже |
| Консоль/ошибки | `page.onConsoleMessage`, `page.onPageError` |

Трассировка — лучший инструмент для CI-падений:

```java
context.tracing().start(new Tracing.StartOptions()
        .setScreenshots(true)
        .setSnapshots(true)
        .setSources(true));

// ... тест ...

context.tracing().stop(new Tracing.StopOptions()
        .setPath(Paths.get("target/traces/test-" + testName + ".zip")));
```

Смотреть: `mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace trace.zip"`
(или открыть файл на <https://trace.playwright.dev>).

## 10. Параллельный запуск

Playwright Java **не потокобезопасен**:

> все методы `Playwright` и созданных им объектов (`Browser`, `BrowserContext`, `Page`)
> должны вызываться в том же потоке, где создан `Playwright`, либо нужна синхронизация.

Практика:

- отдельный `Playwright` (и обычно `Browser`) на поток;
- `BrowserContext`/`Page` — на тест;
- `ThreadLocal<Page>` или поля, создаваемые в `@BeforeMethod`;
- в TestNG: `<suite parallel="methods" thread-count="4">`.

```java
private static final ThreadLocal<Playwright> PLAYWRIGHT = ThreadLocal.withInitial(Playwright::create);
private static final ThreadLocal<Browser> BROWSER = ThreadLocal.withInitial(
        () -> PLAYWRIGHT.get().chromium().launch()
);
```

В текущем проекте параллельности нет: браузер один, контексты последовательные. Это
осознанно — 6 тестов, экономия не стоит усложнения.

## 11. Playwright vs Selenium

| | Playwright | Selenium |
| --- | --- | --- |
| Протокол | собственный (CDP и др.) через WebSocket | W3C WebDriver, HTTP |
| Драйверы браузеров | не нужны, браузеры скачиваются | нужны chromedriver/geckodriver |
| Автоожидания | встроены | нужно явно (`WebDriverWait`) |
| Проверки | web-first assertions с ретраем | обычные ассерты |
| Изоляция | `BrowserContext` — дешёвая | новый профиль/браузер дороже |
| Сеть | встроенный перехват, HAR | ограниченно (CDP/bippy) |
| Трассировка | trace viewer | внешние инструменты |
| Codegen | есть | IDE-плагины |
| API-тесты | `APIRequestContext` | нет (RestAssured отдельно) |
| Реальные устройства | нет | Appium |
| Экосистема/Grid | моложе | зрелее, Selenium Grid |

**Вопрос:** когда выбрать Selenium?
**Ответ:** если нужны реальные мобильные устройства (Appium), зрелый Grid в
организации, поддержка старых браузеров или уже большой парк Selenium-тестов.

## 12. Ограничения Playwright

- Нет настоящего Safari/iOS Safari — WebKit собрание, не Apple.
- Нет реальных мобильных устройств (эмуляция viewport/UA).
- Java-версия запускает Node-driver — дополнительный процесс и память.
- Не потокобезопасен по дизайну.
- Нет встроенного репортинга в Java (используем TestNG/Surefire/Allure).
- Не всё можно проверить через UI: визуальные регрессии требуют скриншотов/попадания.

## 13. Разбор E2E-сценариев проекта

### 13.1 CRUD + прогресс + удаление

```java
Locator goal = createGoal(goalName, "Created by Playwright");
select(goal);

Locator task = createTask(taskName);
awaitText(goal.locator(".progress-text"), "0%");
task.locator("input[type='checkbox']").check();
awaitText(goal.locator(".progress-text"), "100%");

task.locator(".task-delete-btn").click();
awaitCount(page.locator(".task-item").filter(hasText(taskName)), 0);
```

Проверяем: создание, выбор, прогресс, удаление задачи, удаление цели, скрытие панели
задач. Данные уникальны (`System.nanoTime()`), поэтому тест можно гонять сколько угодно.

### 13.2 AI-подсказки (мок сети)

```java
page.route("**/api/goals/*/analyze", route -> route.fulfill(...));
page.locator("#analyze-goal-button").click();

Locator suggested = page.locator("#goal-analysis .suggested-tasks li")
        .filter(hasText(suggestedTask));
suggested.locator("button").click();
awaitText(suggested.locator("button"), "Added");
```

Проверяем: рендер анализа, список задач, кнопку «Добавить», появление задачи в списке.
Сеть подменена — тест не зависит от OpenRouter.

### 13.3 XSS

```java
String title = "<img src=x onerror=\"window.__goalXss=1\">Goal";
...
assertEquals(goal.locator("h3").textContent(), title);
assertEquals(goal.locator("img").count(), 0);
assertNull(page.evaluate("() => window.__goalXss ?? null"));
```

Проверяем, что пользовательский текст рендерится как текст (`textContent`), а не как
HTML. Это пример «тест не только на функционал, но и на безопасность».

## 14. Вопросы-ответы (проговорить вслух)

**Чем `Locator` отличается от `ElementHandle`?**
Локатор ленивый и переискивает элемент при каждом действии; handle — конкретный
элемент, который может устареть после ре-рендера. Используйте локаторы.

**Как Playwright понимает, что элемент готов?**
Проверки actionability: attached, visible, stable, receives events, enabled/editable.

**Почему не `Thread.sleep`?**
Он не знает о состоянии страницы: либо лишняя задержка, либо всё равно мало. Playwright
ждёт событие/условие.

**Как проверить, что элемента нет?**
`assertThat(locator).hasCount(0)` или `isHidden()`; `waitFor` с `HIDDEN`. В проекте —
`awaitCount(locator, 0)`.

**Как дождаться ответа API?**
`page.waitForResponse(predicate, action)`.

**Как замокать бэкенд?**
`page.route(...)` + `fulfill`, либо `routeFromHAR` для записи/воспроизведения.

**Как тестировать загрузку файла?**
`setInputFiles(Path)`; для drag&drop — `DataTransfer` через `page.evaluate`.

**Как обработать `alert`/`confirm`?**
`page.onDialog(dialog -> dialog.accept())` — обязательно до действия.

**Как переключиться в iframe?**
`page.frameLocator("iframe").locator(...)`.

**Как открыть новую вкладку?**
`page.waitForPopup(() -> click)`.

**Как проверить locale/даты?**
`newContext(new Browser.NewContextOptions().setLocale("ru-RU").setTimezoneId("Europe/Moscow"))`.

**Как ускорить E2E?**
Меньше UI-шагов, API-подготовка данных, `storageState` вместо логина через UI,
параллельные воркеры, моки внешних сервисов.

**Почему тесты падают только в CI?**
Другой размер окна/шрифты/скорость, отсутствие браузера, headless-отличия, гонки,
зависимость от времени/локали. Лечится trace + фиксированным viewport/locale + ожиданиями.

**Что такое `BrowserContext` и зачем он?**
Изолированная сессия внутри браузера: свои cookies, storage, кэш, permissions.
Дешевле нового браузера, но полностью разделяет тесты.

**Можно ли использовать один браузер для всех тестов?**
Да, контекст на тест. Браузер — дорогой ресурс, контекст — дешёвый.
