# TestNG для AQA-интервью

> В проекте TestNG — единственный тест-фреймворк: и API-тесты, и Playwright E2E.
> JUnit специально не подключён, чтобы Maven Surefire выбрал TestNG-провайдер.

## 1. Как TestNG подключён к проекту

`pom.xml`:

```xml
<properties>
    <testng.groups>api</testng.groups>
    <testng.version>7.12.0</testng.version>
</properties>

<dependency>
    <groupId>org.testng</groupId>
    <artifactId>testng</artifactId>
    <version>${testng.version}</version>
    <scope>test</scope>
</dependency>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <groups>${testng.groups}</groups>
    </configuration>
</plugin>
```

Профили:

```xml
<profile><id>e2e</id><properties><testng.groups>e2e</testng.groups></properties></profile>
<profile><id>all-tests</id><properties><testng.groups>api,e2e</testng.groups></properties></profile>
```

Команды:

```powershell
.\mvnw.cmd test              # группы api
.\mvnw.cmd test -Pe2e        # группы e2e
.\mvnw.cmd test -Pall-tests  # api + e2e
```

**Вопрос:** почему Surefire запускает именно TestNG?
**Ответ:** Surefire определяет провайдера по classpath. Есть только TestNG и нет
JUnit → `TestNGProvider`. Если добавить JUnit, провайдер может переключиться, и
тесты перестанут находиться.

## 2. Жизненный цикл: порядок аннотаций

```
@BeforeSuite
  @BeforeTest
    @BeforeClass
      @BeforeGroups
        @BeforeMethod
          @Test
        @AfterMethod
      @AfterGroups
    @AfterClass
  @AfterTest
@AfterSuite
```

| Аннотация | Когда выполняется | В проекте |
| --- | --- | --- |
| `@BeforeSuite` | один раз на весь suite | не используется |
| `@BeforeTest` | перед блоком `<test>` в XML | не используется |
| `@BeforeClass` | один раз на класс | запуск браузера |
| `@BeforeMethod` | перед каждым тестовым методом | новый контекст + регистрация пользователя |
| `@AfterMethod` | после каждого метода, даже упавшего | закрыть контекст / остановить сервер |
| `@AfterClass` | один раз после класса | закрыть браузер |
| `@BeforeGroups` / `@AfterGroups` | вокруг группы | не используется |

Код из `PlaywrightTest`:

```java
@BeforeClass(alwaysRun = true)
public void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(!headed)
    );
}

@BeforeMethod(alwaysRun = true)
public void openPage() {
    context = browser.newContext();     // изоляция: cookies/storage свои
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
```

**Вопрос:** зачем `alwaysRun = true`?
**Ответ:** конфигурационный метод выполнится, даже если он не входит в выбранную
группу или если предыдущий конфиг/тест упал. Без этого `@AfterMethod` может не
закрыть браузер, и JVM зависнет.

## 3. Атрибуты `@Test`, которые надо знать

| Атрибут | Смысл |
| --- | --- |
| `groups` | группы теста (можно несколько) |
| `priority` | порядок внутри класса (меньше — раньше); не средство изоляции |
| `enabled` | `false` — тест пропускается |
| `dependsOnMethods` | запускать после указанных; если те упали — skip |
| `dependsOnGroups` | то же для групп |
| `alwaysRun` | выполнять даже при упавших зависимостях (для тестов — осторожно) |
| `timeOut` | максимум миллисекунд на тест |
| `expectedExceptions` | тест успешен, если брошено указанное исключение |
| `expectedExceptionsMessageRegExp` | + проверка сообщения регуляркой |
| `dataProvider` | имя поставщика данных |
| `invocationCount` | сколько раз запустить |
| `threadPoolSize` | потоков для `invocationCount` |
| `retryAnalyzer` | класс, решающий, перезапускать ли упавший тест |

Классовый `@Test(groups = "api")` задаёт группу всем методам класса:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Test(groups = "api")
public class ApiTest extends AbstractTestNGSpringContextTests { ... }
```

## 4. Группы vs suite XML

- **Группы** — логические метки (`api`, `e2e`, `smoke`, `regression`). Удобны для
  выборочного запуска: `mvn test -Dgroups=smoke`.
- **suite XML** (`testng.xml`) — описывает структуру запуска: какие пакеты/классы,
  какие группы включать/исключать, параллельность, параметры.

Пример `testng.xml`:

```xml
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="goals" parallel="methods" thread-count="4">
    <test name="api">
        <groups>
            <run>
                <include name="api"/>
                <exclude name="e2e"/>
            </run>
        </groups>
        <packages>
            <package name="com.goaltracker.*"/>
        </packages>
    </test>
</suite>
```

Запуск: `mvn test -Dsurefire.suiteXmlFiles=testng.xml`.

**Вопрос:** группы и suite XML — это одно и то же?
**Ответ:** нет. Группы — метки на методах/классах; suite XML — конфигурация запуска,
которая этими метками управляет.

## 5. Ассерты

```java
import static org.testng.Assert.*;

