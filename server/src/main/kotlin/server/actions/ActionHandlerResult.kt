package com.tjclp.xlcr.server.actions

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class ActionHandlerResult {
    data class Success(val data: Map<String, String>) : ActionHandlerResult()
    data class Error(val message: String) : ActionHandlerResult()

    fun toCallToolResult(): CallToolResult {
        return when (this) {
            is Success -> CallToolResult(
                emptyList(),
                false,
                buildJsonObject {
                    data.forEach { (key, value) ->
                        put(key, value)
                    }
                }
            )

            is Error -> CallToolResult(
                emptyList(),
                true,
                buildJsonObject { put("error", message) }
            )
        }
    }
}
