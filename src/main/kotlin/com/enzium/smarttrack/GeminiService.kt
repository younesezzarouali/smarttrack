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
    val dailyInsight: String? = null,
    val answer: String? = null, // Ajout pour le Frontend
    val events: List<LifeEvent> = emptyList(),
    val habitUpdates: List<HabitUpdateIntent> = emptyList(),
    val habitCreations: List<HabitCreationIntent> = emptyList()
)

@ApplicationScoped
class GeminiService(
    @ConfigProperty(name = "gemini.api.key") private val apiKey: String,
    @ConfigProperty(name = "gemini.model.chat") private val chatModel: String,
    @ConfigProperty(name = "gemini.api.version") private val apiVersion: String,
    @RestClient private val geminiApi: GeminiApiClient,
    private val mapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(GeminiService::class.java)

    private val systemPromptTemplate: String by lazy {
        val stream: InputStream = Thread.currentThread().contextClassLoader.getResourceAsStream("prompts/gemini-system.txt")
            ?: throw RuntimeException("Prompt definition missing")
        stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun interact(userInput: String, history: List<LifeEvent>, habits: List<Habit>): GeminiInteractionResponse {
        if (apiKey == "NO_KEY" || apiKey.isBlank()) throw RuntimeException("Gemini API Key missing")

        val habitsContext = if (habits.isEmpty()) "Aucune habitude configurée"
            else habits.joinToString("\n") { "- ID: ${it.id}, Name: ${it.name}, Category: ${it.category}" }

        val historyContext = if (history.isEmpty()) "Aucun historique" 
            else history.joinToString("\n") { "- [${it.type}] ${it.content}: ${it.fullDescription}" }

        val prompt = systemPromptTemplate
            .replace("{{HABITS}}", habitsContext)
            .replace("{{CONTEXT}}", historyContext)
            .replace("{{INPUT}}", userInput)
        
        val request = GeminiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(response_mime_type = "application/json")
        )

        return executeWithRetry(3) {
            val response = geminiApi.generateContent(apiKey, apiVersion, chatModel, request)
            parseInteractionResponse(response, habits)
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
                    log.warnf("Gemini Rate Limit. Retry %d...", attempt)
                    TimeUnit.SECONDS.sleep(2)
                    continue
                }
                break
            }
        }
        throw RuntimeException("AI Service busy", lastException)
    }

    private fun parseInteractionResponse(response: GeminiResponse, habits: List<Habit>): GeminiInteractionResponse {
        val jsonString = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw RuntimeException("Empty AI response")
        
        return try {
            val node = mapper.readTree(jsonString)
            val intent = node.get("intent")?.asText() ?: "CAPTURE"
            val dailyInsight = node.get("dailyInsight")?.asText()
            
            val habitUpdates = mutableListOf<HabitUpdateIntent>()
            node.get("habitUpdates")?.let { updatesNode ->
                if (updatesNode.isArray) {
                    updatesNode.mapTo(habitUpdates) { updateNode ->
                        val hName = updateNode.get("habitName")?.asText()
                        val hId = updateNode.get("habitId")?.asText()
                        val resolvedId = hId ?: habits.find { it.name.contains(hName ?: "---", ignoreCase = true) }?.id
                        
                        HabitUpdateIntent(
                            habitId = resolvedId,
                            habitName = hName,
                            progressDelta = updateNode.get("progressDelta")?.asDouble() ?: 0.0,
                            confidence = updateNode.get("confidence")?.asDouble() ?: 1.0
                        )
                    }
                }
            }

            val events = mutableListOf<LifeEvent>()
            node.get("events")?.let { eventsNode ->
                if (eventsNode.isArray) {
                    eventsNode.mapTo(events) { eventNode ->
                        LifeEvent().apply {
                            userId = "default-user"
                            type = eventNode.get("type")?.asText() ?: "NOTE"
                            content = eventNode.get("content")?.asText() ?: ""
                            fullDescription = eventNode.get("fullDescription")?.asText() ?: content
                            val pMap = mutableMapOf<String, String>()
                            eventNode.get("payload")?.fields()?.forEach { pMap[it.key] = it.value.asText() }
                            
                            val matchingUpdate = habitUpdates.find { content.contains(it.habitName ?: "---", ignoreCase = true) }
                            if (matchingUpdate?.habitId != null) {
                                pMap["linkedHabitId"] = matchingUpdate.habitId
                            }
                            
                            payload = pMap
                        }
                    }
                }
            }

            GeminiInteractionResponse(
                intent = intent, 
                dailyInsight = dailyInsight, 
                answer = dailyInsight, // Copie pour le Front
                events = events, 
                habitUpdates = habitUpdates
            )
        } catch (e: Exception) {
            log.error("Failed to parse AI JSON", e)
            throw RuntimeException("AI format error")
        }
    }

    fun generateBriefing(history: List<LifeEvent>): String {
        return "Calculated in interaction."
    }
}
