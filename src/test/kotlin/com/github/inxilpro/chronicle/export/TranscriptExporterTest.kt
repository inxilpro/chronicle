package com.github.inxilpro.chronicle.export

import com.github.inxilpro.chronicle.events.*
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.github.inxilpro.chronicle.settings.ChronicleSettings
import com.github.inxilpro.chronicle.settings.ExportFormat
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TranscriptExporterTest : BasePlatformTestCase() {

    private lateinit var transcriptService: ActivityTranscriptService
    private lateinit var settings: ChronicleSettings
    private lateinit var exporter: TranscriptExporter

    override fun setUp() {
        super.setUp()
        transcriptService = project.service<ActivityTranscriptService>()
        settings = project.service<ChronicleSettings>()
        exporter = TranscriptExporter.getInstance(project)
        transcriptService.startLogging()
        transcriptService.resetSession()
    }

    fun testGenerateJsonReturnsValidJson() {
        transcriptService.log(FileOpenedEvent(path = "/test/file.kt"))

        val json = exporter.generateJson()

        assertTrue(json.contains("\"session\""))
        assertTrue(json.contains("\"events\""))
    }

    fun testGenerateJsonIncludesSessionInfo() {
        val json = exporter.generateJson()

        assertTrue(json.contains("\"sessionStart\""))
        assertTrue(json.contains("\"exportedAt\""))
        assertTrue(json.contains("\"projectName\""))
    }

    fun testGenerateJsonIncludesEvents() {
        transcriptService.log(FileOpenedEvent(path = "/test/file.kt", isInitial = false))
        transcriptService.log(FileClosedEvent(path = "/test/file.kt"))

        val json = exporter.generateJson()

        assertTrue(json.contains("\"file_opened\""))
        assertTrue(json.contains("\"file_closed\""))
    }

    fun testGenerateJsonHandlesSelectionEvent() {
        transcriptService.log(SelectionEvent(
            path = "/test/file.kt",
            startLine = 10,
            endLine = 20,
            text = "selected text"
        ))

        val json = exporter.generateJson()

        assertTrue(json.contains("\"selection\""))
        assertTrue(json.contains("\"startLine\""))
        assertTrue(json.contains("10"))
        assertTrue(json.contains("\"endLine\""))
        assertTrue(json.contains("20"))
    }

    fun testGenerateJsonHandlesAudioTranscriptionEvent() {
        transcriptService.log(AudioTranscriptionEvent(
            transcriptionText = "Hello world",
            durationMs = 5000,
            language = "en",
            confidence = 0.95f
        ))

        val json = exporter.generateJson()

        assertTrue(json.contains("\"audio_transcription\""))
        assertTrue(json.contains("Hello world"))
        assertTrue(json.contains("\"durationMs\""))
        assertTrue(json.contains("5000"))
    }

    fun testGenerateJsonEscapesSpecialCharacters() {
        transcriptService.log(AudioTranscriptionEvent(
            transcriptionText = "Text with \"quotes\" and\nnewlines",
            durationMs = 1000,
            language = "en",
            confidence = 0.9f
        ))

        val json = exporter.generateJson()

        assertTrue(json.contains("\\\"quotes\\\""))
        assertTrue(json.contains("\\n"))
    }

    fun testGenerateExportContentReturnsJsonWhenJsonFormat() {
        settings.exportFormat = ExportFormat.JSON
        transcriptService.log(FileOpenedEvent(path = "/test/file.kt"))

        val content = exporter.generateExportContent()

        assertTrue(content.startsWith("{"))
        assertTrue(content.contains("\"session\""))
    }

    fun testGenerateExportContentReturnsMarkdownWhenMarkdownFormat() {
        settings.exportFormat = ExportFormat.MARKDOWN
        transcriptService.log(FileOpenedEvent(path = "/test/file.kt"))

        val content = exporter.generateExportContent()

        assertTrue(content.contains("## Purpose"))
        assertTrue(content.contains("\"session\""))
    }

    fun testMarkdownExportReplacesPlaceholder() {
        settings.exportFormat = ExportFormat.MARKDOWN
        val template = settings.getSelectedTemplate()
        assertNotNull(template)
        template!!.content = "Before {{SESSION_JSON}} After"
        transcriptService.log(FileOpenedEvent(path = "/test/file.kt"))

        val content = exporter.generateExportContent()

        assertTrue(content.startsWith("Before "))
        assertTrue(content.endsWith(" After"))
        assertFalse(content.contains("{{SESSION_JSON}}"))
        assertTrue(content.contains("\"session\""))
    }

    fun testGenerateJsonHandlesShellCommandEvent() {
        transcriptService.log(ShellCommandEvent(
            command = "git status",
            shell = "zsh",
            workingDirectory = "/home/user/project"
        ))

        val json = exporter.generateJson()

        assertTrue(json.contains("\"shell_command\""))
        assertTrue(json.contains("git status"))
        assertTrue(json.contains("zsh"))
    }

    fun testGenerateJsonHandlesBranchChangedEvent() {
        transcriptService.log(BranchChangedEvent(
            repository = "/project",
            branch = "feature/test",
            state = "NORMAL"
        ))

        val json = exporter.generateJson()

        assertTrue(json.contains("\"branch_changed\""))
        assertTrue(json.contains("feature/test"))
    }

    fun testGenerateJsonHandlesNullBranch() {
        transcriptService.log(BranchChangedEvent(
            repository = "/project",
            branch = null,
            state = "DETACHED"
        ))

        val json = exporter.generateJson()

        assertTrue(json.contains("\"branch\":"))
    }

    fun testGenerateJsonIncludesEventCount() {
        transcriptService.log(FileOpenedEvent(path = "/test/file1.kt"))
        transcriptService.log(FileOpenedEvent(path = "/test/file2.kt"))

        val json = exporter.generateJson()

        assertTrue(json.contains("\"eventCount\""))
    }
}
