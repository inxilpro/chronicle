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

    fun testDefaultTemplateIsCreatedWithContent() {
        val settings = ChronicleSettings.getInstance(project)
        val templates = settings.getTemplates()

        assertTrue(templates.isNotEmpty())
        val defaultTemplate = templates.find { it.id == ChronicleSettings.DEFAULT_TEMPLATE_ID }
        assertNotNull(defaultTemplate)
        assertTrue(defaultTemplate!!.content.isNotBlank())
        assertTrue(defaultTemplate.content.contains("{{SESSION_JSON}}"))
    }

    fun testDefaultTemplateContentIsRestoredIfBlank() {
        val settings = ChronicleSettings.getInstance(project)

        // Simulate a template with blank content
        val defaultTemplate = settings.promptTemplates.find { it.id == ChronicleSettings.DEFAULT_TEMPLATE_ID }
        assertNotNull(defaultTemplate)
        defaultTemplate!!.content = ""

        // Call getInstance again to trigger the fix
        val settingsAgain = ChronicleSettings.getInstance(project)
        val template = settingsAgain.getSelectedTemplate()

        assertNotNull(template)
        assertTrue(template!!.content.isNotBlank())
        assertEquals(ChronicleSettings.DEFAULT_MARKDOWN_PROMPT, template.content)
    }

    fun testAddTemplateCreatesNewTemplate() {
        val settings = ChronicleSettings.getInstance(project)
        val initialCount = settings.getTemplates().size

        val newTemplate = settings.addTemplate("Test Template", "Test content {{SESSION_JSON}}")

        assertEquals(initialCount + 1, settings.getTemplates().size)
        assertNotNull(newTemplate.id)
        assertEquals("Test Template", newTemplate.name)
        assertEquals("Test content {{SESSION_JSON}}", newTemplate.content)
    }

    fun testRemoveTemplatePreservesOtherTemplates() {
        val settings = ChronicleSettings.getInstance(project)

        // Get the default template content before adding a new template
        val defaultTemplate = settings.getSelectedTemplate()
        assertNotNull(defaultTemplate)
        val originalContent = defaultTemplate!!.content
        assertTrue(originalContent.isNotBlank())

        // Add a new template
        val newTemplate = settings.addTemplate("To Remove", "Remove me")
        assertEquals(2, settings.getTemplates().size)

        // Remove the new template
        val removed = settings.removeTemplate(newTemplate.id)
        assertTrue(removed)
        assertEquals(1, settings.getTemplates().size)

        // Verify the original template still has its content
        val remainingTemplate = settings.getSelectedTemplate()
        assertNotNull(remainingTemplate)
        assertEquals(originalContent, remainingTemplate!!.content)
    }

    fun testCannotRemoveLastTemplate() {
        val settings = ChronicleSettings.getInstance(project)

        // Ensure we only have the default template
        while (settings.getTemplates().size > 1) {
            val toRemove = settings.getTemplates().find { it.id != ChronicleSettings.DEFAULT_TEMPLATE_ID }
            if (toRemove != null) settings.removeTemplate(toRemove.id)
        }

        assertEquals(1, settings.getTemplates().size)

        // Try to remove the last template
        val removed = settings.removeTemplate(ChronicleSettings.DEFAULT_TEMPLATE_ID)
        assertFalse(removed)
        assertEquals(1, settings.getTemplates().size)
    }

    fun testUpdateTemplateModifiesContent() {
        val settings = ChronicleSettings.getInstance(project)
        val template = settings.addTemplate("Update Test", "Original content")

        settings.updateTemplate(template.id, content = "Updated content")

        val updated = settings.getTemplateById(template.id)
        assertNotNull(updated)
        assertEquals("Updated content", updated!!.content)
    }

    fun testSelectTemplateChangesSelectedTemplate() {
        val settings = ChronicleSettings.getInstance(project)
        val newTemplate = settings.addTemplate("Select Test", "Test content")

        settings.selectTemplate(newTemplate.id)

        assertEquals(newTemplate.id, settings.selectedTemplateId)
        assertEquals(newTemplate.id, settings.getSelectedTemplate()?.id)
    }

    fun testDefaultExportDestinationIsClipboard() {
        val settings = ChronicleSettings.getInstance(project)
        assertEquals(ExportDestination.CLIPBOARD, settings.exportDestination)
    }
}
