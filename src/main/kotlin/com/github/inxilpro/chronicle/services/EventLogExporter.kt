package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.time.Instant

object EventLogExporter {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    fun exportToJson(
        events: List<TranscriptEvent>,
        sessionStart: Instant,
        projectName: String
    ): String {
        val root = JsonObject()
        root.addProperty("version", "1.0")
        root.addProperty("projectName", projectName)
        root.addProperty("sessionStart", sessionStart.toString())
        root.addProperty("exportedAt", Instant.now().toString())
        root.addProperty("eventCount", events.size)

        val eventsArray = gson.toJsonTree(events.map { eventToMap(it) })
        root.add("events", eventsArray)

        return gson.toJson(root)
    }

    private fun eventToMap(event: TranscriptEvent): Map<String, Any?> {
        val baseMap = mutableMapOf<String, Any?>(
            "type" to event.type,
            "timestamp" to event.timestamp.toString(),
            "summary" to event.summary()
        )

        when (event) {
            is FileOpenedEvent -> {
                baseMap["path"] = event.path
                baseMap["isInitial"] = event.isInitial
            }
            is FileClosedEvent -> {
                baseMap["path"] = event.path
            }
            is FileSelectedEvent -> {
                baseMap["path"] = event.path
                baseMap["previousPath"] = event.previousPath
            }
            is RecentFileEvent -> {
                baseMap["path"] = event.path
            }
            is SelectionEvent -> {
                baseMap["path"] = event.path
                baseMap["startLine"] = event.startLine
                baseMap["endLine"] = event.endLine
                baseMap["text"] = event.text
            }
            is DocumentChangedEvent -> {
                baseMap["path"] = event.path
                baseMap["lineCount"] = event.lineCount
            }
            is VisibleAreaEvent -> {
                baseMap["path"] = event.path
                baseMap["startLine"] = event.startLine
                baseMap["endLine"] = event.endLine
                baseMap["contentDescription"] = event.contentDescription
            }
            is FileCreatedEvent -> {
                baseMap["path"] = event.path
            }
            is FileDeletedEvent -> {
                baseMap["path"] = event.path
            }
            is FileRenamedEvent -> {
                baseMap["oldPath"] = event.oldPath
                baseMap["newPath"] = event.newPath
            }
            is FileMovedEvent -> {
                baseMap["oldPath"] = event.oldPath
                baseMap["newPath"] = event.newPath
            }
            is BranchChangedEvent -> {
                baseMap["repository"] = event.repository
                baseMap["branch"] = event.branch
                baseMap["state"] = event.state
            }
            is SearchEvent -> {
                baseMap["query"] = event.query
            }
            is RefactoringEvent -> {
                baseMap["refactoringType"] = event.refactoringType
                baseMap["details"] = event.details
            }
            is RefactoringUndoEvent -> {
                baseMap["refactoringType"] = event.refactoringType
            }
            is AudioTranscriptionEvent -> {
                baseMap["transcriptionText"] = event.transcriptionText
                baseMap["durationMs"] = event.durationMs
                baseMap["language"] = event.language
                baseMap["confidence"] = event.confidence
                baseMap["speakerSegment"] = event.speakerSegment
            }
        }

        return baseMap
    }
}
