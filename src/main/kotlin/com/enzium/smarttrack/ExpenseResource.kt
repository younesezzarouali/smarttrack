package com.enzium.smarttrack

import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType

@Path("/expenses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExpenseResource {
    @Inject
    lateinit var repo: ExpenseRepository

    @GET
    fun all(): List<Expense> = repo.listAll()

    @POST
    @Transactional
    fun create(expense: Expense): Response {
        repo.persist(expense)
        return Response.status(Response.Status.CREATED).entity(expense).build()
    }

    @GET
    @Path("/summary")
    fun summary(): Map<String, Any> {
        val all = repo.listAll()
        val total = all.sumOf { it.amount }
        val byCat = all.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
        return mapOf("totalByCategory" to byCat, "total" to total)
    }

    @OPTIONS
    @Path("{path:.*}")
    fun options(): Response {
        return Response.ok().build()
    }
}
