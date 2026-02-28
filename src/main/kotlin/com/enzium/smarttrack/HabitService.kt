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
                    streak = item["streak"]?.n()?.toInt() ?: 0,
                    lastCompletedDate = item["lastCompletedDate"]?.s() ?: "",
                    active = item["active"]?.bool() ?: true
                )
            }.filter { it.active }
        } catch (e: Exception) {
            log.error("Failed to fetch habits", e)
            emptyList()
        }
    }

    fun syncDailyProgress(userId: String = "default-user") {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val habits = getHabits(userId)
        if (habits.isEmpty()) return

        // Search for all events in the last 24 hours to be safe with timezones
        val sinceTs = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli()
        val recentEvents = eventService.listAll(userId, sinceTimestamp = sinceTs)

        log.infof("Syncing %d habits with %d recent events", habits.size, recentEvents.size)

        val progressMap = mutableMapOf<String, Double>()
        val newlyCompleted = mutableListOf<String>()

        habits.forEach { habit ->
            val totalValue = when (habit.type) {
                "TIME" -> recentEvents.filter { 
                    (it.type.equals(habit.category, ignoreCase = true) || it.content.contains(habit.name, ignoreCase = true)) 
                    && it.payload.containsKey("duration_min") 
                }.sumOf { it.payload["duration_min"]?.toDouble() ?: 0.0 }
                
                "COUNT" -> recentEvents.filter { 
                    (it.type.equals(habit.category, ignoreCase = true) || it.content.contains(habit.name, ignoreCase = true))
                    && it.payload.containsKey("value") 
                }.sumOf { it.payload["value"]?.toDouble() ?: 0.0 }
                
                else -> 0.0
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
        if (habit.lastCompletedDate == yesterday) {
            habit.streak += 1
        } else {
            habit.streak = 1
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
        item["streak"] = habit.streak.toDouble().toAV()
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
                userId = userId,
                date = today,
                progressMap = item["progressMap"]?.m()?.mapValues { it.value.n().toDouble() } ?: emptyMap(),
                completedIds = item["completedIds"]?.ss()?.filter { it != "NONE" } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }
}
