package com.github.inxilpro.chronicle.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

enum class ExportFormat {
    JSON,
    MARKDOWN
}

@State(
    name = "ChronicleSettings",
    storages = [Storage("ChronicleSettings.xml")]
)
@Service(Service.Level.PROJECT)
class ChronicleSettings : PersistentStateComponent<ChronicleSettings> {

    var exportFormat: ExportFormat = ExportFormat.MARKDOWN

    var markdownPromptTemplate: String = DEFAULT_MARKDOWN_PROMPT

    var showWaveformVisualization: Boolean = false

    var enableSecretFiltering: Boolean = true

    override fun getState(): ChronicleSettings = this

    override fun loadState(state: ChronicleSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): ChronicleSettings {
            return project.getService(ChronicleSettings::class.java)
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
