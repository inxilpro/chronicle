package com.github.inxilpro.chronicle.services

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioCaptureManagerTest {

    @Test
    fun testAudioChunkDataClassEquality() {
        val data1 = byteArrayOf(1, 2, 3, 4)
        val data2 = byteArrayOf(1, 2, 3, 4)
        val data3 = byteArrayOf(1, 2, 3, 5)

        val chunk1 = AudioCaptureManager.AudioChunk(data1, 1000L, 5000L)
        val chunk2 = AudioCaptureManager.AudioChunk(data2, 1000L, 5000L)
        val chunk3 = AudioCaptureManager.AudioChunk(data3, 1000L, 5000L)

        assertEquals(chunk1, chunk2)
        assertNotEquals(chunk1, chunk3)
    }

    @Test
    fun testAudioChunkHashCode() {
        val data1 = byteArrayOf(1, 2, 3, 4)
        val data2 = byteArrayOf(1, 2, 3, 4)

        val chunk1 = AudioCaptureManager.AudioChunk(data1, 1000L, 5000L)
        val chunk2 = AudioCaptureManager.AudioChunk(data2, 1000L, 5000L)

        assertEquals(chunk1.hashCode(), chunk2.hashCode())
    }

    @Test
    fun testAudioDeviceDataClass() {
        val device = AudioCaptureManager.AudioDevice(
            name = "Test Microphone",
            description = "A test microphone device",
            vendor = "Test Vendor"
        )

        assertEquals("Test Microphone", device.name)
        assertEquals("A test microphone device", device.description)
        assertEquals("Test Vendor", device.vendor)
    }

    @Test
    fun testInitialState() {
        val manager = AudioCaptureManager()
        assertFalse(manager.isRecording())
        assertNull(manager.pollChunk())
        assertFalse(manager.hasChunks())
    }

    @Test
    fun testDisposeWhenNotRecording() {
        val manager = AudioCaptureManager()
        manager.dispose()
        assertFalse(manager.isRecording())
    }

    @Test
    fun testListAvailableDevicesReturnsNonNull() {
        val manager = AudioCaptureManager()
        val devices = manager.listAvailableDevices()
        assertNotNull(devices)
    }

    @Test
    fun testCalculateRmsWithSilence() {
        val silentBuffer = ByteArray(1024)
        val rms = AudioCaptureManager.calculateRms(silentBuffer, silentBuffer.size)
        assertEquals(0f, rms, 0.001f)
    }

    @Test
    fun testCalculateRmsWithMaxAmplitude() {
        val buffer = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(Short.MAX_VALUE)
            .putShort(Short.MAX_VALUE)
            .array()

        val rms = AudioCaptureManager.calculateRms(buffer, buffer.size)
        assertTrue(rms > 0.99f)
    }

    @Test
    fun testCalculateRmsWithMixedSignal() {
        val buffer = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(16384)
            .putShort(-16384)
            .putShort(16384)
            .putShort(-16384)
            .array()

        val rms = AudioCaptureManager.calculateRms(buffer, buffer.size)
        assertEquals(0.5f, rms, 0.01f)
    }

    @Test
    fun testCalculateRmsWithEmptyBuffer() {
        val rms = AudioCaptureManager.calculateRms(byteArrayOf(), 0)
        assertEquals(0f, rms, 0.001f)
    }

    @Test
    fun testCalculateRmsWithSingleByte() {
        val rms = AudioCaptureManager.calculateRms(byteArrayOf(1), 1)
        assertEquals(0f, rms, 0.001f)
    }

    @Test
    fun testDefaultChunkingParameters() {
        val manager = AudioCaptureManager()
        assertFalse(manager.isRecording())
    }

    @Test
    fun testCustomChunkingParameters() {
        val manager = AudioCaptureManager(
            minChunkMs = 10_000,
            maxChunkMs = 60_000,
            silenceThresholdRms = 0.02f,
            silenceDurationMs = 2000
        )
        assertFalse(manager.isRecording())
    }
}
