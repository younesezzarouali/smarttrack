package com.enzium.smarttrack

import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "gemini-api")
interface GeminiApiClient {

    /**
     * Pour discuter : Utilise le modèle flash (rapide)
     * On injecte dynamiquement la version de l'API et le modèle.
     */
    @POST
    @Path("/{version}/models/{model}:generateContent")
    fun generateContent(
        @QueryParam("key") apiKey: String,
        @PathParam("version") apiVersion: String,
        @PathParam("model") modelName: String,
        request: GeminiRequest
    ): GeminiResponse

    /**
     * Pour la mémoire (RAG) : Utilise le modèle d'embedding (spécialisé)
     */
    @POST
    @Path("/{version}/models/{model}:embedContent")
    fun embedContent(
        @QueryParam("key") apiKey: String,
        @PathParam("version") apiVersion: String,
        @PathParam("model") modelName: String,
        request: GeminiEmbedRequest
    ): GeminiEmbedResponse
}

data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig
)

data class Content(
    val role: String? = null,
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val response_mime_type: String
)

data class GeminiResponse(
    val candidates: List<Candidate>
)

data class Candidate(
    val content: Content
)

// --- RAG / Embedding Models ---

data class GeminiEmbedRequest(
    val content: EmbeddingContent
)

data class EmbeddingContent(
    val parts: List<Part>
)

data class GeminiEmbedResponse(
    val embedding: EmbeddingValue
)

data class EmbeddingValue(
    val values: List<Double>
)
