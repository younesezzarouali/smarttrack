package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.util.stream.Collectors

@ApplicationScoped
class LifeEventService(
    private val dynamoDbClient: DynamoDbClient
) {
    private val log: Logger = Logger.getLogger(LifeEventService::class.java)
    private val tableName = "LifeEvents"

    private fun String.toAV(): AttributeValue = AttributeValue.builder().s(this).build()
    private fun Long.toAV(): AttributeValue = AttributeValue.builder().n(this.toString()).build()
    private fun Map<String, String>.toAV(): AttributeValue = 
        AttributeValue.builder().m(this.mapValues { it.value.toAV() }).build()

    fun addEvents(events: List<LifeEvent>) {
        if (events.isEmpty()) return
        log.infof("Adding %d events...", events.size)
        events.forEachIndexed { index, event ->
            event.timestamp += index.toLong()
            updateEvent(event)
        }
    }

    fun updateEvent(event: LifeEvent) {
        try {
            val items = mutableMapOf<String, AttributeValue>()
            items["userId"] = event.userId.toAV()
            items["timestamp"] = event.timestamp.toAV()
            items["type"] = event.type.toAV()
            items["content"] = event.content.toAV()
            items["payload"] = event.payload.toAV()

            val request = PutItemRequest.builder()
                .tableName(tableName)
                .item(items)
                .build()

            dynamoDbClient.putItem(request)
            log.infof("Event saved successfully: %s", event.content)
        } catch (e: Exception) {
            log.error("Failed to save event", e)
            throw RuntimeException(e)
        }
    }

    fun deleteEvent(timestamp: Long, userId: String = "default-user") {
        try {
            val key = mapOf("userId" to userId.toAV(), "timestamp" to timestamp.toAV())
            dynamoDbClient.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build())
        } catch (e: Exception) {
            log.error("Delete failed", e)
        }
    }

    fun listAll(userId: String = "default-user", limit: Int? = null, sinceTimestamp: Long? = null): List<LifeEvent> {
        val expressionValues = mutableMapOf(":v_userId" to userId.toAV())
        var condition = "userId = :v_userId"
        
        val attributeNames = mutableMapOf<String, String>()
        if (sinceTimestamp != null) {
            condition += " AND #ts >= :since"
            expressionValues[":since"] = sinceTimestamp.toAV()
            attributeNames["#ts"] = "timestamp"
        }

        val requestBuilder = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression(condition)
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false)

        if (attributeNames.isNotEmpty()) {
            requestBuilder.expressionAttributeNames(attributeNames)
        }

        if (limit != null) {
            requestBuilder.limit(limit)
        }

        return try {
            val response = dynamoDbClient.query(requestBuilder.build())
            response.items().map { mapToLifeEvent(it) }
        } catch (e: Exception) {
            log.error("Query failed", e)
            emptyList()
        }
    }

    private fun mapToLifeEvent(item: Map<String, AttributeValue>) = LifeEvent().apply {
        userId = item["userId"]?.s() ?: ""
        timestamp = item["timestamp"]?.n()?.toLong() ?: 0L
        type = item["type"]?.s() ?: "NOTE"
        content = item["content"]?.s() ?: ""
        payload = item["payload"]?.m()?.mapValues { it.value.s() } ?: emptyMap()
    }

    fun clearAll(userId: String = "default-user") {
        listAll(userId).forEach { deleteEvent(it.timestamp, userId) }
    }
}