assertEquals(actual, expected);       // порядок: actual, expected
assertTrue(condition, "message");
assertNotNull(object);
assertNull(object);
fail("always fails");
```

### 5.1 `assertThrows` возвращает `void` — реальная ловушка

В TestNG:

```java
Assert.assertThrows(Class<T>, ThrowingRunnable);   // void
Assert.expectThrows(Class<T>, ThrowingRunnable);   // возвращает T
```

В проекте это выстрелило при компиляции:

```java
// не скомпилируется: void нельзя присвоить
AiException exception = assertThrows(AiException.class, () -> analyzer.analyze(goal, "en"));
```

Рабочие варианты:

```java
// 1) expectThrows — сразу возвращает исключение
AiException exception = expectThrows(AiException.class, () -> analyzer.analyze(goal, "en"));
assertEquals(exception.getStatus(), 502);

// 2) свой helper (используется в проекте)
private static AiException expectAiException(Runnable action) {
    try {
        action.run();
    } catch (AiException exception) {
        return exception;
    }
    fail("Expected an AiException to be thrown");
    return null;
}

// 3) декларативно, без доступа к объекту исключения
@Test(expectedExceptions = AiException.class,
      expectedExceptionsMessageRegExp = ".*not configured.*")
public void failsWithoutKey() { ... }
```

Это хороший ответ на вопрос «расскажите о баге, который вы нашли в тестах».

### 5.2 SoftAssert

```java
SoftAssert soft = new SoftAssert();
soft.assertEquals(goal.title(), "Цель");
soft.assertEquals(goal.description(), "Описание");
soft.assertAll();   // обязательно, иначе проверки не сработают
```

Жёсткий ассерт прерывает тест, мягкий — копит ошибки и падает в конце. Полезно,
когда хотим увидеть все расхождения в одном прогоне.

## 6. DataProvider и параметры

```java
@DataProvider(name = "invalidEmails")
public Object[][] invalidEmails() {
    return new Object[][] {
            {"", "Email is required"},
            {"not-an-email", "Email is invalid"},
            {"user@example.com ", "Email must not contain spaces"}
    };
}

@Test(dataProvider = "invalidEmails")
public void rejectsInvalidEmail(String email, String expectedMessage) {
    // ...
}
```

- `@DataProvider(parallel = true)` — строки выполняются параллельно.
- `dataProviderClass` — если провайдер в другом классе.
- `@Parameters({"browser"})` — значения из suite XML, только `String`/примитивы.

```xml
<parameter name="browser" value="chromium"/>
```

**Вопрос:** DataProvider vs `@Parameters`?
**Ответ:** DataProvider — данные из кода (объекты, коллекции, генерация), параметры —
простые значения из конфигурации XML.

## 7. Зависимости тестов

```java
@Test
public void createGoal() { ... }

@Test(dependsOnMethods = "createGoal")
public void deleteGoal() { ... }
```

Если `createGoal` упал — `deleteGoal` получит статус SKIP, а не FAIL.

**Почему в проекте этим не пользуются:** зависимые тесты нельзя запускать по
отдельности и параллельно, падение одного «валит» цепочку, сложнее локализовать
причину. Вместо этого каждый тест сам создаёт нужные данные (своего пользователя,
свою цель).

## 8. Listeners: логирование, скриншоты, retry

```java
public class TestListener implements ITestListener {

