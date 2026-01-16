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
    private val audioManager: AudioCaptureManager,
    private val processingIntervalMs: Long = 5 * 60 * 1000
) : Disposable {

    companion object {
        // Whisper requires minimum 1 second of audio (16kHz sample rate)
        // Adding small buffer to account for rounding
        private const val MIN_SAMPLES = 16100
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

        scheduledTask = executor.scheduleAtFixedRate(
            { processAllChunks() },
            processingIntervalMs,
            processingIntervalMs,
            TimeUnit.MILLISECONDS
        )
        thisLogger().info("Started batch transcription processing (interval: ${processingIntervalMs / 1000}s)")
    }

    fun stopProcessing() {
        scheduledTask?.cancel(false)
        scheduledTask = null

        // Wait for any in-progress processing to complete
        var waitCount = 0
        while (processing.get() && waitCount < 50) {
            thisLogger().info("Waiting for in-progress processing to complete...")
            Thread.sleep(100)
            waitCount++
        }

        thisLogger().info("Processing remaining audio chunks...")
        processAllChunks()
        thisLogger().info("Stopped batch transcription processing")
    }

    private fun processAllChunks() {
        if (!processing.compareAndSet(false, true)) {
            thisLogger().warn("Skipping chunk processing: already in progress")
            return
        }

        try {
            var chunkCount = 0
            while (audioManager.hasChunks()) {
                chunkCount++
                processNextChunk()
            }
            thisLogger().info("Processed $chunkCount audio chunks")
        } finally {
            processing.set(false)
        }
    }

    private fun processNextChunk() {
        val chunk = audioManager.pollChunk() ?: run {
            thisLogger().warn("pollChunk returned null")
            return
        }
        val context = whisperContext ?: run {
            thisLogger().warn("whisperContext is null")
            return
        }
        val params = whisperParams ?: run {
            thisLogger().warn("whisperParams is null")
            return
        }

        thisLogger().info("Processing chunk: ${chunk.data.size} bytes, ${chunk.durationMs}ms")

        try {
            var samples = convertBytesToFloats(chunk.data)
            if (samples.isEmpty()) {
                thisLogger().warn("Skipping empty audio chunk")
                return
            }

            // Whisper requires at least 1 second of audio (16000 samples at 16kHz)
            if (samples.size < MIN_SAMPLES) {
                thisLogger().info("Padding short audio chunk from ${samples.size} to $MIN_SAMPLES samples")
                samples = samples.copyOf(MIN_SAMPLES)
            }

            thisLogger().info("Calling whisper.full with ${samples.size} samples")
            val whisper = WhisperJNI()
            val result = whisper.full(context, params, samples, samples.size)
            thisLogger().info("Whisper returned result: $result")

            if (result == 0) {
                val numSegments = whisper.fullNSegments(context)
                thisLogger().info("Whisper found $numSegments segments")
                val chunkTimestamp = Instant.ofEpochMilli(chunk.captureTime)

                for (i in 0 until numSegments) {
                    val text = whisper.fullGetSegmentText(context, i)?.trim() ?: continue
                    thisLogger().info("Segment $i raw text: '$text'")
                    if (text.isEmpty() || text.isBlank()) continue

                    val startTime = whisper.fullGetSegmentTimestamp0(context, i)
                    val endTime = whisper.fullGetSegmentTimestamp1(context, i)
                    val durationMs = ((endTime - startTime) * 10).toLong()

                    val segmentTimestamp = chunkTimestamp.plusMillis(startTime * 10)

                    val event = AudioTranscriptionEvent(
                        transcriptionText = text,
                        durationMs = durationMs,
                        language = "en",
                        confidence = 0.9f,
                        timestamp = segmentTimestamp
                    )
                    thisLogger().info("Logging AudioTranscriptionEvent: ${text.take(50)}...")
                    transcriptService.log(event, allowBackfill = true)
                    thisLogger().info("Event logged successfully")
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
