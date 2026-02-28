package com.enzium.smarttrack

import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "gemini-api")
interface GeminiApiClient {

    @POST
    @Path(":generateContent")
    fun generateContent(
        @QueryParam("key") apiKey: String,
        request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig
)

data class Content(
    val role: String,
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
