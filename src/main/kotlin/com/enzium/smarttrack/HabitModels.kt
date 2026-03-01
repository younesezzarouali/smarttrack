package com.enzium.smarttrack

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class Habit(
    var id: String = "",
    var userId: String = "default-user",
    var name: String = "",
    var type: String = "TIME", // TIME, COUNT, BOOLEAN
    var targetValue: Double = 0.0,
    var unit: String = "min",
    var frequency: String = "DAILY",
    var category: String = "HEALTH",
    var currentStreak: Int = 0,
    var longestStreak: Int = 0,
    var priority: Int = 2, // 1 (High), 2 (Medium), 3 (Low)
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
data class HabitLogRequest(
    val date: String = "",
    val delta: Double = 0.0,
    val unit: String = "min",
    val source: String = "manual"
)

@RegisterForReflection
data class DaySummary(
    val date: String,
    val label: String, // M, T, W...
    val ratio: Double // 0.0 to 1.0
)

@RegisterForReflection
data class WeeklySummary(
    val days: List<DaySummary>,
    val totalScore: String // e.g. "12/21"
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
    val frequency: String = "DAILY",
    val priority: Int = 2
)

@RegisterForReflection
data class ConversationPreview(
    val previewId: String = "",
    val events: List<LifeEvent> = emptyList(),
    val habitUpdates: List<HabitUpdateIntent> = emptyList(),
    val habitCreations: List<HabitCreationIntent> = emptyList(),
    val dailyInsight: String? = null
)
