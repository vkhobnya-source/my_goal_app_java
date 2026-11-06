# Java для AQA-интервью

> Цель: уметь уверенно объяснить 15–20 базовых тем и подкрепить каждую примером из
> проекта. Глубина — уровень «middle AQA», без олимпиадных задач.

## 0. Карта тем: что реально спрашивают

| Тема | Минимум, который надо сказать |
| --- | --- |
| JDK / JRE / JVM | JDK = компилятор + JRE; JRE = JVM + библиотеки; JVM исполняет байткод |
| Примитивы / обёртки | 8 примитивов, autoboxing, кэш `Integer` от −128 до 127 |
| `String` | неизменяемый, пул строк, `StringBuilder` для склейки в цикле |
| `==` / `equals` | ссылки vs значения; контракт `equals`/`hashCode` |
| ООП | инкапсуляция, наследование, полиморфизм, абстракция |
| Коллекции | `ArrayList`, `HashMap`, `HashSet`; устройство `HashMap` |
| Исключения | checked/unchecked, `try-with-resources`, свои исключения |
| Generics | параметризация, стирание типов, `? extends` / `? super` |
| Лямбды / Stream | функциональные интерфейсы, ленивые операции, `collect` |
| `Optional` | вместо `null` в возвращаемых значениях |
| `record` | неизменяемый носитель данных, `equals/hashCode/toString` бесплатно |
| JVM | heap/stack/metaspace, GC (G1), `-Xmx`, утечки |
| Многопоточность | `Thread`, `ExecutorService`, `synchronized`, `volatile`, virtual threads |
| Maven | lifecycle, фазы, scopes, wrapper, profiles |
| Spring-минимум | DI/IoC, стереотипы, `@Transactional` через прокси |

## 1. Платформа: JDK, JRE, JVM

- **JVM** — виртуальная машина, исполняет байткод (`*.class`). Одна и та же программа
  работает на Windows/Linux/macOS, потому что байткод одинаковый.
- **JRE** = JVM + стандартная библиотека (классы `java.*`). Запустить готовое приложение.
- **JDK** = JRE + компилятор `javac`, `javadoc`, `jshell`, `jcmd`, `jfr` и т.д.
- **Версии:** Java 25 — LTS (поддержка до ~2030), 26 и 27 — non-LTS (6 месяцев).
  На проде обычно LTS.
- **Байткод и версия class-файла:** Java 25 → major version 69. Более новая JVM читает
  старый байткод, но не наоборот. Поэтому в `pom.xml` стоит `--release 25`: собираем
  на любой JDK 25+, а получаем совместимый с 25 байткод.

**Вопрос:** чем `javac --release 25` лучше `-source 25 -target 25`?
**Ответ:** `--release` дополнительно подставляет API именно 25-й версии и запрещает
использовать методы из более новых JDK. `-source/-target` этого не гарантируют —
можно случайно скомпилировать вызов метода, которого в 25-й нет.

## 2. Примитивы, обёртки, строки

### 2.1 Примитивы

8 штук: `byte` (1 байт), `short` (2), `int` (4), `long` (8), `float` (4), `double` (8),
`char` (2, UTF-16), `boolean` (JVM-зависимо, обычно 1 байт в массиве).

Обёртки: `Integer`, `Long`, `Boolean`, ... нужны для generics (`List<Integer>`),
`null`, методов (`Integer.parseInt`).

**Autoboxing** — автоматическая упаковка/распаковка. Ловушка:

```java
Integer a = 127;
Integer b = 127;
System.out.println(a == b);   // true  — значения из кэша Integer
Integer c = 128;
Integer d = 128;
System.out.println(c == d);   // false — два разных объекта
```

Поэтому сравнивать обёртки всегда через `equals` / `Objects.equals`.

### 2.2 String

- Неизменяемый (`immutable`): любая «модификация» создаёт новую строку.
- **Пул строк**: литералы переиспользуются, `"a" == "a"` → `true`.
- `new String("a")` создаёт новый объект вне пула.
- Склейка в цикле через `+` → много мусора; используем `StringBuilder`
  (`StringBuffer` — потокобезопасный, медленнее).
- `String` — `final`-класс, поэтому его можно безопасно использовать в ключах `HashMap`.

