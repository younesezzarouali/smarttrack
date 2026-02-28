package com.enzium.smarttrack

import io.smallrye.common.annotation.Blocking
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger

@Path("/expenses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExpenseResource(
    private val eventService: LifeEventService
) {
    private val log: Logger = Logger.getLogger(ExpenseResource::class.java)

    @GET
    @Blocking
    fun all(): List<Map<String, Any>> {
        return eventService.listAll().filter { it.type == "FINANCE" }.map { event ->
            mapOf(
                "label" to (event.payload["label"] ?: event.content),
                "amount" to (event.payload["amount"]?.toDouble() ?: 0.0),
                "category" to (event.payload["category"] ?: "OTHER"),
                "timestamp" to event.timestamp
            )
        }
    }

    @POST
    @Blocking
    fun create(expense: Map<String, Any>): Response {
        log.info("Creating new expense event")
        val event = LifeEvent().apply {
            type = "FINANCE"
            content = expense["label"]?.toString() ?: ""
            payload = mapOf(
                "label" to (expense["label"]?.toString() ?: ""),
                "amount" to (expense["amount"]?.toString() ?: "0.0"),
                "category" to (expense["category"]?.toString() ?: "OTHER")
            )
        }
        eventService.addEvent(event)
        return Response.status(Response.Status.CREATED).entity(expense).build()
    }

    @GET
    @Path("/summary")
    @Blocking
    fun summary(): Map<String, Any> {
        val financeEvents = eventService.listAll().filter { it.type == "FINANCE" }
        
        val total = financeEvents.sumOf { it.payload["amount"]?.toDouble() ?: 0.0 }
        
        val byCat = financeEvents.groupBy { it.payload["category"] ?: "OTHER" }
            .mapValues { entry -> 
                entry.value.sumOf { it.payload["amount"]?.toDouble() ?: 0.0 } 
            }
            
        return mapOf("totalByCategory" to byCat, "total" to total)
    }

    @OPTIONS
    @Path("{path:.*}")
    fun options(): Response {
        return Response.ok().build()
    }
}
