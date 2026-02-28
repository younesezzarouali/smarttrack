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
    var category: String = "HEALTH",
    var streak: Int = 0,
    var lastCompletedDate: String = "",
    var active: Boolean = true
)

@RegisterForReflection
data class HabitProgress(
    var userId: String = "default-user",
    var date: String = "", // Format: YYYY-MM-DD
    var progressMap: Map<String, Double> = mutableMapOf(), // habitId -> currentValue
    var completedIds: List<String> = emptyList()
)