```java
String s = "goal";
String t = s.toUpperCase();   // "GOAL", s не изменилась
```

**Вопрос:** почему `String` делают неизменяемым?
**Ответ:** безопасность (пароли/пути не поменяются), потокобезопасность, кэширование
`hashCode`, работа в пуле и как ключ `HashMap`.

### 2.3 `==` vs `equals`

- `==` для примитивов — значения, для ссылок — **адреса**.
- `equals` — логическое равенство; по умолчанию в `Object` тоже сравнивает ссылки,
  поэтому переопределяют.
- Контракт: если `a.equals(b)`, то `a.hashCode() == b.hashCode()`. Обратное неверно.
- Нарушишь контракт — объект «потеряется» в `HashSet`/`HashMap`.

В проекте это не проблема: DTO — `record`, а он генерирует корректные
`equals/hashCode` по всем компонентам.

## 3. ООП и классы

### 3.1 Четыре принципа (и пример из проекта)

| Принцип | Смысл | В проекте |
| --- | --- | --- |
| Инкапсуляция | состояние + поведение вместе, детали скрыты | репозитории прячут SQL, наружу — методы `findByEmail` |
| Наследование | переиспользование через «является» | `ApiException extends RuntimeException` |
| Полиморфизм | один интерфейс — разные реализации | `GoalAnalyzer` → `OpenRouterGoalAnalyzer` / fake-бин |
| Абстракция | работаем с сущностью, не с деталями | контроллеры зависят от `GoalService`, не от JDBC |

### 3.2 Интерфейс vs абстрактный класс

- Интерфейс — контракт, множественная реализация, поля — только константы,
  методы могут быть `default`/`static`/`private`.
- Абстрактный класс — частичная реализация + состояние, один родитель.
- Правило: интерфейс для «умеет», абстрактный класс — для общей реализации.

```java
public interface GoalAnalyzer {
    GoalAnalysis analyze(Goal goal, String language);
}
```

Зачем: в тестах подменяем реальный OpenRouter фейком через `@Primary`-бин и не
зависим от сети. Это и есть практическая польза абстракции.

### 3.3 `final`, `finally`, `finalize`

- `final` — переменная не переприсваивается, метод не переопределяется, класс не
  наследуется.
- `finally` — блок, который выполнится после `try` (даже при `return`/исключении);
  используется для закрытия ресурсов. Современный вариант — `try-with-resources`.
- `finalize` — устарел и ненадёжен, не использовать.

### 3.4 `static` и порядок инициализации

Порядок: статические поля/блоки (один раз при загрузке класса) → поля экземпляра →
блоки инициализации → конструктор.

В проекте статический блок в тестах удаляет файл БД **до** старта Spring-контекста:

```java
static {
    try {
        Files.deleteIfExists(DATABASE);
    } catch (IOException exception) {
        throw new ExceptionInInitializerError(exception);
    }
}
```

### 3.5 Перегрузка vs переопределение

| | Overload | Override |
| --- | --- | --- |
| Где | в одном классе | в наследнике |
| Что меняется | параметры | реализация при той же сигнатуре |
| Выбор | на этапе компиляции | на этапе выполнения (динамически) |

Нельзя переопределить `static`, `final`, `private` методы.

### 3.6 `record` — главная «новая» Java в проекте

```java
public record TaskResponse(
        long id,
        String title,
        @JsonProperty("is_completed") boolean isCompleted,
        @JsonProperty("goal_id") long goalId
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(task.id(), task.title(), task.isCompleted(), task.goalId());
    }
}
```

Что даёт `record`:

- `final`-класс, поля `private final`, геттеры `id()`, `title()` (без `get`).
- Автоматические `equals`, `hashCode`, `toString`.
- **Компактный конструктор** для валидации/дефолтов:

```java
public record AiProperties(String apiKey, String model, String apiUrl, String siteUrl, String appName) {
    public AiProperties {
        apiKey = apiKey == null ? "" : apiKey;
        model = (model == null || model.isBlank()) ? "deepseek/deepseek-v4.1-flash" : model;
    }
}
```

- Нельзя наследовать, нельзя добавить изменяемое поле, но можно реализовывать интерфейсы.
- Хорошо подходит для DTO, плохо — для сущностей с жизненным циклом (JPA-entity).

