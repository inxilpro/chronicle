package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.*
import com.github.inxilpro.chronicle.settings.ChronicleSettings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.time.Instant

class PhileasFilteringServiceTest : BasePlatformTestCase() {

    private lateinit var filteringService: PhileasFilteringService
    private lateinit var settings: ChronicleSettings

    override fun setUp() {
        super.setUp()
        filteringService = project.service<PhileasFilteringService>()
        settings = project.service<ChronicleSettings>()
        settings.enableSecretFiltering = true
    }

    fun testServiceCanBeInstantiated() {
        assertNotNull(filteringService)
    }

    fun testFilterTextRedactsCreditCards() {
        val input = "My credit card is 4121742025464465"
        val result = filteringService.filterText(input)
        assertFalse("Credit card should be redacted", result.contains("4121742025464465"))
        assertTrue("Result should contain redaction marker", result.contains("REDACTED"))
    }

    fun testFilterTextRedactsSsn() {
        val input = "My SSN is 123-45-6789"
        val result = filteringService.filterText(input)
        assertFalse("SSN should be redacted", result.contains("123-45-6789"))
        assertTrue("Result should contain redaction marker", result.contains("REDACTED"))
    }

    fun testFilterTextRedactsEmailAddresses() {
        val input = "Contact me at test@example.com"
        val result = filteringService.filterText(input)
        assertFalse("Email should be redacted", result.contains("test@example.com"))
        assertTrue("Result should contain redaction marker", result.contains("REDACTED"))
    }

    fun testFilterTextRedactsAwsAccessKeys() {
        val input = "export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE"
        val result = filteringService.filterText(input)
        assertFalse("AWS key should be redacted", result.contains("AKIAIOSFODNN7EXAMPLE"))
    }

    fun testFilterTextRedactsGithubTokens() {
        val input = "GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx12345"
        val result = filteringService.filterText(input)
        assertFalse("GitHub token should be redacted", result.contains("ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx12345"))
    }

    fun testFilterTextRedactsDatabaseUrls() {
        val input = "postgresql://user:secretpassword@localhost:5432/mydb"
        val result = filteringService.filterText(input)
        assertFalse("Database URL with credentials should be redacted", result.contains("secretpassword"))
    }

    fun testFilterTextRedactsPasswordArgs() {
        val input = "mysql -u admin -pMySecretPass123 database"
        val result = filteringService.filterText(input)
        assertFalse("Password argument should be redacted", result.contains("MySecretPass123"))
    }

    fun testFilterTextRedactsBearerTokens() {
        val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
        val result = filteringService.filterText(input)
        assertFalse("Bearer token should be redacted", result.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
    }

    fun testFilterTextPreservesInnocuousText() {
        val input = "This is a normal log message about building a project"
        val result = filteringService.filterText(input)
        assertEquals("Normal text should be preserved", input, result)
    }

    fun testFilterTextHandlesEmptyInput() {
        val result = filteringService.filterText("")
        assertEquals("Empty input should return empty", "", result)
    }

    fun testFilterTextHandlesBlankInput() {
        val result = filteringService.filterText("   ")
        assertEquals("Blank input should return unchanged", "   ", result)
    }

    fun testFilterShellCommandEvent() {
        val event = ShellCommandEvent(
            command = "export API_KEY=sk_live_1234567890abcdefghij",
            shell = "zsh",
            workingDirectory = "/home/user/project",
            timestamp = Instant.now()
        )

        val filtered = filteringService.filterEvent(event)

        assertTrue("Result should be ShellCommandEvent", filtered is ShellCommandEvent)
        val filteredEvent = filtered as ShellCommandEvent
        assertFalse("API key should be redacted", filteredEvent.command.contains("sk_live_1234567890abcdefghij"))
    }

    fun testFilterSelectionEvent() {
        val event = SelectionEvent(
            path = "/project/config.py",
            startLine = 10,
            endLine = 12,
            text = "password = 'MySuperSecretPassword123'",
            timestamp = Instant.now()
        )

        val filtered = filteringService.filterEvent(event)

        assertTrue("Result should be SelectionEvent", filtered is SelectionEvent)
        val filteredEvent = filtered as SelectionEvent
        assertNotNull("Text should not be null", filteredEvent.text)
    }

    fun testFilterSearchEvent() {
        val event = SearchEvent(
            query = "password=admin123",
            timestamp = Instant.now()
        )

        val filtered = filteringService.filterEvent(event)

        assertTrue("Result should be SearchEvent", filtered is SearchEvent)
        val filteredEvent = filtered as SearchEvent
        assertFalse("Password should be redacted", filteredEvent.query.contains("admin123"))
    }

    fun testFilterAudioTranscriptionEvent() {
        val event = AudioTranscriptionEvent(
            transcriptionText = "The API key is AKIAIOSFODNN7EXAMPLE",
            durationMs = 5000,
            language = "en",
            confidence = 0.95f,
            timestamp = Instant.now()
        )

        val filtered = filteringService.filterEvent(event)

        assertTrue("Result should be AudioTranscriptionEvent", filtered is AudioTranscriptionEvent)
        val filteredEvent = filtered as AudioTranscriptionEvent
        assertFalse("AWS key should be redacted", filteredEvent.transcriptionText.contains("AKIAIOSFODNN7EXAMPLE"))
    }

    fun testFilteringDisabledReturnsOriginalEvent() {
        settings.enableSecretFiltering = false

        val event = ShellCommandEvent(
            command = "export API_KEY=sk_live_1234567890abcdefghij",
            shell = "zsh",
            timestamp = Instant.now()
        )

        val filtered = filteringService.filterEvent(event)

        assertTrue("Result should be ShellCommandEvent", filtered is ShellCommandEvent)
        val filteredEvent = filtered as ShellCommandEvent
        assertEquals("Command should be unchanged when filtering disabled",
            "export API_KEY=sk_live_1234567890abcdefghij",
            filteredEvent.command)
    }

    fun testNonTextEventsPassThrough() {
        val event = FileOpenedEvent(
            path = "/project/src/Main.kt",
            isInitial = false,
            timestamp = Instant.now()
        )

        val filtered = filteringService.filterEvent(event)

        assertSame("Non-text events should pass through unchanged", event, filtered)
    }

    fun testFilterDocumentChangedEventPassesThrough() {
        val event = DocumentChangedEvent(
            path = "/project/secret.txt",
            lineCount = 100,
            timestamp = Instant.now()
        )

        val filtered = filteringService.filterEvent(event)

        assertSame("DocumentChangedEvent should pass through unchanged", event, filtered)
    }

    fun testFilterBranchChangedEventPassesThrough() {
        val event = BranchChangedEvent(
            repository = "project",
            branch = "feature/secret-handling",
            state = "CHANGED",
            timestamp = Instant.now()
        )

        val filtered = filteringService.filterEvent(event)

        assertSame("BranchChangedEvent should pass through unchanged", event, filtered)
    }
}
