package com.github.inxilpro.chronicle.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sound.sampled.*
import kotlin.concurrent.thread

class AudioCaptureManager(
    private val chunkDurationMs: Long = 5 * 60 * 1000
) : Disposable {

    private val audioFormat = AudioFormat(16000f, 16, 1, true, false)
    private var targetDataLine: TargetDataLine? = null
    private var recordingThread: Thread? = null
    private val audioChunks = ConcurrentLinkedQueue<AudioChunk>()

    @Volatile
    private var isRecording = false

    fun startRecording(deviceName: String? = null) {
        if (isRecording) {
            thisLogger().warn("Recording already in progress")
            return
        }

        try {
            val mixer = findMixer(deviceName)
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)

            targetDataLine = if (mixer != null) {
                AudioSystem.getLine(info) as TargetDataLine
            } else {
                AudioSystem.getTargetDataLine(audioFormat)
            }

            targetDataLine?.open(audioFormat)
            targetDataLine?.start()

            isRecording = true
            recordingThread = thread(name = "Chronicle-AudioCapture") {
                captureAudio()
            }
            thisLogger().info("Started audio recording from device: ${deviceName ?: "default"}")
        } catch (e: LineUnavailableException) {
            thisLogger().error("Failed to start audio recording: audio line unavailable", e)
            throw e
        } catch (e: Exception) {
            thisLogger().error("Failed to start audio recording", e)
            throw e
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        recordingThread?.join(5000)
        targetDataLine?.stop()
        targetDataLine?.close()
        targetDataLine = null
        recordingThread = null
        thisLogger().info("Stopped audio recording")
    }

    private fun captureAudio() {
        val buffer = ByteArray(4096)
        val chunkBytes = ByteArrayOutputStream()
        var chunkStartTime = System.currentTimeMillis()

        while (isRecording) {
            val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
            if (bytesRead > 0) {
                chunkBytes.write(buffer, 0, bytesRead)

                if (System.currentTimeMillis() - chunkStartTime >= chunkDurationMs) {
                    val audioData = chunkBytes.toByteArray()
                    if (audioData.isNotEmpty()) {
                        audioChunks.offer(AudioChunk(
                            data = audioData,
                            captureTime = chunkStartTime,
                            durationMs = System.currentTimeMillis() - chunkStartTime
                        ))
                        thisLogger().debug("Buffered audio chunk: ${audioData.size} bytes")
                    }
                    chunkBytes.reset()
                    chunkStartTime = System.currentTimeMillis()
                }
            }
        }

        if (chunkBytes.size() > 0) {
            audioChunks.offer(AudioChunk(
                data = chunkBytes.toByteArray(),
                captureTime = chunkStartTime,
                durationMs = System.currentTimeMillis() - chunkStartTime
            ))
            thisLogger().debug("Buffered final audio chunk: ${chunkBytes.size()} bytes")
        }
    }

    fun pollChunk(): AudioChunk? = audioChunks.poll()

    fun hasChunks(): Boolean = audioChunks.isNotEmpty()

    fun listAvailableDevices(): List<AudioDevice> {
        return AudioSystem.getMixerInfo()
            .mapNotNull { mixerInfo ->
                try {
                    val mixer = AudioSystem.getMixer(mixerInfo)
                    val hasTargetLine = mixer.targetLineInfo.any { lineInfo ->
                        lineInfo is DataLine.Info && lineInfo.isFormatSupported(audioFormat)
                    }
                    if (hasTargetLine) {
                        AudioDevice(
                            name = mixerInfo.name,
                            description = mixerInfo.description,
                            vendor = mixerInfo.vendor
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    thisLogger().debug("Skipping mixer ${mixerInfo.name}: ${e.message}")
                    null
                }
            }
    }

    private fun findMixer(deviceName: String?): Mixer.Info? {
        if (deviceName == null) return null
        return AudioSystem.getMixerInfo().firstOrNull { it.name == deviceName }
    }

    fun isRecording(): Boolean = isRecording

    override fun dispose() {
        stopRecording()
        audioChunks.clear()
    }

    data class AudioChunk(
        val data: ByteArray,
        val captureTime: Long,
        val durationMs: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AudioChunk
            return data.contentEquals(other.data) && captureTime == other.captureTime && durationMs == other.durationMs
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + captureTime.hashCode()
            result = 31 * result + durationMs.hashCode()
            return result
        }
    }

    data class AudioDevice(
        val name: String,
        val description: String,
        val vendor: String
    )
}
