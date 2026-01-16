package com.github.inxilpro.chronicle.services

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object ModelDownloader {

    private const val MODEL_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
    private val modelsDir = File(System.getProperty("user.home"), ".chronicle/models")

    private val downloadInProgress = AtomicBoolean(false)

    enum class WhisperModel(val fileName: String, val sizeBytes: Long, val description: String) {
        TINY_EN("ggml-tiny.en.bin", 75_000_000, "Tiny English (75MB, fastest)"),
        BASE_EN("ggml-base.en.bin", 142_000_000, "Base English (142MB, fast)"),
        SMALL_EN("ggml-small.en.bin", 466_000_000, "Small English (466MB, balanced)"),
        MEDIUM_EN("ggml-medium.en.bin", 1_500_000_000, "Medium English (1.5GB, accurate)"),
        LARGE("ggml-large-v3.bin", 3_100_000_000, "Large v3 (3.1GB, most accurate)")
    }

    fun getModelFile(model: WhisperModel = WhisperModel.MEDIUM_EN): File {
        modelsDir.mkdirs()
        return File(modelsDir, model.fileName)
    }

    fun isModelDownloaded(model: WhisperModel = WhisperModel.MEDIUM_EN): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() > model.sizeBytes * 0.9
    }

    fun downloadModelAsync(
        project: Project,
        model: WhisperModel = WhisperModel.MEDIUM_EN,
        onComplete: (File?) -> Unit
    ) {
        if (!downloadInProgress.compareAndSet(false, true)) {
            thisLogger().warn("Model download already in progress")
            return
        }

        val modelFile = getModelFile(model)
        if (isModelDownloaded(model)) {
            downloadInProgress.set(false)
            onComplete(modelFile)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Downloading Whisper Model", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Downloading ${model.fileName}..."
                    indicator.text2 = "This may take several minutes depending on your connection"
                    indicator.isIndeterminate = false

                    val url = URL(MODEL_BASE_URL + model.fileName)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.setRequestProperty("User-Agent", "Chronicle-IntelliJ-Plugin")

                    if (connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                        val redirectUrl = connection.getHeaderField("Location")
                        connection.disconnect()
                        downloadFromUrl(URL(redirectUrl), modelFile, indicator, model.sizeBytes)
                    } else {
                        downloadFromUrl(url, modelFile, indicator, model.sizeBytes)
                    }

                    thisLogger().info("Model downloaded successfully: ${modelFile.absolutePath}")
                } catch (e: Exception) {
                    thisLogger().error("Failed to download model", e)
                    modelFile.delete()
                    throw e
                } finally {
                    downloadInProgress.set(false)
                }
            }

            override fun onSuccess() {
                onComplete(modelFile)
            }

            override fun onCancel() {
                downloadInProgress.set(false)
                modelFile.delete()
                onComplete(null)
            }

            override fun onThrowable(error: Throwable) {
                downloadInProgress.set(false)
                modelFile.delete()
                onComplete(null)
            }
        })
    }

    private fun downloadFromUrl(
        url: URL,
        targetFile: File,
        indicator: ProgressIndicator,
        expectedSize: Long
    ) {
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "Chronicle-IntelliJ-Plugin")

        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (indicator.isCanceled) {
                        throw InterruptedException("Download cancelled")
                    }

                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    indicator.fraction = totalRead.toDouble() / expectedSize.toDouble()
                    indicator.text2 = "${formatBytes(totalRead)} / ${formatBytes(expectedSize)}"
                }
            }
        }
    }

    fun downloadModelSync(model: WhisperModel = WhisperModel.MEDIUM_EN): File {
        val modelFile = getModelFile(model)
        if (isModelDownloaded(model)) {
            return modelFile
        }

        modelsDir.mkdirs()

        val url = URL(MODEL_BASE_URL + model.fileName)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "Chronicle-IntelliJ-Plugin")

        if (connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
            connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
            val redirectUrl = connection.getHeaderField("Location")
            connection.disconnect()
            downloadFromUrlSync(URL(redirectUrl), modelFile)
        } else {
            downloadFromUrlSync(url, modelFile)
        }

        return modelFile
    }

    private fun downloadFromUrlSync(url: URL, targetFile: File) {
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "Chronicle-IntelliJ-Plugin")

        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    fun getModelsDirectory(): File = modelsDir

    fun listDownloadedModels(): List<WhisperModel> {
        return WhisperModel.entries.filter { isModelDownloaded(it) }
    }
}
