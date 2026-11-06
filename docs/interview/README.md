# Подготовка к интервью AQA (Java + Playwright + TestNG)

Материал построен на реальном проекте `my_goal_app_java`: все примеры кода — из него,
поэтому на интервью можно честно говорить «в моём проекте это сделано вот так».

## Как пользоваться

| Файл | О чём | Когда читать |
| --- | --- | --- |
| `01-java-core.md` | Java: типы, ООП, коллекции, исключения, JVM, Maven, Spring-минимум | 1-й и 2-й день |
| `02-testng.md` | TestNG: жизненный цикл, группы, DataProvider, listeners, parallel | 2-й день |
| `03-playwright.md` | Playwright Java: локаторы, автоожидания, сеть, трассировка, parallel | 3-й день |
| `04-framework-and-qa.md` | Архитектура тестов, изоляция, моки, CI, «что улучшить», вопросы по коду | 3-й и 4-й день |
| `05-rapid-fire.md` | 80 коротких вопросов-ответов на повторение | перед интервью |
| `06-project-story.md` | Рассказ о проекте (RU/EN), 3 истории багов, вопросы интервьюеру | 4-й день |

## План на 4 дня (по 1,5–2 часа)

1. **День 1 — Java-база.** `01-java-core.md` целиком. После чтения вслух объясни:
   `equals/hashCode`, `HashMap`, `checked/unchecked`, `record`, `Stream`, `@Transactional`.
2. **День 2 — TestNG + Java-практика.** `02-testng.md`, затем открой `ApiTest` и назови
   вслух, что делает каждый блок. Повтори `assertThrows`/`expectThrows`, группы, listeners.
3. **День 3 — Playwright + фреймворк.** `03-playwright.md` и `04-framework-and-qa.md`.
   Прогони `.\test.ps1 -Suite e2e -Headed` и параллельно рассказывай, что происходит.
4. **День 4 — речь.** `05-rapid-fire.md` + `06-project-story.md`. Запиши на диктофон
   60-секундный рассказ о проекте, послушай, перепиши.

## Карта проекта (что где лежит)

```
frontend/                                   статический фронтенд (не менялся)
src/main/java/com/goaltracker/
  GoalsTrackerApplication.java              точка входа Spring Boot
  api/          контроллеры, ApiException, GlobalExceptionHandler
  auth/         JwtService (HS256), PasswordService (BCrypt)
  ai/           GoalAnalyzer, OpenRouterGoalAnalyzer, AiException
  config/       DataSourceConfig, WebConfig, CurrentUser + резолвер
  domain/       User, Goal, Task (records)
  dto/          запросы/ответы (records + @JsonProperty)
  i18n/         Messages: en/ru + разбор Accept-Language
  repository/   JdbcTemplate + PreparedStatement
  service/      AuthService, GoalService, TaskService (@Transactional)
src/main/resources/
  application.yml                          конфигурация через env
  schema.sql                               схема SQLite + индексы
src/test/java/com/goaltracker/
  api/ApiTest.java                         18 API-тестов (TestNG + TestRestTemplate)
  ai/OpenRouterGoalAnalyzerTest.java       7 тестов клиента на локальном HttpServer
  i18n/MessagesTest.java                   4 теста локализации
  e2e/PlaywrightTest.java                  6 E2E-сценариев
.github/workflows/ci.yml                   2 job'а: API и E2E
```

## Цифры, которые стоит помнить

- 35 тестов: 18 API + 11 unit + 6 E2E.
- API-прогон ~5–10 секунд, E2E ~40 секунд (headless, Chromium).
- 12 эндпоинтов API; JWT живёт 30 минут; BCrypt для паролей; SQLite.
- 3 уровня подмены внешнего AI: fake-бин, локальный HTTP-сервер, `page.route`.
- 2 job'а в CI, JDK 25 (Temurin), Maven Wrapper 3.9.16, Playwright 1.64.0.

## Главные тезисы для интервью (запомнить дословно)

1. «Тесты не зависят от порядка: у каждого свой пользователь, своя БД на сьют и
   случайный порт».
2. «Внешний AI я не дёргаю в тестах: подменяю на трёх уровнях — бин, HTTP-сервер,
   сеть браузера. Проверяю свой код, а не OpenRouter».
3. «E2E — только критичные пользовательские сценарии (6 тестов), основная масса
   проверок — API (18 тестов) и unit (11). Это пирамида, а не «всё через UI»».
4. «Playwright сам ждёт готовности элемента; `Thread.sleep` в проекте нет».
5. «Ошибки API всегда `{"detail": "..."}` и локализованы по `Accept-Language`,
   поэтому неизменённый фронтенд показывает их как есть».
