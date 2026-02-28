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
        log.infof("Processing magic input: %s", text)

        return try {
            val events = geminiService.parseInput(text)
            eventService.addEvents(events)
            Response.ok(events).build()
        } catch (e: Exception) {
            log.error("Parsing or persistence failed", e)
            Response.status(500).entity(mapOf("error" to e.message)).build()
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
        eventService.clearAll()
        return Response.noContent().build()
    }
}
