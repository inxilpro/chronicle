# Test Modernization Recommendations

This document compares Chronicle's current test setup against modern Kotlin testing best practices and official JetBrains IntelliJ Platform testing guidelines.

## Current Setup Summary

| Aspect | Current State |
|--------|---------------|
| Test Framework | JUnit 4.13.2 |
| Assertions | JUnit assertions only |
| Mocking | Manual object expressions |
| Platform Testing | IntelliJ `BasePlatformTestCase` ✓ |
| Test Count | 13 test files, ~1,700 lines |
| Async Handling | `Thread.sleep()` |
| Test Data | Inline in tests |

---

## IntelliJ Platform Testing Philosophy

Before diving into recommendations, it's important to understand JetBrains' official testing philosophy for IntelliJ plugins, as it differs from typical application testing.

### Model-Level Functional Tests Over Unit Tests

JetBrains explicitly recommends **model-level functional tests** rather than isolated unit tests:

> "The IntelliJ Platform emphasizes model-level functional tests... Features are tested holistically rather than in isolation."

This means:
- Tests operate in a **headless environment using production implementations**
- UI testing **bypasses Swing components**, focusing on underlying models
- Input/output comparison validates results using source files or markup

**Benefit:** "Tests are very stable and require very little maintenance once written, no matter how much the underlying implementation is refactored."

### Avoid Mocking

JetBrains **discourages mocking** due to the platform's complexity:

> "The platform discourages mocking due to complexity. Instead, work with real components."

For cases where you need to replace components:
- Use `ServiceContainerUtil` to replace services in tests
- Use `ExtensionTestUtil` to replace extension points
- Use `testServiceImplementation` in service declarations

**Current Status:** Chronicle correctly follows this guidance by using real service instances rather than mocks.

### Light vs Heavy Tests

The project correctly uses **Light Tests** (`BasePlatformTestCase`), which reuse projects across test runs. This is the recommended approach:

> "Plugin developers should write light tests whenever possible."

**Heavy Tests** (`HeavyPlatformTestCase`) create a new project per test and should only be used for multi-module configurations.

---

## Recommendations

### 1. Use IntelliJ Platform Waiting Utilities (High Priority)

**Current:** Tests use raw `Thread.sleep()` to wait for debounced events:
```kotlin
Thread.sleep(600) // Wait for debounce
```

**Problems:**
- Flaky tests (timing-dependent)
- Slow test execution
- Race conditions

**IntelliJ Platform Solution:** Use built-in utilities from the testing FAQ:

#### A. `WaitFor` - Condition-based waiting with timeout
```kotlin
import com.intellij.util.WaitFor

// Wait up to 2 seconds for condition
WaitFor(2000) {
    service.getEvents().filterIsInstance<SelectionChangedEvent>().isNotEmpty()
}
```

#### B. `TimeoutUtil.sleep()` - When delay is truly needed
```kotlin
import com.intellij.util.TimeoutUtil

TimeoutUtil.sleep(600) // Platform-aware sleep
```

#### C. `PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()`
For tests that need to process pending UI events:
```kotlin
PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
```

#### D. `IndexingTestUtil.waitUntilIndexesAreReady()`
For tests that depend on indexing completion:
```kotlin
import com.intellij.testFramework.IndexingTestUtil

IndexingTestUtil.waitUntilIndexesAreReady(project)
```

#### E. `StartupActivityTestUtil.waitForProjectActivitiesToComplete()`
Since `ProjectActivity` is no longer awaited in tests:
```kotlin
StartupActivityTestUtil.waitForProjectActivitiesToComplete(project)
```

#### F. Expose Flush Methods (Already partially done)
The codebase already has `flushPendingEvents()` methods - ensure all debounced components expose similar methods for testing.

**Priority:** High - Reduces test flakiness and aligns with platform best practices.

---

### 2. Proper Test Teardown (High Priority)

**Current:** Unknown if `super.tearDown()` is always called in `finally` blocks.

**JetBrains Requirement:** From the testing FAQ:

> "Always invoke `super.tearDown()` within a `finally {}` block to prevent leaks from previous test failures."

```kotlin
class MyPluginTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            // Your cleanup code here
        } finally {
            super.tearDown()
        }
    }
}
```

**Why:** If a test fails before tearDown completes, subsequent tests may inherit leaked state, causing cascading failures.

**Priority:** High - Prevents flaky test suites.

---

### 3. Add Test Data Directory (Medium Priority)

**Current:** Test data is created inline within test methods.

**JetBrains Recommendation:** Use a dedicated `testData` directory:

