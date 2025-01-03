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
            if (contentStr != null && contentType == "markdown") {
                val updateResult = session.updateFromMarkdown(contentStr)
                if (updateResult.isFailure) {
                    return ActionHandlerResult.Error(
                        updateResult.exceptionOrNull()?.message ?: "Failed to update markdown"
                    )
                }
            } else {
                return ActionHandlerResult.Error("Unsupported contentType or missing content")
            }

            val newMarkdown = session.getMarkdown()
            val newSvg = session.getSvg()

            return if (newMarkdown.isSuccess && newSvg.isSuccess) {
                ActionHandlerResult.Success(
                    mapOf(
                        "sessionId" to sessionId,
                        "markdown" to (newMarkdown.getOrNull() ?: ""),
                        "svg" to (newSvg.getOrNull() ?: "")
                    )
                )
            } else {
                ActionHandlerResult.Error("Failed to retrieve updated content")
            }
        } catch (e: Exception) {
            return ActionHandlerResult.Error(e.message ?: "Unknown error")
        }
    }
}