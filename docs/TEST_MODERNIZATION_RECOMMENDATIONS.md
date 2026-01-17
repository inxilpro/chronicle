# Test Modernization Recommendations

This document compares Chronicle's current test setup against modern Kotlin testing best practices and provides recommendations for improvement.

## Current Setup Summary

| Aspect | Current State |
|--------|---------------|
| Test Framework | JUnit 4.13.2 |
| Assertions | JUnit assertions only |
| Mocking | Manual object expressions |
| Platform Testing | IntelliJ `BasePlatformTestCase` |
| Test Count | 13 test files, ~1,700 lines |
| Async Handling | `Thread.sleep()` |

---

## Recommendations

### 1. Migrate to JUnit 5 (Jupiter)

**Current:** JUnit 4 with `@Test` annotations and `@Rule` for temporary folders.

**Modern Practice:** JUnit 5 (Jupiter) is the standard for new Kotlin projects, offering:
- More expressive lifecycle annotations (`@BeforeEach`, `@AfterEach`)
- Nested test classes with `@Nested` for better organization
- `@TempDir` parameter injection (replaces `TemporaryFolder` rule)
- `@ParameterizedTest` with various sources
- `@DisplayName` for readable test names
- Better extension model (`@ExtendWith`)

**Example Migration:**
```kotlin
// Current (JUnit 4)
class ShellHistoryParserTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testParseZshExtendedHistoryFormat() { ... }
}

// Modern (JUnit 5)
class ShellHistoryParserTest {
    @Test
    @DisplayName("parses zsh extended history format correctly")
    fun parseZshExtendedHistoryFormat(@TempDir tempDir: Path) { ... }

    @Nested
    inner class ZshHistoryParsing {
        @ParameterizedTest
        @CsvSource("': 1234567890:0;echo hello', 'echo hello'")
        fun `extracts command from extended format`(line: String, expected: String) { ... }
    }
}
```

**Consideration:** IntelliJ Platform's test framework (`BasePlatformTestCase`) is JUnit 3-style. Platform tests would need the JUnit 4/5 compatibility layer or could remain as-is while unit tests migrate.

**Priority:** Medium - Improves developer experience but existing tests work fine.

---

### 2. Adopt a Kotlin-Idiomatic Assertion Library

**Current:** Basic JUnit assertions like `assertEquals()`, `assertTrue()`.

**Modern Practice:** Use expressive assertion libraries designed for Kotlin:

#### Option A: Kotest Assertions (Recommended)
```kotlin
// Current
assertEquals("Opened Main.kt", event.summary())
assertTrue(events.isNotEmpty())
assertNull(result)

// Kotest assertions
event.summary() shouldBe "Opened Main.kt"
events.shouldNotBeEmpty()
result.shouldBeNull()

// More expressive matchers
events shouldHaveSize 3
event.path shouldContain "Main.kt"
events.shouldContainExactlyInAnyOrder(event1, event2)
```

#### Option B: AssertJ (Java-based, also excellent)
```kotlin
assertThat(event.summary()).isEqualTo("Opened Main.kt")
assertThat(events).isNotEmpty()
assertThat(events).hasSize(3).extracting("path").contains("Main.kt")
```

#### Option C: Strikt (Kotlin-first, composable)
```kotlin
expectThat(event.summary()).isEqualTo("Opened Main.kt")
expectThat(events).isNotEmpty().hasSize(3)
```

**Recommendation:** Kotest assertions are the most Kotlin-idiomatic and can be used independently of the Kotest test framework.

**Priority:** Medium - Significantly improves test readability and error messages.

---

### 3. Add a Mocking Framework

**Current:** Manual object expressions for mocking:
```kotlin
val mockParser = object : ShellHistoryParser() {
    override fun parseRecentCommands(since: Instant, limit: Int): List<ShellCommand> {
        return listOf(ShellCommand("echo test", Instant.now(), "bash"))
    }
}
```

**Modern Practice:** Use MockK, the standard mocking library for Kotlin:

```kotlin
// MockK example
val mockParser = mockk<ShellHistoryParser> {
    every { parseRecentCommands(any(), any()) } returns listOf(
        ShellCommand("echo test", Instant.now(), "bash")
    )
}

// Verification
verify { mockParser.parseRecentCommands(any(), limit = 100) }

// Relaxed mocks for simple cases
val relaxedMock = mockk<SomeService>(relaxed = true)

// Coroutine support
coEvery { asyncService.fetchData() } returns Result.success(data)
```

**Benefits:**
- Less boilerplate than manual object expressions
- Verification of interactions
- Argument capturing
- Coroutine support with `coEvery`/`coVerify`
- Spy functionality

**Priority:** Low - Current approach works; only beneficial if test complexity grows.

---

### 4. Replace Thread.sleep() with Deterministic Waiting

**Current:** Tests use `Thread.sleep()` to wait for debounced events:
```kotlin
Thread.sleep(600) // Wait for debounce
```

**Problems:**
- Flaky tests (timing-dependent)
- Slow test execution
- Race conditions

**Modern Approaches:**

#### A. Awaitility (Recommended for existing architecture)
```kotlin
await().atMost(2, SECONDS).untilAsserted {
    val events = service.getEvents().filterIsInstance<SelectionChangedEvent>()
    assertThat(events).isNotEmpty()
}
```

#### B. Inject a Test Scheduler/Clock
```kotlin
// Production code uses injected time source
class DebouncedListener(private val clock: Clock = Clock.systemUTC())

// Test provides controllable clock
val testClock = MutableClock()
val listener = DebouncedListener(testClock)
testClock.advance(600.milliseconds)
```

