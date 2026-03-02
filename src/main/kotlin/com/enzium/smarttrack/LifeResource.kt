package com.enzium.smarttrack

import io.smallrye.common.annotation.Blocking
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger
import java.util.*
import java.time.Instant
import com.enzium.smarttrack.services.DiagnosticService

@Path("/life")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class LifeResource(
    private val geminiService: GeminiService,
    private val eventService: LifeEventService,
    private val habitService: HabitService,
    private val diagnostic: DiagnosticService
) {
    private val log: Logger = Logger.getLogger(LifeResource::class.java)

    @POST
    @Path("/magic")
    @Blocking
    fun magic(input: Map<String, String>): Response {
        diagnostic.mark("t0_request_received")
        val text = input["text"] ?: throw BadRequestException("Input text required")
        log.infof("AI Interaction request (RAG enabled): %s", text)
        
        // 🧠 RAG : Recherche de souvenirs sémantiques
        val relevantMemories = eventService.findRelevantEvents(text)
        diagnostic.mark("t1_rag_fetched")
        
        // 🕰️ Historique chronologique récent
        val recentHistory = eventService.listAll(limit = 15)
        diagnostic.mark("t2_history_fetched")
        
        // 🔗 Fusion intelligente sans doublons
        val fullHistoryContext = (relevantMemories + recentHistory).distinctBy { it.timestamp }
        
        val activeHabits = habitService.getHabits()
        diagnostic.mark("t3_context_ready")
        
        // Délégation à l'IA
        val result = geminiService.interact(text, fullHistoryContext, activeHabits)
        diagnostic.mark("t4_gemini_complete")
        
        diagnostic.logSummary(mapOf(
            "contextSize" to fullHistoryContext.size,
            "habitCount" to activeHabits.size
        ))
        
        return Response.ok(result).build()
    }

    @POST
    @Path("/events/batch")
    @Blocking
    fun saveBatch(events: List<LifeEvent>): Response {
        log.infof("Committing batch of %d events.", events.size)
        eventService.addEvents(events)
        habitService.syncDailyProgress()
        return Response.status(Response.Status.CREATED).build()
    }

    @GET
    @Path("/briefing")
    @Blocking
    fun getBriefing(@QueryParam("timezoneOffset") offsetMinutes: Int?): Map<String, String> {
        val todayEvents = listEventsForToday(offsetMinutes)
        val summary = geminiService.generateBriefing(todayEvents)
        return mapOf("briefing" to summary)
    }

    @GET
    @Path("/events")
    @Blocking
    fun list(): List<LifeEvent> {
        return eventService.listAll(limit = 100).sortedByDescending { it.timestamp }
    }

    @DELETE
    @Path("/events/{timestamp}")
    @Blocking
    fun delete(@PathParam("timestamp") timestamp: Long): Response {
        eventService.deleteEvent(timestamp)
        habitService.syncDailyProgress()
        return Response.noContent().build()
    }

    @DELETE
    @Path("/events")
    @Blocking
    fun clear(): Response {
        eventService.clearAll()
        habitService.syncDailyProgress()
        return Response.noContent().build()
    }

    private fun listEventsForToday(offsetMinutes: Int?): List<LifeEvent> {
        val calendar = Calendar.getInstance()
        if (offsetMinutes != null) calendar.add(Calendar.MINUTE, -offsetMinutes)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        return eventService.listAll(sinceTimestamp = calendar.timeInMillis)
    }
}
