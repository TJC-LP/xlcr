package com.tjclp.xlcr.server.actions

import com.tjclp.xlcr.server.ExcelSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

object OpenAction {
    suspend fun handle(args: Map<String, JsonElement>): ActionHandlerResult {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return ActionHandlerResult.Error("Path not provided")

        return try {
            val sessionId = ExcelSession.create(java.nio.file.Paths.get(path))
            val session = ExcelSession.get(sessionId)
                ?: return ActionHandlerResult.Error("Failed to create session")

            ActionHandlerResult.Success(
                mapOf(
                    "sessionId" to sessionId,
                    "markdown" to "TODO",
                    "svg" to "TODO"
                )
            )
        } catch (e: Exception) {
            ActionHandlerResult.Error(e.message ?: "Unknown error")
        }
    }
}