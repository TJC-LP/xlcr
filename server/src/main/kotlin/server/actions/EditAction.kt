package com.tjclp.xlcr.server.actions

import com.tjclp.xlcr.server.ExcelSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

object EditAction {
    suspend fun handle(args: Map<String, JsonElement>): ActionHandlerResult {
        val sessionId = args["sessionId"]?.jsonPrimitive?.content
            ?: return ActionHandlerResult.Error("sessionId not provided")

        val contentType = args["contentType"]?.jsonPrimitive?.content
        val contentStr = args["content"]?.jsonPrimitive?.content

        val session = ExcelSession.get(sessionId)
            ?: return ActionHandlerResult.Error("Invalid sessionId")

        try {
            return ActionHandlerResult.Success(
                mapOf(
                    "sessionId" to sessionId,
                    "markdown" to "TODO",
                    "svg" to "TODO"
                )
            )
        } catch (e: Exception) {
            return ActionHandlerResult.Error(e.message ?: "Unknown error")
        }
    }
}