```
src/
├── main/kotlin/...
└── test/
    ├── kotlin/...
    └── testData/
        ├── shellHistory/
        │   ├── zsh_extended.txt
        │   └── bash_simple.txt
        └── events/
            └── sample_transcript.json
```

**Implementation:**
```kotlin
class ShellHistoryParserTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String =
        "src/test/testData/shellHistory"

    fun testParseZshExtendedFormat() {
        // Loads from testData directory
        val content = loadFile("zsh_extended.txt")
        // ...
    }
}
```

**Benefits:**
- Separates test logic from test data
- Easier to maintain large test fixtures
- Enables file-based golden testing
- Follows IntelliJ Platform conventions

**Priority:** Medium - Improves maintainability as tests grow.

---

### 4. Use `UsefulTestCase` Assertion Methods (Medium Priority)

**Current:** Basic JUnit assertions.

**IntelliJ Platform Provides:** `UsefulTestCase` (parent of `BasePlatformTestCase`) includes useful assertion methods:

```kotlin
// For unordered comparisons (avoids flakiness from collection ordering)
assertUnorderedCollection(actual, expected1, expected2)

// For comparing sets
assertSameElements(actualCollection, expectedCollection)

// Get test method name for test data file naming
val testName = getTestName(false) // "testParseZshFormat" -> "ParseZshFormat"
```

**Priority:** Medium - Reduces flakiness from ordering assumptions.

---

### 5. Service/Extension Replacement in Tests (Medium Priority)

**Current:** Manual object expressions for mocking.

**IntelliJ Platform Utilities:** When you need to replace services or extensions:

#### Replacing Services
```kotlin
import com.intellij.testFramework.ServiceContainerUtil

// Replace a service for the duration of a test
ServiceContainerUtil.replaceService(
    project,
    MyService::class.java,
    mockService,
    testRootDisposable
)
```

#### Replacing Extension Points
```kotlin
import com.intellij.testFramework.ExtensionTestUtil

ExtensionTestUtil.maskExtensions(
    MY_EXTENSION_POINT,
    listOf(testExtension),
    testRootDisposable
)
```

**Note:** This aligns with JetBrains' guidance to avoid general mocking while still allowing targeted test doubles.

**Priority:** Medium - Use when specific isolation is needed.

---

### 6. Migrate to JUnit 5 (Medium Priority)

**Current:** JUnit 4 with `@Test` annotations and `@Rule` for temporary folders.

**IntelliJ Platform Support:** Since 2021.1, JetBrains provides JUnit 5 base classes:
- `LightJavaCodeInsightFixtureTestCase5` (for Java PSI tests)

**For non-Java tests:** The standard `BasePlatformTestCase` is JUnit 3-style, but you can:
1. Keep platform tests as-is (they work fine)
2. Migrate pure unit tests to JUnit 5
3. Use the fixture approach with JUnit 5

**JUnit 5 Benefits:**
- `@TempDir` parameter injection (replaces `TemporaryFolder` rule)
- `@ParameterizedTest` with various sources
- `@Nested` for test organization
- `@DisplayName` for readable names

**Example for pure unit tests:**
```kotlin
// Unit test (no platform dependency) - can use JUnit 5
class TranscriptEventTest {
    @Test
    @DisplayName("FileOpenedEvent summary extracts filename")
    fun fileOpenedEventSummary(@TempDir tempDir: Path) {
        val event = FileOpenedEvent(path = "/project/src/Main.kt")
        assertEquals("Opened Main.kt", event.summary())
    }
}
```

**Integration Tests Require JUnit 5:** The IntelliJ Starter framework (for full IDE integration tests) **requires JUnit 5 exclusively**.

**Priority:** Medium - Improves developer experience; pure unit tests benefit most.

---

### 7. Adopt Kotlin-Idiomatic Assertions (Low Priority)

**Current:** Basic JUnit assertions like `assertEquals()`, `assertTrue()`.

**Modern Practice:** Use expressive assertion libraries designed for Kotlin:

#### Option A: Kotest Assertions (Recommended)
```kotlin
event.summary() shouldBe "Opened Main.kt"
events.shouldNotBeEmpty()
events shouldHaveSize 3
```

#### Option B: AssertJ
```kotlin
assertThat(event.summary()).isEqualTo("Opened Main.kt")
assertThat(events).isNotEmpty().hasSize(3)
```

**Note:** This is lower priority than using IntelliJ's built-in `UsefulTestCase` assertions, which are already available.

**Priority:** Low - Nice to have but not critical.

---

### 8. Create Shared Test Utilities (Low Priority)