**Вопрос:** почему DTO — records, а не классы с геттерами?
**Ответ:** меньше кода, неизменяемость, корректные `equals/hashCode`, сериализация
Jackson работает «из коробки». Для ответов API и запросов этого достаточно.

### 3.7 Enum

Набор констант с поведением. В проекте почти не нужен, но на интервью спросят:
`enum` — это класс, может иметь поля, конструктор, методы; `values()`, `valueOf()`;
в `switch` работает; singleton через enum — потокобезопасно.

## 4. Коллекции

### 4.1 Иерархия

- `List` — порядок, индексы, дубликаты: `ArrayList`, `LinkedList`.
- `Set` — уникальность: `HashSet`, `LinkedHashSet`, `TreeSet`.
- `Map` — ключ→значение: `HashMap`, `LinkedHashMap`, `TreeMap`, `ConcurrentHashMap`.
- `Queue`/`Deque`: `ArrayDeque`, `PriorityQueue`.

### 4.2 `ArrayList` vs `LinkedList`

| | `ArrayList` | `LinkedList` |
| --- | --- | --- |
| Внутри | массив | двусвязный список |
| Доступ по индексу | O(1) | O(n) |
| Вставка в середину | O(n) (сдвиг) | O(1) при известном узле |
| Память | компактнее | больше на ссылки |
| На практике | почти всегда | редко |

### 4.3 `HashMap`: как устроен

- Массив бакетов (`Node[] table`), начальная ёмкость 16, load factor 0.75.
- Индекс: `(n - 1) & hash`, где `hash` — «размазанный» `hashCode` (`h ^ (h >>> 16)`).
- Коллизии — связный список; при длине ≥ 8 и ёмкости ≥ 64 список превращается в
  красно-чёрное дерево (O(log n)); при уменьшении до 6 — обратно.
- При заполнении > 0.75 — удвоение ёмкости и перераспределение (rehash).
- `null`-ключ разрешён (один, в бакете 0); порядок не гарантируется.
- Не потокобезопасен; для конкурентности — `ConcurrentHashMap` (сегменты/CAS, без
  `null`-ключей).

**Вопрос:** почему `HashMap` требует `equals` и `hashCode` вместе?
**Ответ:** `hashCode` выбирает бакет, `equals` — конкретный ключ внутри бакета. Если
`equals` говорит «равны», а хэши разные, ключ будет искаться не в том бакете.

### 4.4 Неизменяемые коллекции

```java
List<String> tasks = List.of("a", "b");        // immutable, null запрещён
Map<String, Integer> m = Map.of("a", 1);       // immutable
List<String> copy = new ArrayList<>(tasks);    // изменяемая копия
```

`Collectors.toList()` возвращает изменяемый список, а `stream().toList()` — нет.
`Collections.unmodifiableList` — «вид» поверх исходного: если исходный изменить, вид
тоже изменится.

### 4.5 Коллекции в проекте

Группировка задач по цели одним запросом:

```java
Map<Long, List<Task>> tasksByGoal = taskRepository.findByUserId(userId).stream()
        .collect(Collectors.groupingBy(Task::goalId));

return goals.stream()
        .map(goal -> GoalResponse.from(goal, tasksByGoal.getOrDefault(goal.id(), List.of())))
        .toList();
```

Это ответ на вопрос «как избежать N+1»: не делаем запрос на каждую цель, а одним
JOIN-запросом берём все задачи пользователя и группируем в памяти.

## 5. Generics, лямбды, Stream, Optional

### 5.1 Generics

```java
List<Goal> goals = new ArrayList<>();
Optional<User> user = userRepository.findByEmail(email);
ResponseEntity<ApiError> response = ...;
```

- Нужны для типобезопасности: `List<String>` не пустит `Integer`.
- **Стирание типов (type erasure):** в рантайме `List<String>` и `List<Integer>` —
  один класс; поэтому нельзя `new T()`, `instanceof List<String>`.
- Wildcards: `? extends T` — «читаем» (producer), `? super T` — «пишем» (consumer).
  Мнемоника PECS: Producer Extends, Consumer Super.

### 5.2 Лямбды и функциональные интерфейсы

