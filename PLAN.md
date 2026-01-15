Here's a focused implementation summary for the scoped plugin:

---

## IntelliJ Coding Session Transcript Plugin — Implementation Plan

### Architecture Overview

**Central service** (`ActivityTranscriptService`) at project level coordinates all listeners, aggregates events into a timestamped log, and exports to a structured format (JSON or Markdown) for LLM consumption.

```
┌─────────────────────────────────────────────────────┐
│              ActivityTranscriptService              │
├─────────────────────────────────────────────────────┤
│  - events: List<TranscriptEvent>                    │
│  - sessionStart: Instant                            │
│  - debounceTimers: Map<EventType, ScheduledFuture>  │
├─────────────────────────────────────────────────────┤
│  Listeners:                                         │
│  ├─ FileEditorManagerListener (open/close/select)   │
│  ├─ SelectionListener (debounced)                   │
│  ├─ BulkAwareDocumentListener                       │
│  ├─ VisibleAreaListener (debounced + PSI summary)   │
│  ├─ GitRepositoryChangeListener                     │
│  ├─ RefactoringEventListener                        │
│  └─ SearchEverywhereListener (defensive)            │
└─────────────────────────────────────────────────────┘
```

---

### 1. Session Start — Capture Initial State ✅ IMPLEMENTED

**API**: `FileEditorManager` + `EditorHistoryManager` (for recent files)

**Implementation**: `ActivityTranscriptService.captureInitialState()`

The following has been implemented:
- `TranscriptEvent` sealed interface with all event types in `events/TranscriptEvent.kt`
- `ActivityTranscriptService` project-level service in `services/ActivityTranscriptService.kt`
- Service registered in `plugin.xml`
- Initial state capture runs automatically when the service initializes

```kotlin
fun captureInitialState(project: Project) {
    val fem = FileEditorManager.getInstance(project)

    // Currently open files
    fem.openFiles.forEach { file ->
        log(FileOpenedEvent(file.path, isInitial = true))
    }

    // Recent files (last N)
    EditorHistoryManager.getInstance(project)
        .fileList
        .take(20)
        .forEach { file ->
            log(RecentFileEvent(file.path))
        }
}
```

Called in service constructor (`init` block) after initialization.

---

### 2. File Open/Close/Selection — FileEditorManagerListener ✅ IMPLEMENTED

**Registration**: Declarative in `plugin.xml`

**Implementation**: `listeners/FileActivityListener.kt`

The following has been implemented:
- `FileActivityListener` class in `listeners/FileActivityListener.kt`
- Listener registered in `plugin.xml` under `<projectListeners>`
- Handles `fileOpened`, `fileClosed`, and `selectionChanged` events

```kotlin
class FileActivityListener(private val project: Project) : FileEditorManagerListener {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        transcriptService.log(FileOpenedEvent(path = file.path))
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        transcriptService.log(FileClosedEvent(path = file.path))
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        event.newFile?.let { file ->
            transcriptService.log(FileSelectedEvent(
                path = file.path,
                previousPath = event.oldFile?.path
            ))
        }
    }
}
```

---

### 3. Selection Tracking — Debounced SelectionListener ✅ IMPLEMENTED

**Registration**: Programmatic via `EditorFactory.getEventMulticaster()`

**Debounce strategy**: 300ms window, log only final selection state

**Implementation**: `listeners/DebouncedSelectionListener.kt`

The following has been implemented:
- `DebouncedSelectionListener` class that implements `SelectionListener` and `Disposable`
- Registration via static `register()` method called from `ActivityTranscriptService`
- Debouncing using `ScheduledExecutorService` with configurable delay (default 300ms)
- Filters selections to only track the associated project
- Truncates selected text to 500 characters

