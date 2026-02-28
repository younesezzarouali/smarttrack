package com.enzium.smarttrack

import io.smallrye.common.annotation.Blocking
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType

@Path("/life/habits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class HabitResource(
    private val habitService: HabitService
) {

    @GET
    @Blocking
    fun getHabits(): Map<String, Any> {
        habitService.setupDefaultHabits() // Auto-init
        val habits = habitService.getHabits()
        val progress = habitService.getDailyProgress()
        return mapOf("habits" to habits, "progress" to (progress ?: mapOf<String, Any>()))
    }

    @POST
    @Path("/sync")
    @Blocking
    fun sync(): Response {
        habitService.syncDailyProgress()
        return Response.ok().build()
    }
}