Функциональный интерфейс — ровно один абстрактный метод: `Runnable`, `Supplier<T>`,
`Function<T,R>`, `Predicate<T>`, `Consumer<T>`.

```java
GoalAnalyzer fake = (goal, language) -> new GoalAnalysis("...", List.of("..."));
```

Лямбда — это реализация интерфейса, а не «анонимный класс» концептуально, но
компилируется через `invokedynamic`.

### 5.3 Stream

Промежуточные операции ленивы (`map`, `filter`, `sorted`), терминальные запускают
вычисление (`collect`, `forEach`, `count`, `findFirst`, `anyMatch`).

```java
List<String> titles = goals.stream()
        .filter(goal -> !goal.isCompleted())
        .map(Goal::title)
        .sorted()
        .toList();
```

Частые вопросы:

- `map` vs `flatMap`: `map` 1→1, `flatMap` 1→N и «разглаживает».
- `Collectors.groupingBy` — в `Map`.
- Stream нельзя переиспользовать: после терминальной операции он закрыт.
- Параллельные стримы — только для больших CPU-bound задач, не для I/O.

### 5.4 Optional

```java
User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Bad credentials"));
```

- `orElse` вычисляется всегда, `orElseGet` — лениво (важно, если внутри создание объекта).
- `Optional` — для возвращаемых значений, не для полей и не для параметров.
- `Optional.of(null)` бросит NPE, `ofNullable` — нет.

## 6. Исключения

### 6.1 Иерархия

```
Throwable
├── Error                 (OutOfMemoryError, StackOverflowError — не ловим)
└── Exception
    ├── checked           (IOException, SQLException — обязаны обработать или пробросить)
    └── RuntimeException  (NPE, IllegalArgument, наши ApiException/AiException)
```

Checked — про внешние сбои, которые можно восстановить. Unchecked — про ошибки
программиста/бизнес-правила.

### 6.2 Свои исключения в проекте

```java
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String wwwAuthenticate;
    // ...
}
```

Почему `RuntimeException`:

1. не засоряем сигнатуры `throws`;
2. `@Transactional` по умолчанию откатывает транзакцию только на unchecked;
3. глобальный обработчик всё равно превратит его в JSON.

```java
@ExceptionHandler(ApiException.class)
public ResponseEntity<ApiError> handleApiException(ApiException exception) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.getStatus());
    if (exception.getWwwAuthenticate() != null) {
        builder.header(HttpHeaders.WWW_AUTHENTICATE, exception.getWwwAuthenticate());
    }
    return builder.body(new ApiError(exception.getMessage()));
}
```

### 6.3 `try-with-resources`

```java
try (OutputStream output = exchange.getResponseBody()) {
    output.write(bytes);
}
```

Ресурс закрывается автоматически; класс должен реализовывать `AutoCloseable`.
Эквивалент `finally { output.close(); }`, но короче и без NPE.

### 6.4 Что спросят

- Можно ли поймать `Error`? Технически да, но не нужно.
- Что будет, если `finally` содержит `return`? Он «съест» исключение/предыдущий return —
  так делать нельзя.
- Чем `throw` отличается от `throws`? `throw` — бросить, `throws` — объявить в сигнатуре.
- Что делает `Thread.currentThread().interrupt()` в `catch (InterruptedException)`?
  Восстанавливает флаг прерывания, чтобы вызывающий код узнал об отмене. В проекте:

```java
} catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    throw new AiException(502, messages.t("ai_provider_unreachable", language));
}
```

## 7. Многопоточность (кратко, но уверенно)

- `Thread` / `Runnable` — базовый запуск.
- `ExecutorService` — пул потоков; `Executors.newFixedThreadPool(n)`.
- `synchronized` — монитор объекта; `volatile` — видимость, но не атомарность.
- `AtomicInteger` — CAS без блокировки.
- `ConcurrentHashMap` — потокобезопасная мапа.
- `ThreadLocal` — своё значение на поток (драйверы в Selenium/Playwright).
- **Virtual threads (Java 21+)**: дешёвые потоки для I/O; `Executors.newVirtualThreadPerTaskExecutor()`.
  Не помогают для CPU-bound.
- **Deadlock**: два потока держат блокировки в разном порядке. Диагностика — `jstack`.

