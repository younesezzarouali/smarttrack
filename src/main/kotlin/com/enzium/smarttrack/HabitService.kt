package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
class HabitService(
    private val dynamoDbClient: DynamoDbClient,
    private val eventService: LifeEventService
) {
    private val log: Logger = Logger.getLogger(HabitService::class.java)
    private val tableName = "SmartTrack"

    private fun String.toAV(): AttributeValue = AttributeValue.builder().s(this).build()
    private fun Double.toAV(): AttributeValue = AttributeValue.builder().n(this.toString()).build()

    fun getHabits(userId: String = "default-user"): List<Habit> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :pk AND begins_with(sk, :sk)")
            .expressionAttributeValues(mapOf(
                ":pk" to "USER#$userId".toAV(),
                ":sk" to "HABIT#".toAV()
            ))
            .build()

        return try {
            dynamoDbClient.query(request).items().map { item ->
                Habit(
                    id = item["sk"]?.s()?.removePrefix("HABIT#") ?: "",
                    userId = userId,
                    name = item["name"]?.s() ?: "",
                    type = item["type"]?.s() ?: "TIME",
                    targetValue = item["targetValue"]?.n()?.toDouble() ?: 0.0,
                    unit = item["unit"]?.s() ?: "min",
                    frequency = item["frequency"]?.s() ?: "DAILY",
                    category = item["category"]?.s() ?: "LIFE",
                    currentStreak = item["currentStreak"]?.n()?.toInt() ?: item["streak"]?.n()?.toInt() ?: 0,
                    longestStreak = item["longestStreak"]?.n()?.toInt() ?: 0,
                    priority = item["priority"]?.n()?.toInt() ?: 2,
                    lastCompletedDate = item["lastCompletedDate"]?.s() ?: "",
                    active = item["active"]?.bool() ?: true
                )
            }.filter { it.active }
        } catch (e: Exception) {
            log.error("Failed to fetch habits", e)
            emptyList()
        }
    }

    fun logHabitProgress(userId: String, habitId: String, delta: Double, source: String): Habit? {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val habits = getHabits(userId)
        val habit = habits.find { it.id == habitId } ?: return null

        log.infof("Logging progress for habit: %s, delta: %.1f, source: %s", habit.name, delta, source)

        // 1. Update Daily Progress Snapshot
        val currentProgress = getDailyProgress(userId) ?: HabitProgress(userId = userId, date = today)
        val newProgressMap = currentProgress.progressMap.toMutableMap()
        val currentVal = newProgressMap[habitId] ?: 0.0
        val newVal = currentVal + delta
        newProgressMap[habitId] = newVal

        val newCompletedIds = currentProgress.completedIds.toMutableList()
        if (newVal >= habit.targetValue && !newCompletedIds.contains(habitId)) {
            newCompletedIds.add(habitId)
            updateStreak(habit, today)
        }

        saveProgress(userId, today, newProgressMap, newCompletedIds)
        
        // Return updated habit
        return habits.find { it.id == habitId } 
    }

    fun syncDailyProgress(userId: String = "default-user") {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val habits = getHabits(userId)
        if (habits.isEmpty()) return

        val sinceTs = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli()
        val recentEvents = eventService.listAll(userId, sinceTimestamp = sinceTs)

        val progressMap = mutableMapOf<String, Double>()
        val newlyCompleted = mutableListOf<String>()

        habits.forEach { habit ->
            val matchingEvents = recentEvents.filter { event ->
                val linkedId = event.payload["linkedHabitId"]
                if (linkedId != null) linkedId == habit.id
                else event.content.contains(habit.name, ignoreCase = true) || habit.name.contains(event.content, ignoreCase = true)
            }.filter { it.payload.containsKey("duration_min") || it.payload.containsKey("value") }

            val totalValue = matchingEvents.sumOf { 
                it.payload["duration_min"]?.toDoubleOrNull() ?: it.payload["value"]?.toDoubleOrNull() ?: 0.0 
            }
            
            progressMap[habit.id] = totalValue
            
            if (totalValue >= habit.targetValue) {
                newlyCompleted.add(habit.id)
                updateStreak(habit, todayStr)
            }
        }
        saveProgress(userId, todayStr, progressMap, newlyCompleted)
    }

    private fun updateStreak(habit: Habit, today: String) {
        if (habit.lastCompletedDate == today) return
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE)
        
        habit.currentStreak = if (habit.lastCompletedDate == yesterday) habit.currentStreak + 1 else 1
        if (habit.currentStreak > habit.longestStreak) {
            habit.longestStreak = habit.currentStreak
        }
        habit.lastCompletedDate = today
        
        val item = mutableMapOf<String, AttributeValue>()
        item["pk"] = "USER#${habit.userId}".toAV()
        item["sk"] = "HABIT#${habit.id}".toAV()
        item["name"] = habit.name.toAV()
        item["type"] = habit.type.toAV()
        item["targetValue"] = habit.targetValue.toAV()
        item["unit"] = habit.unit.toAV()
        item["frequency"] = habit.frequency.toAV()
        item["category"] = habit.category.toAV()
        item["currentStreak"] = habit.currentStreak.toDouble().toAV()
        item["longestStreak"] = habit.longestStreak.toDouble().toAV()
        item["priority"] = habit.priority.toDouble().toAV()
        item["lastCompletedDate"] = habit.lastCompletedDate.toAV()
        item["active"] = AttributeValue.builder().bool(habit.active).build()
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }

    private fun saveProgress(userId: String, date: String, progress: Map<String, Double>, completed: List<String>) {
        val items = mutableMapOf<String, AttributeValue>()
        items["pk"] = "USER#$userId".toAV()
        items["sk"] = "SNAP#$date".toAV()
        items["progressMap"] = AttributeValue.builder().m(progress.mapValues { it.value.toAV() }).build()
        items["completedIds"] = AttributeValue.builder().ss(completed.ifEmpty { listOf("NONE") }).build()
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(items).build())
    }

    fun getDailyProgress(userId: String = "default-user"): HabitProgress? {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val key = mapOf("pk" to "USER#$userId".toAV(), "sk" to "SNAP#$today".toAV())
        return try {
            val response = dynamoDbClient.getItem(GetItemRequest.builder().tableName(tableName).key(key).build())
            if (!response.hasItem()) return null
            val item = response.item()
            HabitProgress(
                userId = userId, date = today,
                progressMap = item["progressMap"]?.m()?.mapValues { it.value.n().toDouble() } ?: emptyMap(),
                completedIds = item["completedIds"]?.ss()?.filter { it != "NONE" } ?: emptyList()
            )
        } catch (e: Exception) { null }
    }

    fun getWeeklySummary(userId: String = "default-user"): WeeklySummary {
        val habits = getHabits(userId)
        val today = LocalDate.now()
        val days = mutableListOf<DaySummary>()
        var totalCompleted = 0
        var totalPossible = 0
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ISO_DATE)
            val label = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val snapshot = getDailyProgressInternal(userId, dateStr)
            val completedCount = snapshot?.completedIds?.filter { it != "NONE" }?.size ?: 0
            val ratio = if (habits.isNotEmpty()) completedCount.toDouble() / habits.size else 0.0
            days.add(DaySummary(dateStr, label, ratio))
            totalCompleted += completedCount
            totalPossible += habits.size
        }
        return WeeklySummary(days, "$totalCompleted/$totalPossible")
    }

    private fun getDailyProgressInternal(userId: String, date: String): HabitProgress? {
        val key = mapOf("pk" to "USER#$userId".toAV(), "sk" to "SNAP#$date".toAV())
        val response = dynamoDbClient.getItem(GetItemRequest.builder().tableName(tableName).key(key).build())
        if (!response.hasItem()) return null
        val item = response.item()
        return HabitProgress(
            userId = userId, date = date,
            progressMap = item["progressMap"]?.m()?.mapValues { it.value.n().toDouble() } ?: emptyMap(),
            completedIds = item["completedIds"]?.ss()?.filter { it != "NONE" } ?: emptyList()
        )
    }
}
