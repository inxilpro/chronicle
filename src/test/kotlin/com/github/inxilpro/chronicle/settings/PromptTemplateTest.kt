package com.github.inxilpro.chronicle.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PromptTemplateTest : BasePlatformTestCase() {

    fun testTemplateContentSurvivesMultipleSettingsAccesses() {
        val settings = project.service<ChronicleSettings>()

        val customContent = "Custom content {{SESSION_JSON}}"
        val template = settings.getSelectedTemplate()
        template.content = customContent

        // Simulate accessing settings multiple times
        val settings2 = project.service<ChronicleSettings>()
        val settings3 = project.service<ChronicleSettings>()

        assertEquals(customContent, settings2.getSelectedTemplate().content)
        assertEquals(customContent, settings3.getSelectedTemplate().content)
    }

    fun testAddingThenRemovingTemplatePreservesOriginalContent() {
        val settings = project.service<ChronicleSettings>()

        val originalContent = settings.getSelectedTemplate().content
        val originalId = settings.getSelectedTemplate().id

        // Add a new template
        val newTemplate = PromptTemplate(
            name = "Temporary Template",
            content = "Temporary content"
        )
        settings.promptTemplates.add(newTemplate)

        // Verify we have two templates
        assertEquals(2, settings.promptTemplates.size)

        // Remove the new template
        settings.promptTemplates.remove(newTemplate)

        // Verify original template content is preserved
        assertEquals(1, settings.promptTemplates.size)
        assertEquals(originalId, settings.promptTemplates.first().id)
        assertEquals(originalContent, settings.promptTemplates.first().content)
    }

    fun testDefaultTemplateAlwaysHasContent() {
        val settings = project.service<ChronicleSettings>()

        // Clear templates to force recreation
        settings.promptTemplates.clear()
        settings.selectedTemplateId = ""

        // Access selected template which should trigger default creation
        val template = settings.getSelectedTemplate()

        assertNotNull(template)
        assertTrue(template.content.isNotEmpty())
        assertTrue(template.content.contains("{{SESSION_JSON}}"))
    }

    fun testCannotHaveEmptyTemplateList() {
        val settings = project.service<ChronicleSettings>()

        // Clear templates and access - should recreate default
        settings.promptTemplates.clear()
        settings.selectedTemplateId = ""

        val template = settings.getSelectedTemplate()

        assertTrue(settings.promptTemplates.isNotEmpty())
        assertNotNull(template)
    }

    fun testTemplateListCannotBeReducedToZeroViaEnsure() {
        val settings = project.service<ChronicleSettings>()

        // Even after clearing, getSelectedTemplate should ensure at least one exists
        settings.promptTemplates.clear()

        val template = settings.getSelectedTemplate()

        assertNotNull(template)
        assertTrue(settings.promptTemplates.isNotEmpty())
    }

    fun testMultipleTemplatesCanExist() {
        val settings = project.service<ChronicleSettings>()

        val template1 = settings.getSelectedTemplate()
        val template2 = PromptTemplate(
            name = "Second Template",
            content = "Content 2 {{SESSION_JSON}}"
        )
        val template3 = PromptTemplate(
            name = "Third Template",
            content = "Content 3 {{SESSION_JSON}}"
        )

        settings.promptTemplates.add(template2)
        settings.promptTemplates.add(template3)

        assertEquals(3, settings.promptTemplates.size)
    }

    fun testTemplateUniqueIds() {
        val settings = project.service<ChronicleSettings>()

        val template2 = PromptTemplate(
            name = "Second Template",
            content = "Content 2"
        )
        settings.promptTemplates.add(template2)

        val ids = settings.promptTemplates.map { it.id }.toSet()
        assertEquals(settings.promptTemplates.size, ids.size)
    }

    fun testSelectedTemplateIdUpdatesWithNewSelection() {
        val settings = project.service<ChronicleSettings>()

        val firstId = settings.getSelectedTemplate().id

        val secondTemplate = PromptTemplate(
            name = "Second",
            content = "Content 2"
        )
        settings.promptTemplates.add(secondTemplate)
        settings.selectedTemplateId = secondTemplate.id

        assertNotEquals(firstId, settings.selectedTemplateId)
        assertEquals(secondTemplate.id, settings.selectedTemplateId)
    }

    fun testTemplateRenamePreservesContent() {
        val settings = project.service<ChronicleSettings>()

        val template = settings.getSelectedTemplate()
        val originalContent = template.content
        val originalId = template.id

        template.name = "Renamed Template"

        assertEquals("Renamed Template", settings.getSelectedTemplate().name)
        assertEquals(originalContent, settings.getSelectedTemplate().content)
        assertEquals(originalId, settings.getSelectedTemplate().id)
    }

    fun testSwitchingTemplatesPreservesEachContent() {
        val settings = project.service<ChronicleSettings>()

        val template1 = settings.getSelectedTemplate()
        template1.content = "Content 1"

        val template2 = PromptTemplate(
            name = "Template 2",
            content = "Content 2"
        )
        settings.promptTemplates.add(template2)

        // Switch to template 2
        settings.selectedTemplateId = template2.id
        assertEquals("Content 2", settings.getSelectedTemplate().content)

        // Switch back to template 1
        settings.selectedTemplateId = template1.id
        assertEquals("Content 1", settings.getSelectedTemplate().content)
    }

    fun testDefaultTemplateNameIsCorrect() {
        val settings = project.service<ChronicleSettings>()

        val defaultTemplate = settings.promptTemplates.first()
        assertEquals(ChronicleSettings.DEFAULT_TEMPLATE_NAME, defaultTemplate.name)
        assertEquals("Handoff Document", defaultTemplate.name)
    }
}
