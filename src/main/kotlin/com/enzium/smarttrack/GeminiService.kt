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
            ?: throw RuntimeException("Prompt definition missing in resources")
        stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun generateBriefing(history: List<LifeEvent>): String {
        if (apiKey == "NO_KEY" || apiKey.isBlank()) return "Assistant configuration incomplete."
        if (history.isEmpty()) return "Welcome! Start by recording your first activity or expense today."

        val historyContext = history.joinToString("\n") { "- [${it.type}] ${it.content}" }
        
        val prompt = """
            Tu es un assistant Life OS. Voici les événements de la journée de l'utilisateur :
            $historyContext
            
            Fais un résumé très court (3 lignes max), motivant et humain de sa journée. 
            Utilise le 'tu'. Sois percutant.
            Réponds uniquement en texte brut, pas de JSON ici.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(response_mime_type = "text/plain")
        )

        return try {
            val response = executeWithRetry(3) { geminiApi.generateContent(apiKey, request) }
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Good day! Keep track of your progress."
        } catch (e: Exception) {
            "Ready to help you track your day!"
        }
    }

    fun interact(userInput: String, history: List<LifeEvent>): GeminiInteractionResponse {
        if (apiKey == "NO_KEY" || apiKey.isBlank()) throw RuntimeException("Gemini API Key is not configured")

        val historyContext = if (history.isEmpty()) "Aucun historique" 
            else history.joinToString("\n") { "- [${it.type}] ${it.content} (${it.payload})" }

        val prompt = systemPromptTemplate
            .replace("{{CONTEXT}}", historyContext)
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
                    log.warnf("Gemini Rate Limit hit. Retry %d/%d...", attempt, maxAttempts)
                    TimeUnit.SECONDS.sleep(2)
                    continue
                }
                break
            }
        }
        throw RuntimeException("AI Service unavailable", lastException)
    }

    private fun parseInteractionResponse(response: GeminiResponse): GeminiInteractionResponse {
        val jsonString = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw RuntimeException("AI returned an empty response")
        
        return try {
            val node = mapper.readTree(jsonString)
            val intent = node.get("intent")?.asText() ?: "CAPTURE"
            val answer = node.get("answer")?.asText()
            
            val eventsNode = node.get("events")
            val events = if (eventsNode != null && eventsNode.isArray) {
                eventsNode.map { eventNode ->
                    LifeEvent().apply {
                        type = eventNode.get("type")?.asText() ?: "NOTE"
                        content = eventNode.get("content")?.asText() ?: ""
                        val payloadNode = eventNode.get("payload")
                        payload = if (payloadNode != null && payloadNode.isObject) {
                            val payloadMap = mutableMapOf<String, String>()
                            payloadNode.fields().forEach { entry ->
                                payloadMap[entry.key] = entry.value.asText()
                            }
                            payloadMap
                        } else emptyMap()
                    }
                }
            } else null

            GeminiInteractionResponse(intent, answer, events)
        } catch (e: Exception) {
            throw RuntimeException("Structural error in AI response", e)
        }
    }
}
