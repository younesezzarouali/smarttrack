package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest

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

            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(items).build())
        } catch (e: Exception) {
            log.error("Failed to save event", e)
            throw RuntimeException(e)
        }
    }

    fun listAll(userId: String = "default-user"): List<LifeEvent> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("userId = :v_userId")
            .expressionAttributeValues(mapOf(":v_userId" to userId.toAV()))
            .build()

        return try {
            dynamoDbClient.query(request).items().map { item ->
                LifeEvent().apply {
                    this.userId = item["userId"]?.s() ?: ""
                    this.timestamp = item["timestamp"]?.n()?.toLong() ?: 0L
                    this.type = item["type"]?.s() ?: "NOTE"
                    this.content = item["content"]?.s() ?: ""
                    this.payload = item["payload"]?.m()?.mapValues { it.value.s() } ?: emptyMap()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAll(userId: String = "default-user") {
        val events = listAll(userId)
        events.forEach { event ->
            val key = mapOf("userId" to userId.toAV(), "timestamp" to event.timestamp.toAV())
            dynamoDbClient.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build())
        }
    }
}