#### C. Expose Flush Methods (Already partially done)
The codebase already has `flushPendingEvents()` methods - ensure all debounced components expose similar methods for testing.

**Priority:** High - Reduces test flakiness and execution time.

---

### 5. Consider Kotest as Test Framework

**Current:** JUnit 4 with standard test structure.

**Modern Alternative:** Kotest offers Kotlin-native testing with multiple styles:

```kotlin
class TranscriptEventSpec : FunSpec({
    context("FileOpenedEvent") {
        test("summary extracts filename from path") {
            val event = FileOpenedEvent(path = "/project/src/Main.kt")
            event.summary() shouldBe "Opened Main.kt"
        }

        test("handles paths without directory") {
            val event = FileOpenedEvent(path = "Main.kt")
            event.summary() shouldBe "Opened Main.kt"
        }
    }

    context("DocumentChangedEvent") {
        // ...
    }
})
```

**Benefits:**
- DSL-based test structure
- Built-in property-based testing
- Data-driven testing
- Coroutine-first design
- Rich assertion library included

**Consideration:** Significant migration effort; JUnit 5 may be a more pragmatic choice.

**Priority:** Low - Only if team prefers Kotest's style.

---

### 6. Add Property-Based Testing

**Current:** Example-based tests only.

**Modern Practice:** Add property-based tests for data classes and parsers:

```kotlin
// Using Kotest property testing
class TranscriptEventPropertyTest : FunSpec({
    test("all events have non-blank summaries") {
        checkAll(Arb.bind<FileOpenedEvent>()) { event ->
            event.summary().shouldNotBeBlank()
        }
    }

    test("shell history parser round-trips correctly") {
        checkAll(Arb.string(), Arb.instant()) { command, timestamp ->
            val parsed = parser.parse(formatter.format(command, timestamp))
            parsed.command shouldBe command
        }
    }
})
```

**Priority:** Low - Nice to have for parser and serialization logic.

---

### 7. Create Shared Test Utilities

**Current:** Each test is self-contained with repeated setup patterns.

**Recommended Utilities:**

```kotlin
// Test fixtures factory
object TestEvents {
    fun fileOpened(path: String = "/test/File.kt") = FileOpenedEvent(path = path)
    fun documentChanged(path: String = "/test/File.kt", changes: Int = 1) =
        DocumentChangedEvent(path = path, changeCount = changes)
}

// Service test helper
fun Project.withTranscriptService(block: ActivityTranscriptService.() -> Unit) {
    val service = service<ActivityTranscriptService>()
    service.startLogging()
    try {
        service.block()
    } finally {
        service.stopLogging()
    }
}

// Awaiting helper
suspend fun ActivityTranscriptService.awaitEvent(
    timeout: Duration = 2.seconds,
    predicate: (TranscriptEvent) -> Boolean
): TranscriptEvent {
    return withTimeout(timeout) {
        while (true) {
            getEvents().find(predicate)?.let { return@withTimeout it }
            delay(50)
        }
    }
}
```

**Priority:** Medium - Reduces duplication as test suite grows.

---

### 8. Improve Test Organization

**Current:** Flat test methods within each class.

**Modern Practice:** Use nested classes for grouping related tests:

```kotlin
class ShellHistoryParserTest {
    @Nested
    inner class ZshParsing {
        @Test fun `parses extended format`() { ... }
        @Test fun `handles missing timestamp`() { ... }
    }

    @Nested
    inner class BashParsing {
        @Test fun `parses simple format`() { ... }
        @Test fun `handles multiline commands`() { ... }
    }

    @Nested
    inner class EdgeCases {
        @Test fun `empty file returns empty list`() { ... }
        @Test fun `malformed lines are skipped`() { ... }
    }
}
```

**Priority:** Low - Primarily organizational improvement.

---

## Recommended Dependency Additions

```kotlin
// build.gradle.kts additions for test modernization

dependencies {
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Kotest assertions (can use standalone)
    testImplementation("io.kotest:kotest-assertions-core:5.8.1")

    // MockK
    testImplementation("io.mockk:mockk:1.13.10")

    // Awaitility for async testing
    testImplementation("org.awaitility:awaitility-kotlin:4.2.1")
}

tasks.test {
    useJUnitPlatform()
}
```

---

## Migration Priority Summary

| Change | Priority | Effort | Impact |
|--------|----------|--------|--------|
| Replace `Thread.sleep()` with Awaitility | High | Low | Reduces flakiness |
| Add Kotest assertions | Medium | Low | Better readability |
| Create shared test utilities | Medium | Medium | Reduces duplication |
| Migrate to JUnit 5 | Medium | Medium | Modern features |
| Add MockK | Low | Low | Cleaner mocks |
| Nested test organization | Low | Low | Better structure |
| Property-based testing | Low | Medium | Edge case coverage |
| Full Kotest migration | Low | High | Style preference |

---

## What's Working Well

The current test setup has several strengths worth preserving:

1. **Good coverage** - All major components have tests
2. **Correct IntelliJ Platform testing** - Proper use of `BasePlatformTestCase` and fixtures
3. **Clean test isolation** - Each test is independent
4. **Appropriate test granularity** - Mix of unit and integration tests
5. **Flush methods for debouncing** - Good pattern already partially adopted

---

## Conclusion

The most impactful improvements with least disruption would be:

1. **Add Awaitility** to replace `Thread.sleep()` calls
2. **Add Kotest assertions** for more expressive tests
3. **Create a small test utilities module** for common patterns

JUnit 5 migration and MockK are valuable but lower priority given the current tests work correctly. A full Kotest migration would be a larger undertaking best deferred unless the team specifically prefers that style.
