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
            // Enrichir avec l'embedding si manquant
            if (event.embedding == null) {
                val textToEmbed = if (event.fullDescription.isNotBlank()) event.fullDescription else event.content
                event.embedding = embeddingService.getEmbedding(textToEmbed)
            }
            
            // Assurer le timestamp unique
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

    /**
     * Recherche sémantique (RAG) pour trouver les souvenirs pertinents.
     */
    fun findRelevantEvents(query: String, userId: String = "default-user", limit: Int = 10): List<LifeEvent> {
        val queryEmbedding = embeddingService.getEmbedding(query)
        if (queryEmbedding.isEmpty()) return emptyList()

        // On cherche dans les 300 derniers événements
        val allEvents = listAll(userId, limit = 300)
        
        return allEvents
            .filter { it.embedding != null }
            .map { event -> 
                val score = embeddingService.cosineSimilarity(queryEmbedding, event.embedding!!)
                event to score
            }
            .filter { it.second > 0.65 } // Seuil de ressemblance
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
