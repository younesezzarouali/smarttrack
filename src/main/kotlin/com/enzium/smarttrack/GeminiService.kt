package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference

@ApplicationScoped
class GeminiService(
    @ConfigProperty(name = "gemini.api.url") private val apiUrl: String,
    @ConfigProperty(name = "gemini.api.key") private val apiKey: String
) {
    private val log: Logger = Logger.getLogger(GeminiService::class.java)
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val client: HttpClient = HttpClient.newBuilder().build()

    private val systemPrompt = """
        Tu es l'intelligence centrale d'un Life OS. Ta mission est d'extraire des données structurées à partir du texte de l'utilisateur.
        Réponds UNIQUEMENT avec un tableau JSON d'objets respectant ce schéma :
        {
          "type": "FINANCE" | "HEALTH" | "WORK" | "HABIT" | "NOTE",
          "content": "Description courte et propre",
          "payload": {
            "amount": "String",
            "category": "String",
            "label": "String",
            "activity": "String",
            "value": "String",
            "unit": "String",
            "project": "String",
            "duration_min": "String"
          }
        }
        Si l'utilisateur dit "Payé 10€ pizza", type est FINANCE, amount est "10", category est "FOOD", label est "pizza".
    """.trimIndent()

    fun parseInput(userInput: String): List<LifeEvent> {
        if (apiKey == "NO_KEY") throw RuntimeException("Gemini API Key is missing")

        val combinedText = "$systemPrompt\n\nTexte utilisateur : $userInput"
        
        val requestBody = mapOf(
            "contents" to listOf(
                mapOf("role" to "user", "parts" to listOf(mapOf("text" to combinedText)))
            ),
            "generationConfig" to mapOf(
                "response_mime_type" to "application/json"
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$apiUrl?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            log.error("Gemini API Error: ${response.body()}")
            throw RuntimeException("AI processing failed")
        }

        val root = mapper.readTree(response.body())
        val jsonString = root.path("candidates")[0].path("content").path("parts")[0].path("text").asText()
        
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