```kotlin
class DebouncedSelectionListener(
    private val project: Project,
    private val debounceMs: Long = 300
) : SelectionListener, Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    private var pendingJob: ScheduledFuture<*>? = null
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun selectionChanged(event: IdeaSelectionEvent) {
        val editor = event.editor
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        if (editor.project != project) return

        pendingJob?.cancel(false)

        val selection = editor.selectionModel
        if (selection.hasSelection()) {
            val filePath = file.path
            val startLine = document.getLineNumber(selection.selectionStart) + 1
            val endLine = document.getLineNumber(selection.selectionEnd) + 1
            val selectedText = selection.selectedText?.take(500)

            pendingJob = executor.schedule({
                transcriptService.log(SelectionEvent(
                    path = filePath,
                    startLine = startLine,
                    endLine = endLine,
                    text = selectedText
                ))
            }, debounceMs, TimeUnit.MILLISECONDS)
        }
    }

    override fun dispose() {
        pendingJob?.cancel(true)
        executor.shutdownNow()
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): DebouncedSelectionListener {
            val listener = DebouncedSelectionListener(project)
            EditorFactory.getInstance().eventMulticaster.addSelectionListener(listener, parentDisposable)
            Disposer.register(parentDisposable, listener)
            return listener
        }
    }
}
```

---

### 4. Document Changes — BulkAwareDocumentListener ✅ IMPLEMENTED

**Registration**: Via `EditorFactory.getEventMulticaster()` and `VirtualFileManager.VFS_CHANGES`

**Implementation**:
- `listeners/DocumentChangeListener.kt` - Tracks document content changes
- `listeners/FileSystemListener.kt` - Tracks file system events (create/delete/rename/move)

The following has been implemented:
- `DocumentChangeListener` class implementing `BulkAwareDocumentListener.Simple`
- `FileSystemListener` class implementing `BulkFileListener`
- Both listeners registered programmatically in `ActivityTranscriptService.registerListeners()`
- Project-scoped filtering to only track changes within the project directory
- Comprehensive test coverage for both listeners

```kotlin
class DocumentChangeListener(
    private val project: Project
) : BulkAwareDocumentListener.Simple, Disposable {

    override fun afterDocumentChange(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!isProjectFile(file)) return

        transcriptService.log(DocumentChangedEvent(
            path = file.path,
            lineCount = document.lineCount
        ))
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): DocumentChangeListener {
            val listener = DocumentChangeListener(project)
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, parentDisposable)
            Disposer.register(parentDisposable, listener)
            return listener
        }
    }
}
```

```kotlin
class FileSystemListener(
    private val project: Project
) : BulkFileListener, Disposable {

    override fun after(events: List<VFileEvent>) {
        events.forEach { event ->
            if (!isProjectEvent(event)) return@forEach

            when (event) {
                is VFileCreateEvent -> transcriptService.log(FileCreatedEvent(path = event.path))
                is VFileDeleteEvent -> transcriptService.log(FileDeletedEvent(path = event.path))
                is VFilePropertyChangeEvent -> {
                    if (event.propertyName == VirtualFile.PROP_NAME) {
                        transcriptService.log(FileRenamedEvent(
                            oldPath = event.oldPath,
                            newPath = event.path
                        ))
                    }
                }
                is VFileMoveEvent -> transcriptService.log(FileMovedEvent(
                    oldPath = event.oldPath,
                    newPath = event.newPath
                ))
            }
        }
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): FileSystemListener {
            val listener = FileSystemListener(project)
            val connection = project.messageBus.connect(parentDisposable)
            connection.subscribe(VirtualFileManager.VFS_CHANGES, listener)
            Disposer.register(parentDisposable, listener)
            return listener
        }
    }
}
```

---

### 5. Visible Area — Debounced with PSI Summary

**Registration**: Programmatic on each Editor instance

**Challenge**: Need to attach listener when editors open, summarize visible code

