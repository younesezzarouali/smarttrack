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
        
        // 1. Get Context (Recent History + Active Habits)
        val history = eventService.listAll(limit = 20)
        val activeHabits = habitService.getHabits()
        
        // 2. IA analysis with full context
        val result = geminiService.interact(text, history, activeHabits)
        
        return Response.ok(result).build()
    }

    @POST
    @Path("/events/batch")
    @Blocking
    fun saveBatch(events: List<LifeEvent>): Response {
        eventService.addEvents(events)
        habitService.syncDailyProgress()
        return Response.status(Response.Status.CREATED).build()
    }

    @GET
    @Path("/briefing")
    @Blocking
    fun getBriefing(@QueryParam("timezoneOffset") offsetMinutes: Int?): Map<String, String> {
        val calendar = Calendar.getInstance()
        if (offsetMinutes != null) calendar.add(Calendar.MINUTE, -offsetMinutes)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        val todayEvents = eventService.listAll(sinceTimestamp = calendar.timeInMillis)
        val summary = geminiService.generateBriefing(todayEvents)
        return mapOf("briefing" to summary)
    }

    @PUT
    @Path("/events")
    @Blocking
    fun update(event: LifeEvent): Response {
        eventService.updateEvent(event)
        habitService.syncDailyProgress()
        return Response.ok(event).build()
    }

    @GET
    @Path("/events")
    @Blocking
    fun list(): List<LifeEvent> {
        return eventService.listAll(limit = 100)
    }

    @DELETE
    @Path("/events")
    @Blocking
    fun clear(): Response {
        eventService.clearAll()
        habitService.syncDailyProgress()
        return Response.noContent().build()
    }
}
