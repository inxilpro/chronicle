# Chronicle - Claude Code Guidelines

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

## Testing

- All new features require tests
- Use `BasePlatformTestCase` for tests that need IntelliJ platform fixtures
- Test file naming convention: `*Test.kt` (e.g., `VisibleAreaTrackerTest.kt`)
- Place tests in `src/test/kotlin/` mirroring the main source structure
