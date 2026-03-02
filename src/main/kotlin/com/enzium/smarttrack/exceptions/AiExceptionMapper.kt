package com.enzium.smarttrack.exceptions

import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger
import java.net.SocketTimeoutException

data class AiErrorResponse(
    val code: String,
    val message: String,
    val retryAfterMs: Long? = null
)

class AiTimeoutException(message: String) : RuntimeException(message)
class AiRateLimitException(message: String, val retryAfterMs: Long = 2000) : RuntimeException(message)

@Provider
class AiExceptionMapper : ExceptionMapper<Exception> {
    private val log = Logger.getLogger(AiExceptionMapper::class.java)

    override fun toResponse(e: Exception): Response {
        val message = e.message ?: "Unknown error"
        
        return when {
            e is AiTimeoutException || e is SocketTimeoutException || message.contains("timeout", true) -> {
                log.error("AI_TIMEOUT: $message")
                Response.status(504).entity(AiErrorResponse("AI_TIMEOUT", "Gemini a mis trop de temps à répondre.", 2000)).build()
            }
            e is AiRateLimitException || message.contains("429") || message.contains("quota", true) -> {
                log.warn("AI_RATE_LIMIT: Quota exceeded")
                Response.status(429).entity(AiErrorResponse("AI_RATE_LIMIT", "Limite de requêtes atteinte. Réessayez dans 1 min.", 2000)).build()
            }
            message.contains("401") || message.contains("403") -> {
                log.error("AI_AUTH_ERROR: API Key issue")
                Response.status(401).entity(AiErrorResponse("AI_AUTH_ERROR", "Clé API invalide ou expirée.")).build()
            }
            else -> {
                log.error("AI_UPSTREAM_ERROR: $message", e)
                Response.status(502).entity(AiErrorResponse("AI_UPSTREAM_ERROR", message)).build()
            }
        }
    }
}
