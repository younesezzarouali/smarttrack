package com.enzium.smarttrack

import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import java.time.Instant

@Path("/expenses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExpenseResource {

    @Inject
    lateinit var eventService: LifeEventService

    @GET
    fun all(): List<Map<String, Any>> {
        val events = eventService.listAll()
        return events.filter { it.type == "FINANCE" }.map { event ->
            mapOf(
                "label" to (event.payload["label"] ?: event.content),
                "amount" to (event.payload["amount"]?.toDouble() ?: 0.0),
                "category" to (event.payload["category"] ?: "OTHER"),
                "timestamp" to event.timestamp
            )
        }
    }

    @POST
    fun create(expense: Map<String, Any>): Response {
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
    fun summary(): Map<String, Any> {
        val events = eventService.listAll().filter { it.type == "FINANCE" }
        val allExpenses = events.map { 
            (it.payload["amount"]?.toDouble() ?: 0.0) 
        }
        val total = allExpenses.sum()
        
        val byCat = events.groupBy { it.payload["category"] ?: "OTHER" }
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
