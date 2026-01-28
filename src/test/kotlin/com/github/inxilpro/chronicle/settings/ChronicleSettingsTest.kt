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

    fun testDefaultTemplateIsCreated() {
        val settings = project.service<ChronicleSettings>()
        assertTrue(settings.promptTemplates.isNotEmpty())
        assertEquals(ChronicleSettings.DEFAULT_TEMPLATE_NAME, settings.promptTemplates.first().name)
    }

    fun testDefaultTemplateHasContent() {
        val settings = project.service<ChronicleSettings>()
        val template = settings.getSelectedTemplate()
        assertNotNull(template.content)
        assertTrue(template.content.isNotEmpty())
        assertTrue(template.content.contains("{{SESSION_JSON}}"))
    }

    fun testExportFormatCanBeChanged() {
        val settings = project.service<ChronicleSettings>()
        settings.exportFormat = ExportFormat.JSON
        assertEquals(ExportFormat.JSON, settings.exportFormat)
    }

    fun testTemplateContentCanBeChanged() {
        val settings = project.service<ChronicleSettings>()
        val customTemplate = "Custom template: {{SESSION_JSON}}"
        val template = settings.getSelectedTemplate()
        template.content = customTemplate
        assertEquals(customTemplate, settings.getSelectedTemplate().content)
    }

    fun testDefaultPromptContainsExpectedSections() {
        val defaultPrompt = ChronicleSettings.DEFAULT_MARKDOWN_PROMPT
        assertTrue(defaultPrompt.contains("## Purpose"))
        assertTrue(defaultPrompt.contains("## Correlation Rules"))
        assertTrue(defaultPrompt.contains("## Output Structure"))
        assertTrue(defaultPrompt.contains("## Guidelines"))
        assertTrue(defaultPrompt.contains("## Session Data"))
    }

    fun testGetSelectedTemplateReturnsFirstIfNotFound() {
        val settings = project.service<ChronicleSettings>()
        settings.selectedTemplateId = "nonexistent-id"
        val template = settings.getSelectedTemplate()
        assertNotNull(template)
        assertEquals(settings.promptTemplates.first().id, template.id)
    }

    fun testGetTemplateByIdReturnsNullForNonexistent() {
        val settings = project.service<ChronicleSettings>()
        val template = settings.getTemplateById("nonexistent-id")
        assertNull(template)
    }

    fun testGetTemplateByIdReturnsCorrectTemplate() {
        val settings = project.service<ChronicleSettings>()
        val firstTemplate = settings.promptTemplates.first()
        val foundTemplate = settings.getTemplateById(firstTemplate.id)
        assertNotNull(foundTemplate)
        assertEquals(firstTemplate.id, foundTemplate!!.id)
    }

    fun testMultipleTemplatesCanBeAdded() {
        val settings = project.service<ChronicleSettings>()
        val initialCount = settings.promptTemplates.size

        settings.promptTemplates.add(PromptTemplate(
            name = "Second Template",
            content = "Second content {{SESSION_JSON}}"
        ))

        assertEquals(initialCount + 1, settings.promptTemplates.size)
    }

    fun testSelectedTemplateIdCanBeChanged() {
        val settings = project.service<ChronicleSettings>()

        val secondTemplate = PromptTemplate(
            name = "Second Template",
            content = "Second content {{SESSION_JSON}}"
        )
        settings.promptTemplates.add(secondTemplate)
        settings.selectedTemplateId = secondTemplate.id

        val selected = settings.getSelectedTemplate()
        assertEquals(secondTemplate.id, selected.id)
        assertEquals("Second Template", selected.name)
    }

    fun testTemplateNameCanBeChanged() {
        val settings = project.service<ChronicleSettings>()
        val template = settings.promptTemplates.first()
        template.name = "Renamed Template"
        assertEquals("Renamed Template", settings.promptTemplates.first().name)
    }

    fun testDefaultTemplateNameIsHandoffDocument() {
        assertEquals("Handoff Document", ChronicleSettings.DEFAULT_TEMPLATE_NAME)
    }
}
