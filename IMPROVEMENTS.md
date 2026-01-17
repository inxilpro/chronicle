# Chronicle Plugin - Improvement Recommendations

This document outlines potential improvements to the Chronicle IntelliJ plugin, organized by category. These recommendations are based on analysis of the current codebase, merged PRs, and the project's stated goals of capturing developer activity for LLM consumption.

---

## 1. Improved UX

### 1.1 Update README with Actual Project Description
**Priority: High**

The README.md still contains template placeholder text ("This Fancy IntelliJ Platform Plugin is going to be your implementation of the brilliant ideas..."). It should describe what Chronicle actually does:
- Captures developer activity into timestamped transcripts
- Tracks file operations, edits, selections, git branches, search queries, shell commands, and audio
- Exports to JSON for LLM consumption

### 1.2 Add Settings/Preferences Panel
**Priority: Medium**

Currently there's no way to configure plugin behavior. A settings panel should allow users to configure:
- Debounce timings (selection: 300ms, document: 500ms, visible area: 500ms)
- Shell history polling interval (currently fixed at 30s)
- Maximum events to display in tool window (currently 1000)
- Default export directory
- Whisper model selection (TINY_EN through LARGE)
- Enable/disable specific event types
- Recent files limit (currently 20)

### 1.3 Add Keyboard Shortcuts
**Priority: Medium**

The plugin lacks keyboard shortcuts for common operations:
- Start/Stop recording (e.g., `Ctrl+Shift+C, S`)
- Export transcript (e.g., `Ctrl+Shift+C, E`)
- Reset session (e.g., `Ctrl+Shift+C, R`)
- Toggle tool window visibility

Register these via `plugin.xml` action definitions with default keymaps.

### 1.4 Event List Filtering and Search
**Priority: Medium**

With potentially thousands of events, users need ways to find specific activity:
- Text search box to filter events by content
- Filter dropdown by event type (file ops, edits, selections, git, search, shell, audio)
- Date/time range filtering
- Show/hide specific event types via checkboxes

### 1.5 Event Grouping and Collapsing
**Priority: Low**

Group related events for better readability:
- Collapse consecutive events on the same file
- Group by time window (e.g., "10:30-10:35: 15 events on UserController.kt")
- Tree view option organized by file path

### 1.6 Copy to Clipboard Option
**Priority: Low**

Add a "Copy" button alongside "Export JSON" that copies the transcript directly to clipboard without needing to save a file. Useful for quick pasting into LLM interfaces.

### 1.7 Model Download Progress in UI
**Priority: Low**

The `ModelDownloader` shows progress via IntelliJ's progress manager, but the Chronicle panel only shows "Loading model...". Consider showing download percentage in the audio status label.

---

## 2. Improved Functionality

### 2.1 Markdown Export Format
**Priority: High**

PLAN.md mentions "structured format (JSON or Markdown)" but only JSON export is implemented. Add Markdown export that produces human-readable transcripts:
```markdown
# Chronicle Session: my-project
**Started:** 2026-01-15 10:30:00
**Exported:** 2026-01-15 12:15:00

## Timeline

### 10:31:15 - Opened User.php
### 10:32:00 - Selected lines 45-52 in User.php
> public function subscriptions(): HasMany...
### 10:32:30 - Scrolled to view lines 45-65 in User.php
```

### 2.2 Implement Refactoring Event Tracking
**Priority: High**

PLAN.md shows `RefactoringEvent` and `RefactoringUndoEvent` types exist in `TranscriptEvent.kt`, but no `RefactoringEventListener` is implemented. This is valuable for understanding code changes:
- Track rename, move, extract method/variable, inline operations
- Register via `RefactoringEventListener` topic
- Include affected element names in event details

### 2.3 Enhanced Document Change Tracking
**Priority: Medium**

`DocumentChangedEvent` only captures `path` and `lineCount`, which provides minimal context. Consider adding:
- Lines modified (start/end range)
- Character count delta (added/removed)
- Optional: diff snippet for small changes

This helps LLMs understand *what* changed, not just *that* something changed.

### 2.4 Clipboard Operation Tracking
**Priority: Medium**

Track copy/cut/paste operations which are significant developer activities:
- `ClipboardCopyEvent`: file path, selected text (truncated), line range
- `ClipboardPasteEvent`: file path, line where pasted
- Use `CopyPasteManager.getInstance().addContentChangedListener()`

