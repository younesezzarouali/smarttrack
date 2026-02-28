package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ApplicationScoped
class HabitService(
    private val dynamoDbClient: DynamoDbClient,
    private val eventService: LifeEventService
) {
    private val log: Logger = Logger.getLogger(HabitService::class.java)
    private val habitsTable = "Habits"
    private val progressTable = "HabitProgress"

    private fun String.toAV(): AttributeValue = AttributeValue.builder().s(this).build()
    private fun Double.toAV(): AttributeValue = AttributeValue.builder().n(this.toString()).build()

    fun getHabits(userId: String = "default-user"): List<Habit> {
        val request = QueryRequest.builder()
            .tableName(habitsTable)
            .keyConditionExpression("userId = :v_userId")
            .expressionAttributeValues(mapOf(":v_userId" to userId.toAV()))
            .build()

        return try {
            dynamoDbClient.query(request).items().map { item ->
                Habit(
                    id = item["habitId"]?.s() ?: "",
                    userId = item["userId"]?.s() ?: "",
                    name = item["name"]?.s() ?: "",
                    type = item["type"]?.s() ?: "TIME",
                    targetValue = item["targetValue"]?.n()?.toDouble() ?: 0.0,
                    unit = item["unit"]?.s() ?: "",
                    category = item["category"]?.s() ?: "HEALTH",
                    streak = item["streak"]?.n()?.toInt() ?: 0,
                    lastCompletedDate = item["lastCompletedDate"]?.s() ?: "",
                    active = item["active"]?.bool() ?: true
                )
            }
        } catch (e: Exception) {
            log.error("Failed to fetch habits", e)
            emptyList()
        }
    }

    /**
     * Logic: Recalculates habits for today by scanning today's events.
     * High Performance: Only scans events from today.
     */
    fun syncDailyProgress(userId: String = "default-user") {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val habits = getHabits(userId).filter { it.active }
        if (habits.isEmpty()) return

        // 1. Get all events from today
        val startOfDay = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEvents = eventService.listAll(userId, sinceTimestamp = startOfDay)

        val progressMap = mutableMapOf<String, Double>()
        val newlyCompleted = mutableListOf<String>()

        habits.forEach { habit ->
            val totalValue = when (habit.type) {
                "TIME" -> todayEvents.filter { it.type == habit.category && it.payload.containsKey("duration_min") }
                                     .sumOf { it.payload["duration_min"]?.toDouble() ?: 0.0 }
                "COUNT" -> todayEvents.filter { it.type == habit.category && it.payload.containsKey("value") }
                                      .sumOf { it.payload["value"]?.toDouble() ?: 0.0 }
                else -> 0.0
            }
            
            progressMap[habit.id] = totalValue
            if (totalValue >= habit.targetValue) {
                newlyCompleted.add(habit.id)
                updateStreak(habit, today)
            }
        }

        // 2. Save snapshot
        saveProgress(userId, today, progressMap, newlyCompleted)
    }

    private fun updateStreak(habit: Habit, today: String) {
        if (habit.lastCompletedDate == today) return // Already updated today

        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE)
        if (habit.lastCompletedDate == yesterday) {
            habit.streak += 1
        } else {
            habit.streak = 1
        }
        habit.lastCompletedDate = today
        
        // Persist streak back to Habits table
        val item = mutableMapOf<String, AttributeValue>()
        item["userId"] = habit.userId.toAV()
        item["habitId"] = habit.id.toAV()
        item["name"] = habit.name.toAV()
        item["type"] = habit.type.toAV()
        item["targetValue"] = habit.targetValue.toAV()
        item["unit"] = habit.unit.toAV()
        item["category"] = habit.category.toAV()
        item["streak"] = habit.streak.toDouble().toAV()
        item["lastCompletedDate"] = habit.lastCompletedDate.toAV()
        item["active"] = AttributeValue.builder().bool(habit.active).build()

        dynamoDbClient.putItem(PutItemRequest.builder().tableName(habitsTable).item(item).build())
    }

    private fun saveProgress(userId: String, date: String, progress: Map<String, Double>, completed: List<String>) {
        val items = mutableMapOf<String, AttributeValue>()
        items["userId"] = userId.toAV()
        items["date"] = date.toAV()
        items["progressMap"] = AttributeValue.builder().m(progress.mapValues { it.value.toAV() }).build()
        items["completedIds"] = AttributeValue.builder().ss(completed.ifEmpty { listOf("NONE") }).build()

        dynamoDbClient.putItem(PutItemRequest.builder().tableName(progressTable).item(items).build())
    }

    fun getDailyProgress(userId: String = "default-user"): HabitProgress? {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val key = mapOf("userId" to userId.toAV(), "date" to today.toAV())
        
        return try {
            val response = dynamoDbClient.getItem(GetItemRequest.builder().tableName(progressTable).key(key).build())
            if (!response.hasItem()) return null
            
            val item = response.item()
            HabitProgress(
                userId = item["userId"]?.s() ?: "",
                date = item["date"]?.s() ?: "",
                progressMap = item["progressMap"]?.m()?.mapValues { it.value.n().toDouble() } ?: emptyMap(),
                completedIds = item["completedIds"]?.ss()?.filter { it != "NONE" } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bootstraps some default habits if table is empty
     */
    fun setupDefaultHabits(userId: String = "default-user") {
        val existing = getHabits(userId)
        if (existing.isNotEmpty()) return

        val defaults = listOf(
            Habit(id = "h1", name = "Daily Sport", type = "TIME", targetValue = 15.0, unit = "min", category = "HEALTH"),
            Habit(id = "h2", name = "Deep Work", type = "TIME", targetValue = 60.0, unit = "min", category = "WORK")
        )

        defaults.forEach { habit ->
            val item = mutableMapOf<String, AttributeValue>()
            item["userId"] = userId.toAV()
            item["habitId"] = habit.id.toAV()
            item["name"] = habit.name.toAV()
            item["type"] = habit.type.toAV()
            item["targetValue"] = habit.targetValue.toAV()
            item["unit"] = habit.unit.toAV()
            item["category"] = habit.category.toAV()
            item["streak"] = 0.0.toAV()
            item["lastCompletedDate"] = "".toAV()
            item["active"] = AttributeValue.builder().bool(true).build()
            dynamoDbClient.putItem(PutItemRequest.builder().tableName(habitsTable).item(item).build())
        }
    }
}
