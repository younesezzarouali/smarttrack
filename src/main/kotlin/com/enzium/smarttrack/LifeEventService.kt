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

    /**
     * OPTIMIZATION: Uses BatchWriteItem instead of individual PutItem calls.
     * Reduces the number of HTTP requests to AWS and improves performance.
     */
    fun addEvents(events: List<LifeEvent>) {
        if (events.isEmpty()) return

        try {
            val writeRequests = events.mapIndexed { index, event ->
                event.timestamp += index.toLong()
                val items = mapOf(
                    "userId" to event.userId.toAV(),
                    "timestamp" to event.timestamp.toAV(),
                    "type" to event.type.toAV(),
                    "content" to event.content.toAV(),
                    "payload" to event.payload.toAV()
                )
                WriteRequest.builder().putRequest(PutRequest.builder().item(items).build()).build()
            }

            // AWS Limit for BatchWriteItem is 25 items per request
            writeRequests.chunked(25).forEach { chunk ->
                val batchRequest = BatchWriteItemRequest.builder()
                    .requestItems(mapOf(tableName to chunk))
                    .build()
                dynamoDbClient.batchWriteItem(batchRequest)
            }
            log.debugf("Batch saved %d events", events.size)
        } catch (e: Exception) {
            log.error("Batch write failed", e)
            throw RuntimeException("Persistence error", e)
        }
    }

    fun updateEvent(event: LifeEvent) {
        try {
            val items = mapOf(
                "userId" to event.userId.toAV(),
                "timestamp" to event.timestamp.toAV(),
                "type" to event.type.toAV(),
                "content" to event.content.toAV(),
                "payload" to event.payload.toAV()
            )
            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(items).build())
        } catch (e: Exception) {
            log.error("Update failed", e)
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

    /**
     * OPTIMIZATION: Use Query with limit and proper index scanning.
     */
    fun listAll(userId: String = "default-user", limit: Int? = null, sinceTimestamp: Long? = null): List<LifeEvent> {
        val expressionValues = mutableMapOf(":v_userId" to userId.toAV())
        var condition = "userId = :v_userId"
        
        if (sinceTimestamp != null) {
            condition += " AND #ts >= :since"
            expressionValues[":since"] = sinceTimestamp.toAV()
        }

        val requestBuilder = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression(condition)
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false)

        if (sinceTimestamp != null) {
            requestBuilder.expressionAttributeNames(mapOf("#ts" to "timestamp"))
        }

        if (limit != null) {
            requestBuilder.limit(limit)
        }

        return try {
            dynamoDbClient.query(requestBuilder.build()).items().map { mapToLifeEvent(it) }
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
