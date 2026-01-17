package com.github.inxilpro.chronicle.export

import com.github.inxilpro.chronicle.events.TranscriptEvent
import java.time.Instant

data class TranscriptExport(
    val session: SessionMetadata,
    val events: List<TranscriptEvent>
)

data class SessionMetadata(
    val projectName: String,
    val sessionStart: Instant,
    val exportedAt: Instant,
    val eventCount: Int,
    val gitBranch: String?
)
