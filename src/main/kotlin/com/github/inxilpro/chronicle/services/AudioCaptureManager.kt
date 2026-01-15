package com.github.inxilpro.chronicle.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sound.sampled.*
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioCaptureManager(
    private val minChunkMs: Long = 30_000,
    private val maxChunkMs: Long = 5 * 60 * 1000,
    private val silenceThresholdRms: Float = 0.015f,
    private val silenceDurationMs: Long = 1500
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
        var silenceStartTime: Long? = null

        while (isRecording) {
            val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
            if (bytesRead > 0) {
                chunkBytes.write(buffer, 0, bytesRead)

                val currentTime = System.currentTimeMillis()
                val chunkDuration = currentTime - chunkStartTime
                val rms = calculateRms(buffer, bytesRead)
                val isSilence = rms < silenceThresholdRms

                if (isSilence) {
                    if (silenceStartTime == null) {
                        silenceStartTime = currentTime
                    }
                } else {
                    silenceStartTime = null
                }

                val silenceDuration = silenceStartTime?.let { currentTime - it } ?: 0
                val shouldChunkOnSilence = chunkDuration >= minChunkMs && silenceDuration >= silenceDurationMs
                val shouldChunkOnMaxDuration = chunkDuration >= maxChunkMs

                if (shouldChunkOnSilence || shouldChunkOnMaxDuration) {
                    val audioData = chunkBytes.toByteArray()
                    if (audioData.isNotEmpty()) {
                        val reason = if (shouldChunkOnSilence) "silence detected" else "max duration"
                        audioChunks.offer(AudioChunk(
                            data = audioData,
                            captureTime = chunkStartTime,
                            durationMs = chunkDuration
                        ))
                        thisLogger().info("Buffered audio chunk ($reason): ${audioData.size} bytes, ${chunkDuration}ms, queue size: ${audioChunks.size}")
                    }
                    chunkBytes.reset()
                    chunkStartTime = currentTime
                    silenceStartTime = null
                }
            }
        }

        val finalDuration = System.currentTimeMillis() - chunkStartTime
        thisLogger().info("Capture loop ended. Final buffer size: ${chunkBytes.size()} bytes, duration: ${finalDuration}ms")
        if (chunkBytes.size() > 0) {
            audioChunks.offer(AudioChunk(
                data = chunkBytes.toByteArray(),
                captureTime = chunkStartTime,
                durationMs = finalDuration
            ))
            thisLogger().info("Buffered final audio chunk: ${chunkBytes.size()} bytes, ${finalDuration}ms, queue size: ${audioChunks.size}")
        }
    }

    fun pollChunk(): AudioChunk? {
        val chunk = audioChunks.poll()
        thisLogger().info("pollChunk called, returning chunk: ${chunk != null}, remaining: ${audioChunks.size}")
        return chunk
    }

    fun hasChunks(): Boolean {
        val has = audioChunks.isNotEmpty()
        thisLogger().info("hasChunks called, result: $has, queue size: ${audioChunks.size}")
        return has
    }

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

    companion object {
        fun calculateRms(buffer: ByteArray, bytesRead: Int): Float {
            if (bytesRead < 2) return 0f

            val samples = bytesRead / 2
            val shortBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()

            var sumSquares = 0.0
            for (i in 0 until samples) {
                val sample = shortBuffer.get(i) / 32768f
                sumSquares += sample * sample
            }

            return sqrt(sumSquares / samples).toFloat()
        }
    }
}
