package com.github.inxilpro.chronicle.services

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ModelDownloaderTest {

    @Test
    fun testWhisperModelEnumValues() {
        val models = ModelDownloader.WhisperModel.entries
        assertEquals(5, models.size)

        val tinyEn = ModelDownloader.WhisperModel.TINY_EN
        assertEquals("ggml-tiny.en.bin", tinyEn.fileName)
        assertEquals(75_000_000, tinyEn.sizeBytes)

        val mediumEn = ModelDownloader.WhisperModel.MEDIUM_EN
        assertEquals("ggml-medium.en.bin", mediumEn.fileName)
        assertEquals(1_500_000_000, mediumEn.sizeBytes)
    }

    @Test
    fun testGetModelFileReturnsCorrectPath() {
        val modelFile = ModelDownloader.getModelFile(ModelDownloader.WhisperModel.TINY_EN)
        assertTrue(modelFile.absolutePath.contains(".chronicle"))
        assertTrue(modelFile.absolutePath.contains("models"))
        assertTrue(modelFile.absolutePath.endsWith("ggml-tiny.en.bin"))
    }

    @Test
    fun testGetModelsDirectory() {
        val modelsDir = ModelDownloader.getModelsDirectory()
        assertTrue(modelsDir.absolutePath.contains(".chronicle"))
        assertTrue(modelsDir.absolutePath.contains("models"))
    }

    @Test
    fun testIsModelDownloadedReturnsFalseForNonExistent() {
        val nonExistentModel = ModelDownloader.WhisperModel.LARGE
        val isDownloaded = ModelDownloader.isModelDownloaded(nonExistentModel)
        val modelFile = ModelDownloader.getModelFile(nonExistentModel)
        if (!modelFile.exists()) {
            assertFalse(isDownloaded)
        }
    }

    @Test
    fun testListDownloadedModelsReturnsEmptyOrValidList() {
        val downloadedModels = ModelDownloader.listDownloadedModels()
        assertNotNull(downloadedModels)
        downloadedModels.forEach { model ->
            assertTrue(ModelDownloader.isModelDownloaded(model))
        }
    }

    @Test
    fun testModelDescriptions() {
        ModelDownloader.WhisperModel.entries.forEach { model ->
            assertFalse(model.description.isEmpty())
            assertTrue(model.sizeBytes > 0)
            assertTrue(model.fileName.endsWith(".bin"))
        }
    }
}
