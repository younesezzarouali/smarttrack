package com.enzium.smarttrack

import io.smallrye.common.annotation.Blocking
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@Path("/habits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class HabitResource(
    private val repository: UnifiedRepository,
    private val habitService: HabitService
) {

    @GET
    @Path("/active")
    @Blocking
    fun getActive(): List<Habit> {
        return repository.getActiveHabits("default-user")
    }

    @POST
    @Blocking
    fun create(request: HabitCreationIntent): Response {
        if (request.name.isBlank()) throw BadRequestException("Name required")

        val habit = Habit(
            id = UUID.randomUUID().toString(),
            userId = "default-user",
            name = request.name,
            type = request.type,
            targetValue = request.target,
            frequency = request.frequency,
            category = request.category,
            active = true,
            createdAt = System.currentTimeMillis()
        )

        repository.saveHabit("default-user", habit)
        habitService.syncDailyProgress()
        return Response.status(Response.Status.CREATED).entity(habit).build()
    }

    @DELETE
    @Path("/{id}")
    @Blocking
    fun delete(@PathParam("id") id: String): Response {
        repository.archiveHabit("default-user", id)
        habitService.syncDailyProgress()
        return Response.noContent().build()
    }

    @POST
    @Path("/{id}/archive")
    @Blocking
    fun archive(@PathParam("id") id: String): Response {
        repository.archiveHabit("default-user", id)
        return Response.noContent().build()
    }
}
