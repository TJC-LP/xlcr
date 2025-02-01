package com.tjclp.xlcr.server.actions

import com.tjclp.xlcr.Pipeline
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

object TransformAction {
    suspend fun handle(args: Map<String, JsonElement>): ActionHandlerResult {
        val inputPath = args["inputPath"]?.jsonPrimitive?.content
            ?: return ActionHandlerResult.Error("inputPath not provided")
        val outputPath = args["outputPath"]?.jsonPrimitive?.content
            ?: return ActionHandlerResult.Error("outputPath not provided")
        val diffMode = args["diffMode"]?.jsonPrimitive?.content?.toBoolean() ?: false

        return try {
            Pipeline.run(inputPath, outputPath, diffMode)
            ActionHandlerResult.Success(
                mapOf("message" to "Transformation successful", "outputPath" to outputPath)
            )
        } catch (e: Exception) {
            ActionHandlerResult.Error(e.message ?: "Unknown error during transformation")
        }
    }
}