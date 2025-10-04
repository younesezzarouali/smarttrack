package com.enzium.smarttrack

import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/expenses")
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
}