package com.github.inxilpro.chronicle.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ChronicleSettingsTest : BasePlatformTestCase() {

    fun testServiceCanBeInstantiated() {
        val settings = project.service<ChronicleSettings>()
        assertNotNull(settings)
    }

    fun testDefaultExportFormatIsMarkdown() {
        val settings = project.service<ChronicleSettings>()
        assertEquals(ExportFormat.MARKDOWN, settings.exportFormat)
    }

    fun testDefaultMarkdownPromptTemplateIsSet() {
        val settings = project.service<ChronicleSettings>()
        assertNotNull(settings.markdownPromptTemplate)
        assertTrue(settings.markdownPromptTemplate.isNotEmpty())
        assertTrue(settings.markdownPromptTemplate.contains("{{SESSION_JSON}}"))
    }

    fun testExportFormatCanBeChanged() {
        val settings = project.service<ChronicleSettings>()
        settings.exportFormat = ExportFormat.JSON
        assertEquals(ExportFormat.JSON, settings.exportFormat)
    }

    fun testMarkdownPromptTemplateCanBeChanged() {
        val settings = project.service<ChronicleSettings>()
        val customTemplate = "Custom template: {{SESSION_JSON}}"
        settings.markdownPromptTemplate = customTemplate
        assertEquals(customTemplate, settings.markdownPromptTemplate)
    }

    fun testDefaultPromptContainsExpectedSections() {
        val defaultPrompt = ChronicleSettings.DEFAULT_MARKDOWN_PROMPT
        assertTrue(defaultPrompt.contains("## Purpose"))
        assertTrue(defaultPrompt.contains("## Correlation Rules"))
        assertTrue(defaultPrompt.contains("## Output Structure"))
        assertTrue(defaultPrompt.contains("## Guidelines"))
        assertTrue(defaultPrompt.contains("## Session Data"))
    }
}
