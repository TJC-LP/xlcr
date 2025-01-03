package com.tjclp.xlcr.server.actions

import com.tjclp.xlcr.server.ExcelSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

object JsonAction {
    suspend fun handleGet(args: Map<String, JsonElement>): ActionHandlerResult {
        val sessionId = args["sessionId"]?.jsonPrimitive?.content
            ?: return ActionHandlerResult.Error("sessionId not provided")

        val session = ExcelSession.get(sessionId)
            ?: return ActionHandlerResult.Error("Invalid sessionId")

        return try {
            val jsonResult = session.getJson()
            if (jsonResult.isFailure) {
                ActionHandlerResult.Error(
                    jsonResult.exceptionOrNull()?.message ?: "Failed to retrieve JSON"
                )
            } else {
                ActionHandlerResult.Success(
                    mapOf(
                        "json" to (jsonResult.getOrNull() ?: "")
                    )
                )
            }
        } catch (e: Exception) {
            ActionHandlerResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun handlePatch(args: Map<String, JsonElement>): ActionHandlerResult {
        val sessionId = args["sessionId"]?.jsonPrimitive?.content
            ?: return ActionHandlerResult.Error("sessionId not provided")

        val jsonDiff = args["content"]?.jsonPrimitive?.content
            ?: return ActionHandlerResult.Error("No JSON diff provided")

        val session = ExcelSession.get(sessionId)
            ?: return ActionHandlerResult.Error("Invalid sessionId")

        return try {
            val patchResult = session.patchJson(jsonDiff)
            if (patchResult.isFailure) {
                return ActionHandlerResult.Error(
                    patchResult.exceptionOrNull()?.message ?: "Failed to patch JSON"
                )
            }

            val updatedJson = session.getJson()
            if (updatedJson.isFailure) {
                ActionHandlerResult.Error(
                    updatedJson.exceptionOrNull()?.message ?: "Failed to retrieve updated JSON"
                )
            } else {
                ActionHandlerResult.Success(
                    mapOf(
                        "json" to (updatedJson.getOrNull() ?: "")
                    )
                )
            }
        } catch (e: Exception) {
            ActionHandlerResult.Error(e.message ?: "Unknown error")
        }
    }
}