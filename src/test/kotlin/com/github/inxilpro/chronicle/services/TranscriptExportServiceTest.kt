package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.FileClosedEvent
import com.github.inxilpro.chronicle.events.FileOpenedEvent
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.time.Instant

class TranscriptExportServiceTest : BasePlatformTestCase() {

    fun testExportEmptyTranscript() {
        val service = project.service<TranscriptExportService>()
        val transcriptService = project.service<ActivityTranscriptService>()
        transcriptService.resetSession()

        val tempFile = File.createTempFile("test-export", ".json")
        try {
            service.exportToJson(tempFile)

            val json = tempFile.readText()
            assertTrue(json.contains("\"eventCount\":"))
            assertTrue(json.contains("\"events\":"))
            assertTrue(json.contains("\"session\""))
            assertTrue(json.contains("\"projectName\""))
            assertTrue(json.contains("\"projectRoot\""))
            assertTrue(json.contains("\"sessionStart\""))
            assertTrue(json.contains("\"exportedAt\""))
        } finally {
            tempFile.delete()
        }
    }

    fun testExportWithEvents() {
        val transcriptService = project.service<ActivityTranscriptService>()
        transcriptService.startLogging()
        transcriptService.log(FileOpenedEvent("/test.kt"))
        transcriptService.log(FileClosedEvent("/test.kt"))

        val service = project.service<TranscriptExportService>()
        val tempFile = File.createTempFile("test-export", ".json")
        try {
            service.exportToJson(tempFile)

            val json = tempFile.readText()
            assertTrue(json.contains("\"type\": \"file_opened\""))
            assertTrue(json.contains("\"type\": \"file_closed\""))
        } finally {
            tempFile.delete()
        }
    }

    fun testTimestampFormat() {
        val transcriptService = project.service<ActivityTranscriptService>()
        transcriptService.startLogging()
        transcriptService.log(FileOpenedEvent("/test.kt"))

        val service = project.service<TranscriptExportService>()
        val tempFile = File.createTempFile("test-export", ".json")
        try {
            service.exportToJson(tempFile)

            val json = tempFile.readText()
            val timestampPattern = Regex(""""timestamp": "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?Z"""")
            assertTrue(timestampPattern.containsMatchIn(json))
        } finally {
            tempFile.delete()
        }
    }

    fun testSummaryNotIncluded() {
        val transcriptService = project.service<ActivityTranscriptService>()
        transcriptService.startLogging()
        transcriptService.log(FileOpenedEvent("/test.kt"))

        val service = project.service<TranscriptExportService>()
        val tempFile = File.createTempFile("test-export", ".json")
        try {
            service.exportToJson(tempFile)

            val json = tempFile.readText()
            assertFalse(json.contains("\"summary\""))
        } finally {
            tempFile.delete()
        }
    }

    fun testEventsSortedByTimestamp() {
        val transcriptService = project.service<ActivityTranscriptService>()
        transcriptService.startLogging()

        val now = Instant.now()
        val earlier = now.minusSeconds(60)
        val earliest = now.minusSeconds(120)

        // Log events out of order (simulating backfill)
        transcriptService.log(FileOpenedEvent("/second.kt", timestamp = earlier))
        transcriptService.log(FileOpenedEvent("/third.kt", timestamp = now))
        transcriptService.log(FileOpenedEvent("/first.kt", timestamp = earliest))

        val service = project.service<TranscriptExportService>()
        val tempFile = File.createTempFile("test-export", ".json")
        try {
            service.exportToJson(tempFile)

            val json = tempFile.readText()
            val firstIndex = json.indexOf("first.kt")
            val secondIndex = json.indexOf("second.kt")
            val thirdIndex = json.indexOf("third.kt")

            assertTrue("first.kt should appear before second.kt", firstIndex < secondIndex)
            assertTrue("second.kt should appear before third.kt", secondIndex < thirdIndex)
        } finally {
            tempFile.delete()
        }
    }

    fun testPathsRelativizedInExport() {
        val transcriptService = project.service<ActivityTranscriptService>()
        transcriptService.startLogging()

        val basePath = project.basePath
        assertNotNull(basePath)
        transcriptService.log(FileOpenedEvent("$basePath/src/Main.kt"))

        val service = project.service<TranscriptExportService>()
        val json = service.toJson()

        assertTrue("Should contain relative path", json.contains("src/Main.kt"))
        assertTrue("Should contain projectRoot in session", json.contains("\"projectRoot\""))
    }

    fun testStripPrefix() {
        assertEquals("src/Main.kt", TranscriptExportService.stripPrefix("/home/user/project/src/Main.kt", "/home/user/project/"))
        assertEquals("/other/path.kt", TranscriptExportService.stripPrefix("/other/path.kt", "/home/user/project/"))
    }
}
