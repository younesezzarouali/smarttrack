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
            ?: throw RuntimeException("Prompt file not found")
        stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun parseInput(userInput: String): List<LifeEvent> {
        if (apiKey == "NO_KEY") throw RuntimeException("Gemini API Key is missing")

        val combinedText = "$systemPrompt\n\nTexte utilisateur : $userInput"
        
        val request = GeminiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = combinedText)))),
            generationConfig = GenerationConfig(response_mime_type = "application/json")
        )

        // Tentative d'appel avec un petit mécanisme de retry pour l'erreur 429
        var lastException: Exception? = null
        for (i in 1..3) {
            try {
                val response = geminiApi.generateContent(apiKey, request)
                return processResponse(response)
            } catch (e: Exception) {
                lastException = e
                if (e.message?.contains("429") == true) {
                    log.warnf("Gemini API rate limited (429). Retrying in 2s... (Attempt %d/3)", i)
                    TimeUnit.SECONDS.sleep(2)
                    continue
                }
                break
            }
        }
        
        log.error("AI processing failed after retries", lastException)
        throw RuntimeException("AI temporarily unavailable, please try again in a moment", lastException)
    }

    private fun processResponse(response: GeminiResponse): List<LifeEvent> {
        val jsonString = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw RuntimeException("No response from AI")
        
        log.infof("Gemini Parsed: %s", jsonString)
        
        val typeRef = object : TypeReference<List<Map<String, Any>>>() {}
        val rawEvents: List<Map<String, Any>> = mapper.readValue(jsonString, typeRef)
        
        return rawEvents.map { raw ->
            LifeEvent().apply {
                type = raw["type"]?.toString() ?: "NOTE"
                content = raw["content"]?.toString() ?: ""
                payload = (raw["payload"] as? Map<String, Any>)?.mapValues { it.value.toString() } ?: emptyMap()
            }
        }
    }
}
