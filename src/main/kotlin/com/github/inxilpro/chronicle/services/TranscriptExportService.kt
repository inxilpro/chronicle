package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.TranscriptEvent
import com.github.inxilpro.chronicle.export.InstantTypeAdapter
import com.github.inxilpro.chronicle.export.SessionMetadata
import com.github.inxilpro.chronicle.export.TranscriptEventTypeAdapter
import com.github.inxilpro.chronicle.export.TranscriptExport
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.time.Instant

@Service(Service.Level.PROJECT)
class TranscriptExportService(private val project: Project) {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .registerTypeAdapter(TranscriptEvent::class.java, TranscriptEventTypeAdapter())
        .serializeNulls()
        .setPrettyPrinting()
        .create()

    fun exportToJson(file: File) {
        val transcriptService = ActivityTranscriptService.getInstance(project)

        val export = TranscriptExport(
            session = SessionMetadata(
                projectName = transcriptService.getProjectName(),
                sessionStart = transcriptService.getSessionStart(),
                exportedAt = Instant.now(),
                eventCount = transcriptService.getEvents().size,
                gitBranch = transcriptService.getSessionGitBranch()
            ),
            events = transcriptService.getEvents()
        )

        file.writeText(gson.toJson(export))
    }

    companion object {
        fun getInstance(project: Project): TranscriptExportService {
            return project.getService(TranscriptExportService::class.java)
        }
    }
}
