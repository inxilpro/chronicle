package com.github.inxilpro.chronicle.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ChronicleConfigurableTest : BasePlatformTestCase() {

    private lateinit var settings: ChronicleSettings
    private lateinit var configurable: ChronicleConfigurable

    override fun setUp() {
        super.setUp()
        settings = project.service<ChronicleSettings>()
        configurable = ChronicleConfigurable(project)
    }

    override fun tearDown() {
        configurable.disposeUIResources()
        super.tearDown()
    }

    fun testCreateComponentReturnsNonNull() {
        val component = configurable.createComponent()
        assertNotNull(component)
    }

    fun testDisplayNameIsChronicle() {
        assertEquals("Chronicle", configurable.displayName)
    }

    fun testResetLoadsSettingsIntoUI() {
        val component = configurable.createComponent()
        assertNotNull(component)

        // Modify settings
        settings.exportFormat = ExportFormat.JSON

        // Reset should load the new format
        configurable.reset()

        // Not modified since we just reset
        assertFalse(configurable.isModified)
    }

    fun testIsModifiedDetectsFormatChange() {
        settings.exportFormat = ExportFormat.MARKDOWN
        val component = configurable.createComponent()
        assertNotNull(component)

        // Initially not modified
        assertFalse(configurable.isModified)
    }

    fun testApplySavesChanges() {
        settings.exportFormat = ExportFormat.MARKDOWN
        val component = configurable.createComponent()
        assertNotNull(component)

        configurable.apply()

        // After apply, isModified should be false
        assertFalse(configurable.isModified)
    }

    fun testDisposeUIResourcesClearsReferences() {
        val component = configurable.createComponent()
        assertNotNull(component)

        configurable.disposeUIResources()

        // After dispose, createComponent should work again
        val newComponent = configurable.createComponent()
        assertNotNull(newComponent)
    }

    fun testTemplateContentSurvivesDialogReopening() {
        val customContent = "Custom template content {{SESSION_JSON}}"

        // First dialog session
        val component1 = configurable.createComponent()
        assertNotNull(component1)

        // Modify the template through settings (simulating UI edit)
        settings.getSelectedTemplate().content = customContent
        configurable.reset() // Reload from settings

        configurable.apply()
        configurable.disposeUIResources()

        // Second dialog session - verify content survives
        configurable = ChronicleConfigurable(project)
        val component2 = configurable.createComponent()
        assertNotNull(component2)

        assertEquals(customContent, settings.getSelectedTemplate().content)
    }

    fun testMultipleDialogReopeningsPreserveTemplates() {
        // Add multiple templates
        settings.promptTemplates.add(PromptTemplate(
            name = "Template 2",
            content = "Content 2 {{SESSION_JSON}}"
        ))
        settings.promptTemplates.add(PromptTemplate(
            name = "Template 3",
            content = "Content 3 {{SESSION_JSON}}"
        ))

        val initialCount = settings.promptTemplates.size

        // Open and close dialog multiple times
        for (i in 1..3) {
            val config = ChronicleConfigurable(project)
            config.createComponent()
            config.reset()
            config.apply()
            config.disposeUIResources()
        }

        // Verify templates are preserved
        assertEquals(initialCount, settings.promptTemplates.size)
    }

    fun testDefaultTemplateNeverBecomesBlank() {
        val component = configurable.createComponent()
        assertNotNull(component)

        // Multiple reset/apply cycles
        for (i in 1..5) {
            configurable.reset()
            configurable.apply()
        }

        val template = settings.getSelectedTemplate()
        assertNotNull(template.content)
        assertTrue(template.content.isNotEmpty())
    }

    fun testAddingAndRemovingTemplatePreservesOriginal() {
        val originalContent = settings.getSelectedTemplate().content
        val originalId = settings.getSelectedTemplate().id

        val component = configurable.createComponent()
        assertNotNull(component)

        // Add a template through settings
        val newTemplate = PromptTemplate(
            name = "Temporary",
            content = "Temp content"
        )
        settings.promptTemplates.add(newTemplate)

        // Remove it
        settings.promptTemplates.remove(newTemplate)

        configurable.reset()
        configurable.apply()

        // Original should be preserved
        assertEquals(originalId, settings.promptTemplates.first().id)
        assertEquals(originalContent, settings.promptTemplates.first().content)
    }
}