```kotlin
class VisibleAreaTracker(
    private val service: ActivityTranscriptService,
    private val debounceMs: Long = 500
) {
    private val pendingJobs = ConcurrentHashMap<Editor, ScheduledFuture<*>>()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    
    fun attachTo(editor: Editor) {
        editor.scrollingModel.addVisibleAreaListener { event ->
            pendingJobs[editor]?.cancel(false)
            
            pendingJobs[editor] = executor.schedule({
                ApplicationManager.getApplication().runReadAction {
                    logVisibleArea(editor)
                }
            }, debounceMs, TimeUnit.MILLISECONDS)
        }
    }
    
    private fun logVisibleArea(editor: Editor) {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val visibleRange = editor.calculateVisibleRange()
        
        val startLine = editor.document.getLineNumber(visibleRange.startOffset) + 1
        val endLine = editor.document.getLineNumber(visibleRange.endOffset) + 1
        
        // PSI summary for PHP files
        val summary = if (file.extension == "php") {
            summarizeVisiblePHP(editor, visibleRange)
        } else {
            "Lines $startLine-$endLine"
        }
        
        service.log(VisibleAreaEvent(
            path = file.path,
            startLine = startLine,
            endLine = endLine,
            summary = summary
        ))
    }
    
    private fun summarizeVisiblePHP(editor: Editor, range: TextRange): String {
        val psiFile = PsiDocumentManager.getInstance(project)
            .getPsiFile(editor.document) ?: return "Lines ${range}"
        
        val elements = mutableListOf<String>()
        
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.textRange.intersects(range)) {
                    when {
                        // PHP-specific: check for PhpClass, Method, Function
                        element.node.elementType.toString() == "CLASS" -> {
                            elements.add("class ${element.text.substringBefore('{').trim()}")
                        }
                        element.node.elementType.toString() == "CLASS_METHOD" -> {
                            elements.add("method ${element.firstChild?.text ?: "unknown"}")
                        }
                        element.node.elementType.toString() == "FUNCTION" -> {
                            elements.add("function ${element.text.substringBefore('(').trim()}")
                        }
                    }
                }
                super.visitElement(element)
            }
        })
        
        return elements.distinct().take(5).joinToString(", ").ifEmpty { "Lines $startLine-$endLine" }
    }
}
```

**Note**: For PHP-specific PSI, you'll need the PhpStorm OpenAPI dependency. Generic approach works across all IDEs.

---

### 6. Search Everywhere — Defensive Internal API Usage

**API**: `SearchEverywhereUI.SEARCH_EVENTS` (marked `@Internal`)

**Approach**: Wrap in try-catch, degrade gracefully

```kotlin
class SearchEverywhereTracker(private val service: ActivityTranscriptService) {
    
    fun tryRegister(project: Project) {
        try {
            val connection = project.messageBus.connect()
            
            // Use reflection to avoid compile-time dependency on internal API
            val topicClass = Class.forName("com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI")
            val topicField = topicClass.getDeclaredField("SEARCH_EVENTS")
            @Suppress("UNCHECKED_CAST")
            val topic = topicField.get(null) as Topic<Any>
            
            val listener = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(Class.forName("com.intellij.ide.actions.searcheverywhere.SearchListener"))
            ) { _, method, args ->
                when (method.name) {
                    "searchFinished" -> {
                        val query = args?.getOrNull(0) as? String
                        if (!query.isNullOrBlank()) {
                            service.log(SearchEvent(query = query))
                        }
                    }
                }
                null
            }
            
            connection.subscribe(topic, listener)
            
        } catch (e: Exception) {
            // API changed or unavailable — log warning and continue
            thisLogger().warn("Search Everywhere tracking unavailable: ${e.message}")
        }
    }
}
```

---

### 7. Git Branch Changes — GitRepository Listener

**API**: `GitRepository.GIT_REPO_CHANGE` topic from `git4idea` plugin

**Dependency**: Add to `plugin.xml`:
```xml
<depends>Git4Idea</depends>
```

```kotlin
class GitBranchTracker(private val service: ActivityTranscriptService) {
    
    fun register(project: Project) {
        project.messageBus.connect().subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { repository ->
                val branch = repository.currentBranch?.name ?: repository.currentRevision?.take(8)
                service.log(BranchChangedEvent(
                    repository = repository.root.path,
                    branch = branch,
                    state = repository.state.name // NORMAL, MERGING, REBASING, etc.
                ))
            }
        )
    }
}
```

---

### 8. Shell History — External File Parsing

**Approach**: Read history file on session export (not real-time)

