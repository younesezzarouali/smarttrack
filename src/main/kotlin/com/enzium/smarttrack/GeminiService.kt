package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@ApplicationScoped
class GeminiService(
    @ConfigProperty(name = "gemini.api.key") private val apiKey: String,
    @RestClient private val geminiApi: GeminiApiClient,
    private val mapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(GeminiService::class.java)

    private val systemPrompt: String by lazy {
        val stream: InputStream = Thread.currentThread().contextClassLoader.getResourceAsStream("prompts/gemini-system.txt")
            ?: throw RuntimeException("Prompt definition missing in resources")
        stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun parseInput(userInput: String): List<LifeEvent> {
        if (apiKey == "NO_KEY" || apiKey.isBlank()) throw RuntimeException("Gemini API Key is not configured")

        val combinedText = "$systemPrompt\n\nUser Input: $userInput"
        
        val request = GeminiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = combinedText)))),
            generationConfig = GenerationConfig(response_mime_type = "application/json")
        )

        return executeWithRetry(3) {
            val response = geminiApi.generateContent(apiKey, request)
            extractAndMapEvents(response)
        }
    }

    private fun <T> executeWithRetry(maxAttempts: Int, block: () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (e.message?.contains("429") == true && attempt < maxAttempts) {
                    log.warnf("Gemini Rate Limit (429) hit. Retry %d/%d...", attempt, maxAttempts)
                    TimeUnit.SECONDS.sleep(2)
                    continue
                }
                break
            }
        }
        throw RuntimeException("AI Service temporarily unavailable: ${lastException?.message}", lastException)
    }

    private fun extractAndMapEvents(response: GeminiResponse): List<LifeEvent> {
        val jsonString = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw RuntimeException("AI returned an empty response")
        
        log.debugf("Gemini Output: %s", jsonString)
        
        return try {
            val typeRef = object : TypeReference<List<Map<String, Any>>>() {}
            val rawEvents: List<Map<String, Any>> = mapper.readValue(jsonString, typeRef)
            
            rawEvents.map { raw ->
                LifeEvent().apply {
                    type = raw["type"]?.toString() ?: "NOTE"
                    content = raw["content"]?.toString() ?: ""
                    val rawPayload = raw["payload"] as? Map<*, *>
                    payload = rawPayload?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to parse AI JSON output", e)
            throw RuntimeException("Structural error in AI response", e)
        }
    }
}
