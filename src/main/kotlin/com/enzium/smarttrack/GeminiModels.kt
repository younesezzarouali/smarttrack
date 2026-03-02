package com.enzium.smarttrack

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig
)

@RegisterForReflection
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@RegisterForReflection
data class Part(
    val text: String
)

@RegisterForReflection
data class GenerationConfig(
    val response_mime_type: String
)

@RegisterForReflection
data class GeminiEmbedRequest(
    val content: EmbeddingContent
)

@RegisterForReflection
data class EmbeddingContent(
    val parts: List<Part>
)

@RegisterForReflection
data class GeminiEmbedResponse(
    val embedding: EmbeddingValue
)

@RegisterForReflection
data class EmbeddingValue(
    val values: List<Double>
)

@RegisterForReflection
data class AiAdvice(
    val priority_habit: String? = null,
    val action: String? = null,
    val why: String? = null,
    val cta_label: String? = null,
    val cta_habit_id: String? = null,
    val cta_minutes: Int = 5
)

@RegisterForReflection
data class GeminiInteractionResponse(
    val intent: String,
    val dailyInsight: String? = null,
    val answer: String? = null,
    val advice: AiAdvice? = null,
    val events: List<LifeEvent> = emptyList(),
    val habitUpdates: List<HabitUpdateIntent> = emptyList(),
    val habitCreations: List<HabitCreationIntent> = emptyList()
)
