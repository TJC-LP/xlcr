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

            val markdown = session.getMarkdown()
            val svg = session.getSvg()

            if (markdown.isSuccess && svg.isSuccess) {
                ActionHandlerResult.Success(
                    mapOf(
                        "sessionId" to sessionId,
                        "markdown" to (markdown.getOrNull() ?: ""),
                        "svg" to (svg.getOrNull() ?: "")
                    )
                )
            } else {
                ActionHandlerResult.Error("Failed to get content")
            }
        } catch (e: Exception) {
            ActionHandlerResult.Error(e.message ?: "Unknown error")
        }
    }
}