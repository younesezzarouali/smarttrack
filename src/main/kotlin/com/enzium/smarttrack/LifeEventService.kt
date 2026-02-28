package com.enzium.smarttrack

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.stream.Collectors

@ApplicationScoped
class LifeEventService {

    @Inject
    lateinit var dynamoDbClient: DynamoDbClient

    private val enhancedClient: DynamoDbEnhancedClient by lazy {
        DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build()
    }

    private val eventTable: DynamoDbTable<LifeEvent> by lazy {
        enhancedClient.table("LifeEvents", TableSchema.fromBean(LifeEvent::class.java))
    }

    fun addEvent(event: LifeEvent) {
        eventTable.putItem(event)
    }

    fun listAll(userId: String = "default-user"): List<LifeEvent> {
        return eventTable.query { r -> r.queryConditional(
            software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
                .keyEqualTo { k -> k.partitionValue(userId) }
        ) }.items().stream().collect(Collectors.toList())
    }
}
