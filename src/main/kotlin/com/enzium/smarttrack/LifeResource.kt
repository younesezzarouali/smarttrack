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
        val text = input["text"] ?: throw BadRequestException("Input text is required")
        val history = eventService.listAll(limit = 50)
        val result = geminiService.interact(text, history)
        return Response.ok(result).build()
    }

    @GET
    @Path("/briefing")
    @Blocking
    fun getBriefing(@QueryParam("timezoneOffset") offsetMinutes: Int?): Map<String, String> {
        val now = Instant.now()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now.toEpochMilli()
        if (offsetMinutes != null) {
            calendar.add(Calendar.MINUTE, -offsetMinutes)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis

        val todayEvents = eventService.listAll().filter { it.timestamp >= startOfDay }
        val summary = geminiService.generateBriefing(todayEvents)
        return mapOf("briefing" to summary)
    }

    @POST
    @Path("/events/batch")
    @Blocking
    fun saveBatch(events: List<LifeEvent>): Response {
        eventService.addEvents(events)
        // AUTO-SYNC HABITS
        habitService.syncDailyProgress()
        return Response.status(Response.Status.CREATED).build()
    }

    @PUT
    @Path("/events")
    @Blocking
    fun update(event: LifeEvent): Response {
        eventService.updateEvent(event)
        habitService.syncDailyProgress()
        return Response.ok(event).build()
    }

    @DELETE
    @Path("/events/{timestamp}")
    @Blocking
    fun delete(@PathParam("timestamp") timestamp: Long): Response {
        eventService.deleteEvent(timestamp)
        habitService.syncDailyProgress()
        return Response.noContent().build()
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
