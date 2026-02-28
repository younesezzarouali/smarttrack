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
    private val tableName = "SmartTrack"

    private fun String.toAV(): AttributeValue = AttributeValue.builder().s(this).build()
    private fun Long.toAV(): AttributeValue = AttributeValue.builder().n(this.toString()).build()
    private fun Map<String, String>.toAV(): AttributeValue = 
        AttributeValue.builder().m(this.mapValues { it.value.toAV() }).build()

    fun addEvents(events: List<LifeEvent>) {
        if (events.isEmpty()) return
        log.infof("Batch saving %d events...", events.size)
        events.forEachIndexed { index, event ->
            val baseTs = if (event.timestamp > 0) event.timestamp else System.currentTimeMillis()
            event.timestamp = baseTs + index.toLong()
            saveToDb(event)
        }
    }

    fun saveToDb(event: LifeEvent) {
        try {
            val userId = if (event.userId.isNullOrBlank()) "default-user" else event.userId
            // SK format: EVENT#timestamp#uuid
            val sk = "EVENT#${event.timestamp}#${UUID.randomUUID().toString().take(4)}"
            
            val item = mutableMapOf(
                "pk" to "USER#$userId".toAV(),
                "sk" to sk.toAV(),
                "type" to event.type.toAV(),
                "content" to event.content.toAV(),
                "payload" to event.payload.toAV()
            )

            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
            log.infof("Event saved: %s", event.content)
        } catch (e: Exception) {
            log.error("Failed to save event", e)
            throw RuntimeException("Persistence failed", e)
        }
    }

    fun listAll(userId: String = "default-user", limit: Int? = null, sinceTimestamp: Long? = null): List<LifeEvent> {
        val expressionValues = mutableMapOf<String, AttributeValue>()
        expressionValues[":pk"] = "USER#$userId".toAV()
        
        var condition = "pk = :pk"
        
        if (sinceTimestamp != null) {
            // Filter: starts with EVENT# but is greater than or equal to EVENT#timestamp
            condition += " AND sk >= :sk_start"
            expressionValues[":sk_start"] = "EVENT#$sinceTimestamp".toAV()
        } else {
            // Filter: just starts with EVENT#
            condition += " AND begins_with(sk, :sk_prefix)"
            expressionValues[":sk_prefix"] = "EVENT#".toAV()
        }

        val requestBuilder = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression(condition)
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false) // Most recent first

        if (limit != null) {
            requestBuilder.limit(limit)
        }

        return try {
            val response = dynamoDbClient.query(requestBuilder.build())
            response.items().map { mapToLifeEvent(it) }
        } catch (e: Exception) {
            log.error("Query failed in LifeEventService", e)
            emptyList()
        }
    }

    private fun mapToLifeEvent(item: Map<String, AttributeValue>) = LifeEvent().apply {
        userId = item["pk"]?.s()?.removePrefix("USER#") ?: "default-user"
        val sk = item["sk"]?.s() ?: ""
        val skParts = sk.split("#")
        timestamp = skParts.getOrNull(1)?.toLong() ?: 0L
        type = item["type"]?.s() ?: "NOTE"
        content = item["content"]?.s() ?: ""
        payload = item["payload"]?.m()?.mapValues { it.value.s() } ?: emptyMap()
    }

    fun deleteEvent(timestamp: Long, userId: String = "default-user") {
        try {
            // Find items with this timestamp prefix in SK
            val prefix = "EVENT#$timestamp#"
            val expressionValues = mapOf(
                ":pk" to "USER#$userId".toAV(),
                ":sk" to prefix.toAV()
            )
            val query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("pk = :pk AND begins_with(sk, :sk)")
                .expressionAttributeValues(expressionValues)
                .build()
            
            val results = dynamoDbClient.query(query).items()
            results.forEach { item ->
                val key = mapOf("pk" to item["pk"]!!, "sk" to item["sk"]!!)
                dynamoDbClient.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build())
            }
        } catch (e: Exception) {
            log.error("Delete failed", e)
        }
    }

    fun clearAll(userId: String = "default-user") {
        listAll(userId).forEach { deleteEvent(it.timestamp, userId) }
    }
}
