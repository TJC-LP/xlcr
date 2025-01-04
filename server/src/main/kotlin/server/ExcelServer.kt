package com.tjclp.xlcr.server

import com.tjclp.xlcr.server.actions.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.server.MCP
import io.modelcontextprotocol.kotlin.sdk.server.SSEServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "--sse-server-ktor"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 3000

    when (command) {
        "--stdio" -> runMcpServerUsingStdio()
        "--sse-server-ktor" -> runSseMcpServerUsingKtorPlugin(port)
        "--sse-server" -> runSseMcpServerWithPlainConfiguration(port)
        else -> {
            System.err.println("Unknown command: $command")
        }
    }
}

private fun runMcpServerUsingStdio() = runBlocking {
    val server = ExcelServer.configureServer()
    val transport = StdioServerTransport()

    server.connect(transport)
    println("Server running on stdio")

    val done = Job()
    server.onCloseCallback = {
        done.complete()
    }
    done.join()
    println("Server closed")
}

private fun runSseMcpServerWithPlainConfiguration(port: Int) = runBlocking {
    val servers = ConcurrentHashMap<String, Server>()
    println("Starting SSE server on port $port")
    println("Use inspector to connect to http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(SSE)
        routing {
            sse("/sse") {
                val transport = SSEServerTransport("/mcpMessages", this)
                val server = ExcelServer.configureServer()

                servers[transport.sessionId] = server
                server.onCloseCallback = {
                    println("Server closed")
                    servers.remove(transport.sessionId)
                }

                server.connect(transport)
            }

            post("/mcpMessages") {
                println("Received Message")
                val sessionId = call.request.queryParameters["sessionId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sessionId")

                val transport = servers[sessionId]?.transport as? SSEServerTransport
                    ?: return@post call.respond(HttpStatusCode.NotFound, "Session not found")

                transport.handlePostMessage(call)
            }
        }
    }.start(wait = true)
}

private fun runSseMcpServerUsingKtorPlugin(port: Int) = runBlocking {
    println("Starting SSE server on port $port")
    println("Use inspector to connect to http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        MCP {
            return@MCP ExcelServer.configureServer()
        }
    }
}

object ExcelServer {
    private val logger = org.slf4j.LoggerFactory.getLogger(ExcelServer::class.java)

    fun configureServer(): Server {
        val prompts = io.modelcontextprotocol.kotlin.sdk.ServerCapabilities.Prompts(true)
        val resources = io.modelcontextprotocol.kotlin.sdk.ServerCapabilities.Resources(
            subscribe = true,
            listChanged = true
        )
        val tools = io.modelcontextprotocol.kotlin.sdk.ServerCapabilities.Tools(true)

        val emptyJsonObject = buildJsonObject { }
        val capabilities = io.modelcontextprotocol.kotlin.sdk.ServerCapabilities(
            emptyJsonObject,
            emptyJsonObject,
            emptyJsonObject,
            prompts,
            resources,
            tools
        )

        val server = Server(
            io.modelcontextprotocol.kotlin.sdk.Implementation("Excel Server", "1.0.0"),
            io.modelcontextprotocol.kotlin.sdk.server.ServerOptions(capabilities, false)
        ) {
            logger.info("Server closing")
        }

        server.addTool(
            "Excel Editor",
            "Edit Excel files with markdown, view as SVG, and perform file transformations",
            io.modelcontextprotocol.kotlin.sdk.Tool.Input(buildJsonObject { }, listOf("action"))
        ) { request ->
            when (request.arguments["action"]?.jsonPrimitive?.content) {
                "open" -> OpenAction.handle(request.arguments).toCallToolResult()
                "edit" -> EditAction.handle(request.arguments).toCallToolResult()
                "save" -> SaveAction.handle(request.arguments).toCallToolResult()
                "getJson" -> JsonAction.handleGet(request.arguments).toCallToolResult()
                "patchJson" -> JsonAction.handlePatch(request.arguments).toCallToolResult()
                "transform" -> TransformAction.handle(request.arguments).toCallToolResult()
                else -> error("Unexpected action ${request.arguments}")
            }
        }
        return server
    }
}