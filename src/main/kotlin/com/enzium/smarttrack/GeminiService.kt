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
    val events: List<LifeEvent> = emptyList(),
    val habitUpdates: List<HabitUpdateIntent> = emptyList(),
    val habitCreations: List<HabitCreationIntent> = emptyList()
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

    fun interact(userInput: String, history: List<LifeEvent>, habits: List<Habit>): GeminiInteractionResponse {
        if (apiKey == "NO_KEY" || apiKey.isBlank()) throw RuntimeException("Gemini API Key missing")

        val habitsContext = if (habits.isEmpty()) "Aucune habitude configurée"
            else habits.joinToString("\n") { "- ID: ${it.id}, Name: ${it.name}, Category: ${it.category}, Target: ${it.targetValue} ${it.unit}" }

        val historyContext = if (history.isEmpty()) "Aucun historique" 
            else history.take(20).joinToString("\n") { "- [${it.type}] ${it.content}" }

        val prompt = systemPromptTemplate
            .replace("{{HABITS}}", habitsContext)
            .replace("{{CONTEXT}}", historyContext)
            .replace("{{INPUT}}", userInput)
        
        val request = GeminiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(response_mime_type = "application/json")
        )

        return executeWithRetry(3) {
            val response = geminiApi.generateContent(apiKey, request)
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
        
        log.infof("Raw AI JSON: %s", jsonString)

        return try {
            val node = mapper.readTree(jsonString)
            val intent = node.get("intent")?.asText() ?: "CAPTURE"
            val dailyInsight = node.get("dailyInsight")?.asText()
            
            val events = mutableListOf<LifeEvent>()
            node.get("events")?.let { eventsNode ->
                if (eventsNode.isArray) {
                    eventsNode.mapTo(events) { eventNode ->
                        LifeEvent().apply {
                            userId = "default-user"
                            type = eventNode.get("type")?.asText() ?: "NOTE"
                            content = eventNode.get("content")?.asText() ?: ""
                            payload = eventNode.get("payload")?.let { payloadNode ->
                                val payloadMap = mutableMapOf<String, String>()
                                payloadNode.fields().forEach { payloadMap[it.key] = it.value.asText() }
                                payloadMap
                            } ?: emptyMap()
                        }
                    }
                }
            }

            val habitUpdates = mutableListOf<HabitUpdateIntent>()
            node.get("habitUpdates")?.let { updatesNode ->
                if (updatesNode.isArray) {
                    updatesNode.mapTo(habitUpdates) { updateNode ->
                        HabitUpdateIntent(
                            habitId = updateNode.get("habitId")?.asText(),
                            habitName = updateNode.get("habitName")?.asText(),
                            progressDelta = updateNode.get("progressDelta")?.asDouble() ?: 0.0,
                            confidence = updateNode.get("confidence")?.asDouble() ?: 1.0
                        )
                    }
                }
            }

            // CRITICAL FALLBACK: If AI returned a habit update but NO event, create the event
            habitUpdates.forEach { update ->
                val alreadyHasEvent = events.any { it.content.contains(update.habitName ?: "", ignoreCase = true) }
                if (!alreadyHasEvent && update.progressDelta > 0) {
                    log.info("Synthesizing missing event for habit update: ${update.habitName}")
                    val habit = habits.find { it.name.equals(update.habitName, ignoreCase = true) || it.id == update.habitId }
                    events.add(LifeEvent().apply {
                        userId = "default-user"
                        type = habit?.category ?: "HEALTH"
                        content = update.habitName ?: "Activity"
                        payload = mapOf(
                            "duration_min" to update.progressDelta.toString(),
                            "sentiment" to "POSITIVE"
                        )
                    })
                }
            }

            GeminiInteractionResponse(intent, dailyInsight, events, habitUpdates)
        } catch (e: Exception) {
            log.error("Failed to parse AI JSON", e)
            throw RuntimeException("AI format error")
        }
    }

    fun generateBriefing(history: List<LifeEvent>): String {
        return "Insight calculated in interaction."
    }
}