**Вопрос:** почему Playwright-тесты нельзя просто распараллелить одним общим `Page`?
**Ответ:** объекты Playwright не потокобезопасны. Нужен отдельный `Playwright`/`Browser`
на поток (или синхронизация), а `BrowserContext`/`Page` — на тест.

## 8. JVM: память и GC

- **Heap** — объекты; делится на young (Eden + Survivor) и old generation.
- **Stack** — кадры методов, локальные переменные; у каждого потока свой.
- **Metaspace** — метаданные классов (вне heap).
- **GC**: G1 — дефолт для серверов; ZGC — низкие паузы для больших heap;
  Serial — для маленьких.
- Молодые объекты умирают быстро; пережившие несколько сборок попадают в old.
- `OutOfMemoryError: Java heap space` — утечка или мало `-Xmx`.
  `Metaspace` — утечка классов/classloader'ов.
- Диагностика: `jcmd <pid> GC.heap_info`, `jmap -histo`, heap dump, JFR, `jstack`.

**Вопрос:** что такое утечка памяти в Java, если GC есть?
**Ответ:** объект остаётся достижимым из GC roots (статическая коллекция, ThreadLocal,
слушатель, кэш без ограничения), поэтому GC его не удаляет.

## 9. Java 17–25: что стоит знать

| Фича | Версия | Пример |
| --- | --- | --- |
| `var` | 10 | `var goals = new ArrayList<Goal>();` |
| Text blocks | 15 | `"""..."""` для JSON/SQL |
| `record` | 16 | DTO в проекте |
| `sealed` | 17 | ограниченная иерархия |
| Pattern matching `instanceof` | 16 | `if (o instanceof Goal g)` |
| `switch`-выражения | 14 | `return switch (code) { case 1 -> "a"; ... };` |
| Pattern matching в `switch` | 21 | разбор типов/`record`-паттернов |
| Virtual threads | 21 | I/O-нагрузка |
| Scoped values | 25 | замена `ThreadLocal` в некоторых сценариях |
| Compact object headers | 25 | меньше памяти на объект |

На интервью достаточно уверенно назвать `record`, `var`, `switch`-выражения,
virtual threads и `sealed`.

## 10. Maven

- **Lifecycle:** `validate → compile → test → package → verify → install → deploy`.
- **Phase** — шаг, **goal** — задача плагина, привязанная к фазе.
- **Scopes:**
  - `compile` — везде;
  - `provided` — компиляция, но не входит в jar (например, servlet API);
  - `runtime` — только в рантайме (JDBC-драйвер, jjwt-impl);
  - `test` — только тесты (TestNG, Playwright).
- **Транзитивные зависимости** приходят сами; конфликты решаются `dependencyManagement`
  и `exclusions`.
- **Wrapper** (`mvnw`) фиксирует версию Maven — у всех одинаковая сборка.
- **Profiles** в проекте:

```xml
<properties><testng.groups>api</testng.groups></properties>
...
<profile><id>e2e</id><properties><testng.groups>e2e</testng.groups></properties></profile>
<profile><id>all-tests</id><properties><testng.groups>api,e2e</testng.groups></properties></profile>
```

Команды: `mvn test`, `mvn test -Pe2e`, `mvn test -Pall-tests`,
`mvn -DskipTests package` (компилирует тесты, но не запускает),
`mvn -Dmaven.test.skip=true package` (не компилирует тесты).

## 11. Spring-минимум (без него не понять проект)

- **IoC/DI**: контейнер создаёт объекты и внедряет зависимости через конструктор.
  В проекте везде конструкторная инъекция — это удобно для тестов.
- **Стереотипы:** `@RestController`, `@Service`, `@Repository`, `@Configuration`,
  `@Component`. Все попадают в component scan.
- **`@Bean`** — ручное создание бина (например, `DataSource`).
- **`@Value`** — значение из конфигурации; `@ConfigurationProperties` — группа настроек
  (`AiProperties`).
- **`@Transactional`** — Spring оборачивает бин в прокси: открывает/коммитит транзакцию,
  откатывает на `RuntimeException`. Важно: вызов метода **внутри того же класса**
  (`this.method()`) идёт мимо прокси, и транзакция не применится.
