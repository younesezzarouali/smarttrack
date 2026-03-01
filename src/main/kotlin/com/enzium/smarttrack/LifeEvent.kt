package com.enzium.smarttrack

import io.quarkus.runtime.annotations.RegisterForReflection
import java.time.Instant

@RegisterForReflection
class LifeEvent {
    var userId: String = "default-user"
    var timestamp: Long = 0L 
    var type: String = "NOTE" 
    
    // Résumé concis pour l'affichage (ex: "Séance de sport intense")
    var content: String = ""
    
    // Description détaillée pour la mémoire IA (ex: "45min de musculation, focus jambes, ressenti de fatigue à la fin")
    var fullDescription: String = ""
    
    var payload: Map<String, String> = mutableMapOf()
    
    // --- Vector Memory (RAG) ---
    var embedding: List<Double>? = null
}
