package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

@ApplicationScoped
class LifeEventService(
    private val dynamoDbClient: DynamoDbClient
) {
    private val log: Logger = Logger.getLogger(LifeEventService::class.java)
    private val tableName = "LifeEvents"

    // Helper functions for cleaner mapping
    private fun String.toAV(): AttributeValue = AttributeValue.builder().s(this).build()
    private fun Long.toAV(): AttributeValue = AttributeValue.builder().n(this.toString()).build()
    private fun Map<String, String>.toAV(): AttributeValue = 
        AttributeValue.builder().m(this.mapValues { it.value.toAV() }).build()

    fun addEvent(event: LifeEvent) {
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
            log.infof("Event saved: %s for user %s", event.type, event.userId)
        } catch (e: Exception) {
            log.error("Failed to save event to DynamoDB", e)
            throw RuntimeException("Database unreachable", e)
        }
    }

    fun listAll(userId: String = "default-user"): List<LifeEvent> {
        return try {
            val request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("userId = :v_userId")
                .expressionAttributeValues(mapOf(":v_userId" to userId.toAV()))
                .build()

            val response = dynamoDbClient.query(request)
            
            response.items().map { item ->
                LifeEvent().apply {
                    this.userId = item["userId"]?.s() ?: ""
                    this.timestamp = item["timestamp"]?.n()?.toLong() ?: 0L
                    this.type = item["type"]?.s() ?: "NOTE"
                    this.content = item["content"]?.s() ?: ""
                    this.payload = item["payload"]?.m()?.mapValues { it.value.s() } ?: emptyMap()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to query events from DynamoDB", e)
            emptyList()
        }
    }
}
