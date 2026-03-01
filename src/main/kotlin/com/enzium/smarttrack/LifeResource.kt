package com.enzium.smarttrack

import io.smallrye.common.annotation.Blocking
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger
import java.util.*
import java.time.Instant

@Path("/life")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class LifeResource(
    private val geminiService: GeminiService,
    private val eventService: LifeEventService,
    private val habitService: HabitService
) {
    private val log: Logger = Logger.getLogger(LifeResource::class.java)

    @POST
    @Path("/magic")
    @Blocking
    fun magic(input: Map<String, String>): Response {
        val text = input["text"] ?: throw BadRequestException("Input text required")
        log.infof("AI Interaction request (RAG enabled): %s", text)
        
        // 🧠 RAG : Recherche de souvenirs sémantiques
        val relevantMemories = eventService.findRelevantEvents(text)
        
        // 🕰️ Historique chronologique récent
        val recentHistory = eventService.listAll(limit = 15)
        
        // 🔗 Fusion intelligente sans doublons
        val fullHistoryContext = (relevantMemories + recentHistory).distinctBy { it.timestamp }
        
        val activeHabits = habitService.getHabits()
        
        // Délégation à l'IA
        val result = geminiService.interact(text, fullHistoryContext, activeHabits)
        return Response.ok(result).build()
    }

    @POST
    @Path("/events/batch")
    @Blocking
    fun saveBatch(events: List<LifeEvent>): Response {
        log.infof("Committing batch of %d events.", events.size)
        // Le service gère maintenant le calcul des embeddings automatiquement
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
        return eventService.listAll(limit = 100)
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
