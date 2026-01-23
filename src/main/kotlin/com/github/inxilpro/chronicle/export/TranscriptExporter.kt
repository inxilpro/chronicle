package com.github.inxilpro.chronicle.export

import com.github.inxilpro.chronicle.services.TranscriptExportService
import com.github.inxilpro.chronicle.settings.ChronicleSettings
import com.github.inxilpro.chronicle.settings.ExportFormat
import com.github.inxilpro.chronicle.settings.PromptTemplate
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWrapper
import java.time.Instant
import java.time.format.DateTimeFormatter

class TranscriptExporter(private val project: Project) {

    private val settings = ChronicleSettings.getInstance(project)
    private val exportService = TranscriptExportService.getInstance(project)

    fun export(templateId: String? = null) {
        val template = if (templateId != null) {
            settings.getTemplateById(templateId)
        } else {
            settings.getSelectedTemplate()
        }

        val extension = when (settings.exportFormat) {
            ExportFormat.JSON -> "json"
            ExportFormat.MARKDOWN -> "md"
        }

        val descriptor = FileSaverDescriptor(
            "Export Chronicle Session",
            "Choose where to save the session export",
            extension
        )

        val defaultFileName = "chronicle-session-${DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-")}"

        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper: VirtualFileWrapper? = saveDialog.save(defaultFileName)

        wrapper?.let { fileWrapper ->
            val content = generateExportContent(template)
            fileWrapper.file.writeText(content)
        }
    }

    fun generateExportContent(template: PromptTemplate? = null): String {
        val json = generateJson()
        return when (settings.exportFormat) {
            ExportFormat.JSON -> json
            ExportFormat.MARKDOWN -> {
                val templateContent = template?.content ?: settings.getSelectedTemplate().content
                templateContent.replace("{{SESSION_JSON}}", json)
            }
        }
    }

    fun generateJson(): String = exportService.toJson()

    companion object {
        fun getInstance(project: Project): TranscriptExporter {
            return TranscriptExporter(project)
        }
    }
}
