package com.github.inxilpro.chronicle.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.util.*

enum class ExportFormat {
    JSON,
    MARKDOWN
}

enum class ExportDestination {
    CLIPBOARD,
    FILE
}

@Tag("template")
class PromptTemplate() {
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var content: String = ""

    constructor(id: String, name: String, content: String) : this() {
        this.id = id
        this.name = name
        this.content = content
    }

    fun copy(id: String = this.id, name: String = this.name, content: String = this.content): PromptTemplate {
        return PromptTemplate(id, name, content)
    }
}

@State(
    name = "ChronicleSettings",
    storages = [Storage("ChronicleSettings.xml")]
)
@Service(Service.Level.PROJECT)
class ChronicleSettings : PersistentStateComponent<ChronicleSettings> {

    var exportFormat: ExportFormat = ExportFormat.MARKDOWN

    var exportDestination: ExportDestination = ExportDestination.CLIPBOARD

    @Deprecated("Use promptTemplates instead", ReplaceWith("promptTemplates"))
    var markdownPromptTemplate: String = ""

    var showWaveformVisualization: Boolean = false

    @XCollection(style = XCollection.Style.v2)
    var promptTemplates: MutableList<PromptTemplate> = mutableListOf()

    var selectedTemplateId: String = DEFAULT_TEMPLATE_ID

    override fun getState(): ChronicleSettings = this

    override fun loadState(state: ChronicleSettings) {
        XmlSerializerUtil.copyBean(state, this)
        migrateIfNeeded()
    }

    private fun migrateIfNeeded() {
        if (promptTemplates.isEmpty()) {
            @Suppress("DEPRECATION")
            val content = if (markdownPromptTemplate.isNotBlank()) markdownPromptTemplate else DEFAULT_MARKDOWN_PROMPT
            promptTemplates.add(PromptTemplate(DEFAULT_TEMPLATE_ID, DEFAULT_TEMPLATE_NAME, content))
            selectedTemplateId = DEFAULT_TEMPLATE_ID
            @Suppress("DEPRECATION")
            markdownPromptTemplate = ""
        } else {
            // Ensure default template has content (fixes blank template on first load)
            promptTemplates.find { it.id == DEFAULT_TEMPLATE_ID }?.let { defaultTemplate ->
                if (defaultTemplate.content.isBlank()) {
                    defaultTemplate.content = DEFAULT_MARKDOWN_PROMPT
                }
            }
        }
    }

    fun getTemplates(): List<PromptTemplate> {
        if (promptTemplates.isEmpty()) {
            migrateIfNeeded()
        }
        return promptTemplates.toList()
    }

    fun getSelectedTemplate(): PromptTemplate? {
        if (promptTemplates.isEmpty()) {
            migrateIfNeeded()
        }
        return promptTemplates.find { it.id == selectedTemplateId }
            ?: promptTemplates.firstOrNull()
    }

    fun getTemplateById(id: String): PromptTemplate? {
        return promptTemplates.find { it.id == id }
    }

    fun addTemplate(name: String, content: String): PromptTemplate {
        val template = PromptTemplate(UUID.randomUUID().toString(), name, content)
        promptTemplates.add(template)
        return template
    }

    fun updateTemplate(id: String, name: String? = null, content: String? = null) {
        promptTemplates.find { it.id == id }?.let { template ->
            name?.let { template.name = it }
            content?.let { template.content = it }
        }
    }

    fun removeTemplate(id: String): Boolean {
        if (promptTemplates.size <= 1) return false
        val removed = promptTemplates.removeIf { it.id == id }
        if (removed && selectedTemplateId == id) {
            selectedTemplateId = promptTemplates.first().id
        }
        return removed
    }

    fun selectTemplate(id: String) {
        if (promptTemplates.any { it.id == id }) {
            selectedTemplateId = id
        }
    }

    companion object {
        const val DEFAULT_TEMPLATE_ID = "handoff-document"
        const val DEFAULT_TEMPLATE_NAME = "Handoff Document"

        fun getInstance(project: Project): ChronicleSettings {
            val settings = project.getService(ChronicleSettings::class.java)
            if (settings.promptTemplates.isEmpty()) {
                settings.promptTemplates.add(
                    PromptTemplate(DEFAULT_TEMPLATE_ID, DEFAULT_TEMPLATE_NAME, DEFAULT_MARKDOWN_PROMPT)
                )
            } else {
                // Ensure default template has content (fixes blank template on first load)
                settings.promptTemplates.find { it.id == DEFAULT_TEMPLATE_ID }?.let { defaultTemplate ->
                    if (defaultTemplate.content.isBlank()) {
                        defaultTemplate.content = DEFAULT_MARKDOWN_PROMPT
                    }
                }
            }
            return settings
        }

        const val DEFAULT_MARKDOWN_PROMPT = """You are synthesizing an IDE session recording into a developer handoff document. The input contains:

1. **IDE Events**: File opens, selections, navigation, visible areas, code viewed
2. **Audio Transcriptions**: The developer's spoken narration explaining their thinking

## Purpose

Create a document that onboards another developer to implement a feature or make changes. Write as a senior developer handing off to a peer—direct, collaborative, no bureaucratic hedging.

## Correlation Rules

- Match transcriptions to events by timestamp proximity (within ~5 seconds)
- Transcriptions explain intent; events show what was actually examined
- When narration says "this" or "here," connect to the nearest selection/visible_area event

## Output Structure

### Title
A clear, action-oriented title describing what needs to be done.

### Opening Paragraph
2-4 sentences establishing *why* this work matters and *what* the goal is. No labels, just a natural lead-in. Use "we" for collaborative framing.

### Overview
Bullet points that orient the reader:
- What they need to understand before starting
- Where to look (specific files, classes, methods)
- Current state / why it's insufficient
- Key relationships or patterns to be aware of

This section answers: "What do I need to know to do this well?"

### Decisions
Flat assertions of constraints and conclusions already reached:
- Architectural choices that are settled
- Boundaries (what NOT to change)
- Requirements (testing, compatibility, etc.)

No justification needed here—just the constraint. If reasoning is important, it belongs in Overview.

### Next Steps
Ordered checklist of concrete actions. Sequence implies priority:
- [ ] First thing to do
- [ ] Then this
- [ ] Finally this

### Notes (optional)
Anything useful that doesn't fit above—open questions, gotchas, relevant links.

## Guidelines

- Be direct in how you communicate, but preserve the actual level of certainty from the narration
  - If decided: "The solution is X"
  - If intentionally open: "Either A or B would work—use your judgment"
  - If uncertain: "The best approach here isn't clear yet; investigate before committing"
- Don't hedge what was stated confidently; don't assert what was left open
- Include code snippets only when they were explicitly discussed/selected and aid understanding
- Only include file/code references if they support understanding, a decision, or an action
- Omit navigation that didn't lead to a conclusion or instruction
- If narration is unclear or missing, document what was *observed* without inventing intent
- When the developer gives explicit instructions ("do not modify X", "make sure to Y"), capture these verbatim in Decisions or Next Steps

---

## Session Data
```json
{{SESSION_JSON}}
```"""
    }
}
