package com.enzium.smarttrack

import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "gemini-api")
interface GeminiApiClient {

    @POST
    @Path("/{version}/models/{model}:generateContent")
    fun generateContent(
        @QueryParam("key") apiKey: String,
        @PathParam("version") apiVersion: String,
        @PathParam("model") modelName: String,
        request: GeminiRequest
    ): GeminiResponse

    @POST
    @Path("/{version}/models/{model}:embedContent")
    fun embedContent(
        @QueryParam("key") apiKey: String,
        @PathParam("version") apiVersion: String,
        @PathParam("model") modelName: String,
        request: GeminiEmbedRequest
    ): GeminiEmbedResponse
}
