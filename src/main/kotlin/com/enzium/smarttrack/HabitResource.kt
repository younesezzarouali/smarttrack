package com.enzium.smarttrack

import io.smallrye.common.annotation.Blocking
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@Path("/habits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class HabitResource(
    private val repository: UnifiedRepository,
    private val habitService: HabitService // Keeping legacy service for now for transition
) {

    @GET
    @Path("/active")
    @Blocking
    fun getActive(): List<Habit> {
        // Migration path: read from new repository
        return repository.getActiveHabits("default-user")
    }

    @POST
    @Blocking
    fun create(request: HabitCreationIntent): Response {
        if (request.name.isBlank()) {
            throw BadRequestException("Habit name is required")
        }

        val habit = Habit(
            id = UUID.randomUUID().toString(),
            name = request.name,
            type = request.type,
            targetValue = request.target,
            frequency = request.frequency,
            category = request.category,
            active = true,
            createdAt = System.currentTimeMillis()
        )

        repository.saveHabit("default-user", habit)
        
        // Also ensure legacy table is updated for compatibility during migration
        habitService.syncDailyProgress()

        return Response.status(Response.Status.CREATED).entity(habit).build()
    }

    @POST
    @Path("/{id}/archive")
    @Blocking
    fun archive(@PathParam("id") id: String): Response {
        repository.archiveHabit("default-user", id)
        return Response.noContent().build()
    }
}
