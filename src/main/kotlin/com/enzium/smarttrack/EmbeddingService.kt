package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger

@ApplicationScoped
class EmbeddingService(
    @ConfigProperty(name = "gemini.api.key") private val apiKey: String,
    @RestClient private val geminiApi: GeminiApiClient
) {
    private val log: Logger = Logger.getLogger(EmbeddingService::class.java)

    fun getEmbedding(text: String): List<Double> {
        if (apiKey == "NO_KEY" || text.isBlank()) return emptyList()
        
        return try {
            val request = GeminiEmbedRequest(
                content = EmbeddingContent(parts = listOf(Part(text = text)))
            )
            val response = geminiApi.embedContent(apiKey, request)
            response.embedding.values
        } catch (e: Exception) {
            log.error("Failed to get embedding from Gemini", e)
            emptyList()
        }
    }

    fun cosineSimilarity(vectorA: List<Double>, vectorB: List<Double>): Double {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0.0
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += Math.pow(vectorA[i], 2.0)
            normB += Math.pow(vectorB[i], 2.0)
        }
        val denom = Math.sqrt(normA) * Math.sqrt(normB)
        return if (denom == 0.0) 0.0 else dotProduct / denom
    }
}
