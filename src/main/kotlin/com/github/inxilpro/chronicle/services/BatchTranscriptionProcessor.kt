package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.AudioTranscriptionEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import io.github.givimad.whisperjni.WhisperContext
import io.github.givimad.whisperjni.WhisperFullParams
import io.github.givimad.whisperjni.WhisperJNI
import io.github.givimad.whisperjni.WhisperSamplingStrategy
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BatchTranscriptionProcessor(
    private val project: Project,
    private val audioManager: AudioCaptureManager
) : Disposable {

    companion object {
        // Whisper requires minimum 1 second of audio (16kHz sample rate)
        // Adding small buffer to account for rounding
        private const val MIN_SAMPLES = 16100

        private val SILENCE_MARKERS = setOf(
            "[BLANK_AUDIO]",
            "[ Silence ]",
            "[silence]",
            "[SILENCE]",
            "(silence)",
            "[ silence ]",
            "[inaudible]",
            "(inaudible)"
        )

        // Patterns for non-speech annotations like "(eerie music playing)" or "[background noise]"
        private val NON_SPEECH_PATTERN = Regex(
            """^\s*[\[\(][\s\w]*(?:music|singing|playing|noise|sound|laughter|applause|cheering|cough|sneeze|sigh|breath|static|hum|buzz|beep|ring|click|bang|thud|rustle|shuffle|footstep|door|phone|alarm|bird|dog|cat|wind|rain|thunder|water|engine|traffic|crowd|chatter|murmur|whisper|echo|feedback|distortion|interference|tone|ambient|background|eerie|dramatic|soft|loud|faint)[\s\w]*[\]\)]\s*$""",
            RegexOption.IGNORE_CASE
        )

        private fun isSilenceMarker(text: String): Boolean {
            return text in SILENCE_MARKERS || text.lowercase().let {
                it.startsWith("[silence") || it.startsWith("(silence") ||
                it.startsWith("[blank") || it.startsWith("[inaudible") ||
                it.startsWith("(inaudible")
            }
        }

        private fun isNonSpeechAnnotation(text: String): Boolean {
            return NON_SPEECH_PATTERN.matches(text)
        }
    }

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Chronicle-Transcription").apply { isDaemon = true }
    }
    private var scheduledTask: ScheduledFuture<*>? = null

    private var whisperContext: WhisperContext? = null
    private var whisperParams: WhisperFullParams? = null
    private val initialized = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)

    fun initialize(modelFile: File) {
        if (initialized.get()) {
            thisLogger().warn("BatchTranscriptionProcessor already initialized")
            return
        }

        try {
            WhisperJNI.loadLibrary()

            val whisper = WhisperJNI()
            whisperContext = whisper.init(modelFile.toPath())

            whisperParams = WhisperFullParams(WhisperSamplingStrategy.GREEDY).apply {
                language = "en"
                translate = false
                printProgress = false
                printRealtime = false
                printTimestamps = false
                singleSegment = false
            }

            initialized.set(true)
            thisLogger().info("Whisper model initialized from: ${modelFile.absolutePath}")
        } catch (e: Exception) {
            thisLogger().error("Failed to initialize Whisper model", e)
            throw e
        }
    }

    fun startProcessing() {
        if (!initialized.get()) {
            thisLogger().error("Cannot start processing: BatchTranscriptionProcessor not initialized")
            return
        }

        // Use a short polling interval to process chunks promptly after they're created
        val pollingIntervalMs = 2000L
        scheduledTask = executor.scheduleAtFixedRate(
            { processAllChunks() },
            pollingIntervalMs,
            pollingIntervalMs,
            TimeUnit.MILLISECONDS
        )
        thisLogger().info("Started batch transcription processing (polling interval: ${pollingIntervalMs / 1000}s)")
    }

    fun stopProcessing() {
        scheduledTask?.cancel(false)
        scheduledTask = null

        // Wait for any in-progress processing to complete
        var waitCount = 0
        while (processing.get() && waitCount < 50) {
            Thread.sleep(100)
            waitCount++
        }

        processAllChunks()
    }

    private fun processAllChunks() {
        if (!processing.compareAndSet(false, true)) return

        try {
            while (audioManager.hasChunks()) {
                processNextChunk()
            }
        } finally {
            processing.set(false)
        }
    }

    private fun processNextChunk() {
        val chunk = audioManager.pollChunk() ?: return
        val context = whisperContext ?: return
        val params = whisperParams ?: return

        try {
            var samples = convertBytesToFloats(chunk.data)
            if (samples.isEmpty()) return

            // Whisper requires at least 1 second of audio (16000 samples at 16kHz)
            if (samples.size < MIN_SAMPLES) {
                samples = samples.copyOf(MIN_SAMPLES)
            }

            val whisper = WhisperJNI()
            val result = whisper.full(context, params, samples, samples.size)

            if (result == 0) {
                val numSegments = whisper.fullNSegments(context)
                val chunkTimestamp = Instant.ofEpochMilli(chunk.captureTime)

                for (i in 0 until numSegments) {
                    val text = whisper.fullGetSegmentText(context, i)?.trim() ?: continue
                    if (text.isEmpty() || text.isBlank() || isSilenceMarker(text) || isNonSpeechAnnotation(text)) continue

                    val startTime = whisper.fullGetSegmentTimestamp0(context, i)
                    val endTime = whisper.fullGetSegmentTimestamp1(context, i)
                    val durationMs = ((endTime - startTime) * 10).toLong()

                    val segmentTimestamp = chunkTimestamp.plusMillis(startTime * 10)

                    transcriptService.log(
                        AudioTranscriptionEvent(
                            transcriptionText = text,
                            durationMs = durationMs,
                            language = "en",
                            confidence = 0.9f,
                            timestamp = segmentTimestamp
                        ),
                        allowBackfill = true
                    )
                }
            } else {
                thisLogger().error("Whisper transcription failed with result code: $result")
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to transcribe audio chunk", e)
        }
    }

    private fun convertBytesToFloats(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty()) return floatArrayOf()

        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return FloatArray(shorts.size) { shorts[it] / 32768f }
    }

    fun isInitialized(): Boolean = initialized.get()

    override fun dispose() {
        stopProcessing()
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        whisperContext?.close()
        whisperContext = null
        initialized.set(false)
        thisLogger().info("BatchTranscriptionProcessor disposed")
    }
}
