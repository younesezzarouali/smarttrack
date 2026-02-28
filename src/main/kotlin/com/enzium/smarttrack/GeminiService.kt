package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import java.io.InputStream
import java.nio.charset.StandardCharsets

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

        val response = try {
            geminiApi.generateContent(apiKey, request)
        } catch (e: Exception) {
            log.error("Failed to call Gemini API", e)
            throw RuntimeException("AI processing failed", e)
        }

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
