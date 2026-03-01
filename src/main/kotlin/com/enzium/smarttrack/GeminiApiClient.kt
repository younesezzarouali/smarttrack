package com.enzium.smarttrack

import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "gemini-api")
interface GeminiApiClient {

    @POST
    @Path("/v1beta/models/gemini-2.0-flash:generateContent")
    fun generateContent(
        @QueryParam("key") apiKey: String,
        request: GeminiRequest
    ): GeminiResponse

    @POST
    @Path("/v1beta/models/gemini-embedding-001:embedContent")
    fun embedContent(
        @QueryParam("key") apiKey: String,
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
    val model: String = "models/gemini-embedding-001",
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
