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

data class GeminiInteractionResponse(
    val intent: String,
    val answer: String? = null,
    val events: List<LifeEvent>? = null
)

@ApplicationScoped
class GeminiService(
    @ConfigProperty(name = "gemini.api.key") private val apiKey: String,
    @RestClient private val geminiApi: GeminiApiClient,
    private val mapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(GeminiService::class.java)

    private val systemPromptTemplate: String by lazy {
        val stream: InputStream = Thread.currentThread().contextClassLoader.getResourceAsStream("prompts/gemini-system.txt")
            ?: throw RuntimeException("Prompt definition missing")
        stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun interact(userInput: String, history: List<LifeEvent>): GeminiInteractionResponse {
        if (apiKey == "NO_KEY" || apiKey.isBlank()) throw RuntimeException("Gemini API Key is not configured")

        // Format history for the AI
        val historyContext = history.joinToString("\n") { 
            "- [${it.type}] ${it.content} (${it.payload})"
        }

        val prompt = systemPromptTemplate
            .replace("{{CONTEXT}}", if (historyContext.isEmpty()) "Aucun historique" else historyContext)
            .replace("{{INPUT}}", userInput)
        
        val request = GeminiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(response_mime_type = "application/json")
        )

        return executeWithRetry(3) {
            val response = geminiApi.generateContent(apiKey, request)
            parseInteractionResponse(response)
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
                    TimeUnit.SECONDS.sleep(2)
                    continue
                }
                break
            }
        }
        throw RuntimeException("AI Service busy", lastException)
    }

    private fun parseInteractionResponse(response: GeminiResponse): GeminiInteractionResponse {
        val jsonString = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw RuntimeException("AI returned an empty response")
        
        log.debugf("Gemini Output: %s", jsonString)
        
        return try {
            val raw: Map<String, Any> = mapper.readValue(jsonString, object : TypeReference<Map<String, Any>>() {})
            
            val intent = raw["intent"]?.toString() ?: "CAPTURE"
            val answer = raw["answer"]?.toString()
            
            val events = (raw["events"] as? List<Map<String, Any>>)?.map { eventMap ->
                LifeEvent().apply {
                    type = eventMap["type"]?.toString() ?: "NOTE"
                    content = eventMap["content"]?.toString() ?: ""
                    val rawPayload = eventMap["payload"] as? Map<*, *>
                    payload = rawPayload?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
                }
            }

            GeminiInteractionResponse(intent, answer, events)
        } catch (e: Exception) {
            log.error("Failed to parse AI output", e)
            throw RuntimeException("Structural error in AI response", e)
        }
    }
}
