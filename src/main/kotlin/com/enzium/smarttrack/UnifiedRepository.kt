package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.util.UUID

@ApplicationScoped
class UnifiedRepository(private val dynamoDbClient: DynamoDbClient) {
    private val log: Logger = Logger.getLogger(UnifiedRepository::class.java)
    private val tableName = "SmartTrack"

    private fun String.toAV() = AttributeValue.builder().s(this).build()
    private fun Long.toAV() = AttributeValue.builder().n(this.toString()).build()
    private fun Double.toAV() = AttributeValue.builder().n(this.toString()).build()
    private fun Boolean.toAV() = AttributeValue.builder().bool(this).build()

    // --- EVENTS ---
    fun saveEvent(userId: String, event: LifeEvent) {
        val sk = "EVENT#${event.timestamp}#${UUID.randomUUID().toString().take(8)}"
        val item = mutableMapOf(
            "pk" to "USER#$userId".toAV(),
            "sk" to sk.toAV(),
            "type" to event.type.toAV(),
            "content" to event.content.toAV(),
            "payload" to AttributeValue.builder().m(event.payload.mapValues { it.value.toAV() }).build()
        )
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }

    // --- HABITS ---
    fun getActiveHabits(userId: String): List<Habit> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :pk AND begins_with(sk, :sk)")
            .expressionAttributeValues(mapOf(
                ":pk" to "USER#$userId".toAV(),
                ":sk" to "HABIT#".toAV()
            ))
            .build()
        
        return try {
            dynamoDbClient.query(request).items()
                .map { mapToHabit(it) }
                .filter { it.active } // Filter active only
        } catch (e: Exception) {
            log.error("Failed to query habits", e)
            emptyList()
        }
    }

    fun saveHabit(userId: String, habit: Habit) {
        val item = mutableMapOf(
            "pk" to "USER#$userId".toAV(),
            "sk" to "HABIT#${habit.id}".toAV(),
            "id" to habit.id.toAV(),
            "name" to habit.name.toAV(),
            "type" to habit.type.toAV(),
            "targetValue" to habit.targetValue.toAV(),
            "frequency" to habit.frequency.toAV(),
            "category" to habit.category.toAV(),
            "streak" to habit.streak.toDouble().toAV(),
            "active" to habit.active.toAV(),
            "createdAt" to habit.createdAt.toAV()
        )
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }

    fun archiveHabit(userId: String, habitId: String) {
        // Soft delete: set active = false
        val key = mapOf(
            "pk" to "USER#$userId".toAV(),
            "sk" to "HABIT#$habitId".toAV()
        )
        
        val updateRequest = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression("SET active = :active")
            .expressionAttributeValues(mapOf(":active" to false.toAV()))
            .build()

        try {
            dynamoDbClient.updateItem(updateRequest)
        } catch (e: Exception) {
            log.error("Failed to archive habit $habitId", e)
            throw RuntimeException("Archive failed", e)
        }
    }

    private fun mapToHabit(item: Map<String, AttributeValue>): Habit {
        return Habit(
            id = item["id"]?.s() ?: item["sk"]?.s()?.removePrefix("HABIT#") ?: "",
            name = item["name"]?.s() ?: "",
            type = item["type"]?.s() ?: "TIME",
            targetValue = item["targetValue"]?.n()?.toDouble() ?: 0.0,
            frequency = item["frequency"]?.s() ?: "DAILY",
            category = item["category"]?.s() ?: "LIFE",
            streak = item["streak"]?.n()?.toInt() ?: 0,
            active = item["active"]?.bool() ?: true,
            createdAt = item["createdAt"]?.n()?.toLong() ?: 0L
        )
    }
}