    @Override
    public void onTestStart(ITestResult result) {
        System.out.println("START " + result.getName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        // здесь можно приложить скриншот/трейс Playwright
    }

    @Override
    public void onTestSkipped(ITestResult result) { }

    @Override
    public void onTestSuccess(ITestResult result) { }
}
```

Подключение: `@Listeners(TestListener.class)` на классе, `-listener` в suite XML или
`META-INF/services/org.testng.ITestListener`.

Retry:

```java
public class RetryAnalyzer implements IRetryAnalyzer {
    private static final int MAX_ATTEMPTS = 2;
    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    public boolean retry(ITestResult result) {
        return attempts.incrementAndGet() < MAX_ATTEMPTS;
    }
}

@Test(retryAnalyzer = RetryAnalyzer.class)
public void flakyScenario() { ... }
```

Глобально — через `IAnnotationTransformer`, который проставляет `retryAnalyzer`
каждому тесту. Важно: retry прячет симптом, а не причину; в CI это сигнал, а не решение.

## 9. Параллельный запуск и потокобезопасность

В suite XML:

```xml
<suite name="api" parallel="methods" thread-count="4">
```

В Surefire:

```xml
<configuration>
    <parallel>methods</parallel>
    <threadCount>4</threadCount>
</configuration>
```

Правила:

- TestNG создаёт **один экземпляр класса** на класс (не на метод, в отличие от JUnit 5).
  Значит, изменяемые поля класса общие для всех методов.
- В `PlaywrightTest` это учтено: `browser` — общий на класс, `context`/`page` —
  пересоздаются в `@BeforeMethod` (и при параллельности каждый поток идёт в свой
  экземпляр метода, но объекты Playwright всё равно нельзя делить между потоками).
- Для параллельных потоков: отдельный `Playwright` на поток, `ThreadLocal<Page>` или
  поля, создаваемые в `@BeforeMethod`.

```java
private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

@BeforeMethod
public void setUp() {
    PAGE.set(browser.newContext().newPage());
}

@AfterMethod
public void tearDown() {
    PAGE.get().context().close();
    PAGE.remove();
}
```

## 10. TestNG vs JUnit 5

| Возможность | TestNG | JUnit 5 |
| --- | --- | --- |
| Группировка | `groups` | `@Tag` + фильтры |
| Зависимости | `dependsOnMethods` | нет прямого аналога (`@Order`) |
| Параметризация | `@DataProvider` | `@ParameterizedTest` + `@MethodSource` |
| Параллельность | в suite XML / properties | `junit-platform.properties` |
| Soft assertions | `SoftAssert` | `assertAll` / AssertJ |
| Слушатели | `ITestListener` | `TestExecutionListener`, extensions |
| Жизненный цикл | один экземпляр на класс | новый экземпляр на метод |
| Отчёты | TestNG + Surefire | JUnit XML + Surefire |

Оба нормально работают в Maven; выбор чаще исторический/командный.

## 11. Отчёты

- `target/surefire-reports/` — txt и XML по каждому suite.
- TestNG также умеет `index.html` / `emailable-report.html` при запуске через свой
  runner (в Maven обычно смотрите Surefire-отчёты).
- В CI проекте отчёты загружаются артефактами:

```yaml
- name: Upload Surefire reports
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: surefire-api
    path: target/surefire-reports/
```

`if: always()` важен: отчёты нужны именно когда тесты упали.

## 12. Подводные камни (частые вопросы)

1. **Порядок тестов не гарантирован.** Нельзя строить тесты на алфавите или порядке
   объявления. Нужны данные — создай их в тесте.
2. **Общее состояние класса.** TestNG переиспользует экземпляр → мутабельные поля
   текут между тестами. Решение: создавать в `@BeforeMethod`, не делать `static`.
3. **`alwaysRun`** нужен для `@AfterMethod`, иначе при падении `@BeforeMethod` ресурсы
   не закроются.
4. **`assertThrows` возвращает `void`** (см. выше).
5. **`SoftAssert.assertAll()`** забыли — тест зелёный, хотя проверки падали.
6. **Группы + конфигурационные методы.** `@BeforeMethod(groups="e2e")` не выполнится
   для теста без этой группы; для общих предусловий группу не указывают.
7. **JUnit + TestNG в одном classpath** — Surefire может выбрать не того провайдера.
8. **Параллельность и общие данные** — гонки в БД/файлах; в проекте у каждого сьюта
   своя SQLite, а пользователи уникальны по email.
9. **`dependsOn`** превращает набор тестов в хрупкую цепочку.
10. **`timeOut`** не прерывает зависший внешний вызов мгновенно — это ограничение
    самого теста, а не таймаут HTTP-клиента.

## 13. Вопросы-ответы (проговорить вслух)

**Чем `@BeforeClass` отличается от `@BeforeMethod`?**
`@BeforeClass` — один раз на класс (поднять браузер), `@BeforeMethod` — перед каждым
тестом (новый контекст, логин).

**Что делает `@BeforeTest`?**
Выполняется перед всеми классами внутри одного `<test>` в suite XML. Часто путают с
`@BeforeClass`.

**Как запустить только smoke-тесты?**
Пометить `@Test(groups = "smoke")` и запустить `mvn test -Dgroups=smoke` (или профилем).

**Как сделать тест зависимым от другого?**
`dependsOnMethods`, но лучше — независимые тесты с собственными данными.

**Как передать параметр из Maven?**
`mvn test -Dbrowser=firefox`, затем `System.getProperty("browser")`; либо
`@Parameters` из suite XML; либо `-Dtestng.groups=...` как в проекте.

**Как получить скриншот при падении?**
`ITestListener.onTestFailure` + `page.screenshot(...)`, либо tracing Playwright.

**Почему тест может быть SKIP, а не FAIL?**
Упала зависимость (`dependsOn...`) или не выполнилось предусловие конфигурации.

**TestNG создаёт новый экземпляр класса на каждый тест?**
Нет, один на класс (в отличие от JUnit 5). Поэтому поля класса — общие.

**Можно ли запускать тесты параллельно с общим браузером?**
Браузер можно переиспользовать, но `BrowserContext`/`Page` — на тест. Сам `Playwright`
не потокобезопасен: экземпляр на поток.

**Что лучше: `priority` или независимые тесты?**
Независимые. `priority` создаёт скрытый порядок и мешает параллельности.