### 2.5 Run Configuration Execution Tracking
**Priority: Medium**

Track when users run/debug configurations:
- `RunConfigurationEvent`: configuration name, type (run/debug), timestamp
- `RunCompletedEvent`: exit code, duration
- Use `ExecutionManager.EXECUTION_TOPIC` listener

### 2.6 Breakpoint Activity Tracking
**Priority: Low**

For debugging sessions, track breakpoint usage:
- Breakpoint added/removed/enabled/disabled
- Breakpoint hit during debugging
- Use `XBreakpointListener` from xdebugger API

### 2.7 Session Persistence
**Priority: Low**

Allow saving and resuming sessions across IDE restarts:
- Auto-save session state to project directory (`.chronicle/session.json`)
- Option to restore previous session on project open
- Clear separation between "current session" and "historical sessions"

### 2.8 Auto-Export on Session End
**Priority: Low**

Option to automatically export transcript when:
- User clicks "Stop"
- IDE/project closes
- Session exceeds configurable event count

---

## 3. Improved Performance

### 3.1 Optimize Event Storage for High-Volume Scenarios
**Priority: Medium**

`CopyOnWriteArrayList` is efficient for iteration but O(n) for writes. With high-frequency events (rapid typing, scrolling), consider:
- Use `ConcurrentLinkedQueue` for event storage with periodic compaction
- Implement circular buffer with configurable max size
- Batch notifications to listeners (already debounced at 100ms, but storage could be optimized)

### 3.2 Lazy Audio Service Initialization
**Priority: Medium**

`AudioTranscriptionService` creates `AudioCaptureManager` immediately at construction. The Whisper model should only be loaded when:
- User explicitly enables audio
- First recording is started

Current implementation already defers model loading, but `AudioCaptureManager` instance creation could also be deferred.

### 3.3 Virtual Scrolling Verification
**Priority: Low**

The event list uses `JBList` which should support virtual rendering, but verify:
- `fixedCellHeight` is set (done: 24 JBUI units)
- Ensure model operations don't trigger full list re-renders
- Consider `JBTable` with virtual rows for very large event counts

### 3.4 Shell History Background Parsing
**Priority: Low**

For users with very large history files (100k+ entries), initial parsing on tracker start could cause lag. The backwards-reading optimization in `ShellHistoryParser` helps, but consider:
- Parse in background thread (currently done)
- Show loading indicator if parsing takes >500ms
- Cache parsed results with file modification timestamp

### 3.5 Event Deduplication
**Priority: Low**

Some events may be logically duplicated:
- Multiple `DocumentChangedEvent` within same debounce window editing same area
- Rapid `VisibleAreaEvent` while scrolling
- Consider coalescing consecutive identical events beyond debouncing

---

## 4. Improved Testing

### 4.1 Tool Window / UI Tests
**Priority: High**

`ChronicleToolWindowFactory` and `ChroniclePanel` have no test coverage. Add tests for:
- Panel initialization and component layout
- Button state changes (start/stop/export)
- Event list population and scrolling
- Audio control state transitions
- Use `com.intellij.testFramework.fixtures.IdeaTestFixture` or UI testing framework

### 4.2 Integration Tests
**Priority: High**

Current tests are mostly unit tests. Add integration tests that verify end-to-end flows:
- Open file -> Edit -> Select -> Export -> Verify JSON contains all events
- Start recording -> Stop -> Verify state transitions
- Git branch change -> Verify event logged
- Shell command executed -> Verify backfill captures it

### 4.3 AudioTranscriptionService Tests
**Priority: Medium**

`AudioTranscriptionService` lacks comprehensive tests. Add:
- State machine transitions (STOPPED -> INITIALIZING -> RECORDING -> PROCESSING -> STOPPED)
- Error handling paths (model download failure, recording device unavailable)
- Concurrent start/stop requests
- Mock `BatchTranscriptionProcessor` for isolated testing

### 4.4 WrapLayout Tests
**Priority: Low**

The custom `WrapLayout` class in `ChronicleToolWindowFactory` has no tests. While it's a simple layout, edge cases should be verified:
- Empty container
- Single component
- Components exceeding container width
- Resize behavior

### 4.5 Stress/Performance Tests
**Priority: Low**

