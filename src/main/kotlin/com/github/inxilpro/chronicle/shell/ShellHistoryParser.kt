package com.github.inxilpro.chronicle.shell

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant

data class ShellCommand(
    val command: String,
    val timestamp: Instant?,
    val shell: String
)

enum class ShellType(val historyFileName: String) {
    ZSH(".zsh_history"),
    BASH(".bash_history");

    companion object {
        fun fromFileName(name: String): ShellType? = entries.find { it.historyFileName == name }
    }
}

open class ShellHistoryParser {

    private val zshExtendedHistoryRegex = Regex("""^: (\d+):\d+;(.*)$""")
    private val bashTimestampRegex = Regex("""^#(\d+)$""")

    open fun parseRecentCommands(since: Instant, limit: Int = 100): List<ShellCommand> {
        return findHistoryFiles()
            .flatMap { (file, shellType) -> parseHistoryFile(file, shellType, since, limit) }
            .filter { it.timestamp?.isAfter(since) ?: false }
            .sortedBy { it.timestamp }
            .takeLast(limit)
    }

    fun findHistoryFiles(): List<Pair<File, ShellType>> {
        val home = System.getProperty("user.home") ?: return emptyList()
        return ShellType.entries
            .map { shellType -> File(home, shellType.historyFileName) to shellType }
            .filter { (file, _) -> file.exists() && file.canRead() }
    }

    fun parseHistoryFile(
        file: File,
        shellType: ShellType,
        since: Instant,
        limit: Int = 100
    ): List<ShellCommand> {
        return when (shellType) {
            ShellType.ZSH -> parseZshHistory(file, since, limit)
            ShellType.BASH -> parseBashHistory(file, since, limit)
        }
    }

    private fun parseZshHistory(file: File, since: Instant, limit: Int): List<ShellCommand> {
        val lines = readLastLines(file, limit * 3)
        val commands = mutableListOf<ShellCommand>()
        var currentCommand: StringBuilder? = null
        var currentTimestamp: Instant? = null

        for (line in lines) {
            val match = zshExtendedHistoryRegex.find(line)
            if (match != null) {
                currentCommand?.let { cmd ->
                    currentTimestamp?.takeIf { it.isAfter(since) }?.let { ts ->
                        commands.add(ShellCommand(
                            command = cmd.toString().trim(),
                            timestamp = ts,
                            shell = "zsh"
                        ))
                    }
                }
                currentTimestamp = try {
                    Instant.ofEpochSecond(match.groupValues[1].toLong())
                } catch (e: NumberFormatException) {
                    null
                }
                currentCommand = StringBuilder(match.groupValues[2])
            } else {
                currentCommand?.append("\n")?.append(line)
            }
        }

        currentCommand?.let { cmd ->
            currentTimestamp?.takeIf { it.isAfter(since) }?.let { ts ->
                commands.add(ShellCommand(
                    command = cmd.toString().trim(),
                    timestamp = ts,
                    shell = "zsh"
                ))
            }
        }

        return commands.takeLast(limit)
    }

    private fun parseBashHistory(file: File, since: Instant, limit: Int): List<ShellCommand> {
        val lines = readLastLines(file, limit * 3)
        val commands = mutableListOf<ShellCommand>()
        var pendingTimestamp: Instant? = null

        for (line in lines) {
            val timestampMatch = bashTimestampRegex.find(line)
            if (timestampMatch != null) {
                pendingTimestamp = try {
                    Instant.ofEpochSecond(timestampMatch.groupValues[1].toLong())
                } catch (e: NumberFormatException) {
                    null
                }
            } else if (line.isNotBlank()) {
                val timestamp = pendingTimestamp
                if (timestamp != null && timestamp.isAfter(since)) {
                    commands.add(ShellCommand(
                        command = line.trim(),
                        timestamp = timestamp,
                        shell = "bash"
                    ))
                }
                pendingTimestamp = null
            }
        }

        return commands.takeLast(limit)
    }

    private fun readLastLines(file: File, maxLines: Int): List<String> {
        if (!file.exists() || !file.canRead()) return emptyList()

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                if (fileLength == 0L) return emptyList()

                val lines = mutableListOf<String>()
                var position = fileLength - 1
                val currentLine = StringBuilder()
                var linesRead = 0

                while (position >= 0 && linesRead < maxLines) {
                    raf.seek(position)
                    val char = raf.read()

                    if (char == '\n'.code) {
                        if (currentLine.isNotEmpty()) {
                            lines.add(0, currentLine.reverse().toString())
                            currentLine.clear()
                            linesRead++
                        }
                    } else if (char != -1) {
                        currentLine.append(char.toChar())
                    }
                    position--
                }

                if (currentLine.isNotEmpty() && linesRead < maxLines) {
                    lines.add(0, currentLine.reverse().toString())
                }

                lines
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object : ShellHistoryParser()
}