**Current:** Each test is self-contained with repeated setup patterns.

**Recommended Utilities:**

```kotlin
// Test fixtures factory
object TestEvents {
    fun fileOpened(path: String = "/test/File.kt") = FileOpenedEvent(path = path)
    fun documentChanged(path: String = "/test/File.kt", changes: Int = 1) =
        DocumentChangedEvent(path = path, changeCount = changes)
}

// Service test helper using platform patterns
fun BasePlatformTestCase.withTranscriptService(
    block: ActivityTranscriptService.() -> Unit
) {
    val service = project.service<ActivityTranscriptService>()
    service.startLogging()
    try {
        service.block()
    } finally {
        service.stopLogging()
    }
}

// Waiting helper using platform utilities
fun BasePlatformTestCase.awaitEvents(
    timeoutMs: Int = 2000,
    predicate: () -> Boolean
) {
    WaitFor(timeoutMs) { predicate() }
}
```

**Priority:** Low - Reduces duplication as test suite grows.

---

### 9. Consider Mocking Framework (Low Priority)

**Current:** Manual object expressions for mocking.

**JetBrains Guidance:** Avoid mocking when possible. Use real components.

**When mocking is needed:** MockK is the Kotlin standard:
```kotlin
val mockParser = mockk<ShellHistoryParser> {
    every { parseRecentCommands(any(), any()) } returns listOf(...)
}
```

**However:** Given JetBrains' guidance, prefer:
1. Using real implementations
2. `ServiceContainerUtil` for service replacement
3. `ExtensionTestUtil` for extension point replacement

**Priority:** Low - Only if test complexity truly requires it.

---

## Dependency Additions

```kotlin
// build.gradle.kts additions for test modernization

dependencies {
    // JUnit 5 (for pure unit tests and future integration tests)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Optional: Kotest assertions (standalone, works with any framework)
    testImplementation("io.kotest:kotest-assertions-core:5.8.1")

    // Optional: MockK (use sparingly per JetBrains guidance)
    testImplementation("io.mockk:mockk:1.13.10")
}

tasks.test {
    useJUnitPlatform()
}
```

---

## Migration Priority Summary

| Change | Priority | Effort | Impact |
|--------|----------|--------|--------|
| Use `WaitFor` instead of `Thread.sleep()` | High | Low | Reduces flakiness |
| Ensure `super.tearDown()` in `finally` blocks | High | Low | Prevents test leaks |
| Use `assertUnorderedCollection()` for collections | Medium | Low | Reduces ordering flakiness |
| Add `testData` directory structure | Medium | Medium | Better organization |
| Learn `ServiceContainerUtil`/`ExtensionTestUtil` | Medium | Low | Platform-native isolation |
| Migrate pure unit tests to JUnit 5 | Medium | Medium | Modern features |
| Add Kotest assertions | Low | Low | Better readability |
| Create shared test utilities | Low | Medium | Reduces duplication |
| Add MockK | Low | Low | Use sparingly |

---

## What's Working Well

The current test setup aligns well with JetBrains guidelines:

1. **Uses Light Tests** - Correctly extends `BasePlatformTestCase` (not `HeavyPlatformTestCase`)
2. **Avoids excessive mocking** - Uses real service instances per JetBrains recommendation
3. **Model-level testing** - Tests real IntelliJ components, not mocked abstractions
4. **Good coverage** - All major components have tests
5. **Flush methods for debouncing** - Good pattern already partially adopted
6. **Clean test isolation** - Each test is independent

---

## IntelliJ Platform Testing Resources

- [Testing Plugins Overview](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)
- [Light and Heavy Tests](https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html)
- [Tests and Fixtures](https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html)
- [Testing FAQ](https://plugins.jetbrains.com/docs/intellij/testing-faq.html) - Essential reading for common issues
- [Test Project and TestData Directories](https://plugins.jetbrains.com/docs/intellij/test-project-and-testdata-directories.html)
- [Integration Tests Introduction](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html)

---

## Conclusion

The most impactful improvements aligned with JetBrains best practices:

1. **Replace `Thread.sleep()` with `WaitFor`** - Use platform-native waiting utilities
2. **Audit tearDown methods** - Ensure `super.tearDown()` is always in `finally` blocks
3. **Use `assertUnorderedCollection()`** - Avoid flakiness from collection ordering

Lower priority but valuable:
- Add a `testData` directory for file-based test fixtures
- Migrate pure unit tests to JUnit 5 (platform tests can stay as-is)
- Consider Kotest assertions for improved readability

The current approach of avoiding mocks and using real components is **correct** per JetBrains guidance and should be preserved.
