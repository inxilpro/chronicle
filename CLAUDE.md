# Chronicle - Claude Code Guidelines

Chronicle is an IntelliJ IDEA plugin that captures developer activity into a timestamped transcript. It tracks file operations, code edits, selections, scrolling, git branches, search queries, shell commands, and audio transcriptions.

## Build & Run

```bash
./gradlew build          # Build the plugin
./gradlew check          # Run tests
./gradlew runIde         # Launch IDE with plugin for debugging
```

## Architecture

**Event System**: All tracked activities are modeled as data classes implementing the `TranscriptEvent` sealed interface in `events/TranscriptEvent.kt`. New event types should follow this pattern.

**Services**: Core logic lives in project services (`ActivityTranscriptService`, `AudioTranscriptionService`). Access via `ServiceClass.getInstance(project)`.

**Listeners**: IDE events are captured through listeners in `listeners/`. Each listener:
- Implements the appropriate IntelliJ listener interface
- Registers itself using a companion `register()` method
- Uses `Disposer.register()` for cleanup
- Debounces high-frequency events (document changes, selections, scrolling)

**Optional Dependencies**: Git and Search Everywhere integration use reflection to avoid compile-time dependencies on optional plugins, allowing graceful degradation.

## Comments

Only add comments that provide real value. Do not add comments that merely restate what the code already says.

**Don't write comments like these:**
- `/** Event logged when a file is opened. */` on a class named `FileOpenedEvent`
- `/** Get the session start time. */` on a method named `getSessionStart()`
- `/** Log a transcript event. */` on a method named `log(event: TranscriptEvent)`
- `// Log currently open files` before `fem.openFiles.forEach { ... }`

**Do write comments when:**
- Explaining *why* something is done a certain way (not *what* it does)
- Documenting non-obvious behavior or edge cases
- Providing context that isn't clear from the code itself
- Describing complex algorithms or business logic

**Good comment examples:**
- `// Debounce to avoid flooding the log during rapid scrolling`
- `// EditorHistoryManager may return files that no longer exist`
- `// Using CopyOnWriteArrayList for thread-safe iteration during export`

## Code Style

- Prefer self-documenting code with clear names over comments
- Keep functions small and focused
- Use Kotlin idioms (data classes, sealed interfaces, extension functions)
- Use thread-safe collections (`CopyOnWriteArrayList`, `ConcurrentHashMap`) for shared state

## Testing

- All new features require tests
- Use `BasePlatformTestCase` for tests that need IntelliJ platform fixtures
- Use plain JUnit for unit tests (e.g., event property tests, parser logic)
- Test file naming convention: `*Test.kt` (e.g., `VisibleAreaTrackerTest.kt`)
- Place tests in `src/test/kotlin/` mirroring the main source structure