- **`DispatcherServlet`** — фронт-контроллер: находит метод контроллера по URL и
  HTTP-методу, конвертирует JSON через Jackson, отдаёт ответ.
- **`HandlerMethodArgumentResolver`** — механизм, которым в проекте реализован
  `@CurrentUser`: резолвер читает `Authorization: Bearer ...`, проверяет JWT и
  подставляет `User`.
- **`@RestControllerAdvice`** — глобальная обработка исключений: все ошибки
  превращаются в `{"detail": "..."}`.

## 12. Вопросы по Java-коду проекта (с ответами)

**Почему репозитории на `JdbcTemplate`, а не JPA/Hibernate?**
Проект маленький, схема из трёх таблиц; JPA добавил бы магию, lazy-loading и
проблему N+1. `JdbcTemplate` + `PreparedStatement` — прозрачный SQL, легко тестировать
и понимать. Минус — ручной маппинг строк.

**Где защита от SQL-инъекций?**
Все запросы параметризованы `?`, значения передаются отдельно:
`jdbcTemplate.query("... WHERE email = ?", ROW_MAPPER, email)`.
Конкатенации пользовательских данных в SQL нет.

**Почему JWT-ключ — это SHA-256 от секрета?**
HS256 требует ключ ≥ 256 бит, а в конфиге может быть короткая строка. Хэшируем
`SECRET_KEY` в 32 байта — короткий секрет работает, алгоритм остаётся HS256.

**Почему пароли через BCrypt, а не MD5/SHA?**
BCrypt — адаптивный (cost factor), с солью, медленный by design, устойчив к перебору.
`matches()` сравнивает без хранения исходного пароля.

**Почему `ApiException` несёт `HttpStatus`?**
Чтобы сервисный слой не знал про `ResponseEntity`, но мог указать смысл ошибки
(400/401/403/404/422), а глобальный обработчик единообразно отрендерил JSON.

**Почему `@CurrentUser` — своя аннотация, а не Spring Security?**
Нужен был только JWT + BCrypt, без полной цепочки фильтров. Свой резолвер — 40 строк,
полный контроль и понятность. Минус — не получить «из коробки» CSRF, method security,
OAuth2; для учебного проекта это осознанный компромисс.

**Почему `GoalService.list` не делает запрос задач на каждую цель?**
Один `findByUserId` с JOIN + `groupingBy` в памяти. Это осознанное решение против N+1.

**Почему `is_completed` в JSON, а поле называется `isCompleted`?**
Java-конвенция vs контракт API. Jackson аннотацией `@JsonProperty("is_completed")`
сериализует в snake_case, чтобы фронтенд не менялся.

**Почему `@Transactional` стоит на методах сервиса, а не на контроллере?**
Транзакция — граница бизнес-операции. Контроллеры только принимают HTTP и
делегируют; сервис знает, что должно быть атомарно (создать цель, удалить задачи+цель).

**Что будет, если в `TaskService.update` передать `null` вместо тела?**
`request == null` → `ApiException(422, "Некорректное тело запроса")`, а не NPE.

**Почему тесты используют `TestRestTemplate`, а не `MockMvc`?**
Нужен реальный HTTP через Tomcat: так проверяются коды, заголовки, JSON и
поведение фильтров/резолверов. `MockMvc` быстрее, но не проверяет сетевой слой.

**Почему `httpclient5` добавлен только для тестов?**
`TestRestTemplate` под капотом использует клиент, который умеет PATCH. Стандартный
`HttpURLConnection` не умеет PATCH, поэтому без Apache HttpClient 5 тест на PATCH падал.

## 13. Мини-чек-лист «я готов отвечать»

- [ ] Объяснить `==` vs `equals` на примере `Integer` 127/128.
- [ ] Нарисовать `HashMap` и рассказать про 0.75, 8 и 64.
- [ ] Рассказать, чем checked отличается от unchecked и почему `ApiException` — unchecked.
- [ ] Показать `record` и объяснить, что генерируется автоматически.
- [ ] Объяснить `@Transactional` и ловушку self-invocation.
- [ ] Рассказать про Maven scopes и зачем wrapper.
- [ ] Объяснить, почему Playwright не потокобезопасен.
