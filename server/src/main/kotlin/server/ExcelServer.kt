package com.tjclp.xlcr.server

import com.tjclp.xlcr.server.actions.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

object ExcelServer {
    private val logger = LoggerFactory.getLogger(ExcelServer::class.java)

    fun start(port: Int = 3000) {
        logger.info("Starting Excel MCP server on port $port")
        configureServer()
    }

    fun configureServer(): Server {
        val prompts = ServerCapabilities.Prompts(true)
        val resources = ServerCapabilities.Resources(true, true)
        val tools = ServerCapabilities.Tools(true)

        val emptyJsonObject = buildJsonObject {}
        val capabilities = ServerCapabilities(
            emptyJsonObject,
            emptyJsonObject,
            emptyJsonObject,
            prompts,
            resources,
            tools
        )

        val server = Server(
            Implementation("Excel Server", "0.1.0"),
            ServerOptions(capabilities, false)
        ) {
            logger.info("Server closing")
        }

        val inputSchema = Tool.Input(buildJsonObject {}, listOf("action"))

        server.addTool(
            "Excel Editor",
            "Edit Excel files with markdown and view as SVG",
            Tool.Input(buildJsonObject {}, listOf("action"))
        ) { request ->
            when (val action = request.arguments["action"]?.jsonPrimitive?.content) {
                "open" -> OpenAction.handle(request.arguments).toCallToolResult()
                "edit" -> EditAction.handle(request.arguments).toCallToolResult()
                "save" -> SaveAction.handle(request.arguments).toCallToolResult()
                "getJson" -> JsonAction.handleGet(request.arguments).toCallToolResult()
                "patchJson" -> JsonAction.handlePatch(request.arguments).toCallToolResult()
                else -> ActionHandlerResult.Error("Unknown action").toCallToolResult()
            }
        }
        return server
    }
}