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
        log.infof("AI Interaction request: %s", text)
        
        val history = eventService.listAll(limit = 20)
        val activeHabits = habitService.getHabits()
        
        val result = geminiService.interact(text, history, activeHabits)
        return Response.ok(result).build()
    }

    @POST
    @Path("/events/batch")
    @Blocking
    fun saveBatch(events: List<LifeEvent>): Response {
        log.infof("Committing batch of %d events. First event content: %s", events.size, events.firstOrNull()?.content)
        eventService.addEvents(events)
        habitService.syncDailyProgress()
        return Response.status(Response.Status.CREATED).build()
    }

    @GET
    @Path("/briefing")
    @Blocking
    fun getBriefing(@QueryParam("timezoneOffset") offsetMinutes: Int?): Map<String, String> {
        val now = Instant.now()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now.toEpochMilli()
        if (offsetMinutes != null) calendar.add(Calendar.MINUTE, -offsetMinutes)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        val startOfDay = calendar.timeInMillis
        val todayEvents = eventService.listAll(sinceTimestamp = startOfDay)
        
        val summary = geminiService.generateBriefing(todayEvents)
        return mapOf("briefing" to summary)
    }

    @PUT
    @Path("/events")
    @Blocking
    fun update(event: LifeEvent): Response {
        log.infof("Updating existing event: %s", event.content)
        eventService.saveToDb(event)
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
    @Path("/events/{timestamp}")
    @Blocking
    fun delete(@PathParam("timestamp") timestamp: Long): Response {
        log.infof("Deleting event at timestamp: %d", timestamp)
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
}
