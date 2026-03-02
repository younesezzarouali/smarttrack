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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import com.enzium.smarttrack.exceptions.AiTimeoutException
import com.enzium.smarttrack.exceptions.AiRateLimitException

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

        val isAdviceRequest = userInput.contains("conseil", ignoreCase = true) || userInput.contains("faire quoi", ignoreCase = true)

        val historyContext = if (history.isEmpty()) "Aucun historique" 
            else history.take(30).joinToString("\n") { 
                val value = it.payload["amount"] ?: it.payload["duration_min"] ?: it.payload["value"] ?: ""
                "- [${it.type}] ${it.content}${if (value.isNotEmpty()) " ($value)" else ""}"
            }

        val habitsContext = if (habits.isEmpty()) "Aucune habitude configurée"
            else habits.joinToString("\n") { "- ID: ${it.id}, Name: ${it.name}, Category: ${it.category}" }

        val prompt = systemPromptTemplate
            .replace("{{HABITS}}", habitsContext)
            .replace("{{CONTEXT}}", historyContext)
            .replace("{{INPUT}}", userInput)
        
        log.infof("Prompt size: %d chars. Context: %d events.", prompt.length, history.size)

        val request = GeminiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(response_mime_type = "application/json")
        )

        return try {
            CompletableFuture.supplyAsync {
                executeWithRetry(2) {
                    geminiApi.generateContent(apiKey, apiVersion, chatModel, request)
                }
            }.get(15, TimeUnit.SECONDS).let { 
                parseInteractionResponse(it, habits)
            }
        } catch (e: Exception) {
            log.error("Gemini call failed or timed out: ${e.message}")
            if (isAdviceRequest) {
                log.info("Falling back to local advice logic")
                return GeminiInteractionResponse(
                    intent = "ANALYSE",
                    advice = getLocalAdvice(habits),
                    answer = "L'IA est indisponible, voici un conseil basé sur tes habitudes."
                )
            }
            val cause = e.cause
            if (cause is RuntimeException && cause.message?.contains("429") == true) {
                throw AiRateLimitException("Gemini rate limit exceeded")
            }
            if (e is AiTimeoutException || e is AiRateLimitException) throw e
            throw RuntimeException("Erreur de communication avec l'IA")
        }
    }

    private fun getLocalAdvice(habits: List<Habit>): AiAdvice {
        // Priorité 1 : Habitude à 0 de progrès
        // Pour cet exemple simple, on prend la première habitude active non complétée
        // (En prod, on croiserait avec HabitService.progress())
        val target = habits.firstOrNull { it.active } ?: return AiAdvice(priority_habit = "Repos", action = "Profite de ta journée")
        
        return AiAdvice(
            priority_habit = target.name,
            action = "Lance 5 min maintenant",
            why = "C'est l'habitude la plus urgente de ta liste.",
            cta_label = "Démarrer 5 min",
            cta_habit_id = target.id,
            cta_minutes = 5
        )
    }

    private fun <T> executeWithRetry(maxAttempts: Int, block: () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val errorMsg = e.message ?: ""
                log.warnf("Gemini Attempt %d failed: %s", attempt, errorMsg)
                
                if (errorMsg.contains("429") && attempt < maxAttempts) {
                    log.warn("Gemini Rate Limit hit. Retrying in 1s...")
                    TimeUnit.SECONDS.sleep(1)
                    continue
                }
                break
            }
        }
        throw RuntimeException(lastException?.message ?: "Unknown API Error", lastException)
    }

    private fun parseInteractionResponse(response: GeminiResponse, habits: List<Habit>): GeminiInteractionResponse {
        val jsonString = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw RuntimeException("Empty AI response")
        
        return try {
            val node = mapper.readTree(jsonString)
            val intent = node.get("intent")?.asText() ?: "CAPTURE"
            val dailyInsight = node.get("dailyInsight")?.asText()
            
            val adviceNode = node.get("advice")
            val advice = if (adviceNode != null && !adviceNode.isNull) {
                AiAdvice(
                    priority_habit = adviceNode.get("priority_habit")?.asText(),
                    action = adviceNode.get("action")?.asText(),
                    why = adviceNode.get("why")?.asText(),
                    cta_label = adviceNode.get("cta_label")?.asText(),
                    cta_habit_id = adviceNode.get("cta_habit_id")?.asText(),
                    cta_minutes = adviceNode.get("cta_minutes")?.asInt() ?: 5
                )
            } else null

            val habitUpdates = mutableListOf<HabitUpdateIntent>()
            node.get("habitUpdates")?.let { updatesNode ->
                if (updatesNode.isArray) {
                    updatesNode.forEach { updateNode ->
                        val hName = updateNode.get("habitName")?.asText()
                        val hId = updateNode.get("habitId")?.asText()
                        val resolvedId = hId ?: habits.find { it.name.contains(hName ?: "---", ignoreCase = true) }?.id
                        
                        habitUpdates.add(HabitUpdateIntent(
                            habitId = resolvedId,
                            habitName = hName,
                            progressDelta = updateNode.get("progressDelta")?.asDouble() ?: 0.0,
                            confidence = updateNode.get("confidence")?.asDouble() ?: 1.0
                        ))
                    }
                }
            }

            val events = mutableListOf<LifeEvent>()
            node.get("events")?.let { eventsNode ->
                if (eventsNode.isArray) {
                    eventsNode.forEach { eventNode ->
                        val evt = LifeEvent()
                        evt.userId = "default-user"
                        evt.type = eventNode.get("type")?.asText() ?: "NOTE"
                        evt.content = eventNode.get("content")?.asText() ?: ""
                        evt.fullDescription = eventNode.get("fullDescription")?.asText() ?: evt.content
                        
                        val pMap = mutableMapOf<String, String>()
                        eventNode.get("payload")?.fields()?.forEach { field ->
                            val valStr = field.value.asText()
                            if (valStr != "null") pMap[field.key] = valStr
                        }
                        evt.payload = pMap
                        events.add(evt)
                    }
                }
            }

            val finalIntent = if (events.isNotEmpty() || habitUpdates.isNotEmpty()) "CAPTURE" else intent
            log.infof("AI Analysis - Intent: %s (Raw: %s), Events: %d, Updates: %d", finalIntent, intent, events.size, habitUpdates.size)

            GeminiInteractionResponse(
                intent = finalIntent, 
                dailyInsight = dailyInsight, 
                answer = dailyInsight,
                advice = advice,
                events = events, 
                habitUpdates = habitUpdates
            )
        } catch (e: Exception) {
            log.error("Failed to parse AI JSON: ${e.message}")
            throw RuntimeException("Format de réponse AI invalide")
        }
    }

    fun generateBriefing(history: List<LifeEvent>): String {
        return "Calculated in interaction."
    }
}
