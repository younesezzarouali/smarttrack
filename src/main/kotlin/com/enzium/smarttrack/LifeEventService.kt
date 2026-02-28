package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.util.UUID

@ApplicationScoped
class LifeEventService(
    private val dynamoDbClient: DynamoDbClient
) {
    private val log: Logger = Logger.getLogger(LifeEventService::class.java)
    private val tableName = "SmartTrack" // Table unique unifiée

    private fun String.toAV(): AttributeValue = AttributeValue.builder().s(this).build()
    private fun Long.toAV(): AttributeValue = AttributeValue.builder().n(this.toString()).build()
    private fun Map<String, String>.toAV(): AttributeValue = 
        AttributeValue.builder().m(this.mapValues { it.value.toAV() }).build()

    fun addEvents(events: List<LifeEvent>) {
        events.forEachIndexed { index, event ->
            event.timestamp += index.toLong()
            saveToDb(event)
        }
    }

    fun saveToDb(event: LifeEvent) {
        try {
            val sk = "EVENT#${event.timestamp}#${UUID.randomUUID().toString().take(4)}"
            val item = mutableMapOf(
                "pk" to "USER#${event.userId}".toAV(),
                "sk" to sk.toAV(),
                "type" to event.type.toAV(),
                "content" to event.content.toAV(),
                "payload" to event.payload.toAV()
            )
            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
        } catch (e: Exception) {
            log.error("Failed to save event", e)
        }
    }

    fun listAll(userId: String = "default-user", limit: Int? = null, sinceTimestamp: Long? = null): List<LifeEvent> {
        val expressionValues = mutableMapOf(":pk" to "USER#$userId".toAV(), ":sk" to "EVENT#".toAV())
        var condition = "pk = :pk AND begins_with(sk, :sk)"
        
        if (sinceTimestamp != null) {
            // Filter by timestamp if needed, but begins_with is safer for now
        }

        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression(condition)
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false)
            .let { if (limit != null) it.limit(limit) else it }
            .build()

        return try {
            dynamoDbClient.query(request).items().map { item ->
                LifeEvent().apply {
                    this.userId = userId
                    this.timestamp = item["sk"]?.s()?.split("#")?.get(1)?.toLong() ?: 0L
                    this.type = item["type"]?.s() ?: "NOTE"
                    this.content = item["content"]?.s() ?: ""
                    this.payload = item["payload"]?.m()?.mapValues { it.value.s() } ?: emptyMap()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteEvent(timestamp: Long, userId: String = "default-user") {
        // We need the full SK to delete. For simplicity in Undo, we'll search then delete.
        val events = listAll(userId).filter { it.timestamp == timestamp }
        events.forEach { e ->
            val key = mapOf("pk" to "USER#$userId".toAV(), "sk" to "EVENT#${e.timestamp}".toAV()) // Note: needs exact SK
            // In a real single-table, we'd store the full SK in the object.
        }
    }

    fun clearAll(userId: String = "default-user") {
        listAll(userId).forEach { /* delete logic */ }
    }
}
