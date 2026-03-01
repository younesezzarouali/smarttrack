package com.enzium.smarttrack

import io.quarkus.runtime.annotations.RegisterForReflection
import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

@RegisterForReflection
class LifeEvent {
    var userId: String = "default-user"
    var timestamp: Long = 0L 
    var type: String = "NOTE" 
    var content: String = ""
    var fullDescription: String = ""
    var payload: Map<String, String> = mutableMapOf()
    
    // On ignore ce champ lors de l'envoi au Frontend (JSON)
    @JsonIgnore
    var embedding: List<Double>? = null
}
