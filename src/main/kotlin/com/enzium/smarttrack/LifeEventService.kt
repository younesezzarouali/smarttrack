package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.util.UUID

@ApplicationScoped
class LifeEventService(
    private val repository: UnifiedRepository,
    private val embeddingService: EmbeddingService
) {
    private val log: Logger = Logger.getLogger(LifeEventService::class.java)

    fun addEvents(events: List<LifeEvent>, userId: String = "default-user") {
        if (events.isEmpty()) return
        events.forEachIndexed { index, event ->
            if (event.embedding == null) {
                val textToEmbed = if (event.fullDescription.isNotBlank()) event.fullDescription else event.content
                event.embedding = embeddingService.getEmbedding(textToEmbed)
            }
            val baseTs = if (event.timestamp > 0) event.timestamp else System.currentTimeMillis()
            event.timestamp = baseTs + index.toLong()
            repository.saveEvent(userId, event)
        }
    }

    fun listAll(userId: String = "default-user", limit: Int? = null, sinceTimestamp: Long? = null): List<LifeEvent> {
        return repository.queryEvents(userId, limit, sinceTimestamp)
    }

    fun deleteEvent(timestamp: Long, userId: String = "default-user") {
        repository.deleteEvent(userId, timestamp)
    }

    fun clearAll(userId: String = "default-user") {
        listAll(userId).forEach { deleteEvent(it.timestamp, userId) }
    }

    fun findRelevantEvents(query: String, userId: String = "default-user", limit: Int = 10): List<LifeEvent> {
        log.infof("RAG: Searching memories for: %s", query)
        val queryEmbedding = embeddingService.getEmbedding(query)
        if (queryEmbedding.isEmpty()) return emptyList()

        val allEvents = listAll(userId, limit = 500)
        val results = allEvents.filter { it.embedding != null }
            .map { it to embeddingService.cosineSimilarity(queryEmbedding, it.embedding!!) }
            .filter { it.second > 0.45 }
            .sortedByDescending { it.second }
            .take(limit)
        return results.map { it.first }
    }
}
