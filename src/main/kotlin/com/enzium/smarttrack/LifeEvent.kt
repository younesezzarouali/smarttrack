package com.enzium.smarttrack

import io.quarkus.runtime.annotations.RegisterForReflection
import java.time.Instant

@RegisterForReflection
class LifeEvent {
    var userId: String = "default-user"
    var timestamp: Long = 0L 
    var type: String = "NOTE" 
    var content: String = ""
    var payload: Map<String, String> = mutableMapOf()
}
