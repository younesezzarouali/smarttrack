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
    @Path("/magic")
    @Blocking
    fun magic(input: Map<String, String>): Response {
        val text = input["text"] ?: throw BadRequestException("Input text is required")
        
        // 1. Fetch only the 50 most recent events to limit token usage and costs
        val history = eventService.listAll(limit = 50)
        
        // 2. Interact with AI
        val result = geminiService.interact(text, history)
        
        // 3. Return the result (Capture or Answer)
        return Response.ok(result).build()
    }

    @POST
    @Path("/events/batch")
    @Blocking
    fun saveBatch(events: List<LifeEvent>): Response {
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
