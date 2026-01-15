package com.github.inxilpro.chronicle.services

import org.junit.Assert.*
import org.junit.Test

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
}
