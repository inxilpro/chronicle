# Chronicle - Claude Code Guidelines

Chronicle is an IntelliJ IDEA plugin that captures developer activity into a timestamped transcript. It tracks file operations, code edits, selections, scrolling, git branches, search queries, shell commands, and audio transcriptions—then exports them as JSON for LLM consumption.

## Build & Run

```bash
./gradlew build          # Build the plugin
./gradlew check          # Run tests
./gradlew runIde         # Launch IDE with plugin for debugging
```

## Architecture

```
events/       TranscriptEvent sealed interface and 17 event data classes
listeners/    IDE event listeners (file, document, selection, scroll, git, search)
services/     Core services (ActivityTranscript, AudioTranscription, TranscriptExport)
shell/        Shell history parsing and tracking (zsh, bash)
export/       JSON serialization (Gson type adapters, export data classes)
toolWindow/   Chronicle panel UI
```

**Event System**: All tracked activities implement `TranscriptEvent` in `events/TranscriptEvent.kt`. Each event has a `type` string, `timestamp`, and `summary()` method.

**Services**: Project-level services accessed via `ServiceClass.getInstance(project)`:
- `ActivityTranscriptService` — Central event aggregation, session management, listener coordination
- `AudioTranscriptionService` — Audio recording pipeline with WhisperJNI transcription
- `TranscriptExportService` — JSON export with session metadata

**Listeners**: Each listener in `listeners/`:
- Implements the appropriate IntelliJ listener interface
- Has a companion `register(project, parentDisposable)` method
- Uses `Disposer.register()` for cleanup
- Debounces high-frequency events (300ms selections, 500ms document changes/scrolling)

**Optional Dependencies**: Git and Search Everywhere use reflection to avoid compile-time dependencies, allowing graceful degradation when plugins are unavailable.

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
