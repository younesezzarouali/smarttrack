package com.enzium.smarttrack

import io.smallrye.common.annotation.Blocking
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger

@Path("/life")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class LifeResource(
    private val geminiService: GeminiService,
    private val eventService: LifeEventService
) {
    private val log: Logger = Logger.getLogger(LifeResource::class.java)

    @POST
    @Path("/parse")
    @Blocking
    fun parse(input: Map<String, String>): List<LifeEvent> {
        val text = input["text"] ?: throw BadRequestException("Input text is required")
        log.infof("Parsing text: %s", text)
        return geminiService.parseInput(text)
    }

    @POST
    @Path("/events/batch")
    @Blocking
    fun saveBatch(events: List<LifeEvent>): Response {
        log.infof("Saving batch of %d events", events.size)
        eventService.addEvents(events)
        return Response.status(Response.Status.CREATED).build()
    }

    @PUT
    @Path("/events")
    @Blocking
    fun update(event: LifeEvent): Response {
        eventService.updateEvent(event)
        return Response.ok(event).build()
    }

    @GET
    @Path("/events")
    @Blocking
    fun list(): List<LifeEvent> {
        return eventService.listAll()
    }

    @DELETE
    @Path("/events")
    @Blocking
    fun clear(): Response {
        eventService.clearAll()
        return Response.noContent().build()
    }
}
