package com.github.inxilpro.chronicle.shell

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class ShellHistoryParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testParseZshExtendedHistoryFormat() {
        val historyFile = tempFolder.newFile(".zsh_history")
        val timestamp = Instant.now().epochSecond
        historyFile.writeText("""
            : $timestamp:0;echo hello
            : ${timestamp + 1}:0;git status
            : ${timestamp + 2}:0;npm install
        """.trimIndent())

        val since = Instant.ofEpochSecond(timestamp - 1)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.ZSH, since, 100)

        assertEquals(3, commands.size)
        assertEquals("echo hello", commands[0].command)
        assertEquals("git status", commands[1].command)
        assertEquals("npm install", commands[2].command)
        assertEquals("zsh", commands[0].shell)
    }

    @Test
    fun testParseZshMultilineCommand() {
        val historyFile = tempFolder.newFile(".zsh_history")
        val timestamp = Instant.now().epochSecond
        historyFile.writeText("""
            : $timestamp:0;echo "line1
            line2
            line3"
            : ${timestamp + 1}:0;simple command
        """.trimIndent())

        val since = Instant.ofEpochSecond(timestamp - 1)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.ZSH, since, 100)

        assertEquals(2, commands.size)
        assertTrue(commands[0].command.contains("line1"))
        assertTrue(commands[0].command.contains("line2"))
        assertTrue(commands[0].command.contains("line3"))
        assertEquals("simple command", commands[1].command)
    }

    @Test
    fun testParseBashHistoryWithTimestamps() {
        val historyFile = tempFolder.newFile(".bash_history")
        val timestamp = Instant.now().epochSecond
        historyFile.writeText("""
            #$timestamp
            echo hello
            #${timestamp + 1}
            git status
            #${timestamp + 2}
            npm install
        """.trimIndent())

        val since = Instant.ofEpochSecond(timestamp - 1)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.BASH, since, 100)

        assertEquals(3, commands.size)
        assertEquals("echo hello", commands[0].command)
        assertEquals("git status", commands[1].command)
        assertEquals("npm install", commands[2].command)
        assertEquals("bash", commands[0].shell)
    }

    @Test
    fun testFiltersBySinceTimestamp() {
        val historyFile = tempFolder.newFile(".zsh_history")
        val oldTimestamp = Instant.now().epochSecond - 3600
        val newTimestamp = Instant.now().epochSecond
        historyFile.writeText("""
            : $oldTimestamp:0;old command
            : $newTimestamp:0;new command
        """.trimIndent())

        val since = Instant.ofEpochSecond(newTimestamp - 1)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.ZSH, since, 100)

        assertEquals(1, commands.size)
        assertEquals("new command", commands[0].command)
    }

    @Test
    fun testRespectsLimit() {
        val historyFile = tempFolder.newFile(".zsh_history")
        val timestamp = Instant.now().epochSecond
        val lines = (1..20).joinToString("\n") { i ->
            ": ${timestamp + i}:0;command $i"
        }
        historyFile.writeText(lines)

        val since = Instant.ofEpochSecond(timestamp)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.ZSH, since, 5)

        assertEquals(5, commands.size)
        assertEquals("command 16", commands[0].command)
        assertEquals("command 20", commands[4].command)
    }

    @Test
    fun testHandlesEmptyFile() {
        val historyFile = tempFolder.newFile(".zsh_history")
        historyFile.writeText("")

        val since = Instant.now().minusSeconds(3600)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.ZSH, since, 100)

        assertTrue(commands.isEmpty())
    }

    @Test
    fun testHandlesNonExistentFile() {
        val nonExistentFile = File(tempFolder.root, "nonexistent")

        val since = Instant.now().minusSeconds(3600)
        val commands = ShellHistoryParser.parseHistoryFile(nonExistentFile, ShellType.ZSH, since, 100)

        assertTrue(commands.isEmpty())
    }

    @Test
    fun testShellTypeFromFileName() {
        assertEquals(ShellType.ZSH, ShellType.fromFileName(".zsh_history"))
        assertEquals(ShellType.BASH, ShellType.fromFileName(".bash_history"))
        assertNull(ShellType.fromFileName(".fish_history"))
    }

    @Test
    fun testZshCommandWithSpecialCharacters() {
        val historyFile = tempFolder.newFile(".zsh_history")
        val timestamp = Instant.now().epochSecond
        historyFile.writeText("""
            : $timestamp:0;echo "hello world" | grep 'world'
            : ${timestamp + 1}:0;ls -la /path/to/dir && cd /tmp
        """.trimIndent())

        val since = Instant.ofEpochSecond(timestamp - 1)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.ZSH, since, 100)

        assertEquals(2, commands.size)
        assertEquals("echo \"hello world\" | grep 'world'", commands[0].command)
        assertEquals("ls -la /path/to/dir && cd /tmp", commands[1].command)
    }

    @Test
    fun testBashCommandWithoutTimestamps() {
        val historyFile = tempFolder.newFile(".bash_history")
        historyFile.writeText("""
            echo hello
            git status
            npm install
        """.trimIndent())

        val since = Instant.now().minusSeconds(3600)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.BASH, since, 100)

        assertTrue(commands.isEmpty())
    }

    @Test
    fun testTimestampsParsedCorrectly() {
        val historyFile = tempFolder.newFile(".zsh_history")
        val timestamp = 1704067200L // 2024-01-01 00:00:00 UTC
        historyFile.writeText(": $timestamp:0;test command")

        val since = Instant.ofEpochSecond(timestamp - 1)
        val commands = ShellHistoryParser.parseHistoryFile(historyFile, ShellType.ZSH, since, 100)

        assertEquals(1, commands.size)
        assertEquals(Instant.ofEpochSecond(timestamp), commands[0].timestamp)
    }
}
