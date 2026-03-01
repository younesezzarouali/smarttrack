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
    private fun List<Double>.toAV() = AttributeValue.builder().l(this.map { it.toAV() }).build()

    // --- EVENTS ---

    fun saveEvent(userId: String, event: LifeEvent) {
        try {
            val sk = "EVENT#${event.timestamp}#${UUID.randomUUID().toString().take(8)}"
            
            val item = mutableMapOf(
                "pk" to "USER#$userId".toAV(),
                "sk" to sk.toAV(),
                "type" to (event.type ?: "NOTE").toAV(),
                "content" to (event.content ?: "").toAV(),
                "fullDescription" to (event.fullDescription ?: event.content ?: "").toAV(),
                "payload" to AttributeValue.builder().m(event.payload.mapValues { it.value.toAV() }).build()
            )
            
            event.embedding?.let { item["embedding"] = it.toAV() }
            
            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
            log.infof("DB: Event successfully saved: %s", event.content)
        } catch (e: Exception) {
            log.error("DB: Failed to save event", e)
            throw e
        }
    }

    fun queryEvents(userId: String, limit: Int? = null, sinceTimestamp: Long? = null): List<LifeEvent> {
        val expressionValues = mutableMapOf<String, AttributeValue>()
        expressionValues[":pk"] = "USER#$userId".toAV()
        
        var condition = "pk = :pk"
        
        if (sinceTimestamp != null) {
            condition += " AND sk BETWEEN :sk_start AND :sk_end"
            expressionValues[":sk_start"] = "EVENT#$sinceTimestamp".toAV()
            expressionValues[":sk_end"] = "EVENT#z".toAV()
        } else {
            condition += " AND begins_with(sk, :sk_prefix)"
            expressionValues[":sk_prefix"] = "EVENT#".toAV()
        }

        val requestBuilder = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression(condition)
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false)

        if (limit != null) requestBuilder.limit(limit)

        return try {
            val response = dynamoDbClient.query(requestBuilder.build())
            response.items().mapNotNull { mapToLifeEvent(it) }
        } catch (e: Exception) {
            log.error("DB: Query failed", e)
            emptyList()
        }
    }

    fun deleteEvent(userId: String, timestamp: Long) {
        try {
            val prefix = "EVENT#$timestamp#"
            val query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("pk = :pk AND begins_with(sk, :sk)")
                .expressionAttributeValues(mapOf(
                    ":pk" to "USER#$userId".toAV(),
                    ":sk" to prefix.toAV()
                ))
                .build()
            
            dynamoDbClient.query(query).items().forEach { item ->
                val key = mapOf("pk" to item["pk"]!!, "sk" to item["sk"]!!)
                dynamoDbClient.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build())
            }
        } catch (e: Exception) {
            log.error("DB: Delete failed", e)
        }
    }

    private fun mapToLifeEvent(item: Map<String, AttributeValue>): LifeEvent? {
        val sk = item["sk"]?.s() ?: return null
        if (!sk.startsWith("EVENT#")) return null
        
        val timestamp = sk.split("#").getOrNull(1)?.toLongOrNull() ?: return null
        
        return LifeEvent().apply {
            this.userId = item["pk"]?.s()?.removePrefix("USER#") ?: "default-user"
            this.timestamp = timestamp
            this.type = item["type"]?.s() ?: "NOTE"
            this.content = item["content"]?.s() ?: ""
            this.fullDescription = item["fullDescription"]?.s() ?: this.content
            this.payload = item["payload"]?.m()?.mapValues { it.value.s() } ?: emptyMap()
            this.embedding = item["embedding"]?.l()?.mapNotNull { it.n().toDoubleOrNull() }
        }
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
                .filter { it.active } 
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
            "currentStreak" to habit.currentStreak.toDouble().toAV(),
            "longestStreak" to habit.longestStreak.toDouble().toAV(),
            "priority" to habit.priority.toDouble().toAV(),
            "active" to habit.active.toAV(),
            "createdAt" to habit.createdAt.toAV()
        )
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }

    fun archiveHabit(userId: String, habitId: String) {
        val key = mapOf("pk" to "USER#$userId".toAV(), "sk" to "HABIT#$habitId".toAV())
        val updateRequest = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression("SET active = :active")
            .expressionAttributeValues(mapOf(":active" to false.toAV()))
            .build()
        dynamoDbClient.updateItem(updateRequest)
    }

    private fun mapToHabit(item: Map<String, AttributeValue>): Habit {
        return Habit(
            id = item["id"]?.s() ?: item["sk"]?.s()?.removePrefix("HABIT#") ?: "",
            name = item["name"]?.s() ?: "",
            type = item["type"]?.s() ?: "TIME",
            targetValue = item["targetValue"]?.n()?.toDouble() ?: 0.0,
            frequency = item["frequency"]?.s() ?: "DAILY",
            category = item["category"]?.s() ?: "LIFE",
            currentStreak = item["currentStreak"]?.n()?.toInt() ?: item["streak"]?.n()?.toInt() ?: 0,
            longestStreak = item["longestStreak"]?.n()?.toInt() ?: 0,
            priority = item["priority"]?.n()?.toInt() ?: 2,
            active = item["active"]?.bool() ?: true,
            createdAt = item["createdAt"]?.n()?.toLong() ?: 0L
        )
    }
}