```kotlin
object ShellHistoryParser {
    
    fun parseRecentCommands(since: Instant, limit: Int = 100): List<ShellCommand> {
        val historyFile = findHistoryFile() ?: return emptyList()
        
        return historyFile.readLines()
            .takeLast(limit * 2) // Over-read to account for timestamps
            .mapNotNull { line -> parseHistoryLine(line) }
            .filter { it.timestamp?.isAfter(since) ?: true }
            .takeLast(limit)
    }
    
    private fun findHistoryFile(): File? {
        val home = System.getProperty("user.home")
        return listOf(
            "$home/.zsh_history",
            "$home/.bash_history",
            "$home/.local/share/fish/fish_history"
        ).map { File(it) }.firstOrNull { it.exists() }
    }
    
    private fun parseHistoryLine(line: String): ShellCommand? {
        // Zsh extended history format: : 1234567890:0;command
        val zshMatch = Regex("""^: (\d+):\d+;(.+)$""").find(line)
        if (zshMatch != null) {
            return ShellCommand(
                command = zshMatch.groupValues[2],
                timestamp = Instant.ofEpochSecond(zshMatch.groupValues[1].toLong())
            )
        }
        
        // Plain history (bash default): just the command
        return ShellCommand(command = line.trim(), timestamp = null)
    }
}

data class ShellCommand(val command: String, val timestamp: Instant?)
```

---

### 9. Refactoring Events — RefactoringEventListener

**Registration**: Declarative

```xml
<applicationListeners>
  <listener class="com.example.RefactoringTracker"
            topic="com.intellij.refactoring.listeners.RefactoringEventListener"/>
</applicationListeners>
```

```kotlin
class RefactoringTracker : RefactoringEventListener {
    
    override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        // Optional: log intent
    }
    
    override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        val details = when (refactoringId) {
            "refactoring.rename" -> {
                val element = afterData?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY)
                "Renamed to ${element?.text?.take(50)}"
            }
            "refactoring.move" -> "Move completed"
            "refactoring.safeDelete" -> "Safe delete completed"
            else -> refactoringId
        }
        
        transcriptService.log(RefactoringEvent(
            type = refactoringId,
            details = details
        ))
    }
    
    override fun undoRefactoring(refactoringId: String) {
        transcriptService.log(RefactoringUndoEvent(type = refactoringId))
    }
    
    override fun conflictsDetected(refactoringId: String, conflicts: RefactoringEventData) {
        // Optional
    }
}
```

**Limitation**: Only rename, move, and safeDelete are tracked. Extract/inline won't fire.

---

### Output Format

Export as structured JSON for LLM consumption:

```json
{
  "session": {
    "start": "2026-01-15T10:30:00Z",
    "end": "2026-01-15T12:15:00Z",
    "project": "my-laravel-app"
  },
  "initialState": {
    "openFiles": ["app/Http/Controllers/UserController.php", "..."],
    "recentFiles": ["...", "..."]
  },
  "events": [
    {
      "type": "file_opened",
      "timestamp": "2026-01-15T10:31:15Z",
      "path": "app/Models/User.php"
    },
    {
      "type": "selection",
      "timestamp": "2026-01-15T10:32:00Z",
      "path": "app/Models/User.php",
      "lines": "45-52",
      "text": "public function subscriptions(): HasMany..."
    },
    {
      "type": "visible_area",
      "timestamp": "2026-01-15T10:32:30Z",
      "path": "app/Models/User.php",
      "summary": "class User, method subscriptions, method activeSubscription"
    },
    {
      "type": "branch_changed",
      "timestamp": "2026-01-15T11:00:00Z",
      "branch": "feature/billing-refactor"
    }
  ],
  "shellHistory": [
    {"timestamp": "2026-01-15T10:45:00Z", "command": "php artisan tinker"},
    {"timestamp": "2026-01-15T10:46:30Z", "command": "git status"}
  ]
}
```

---

### Project Setup Checklist

1. **Gradle plugin project** with IntelliJ Platform Gradle Plugin 2.x
2. **Dependencies**: `Git4Idea` (optional), PhpStorm OpenAPI (for PHP PSI)
3. **plugin.xml**: Declare listeners, service, optional depends
4. **Minimum IDE version**: 2024.1+ for stable APIs
5. **Action**: Add toolbar button or menu item to start/stop/export session

---

Want me to scaffold the initial Gradle project structure and `plugin.xml`?