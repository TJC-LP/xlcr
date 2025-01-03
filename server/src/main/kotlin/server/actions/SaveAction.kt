package com.tjclp.xlcr.server.actions

import com.tjclp.xlcr.server.ExcelSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

object SaveAction {
    suspend fun handle(args: Map<String, JsonElement>): ActionHandlerResult {
        val sessionId = args["sessionId"]?.jsonPrimitive?.content
            ?: return ActionHandlerResult.Error("sessionId not provided")

        val session = ExcelSession.get(sessionId)
            ?: return ActionHandlerResult.Error("Invalid sessionId")

        return try {
            val saveResult = session.save()
            if (saveResult.isFailure) {
                ActionHandlerResult.Error(
                    saveResult.exceptionOrNull()?.message ?: "Failed to save"
                )
            } else {
                ActionHandlerResult.Success(
                    mapOf(
                        "sessionId" to sessionId,
                        "status" to "Saved successfully"
                    )
                )
            }
        } catch (e: Exception) {
            ActionHandlerResult.Error(e.message ?: "Unknown error")
        }
    }
}