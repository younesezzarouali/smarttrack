package com.enzium.smarttrack

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class Habit(
    var id: String = "",
    var userId: String = "default-user",
    var name: String = "",
    var type: String = "TIME", // TIME, COUNT, BOOLEAN
    var targetValue: Double = 0.0,
    var unit: String = "",
    var frequency: String = "DAILY",
    var category: String = "HEALTH",
    var streak: Int = 0,
    var lastCompletedDate: String = "",
    var active: Boolean = true,
    var createdAt: Long = System.currentTimeMillis()
)

@RegisterForReflection
data class HabitProgress(
    var userId: String = "default-user",
    var date: String = "", // Format: YYYY-MM-DD
    var progressMap: Map<String, Double> = mutableMapOf(), // habitId -> currentValue
    var completedIds: List<String> = emptyList()
)

@RegisterForReflection
data class HabitUpdateIntent(
    val habitId: String? = null,
    val habitName: String? = null,
    val progressDelta: Double = 0.0,
    val confidence: Double = 1.0
)

@RegisterForReflection
data class HabitCreationIntent(
    val name: String = "",
    val type: String = "TIME",
    val target: Double = 0.0,
    val category: String = "HEALTH",
    val frequency: String = "DAILY"
)

@RegisterForReflection
data class ConversationPreview(
    val previewId: String = "",
    val events: List<LifeEvent> = emptyList(),
    val habitUpdates: List<HabitUpdateIntent> = emptyList(),
    val habitCreations: List<HabitCreationIntent> = emptyList(),
    val dailyInsight: String? = null
)
