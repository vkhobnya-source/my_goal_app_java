# Goals and Tasks Tracker (Java)

[![CI](https://github.com/vkhobnya-source/my_goal_app_java/actions/workflows/ci.yml/badge.svg)](https://github.com/vkhobnya-source/my_goal_app_java/actions/workflows/ci.yml)

Тот же трекер целей и задач, что и в `my_goal_app`, но бэкенд переписан на Java.
Фронтенд (`frontend/index.html`, `frontend/style.css`) скопирован без изменений.

- **Backend:** Java 25, Spring Boot 3.5.16, Spring Web + JdbcTemplate
- **БД:** SQLite (файл создаётся автоматически)
- **Тесты:** TestNG (API) и Playwright Java (E2E)
- **Без Docker:** Spring Boot сам раздаёт статический фронтенд из `frontend/`
- **Сборка:** Maven Wrapper (`mvnw`/`mvnw.cmd`), Maven 3.9.16 скачивается автоматически

## Требования

- JDK 25+ (подойдёт JBR из IntelliJ IDEA — скрипты найдут его сами)
- Windows: PowerShell 5.1+; Linux/macOS: `mvnw` и `bash`
- Интернет при первой сборке (зависимости Maven и браузер Playwright)

## Запуск

```powershell
.\run.ps1
```

Приложение: <http://localhost:8080> (API: `/api`, health-check: `/health`).

Параметры:

```powershell
.\run.ps1 -Port 8081
.\run.ps1 -Database .\data\goals.db
```

Сборка и запуск jar:

```powershell
.\mvnw.cmd -DskipTests package
java -jar target\goals-tracker-1.0.0.jar
```

## Тесты

```powershell
.\test.ps1                  # API-тесты (TestNG, группа api)
.\test.ps1 -Suite e2e       # Playwright Java (группа e2e)
.\test.ps1 -Suite all       # обе группы
.\test.ps1 -Suite e2e -Headed
```

Перед первым запуском E2E скачайте браузер:

```powershell
.\scripts\install-playwright.ps1
```

Тесты поднимают приложение на случайном порту и используют отдельные файлы БД:
`target/test-data/api.db` и `target/test-data/e2e.db`.

## CI

GitHub Actions (`.github/workflows/ci.yml`) на каждый push в `main` и pull request
запускает две независимые задачи: API-тесты (TestNG) и E2E-тесты (Playwright,
Chromium устанавливается в runner). Отчёты Surefire сохраняются артефактами.

## Настройка

Все параметры задаются переменными окружения (или аргументами `--app.*`):

| Переменная | По умолчанию | Назначение |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Порт HTTP-сервера |
| `DATABASE_PATH` | `goals.db` | Путь к файлу SQLite |
| `SECRET_KEY` | `your-secret-key-change-this-in-production` | Секрет для подписи JWT |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | `30` | Время жизни токена |
| `CORS_ORIGINS` | `http://localhost:8080` | Разрешённые origin через запятую |
| `OPENROUTER_API_KEY` | пусто | Ключ OpenRouter для AI-анализа |
| `OPENROUTER_MODEL` | `deepseek/deepseek-v4.1-flash` | Модель |
| `OPENROUTER_API_URL` | `https://openrouter.ai/api/v1/chat/completions` | Endpoint провайдера |
| `OPENROUTER_SITE_URL` | `http://localhost` | Заголовок `HTTP-Referer` |
| `OPENROUTER_APP_NAME` | `My Goal App` | Заголовок `X-Title` |

## API

Контракт совпадает с Python-версией, ошибки возвращаются как `{"detail": "..."}`
с локализацией по `Accept-Language` (`en`/`ru`).

| Метод | Путь | Описание |
| --- | --- | --- |
| `GET` | `/health` | Проверка живости |
| `POST` | `/api/auth/register` | Регистрация, возвращает JWT |
| `POST` | `/api/auth/login` | Вход, возвращает JWT |
| `GET` | `/api/goals` | Цели текущего пользователя вместе с задачами |
| `POST` | `/api/goals` | Создать цель |
| `PUT` | `/api/goals/{id}` | Обновить цель (необязательные поля сохраняются) |
| `DELETE` | `/api/goals/{id}` | Удалить цель вместе с задачами |
| `POST` | `/api/goals/{id}/analyze` | AI-анализ цели и предложенные задачи |
| `GET` | `/api/goals/{id}/tasks` | Задачи цели |
| `POST` | `/api/goals/{id}/tasks` | Создать задачу |
| `PUT`/`PATCH` | `/api/tasks/{id}` | Обновить задачу |
| `DELETE` | `/api/tasks/{id}` | Удалить задачу |

## Структура

```
frontend/                     статический фронтенд (без изменений)
src/main/java/com/goaltracker/
  api/                        контроллеры, обработка ошибок
  auth/                       JWT и BCrypt
  ai/                         клиент OpenRouter
  config/                     DataSource, CORS, резолвер текущего пользователя
  domain/, dto/, repository/  модели, DTO и доступ к SQLite
  i18n/                       сообщения en/ru
  service/                    бизнес-логика
src/main/resources/           application.yml, schema.sql
src/test/java/com/goaltracker/
  api/                        API-тесты TestNG
  ai/, i18n/                  модульные тесты
  e2e/                        Playwright Java
scripts/                      вспомогательные скрипты PowerShell
```