Add tests that verify performance under load:
- Log 10,000 events rapidly, verify no dropped events
- Scroll event list with 100,000 events, verify UI responsiveness
- Concurrent logging from multiple listeners

### 4.6 Test Coverage Gaps
**Priority: Low**

Based on test file analysis, these areas lack coverage:
- `BatchTranscriptionProcessor` (only basic tests)
- Export service error handling
- Listener disposal and cleanup

---

## 5. Improved CI Workflow

### 5.1 Test Coverage Thresholds
**Priority: High**

The workflow uploads coverage to Codecov but doesn't enforce minimums. Add:
```yaml
- name: Check Coverage Threshold
  run: |
    COVERAGE=$(grep -oP 'line-rate="\K[^"]+' build/reports/kover/report.xml)
    if (( $(echo "$COVERAGE < 0.70" | bc -l) )); then
      echo "Coverage $COVERAGE is below 70% threshold"
      exit 1
    fi
```

### 5.2 Run Tests in Parallel with Build
**Priority: Medium**

Currently `test` job depends on `build` completing. Since tests run their own build anyway, these could run in parallel:
```yaml
test:
  name: Test
  runs-on: ubuntu-latest
  # Remove: needs: [ build ]
```

This would reduce total CI time by running build artifact creation and tests concurrently.

### 5.3 Add Caching for Whisper Models in Tests
**Priority: Medium**

If any tests require Whisper models (audio integration tests), add caching:
```yaml
- name: Cache Whisper Models
  uses: actions/cache@v4
  with:
    path: ~/.chronicle/models
    key: whisper-models-${{ hashFiles('**/ModelDownloader.kt') }}
```

### 5.4 Matrix Testing for Multiple IDE Versions
**Priority: Medium**

The plugin should be tested against multiple IntelliJ versions:
```yaml
strategy:
  matrix:
    ide-version: ['2024.1', '2024.2', '2024.3', '2025.1']
```

This catches compatibility issues early.

### 5.5 Performance Regression Testing
**Priority: Low**

Add benchmarks that track performance over time:
- Event logging throughput (events/second)
- Memory usage with large event counts
- UI refresh latency
- Use GitHub Actions benchmark action to track trends

### 5.6 Security Scanning
**Priority: Low**

While dependabot handles dependency updates, consider adding:
- CodeQL analysis for code security issues
- Secrets scanning to prevent accidental commits
```yaml
- name: CodeQL Analysis
  uses: github/codeql-action/analyze@v3
```

### 5.7 Documentation Generation
**Priority: Low**

Auto-generate and publish API documentation:
- Dokka for Kotlin documentation
- Publish to GitHub Pages on release
```yaml
- name: Generate Docs
  run: ./gradlew dokkaHtml

- name: Deploy to Pages
  uses: peaceiris/actions-gh-pages@v4
  with:
    publish_dir: ./build/dokka/html
```

### 5.8 Artifact Naming with Version
**Priority: Low**

The build artifact name comes from the ZIP filename, but could be more explicit:
```yaml
- name: Upload artifact
  uses: actions/upload-artifact@v6
  with:
    name: chronicle-${{ github.sha }}-${{ github.run_number }}
```

---

## Implementation Priority Summary

### Immediate (High Impact, Low Effort)
1. Update README with actual description (1.1)
2. Implement Markdown export (2.1)
3. Add test coverage thresholds to CI (5.1)

### Short-term (High Impact)
4. Implement RefactoringEventListener (2.2)
5. Add Tool Window tests (4.1)
6. Add integration tests (4.2)
7. Settings/Preferences panel (1.2)

### Medium-term (Nice to Have)
8. Keyboard shortcuts (1.3)
9. Event filtering/search (1.4)
10. Enhanced document change tracking (2.3)
11. Clipboard tracking (2.4)
12. Matrix testing for IDE versions (5.4)

### Long-term (Polish)
13. Event grouping (1.5)
14. Session persistence (2.7)
15. Performance stress tests (4.5)
16. Performance regression CI (5.5)

---

## Notes

- All changes should maintain the plugin's core philosophy: minimal overhead, maximum context for LLMs
- New event types should follow the existing `TranscriptEvent` sealed interface pattern
- UI changes should use IntelliJ's UI toolkit (JB* components, JBUI scaling)
- Tests should follow existing patterns: `BasePlatformTestCase` for platform tests, plain JUnit for unit tests
