package com.enzium.smarttrack

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import java.time.Instant

@DynamoDbBean
class LifeEvent {
    @get:DynamoDbPartitionKey
    var userId: String = "default-user"
    
    @get:DynamoDbSortKey
    var timestamp: Long = Instant.now().toEpochMilli()
    
    var type: String = "NOTE" // FINANCE, HEALTH, WORK, HABIT, NOTE
    var content: String = ""
    var payload: Map<String, String> = mutableMapOf()
}
