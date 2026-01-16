package com.github.inxilpro.chronicle.shell

import com.github.inxilpro.chronicle.events.ShellCommandEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ShellHistoryTracker(
    private val project: Project,
    private val pollIntervalSeconds: Long = 30,
    private val parser: ShellHistoryParser = ShellHistoryParser
) : Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ShellHistoryTracker-${project.name}").apply { isDaemon = true }
    }
    private var pollTask: ScheduledFuture<*>? = null
    private val lastBackfillTime = AtomicReference<Instant>(null)
    private val loggedCommands = mutableSetOf<String>()

    fun startTracking(sessionStart: Instant) {
        lastBackfillTime.set(sessionStart)
        loggedCommands.clear()

        backfillHistory()

        pollTask = executor.scheduleAtFixedRate(
            { backfillHistory() },
            pollIntervalSeconds,
            pollIntervalSeconds,
            TimeUnit.SECONDS
        )

        thisLogger().info("Shell history tracking started for project: ${project.name}")
    }

    fun stopTracking() {
        pollTask?.cancel(false)
        pollTask = null

        backfillHistory()

        thisLogger().info("Shell history tracking stopped for project: ${project.name}")
    }

    fun backfillHistory() {
        val since = lastBackfillTime.get() ?: return

        try {
            val commands = parser.parseRecentCommands(since, limit = 200)

            var newCommandCount = 0
            for (command in commands) {
                val key = makeCommandKey(command)
                if (key !in loggedCommands) {
                    loggedCommands.add(key)
                    transcriptService.log(
                        ShellCommandEvent(
                            command = command.command,
                            shell = command.shell,
                            timestamp = command.timestamp ?: Instant.now()
                        ),
                        allowBackfill = true
                    )
                    newCommandCount++
                }
            }

            if (newCommandCount > 0) {
                thisLogger().debug("Backfilled $newCommandCount shell commands")
            }

            lastBackfillTime.set(Instant.now())
        } catch (e: Exception) {
            thisLogger().warn("Failed to backfill shell history", e)
        }
    }

    private fun makeCommandKey(command: ShellCommand): String {
        return "${command.shell}:${command.timestamp?.epochSecond}:${command.command}"
    }

    override fun dispose() {
        pollTask?.cancel(true)
        executor.shutdownNow()
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): ShellHistoryTracker {
            val tracker = ShellHistoryTracker(project)
            Disposer.register(parentDisposable, tracker)
            return tracker
        }
    }
}
