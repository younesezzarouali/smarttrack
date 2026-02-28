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
            dynamoDbClient.query(request).items().map { item ->
                Habit(
                    id = item["sk"]!!.s().removePrefix("HABIT#"),
                    name = item["name"]!!.s(),
                    type = item["type"]!!.s(),
                    targetValue = item["targetValue"]!!.n().toDouble(),
                    frequency = item["frequency"]?.s() ?: "DAILY",
                    category = item["category"]!!.s(),
                    streak = item["streak"]?.n()?.toInt() ?: 0,
                    active = item["active"]?.bool() ?: true
                )
            }
        } catch (e: Exception) {
            log.error("Failed to query habits", e)
            emptyList()
        }
    }

    fun saveHabit(userId: String, habit: Habit) {
        val item = mutableMapOf(
            "pk" to "USER#$userId".toAV(),
            "sk" to "HABIT#${habit.id}".toAV(),
            "name" to habit.name.toAV(),
            "type" to habit.type.toAV(),
            "targetValue" to habit.targetValue.toAV(),
            "frequency" to habit.frequency.toAV(),
            "category" to habit.category.toAV(),
            "streak" to habit.streak.toDouble().toAV(),
            "active" to AttributeValue.builder().bool(habit.active).build(),
            "createdAt" to habit.createdAt.toAV()
        )
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }
    
    // Add logic for Snapshots (SNAP#yyyyMMdd#habitId) similarly...
}
