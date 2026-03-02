package com.enzium.smarttrack

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class GeminiResponse(
    val candidates: List<Candidate>
)

@RegisterForReflection
data class Candidate(
    val content: Content
)
