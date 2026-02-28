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
        val text = input["text"] ?: return Response.status(400).entity("Missing text").build()
        log.infof("Magic input received: %s", text)

        try {
            val events = geminiService.parseInput(text)
            var offset = 0L
            events.forEach { event ->
                event.timestamp = event.timestamp + (offset++)
                eventService.addEvent(event)
            }
            return Response.ok(events).build()
        } catch (e: Exception) {
            log.error("Magic processing failed", e)
            return Response.status(500).entity(mapOf("error" to e.message)).build()
        }
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
        log.info("Request to clear all events")
        eventService.clearAll()
        return Response.noContent().build()
    }
}
