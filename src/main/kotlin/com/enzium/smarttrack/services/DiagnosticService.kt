package com.enzium.smarttrack.services

import io.quarkus.runtime.Startup
import jakarta.enterprise.context.RequestScoped
import java.util.UUID
import org.jboss.logging.Logger

@RequestScoped
class DiagnosticService {
    private val logger = Logger.getLogger(DiagnosticService::class.java)
    private val requestId = UUID.randomUUID().toString()
    private val timings = mutableMapOf<String, Long>()
    private var startTime = System.currentTimeMillis()

    init {
        timings["t0_start"] = startTime
    }

    fun mark(step: String) {
        val now = System.currentTimeMillis()
        timings[step] = now
        logger.info("[$requestId] STEP: $step (+${now - startTime}ms)")
    }

    fun logSummary(extraInfo: Map<String, Any> = emptyMap()) {
        val totalDuration = System.currentTimeMillis() - startTime
        val steps = timings.entries.sortedBy { it.value }
            .joinToString(" -> ") { "${it.key}: ${it.value - startTime}ms" }
        
        logger.info("[$requestId] PERF_SUMMARY: Total=${totalDuration}ms | $steps | Context=$extraInfo")
    }
}
