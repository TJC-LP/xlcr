package com.tjclp.xlcr.v2.server

import zio._
import zio.http._

import com.tjclp.xlcr.v2.server.routes.Routes

/**
 * XLCR HTTP Server main entry point.
 *
 * A stateless REST API for document conversion and splitting using the v2 transform system.
 *
 * Usage:
 * {{{
 * # Start the server
 * ./mill 'server[3.3.4].run'
 *
 * # With custom configuration
 * XLCR_PORT=9000 ./mill 'server[3.3.4].run'
 * }}}
 *
 * Endpoints:
 *   - POST /convert?to=<mime> - Convert document to target format
 *   - POST /split - Split document into fragments (ZIP)
 *   - POST /info - Get document metadata
 *   - GET /capabilities - List all supported operations
 *   - GET /health - Health check
 *
 * Example requests:
 * {{{
 * # Convert DOCX to PDF
 * curl -X POST "http://localhost:8080/convert?to=pdf" \
 *   -H "Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" \
 *   --data-binary @document.docx -o output.pdf
 *
 * # Split XLSX into sheets
 * curl -X POST "http://localhost:8080/split" \
 *   -H "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
 *   --data-binary @workbook.xlsx -o sheets.zip
 *
 * # Get document info
 * curl -X POST "http://localhost:8080/info" \
 *   -H "Content-Type: application/pdf" \
 *   --data-binary @document.pdf
 *
 * # List capabilities
 * curl http://localhost:8080/capabilities
 * }}}
 */
object Server extends ZIOAppDefault:

  override def run: ZIO[Any, Throwable, Nothing] =
    val config = ServerConfig.fromEnv

    val program =
      for
        _ <- ZIO.logInfo(s"Starting XLCR Server on ${config.host}:${config.port}")
        _ <- ZIO.logInfo("Endpoints:")
        _ <- ZIO.logInfo("  POST /convert?to=<mime>  - Convert document")
        _ <- ZIO.logInfo("  POST /split              - Split document (ZIP output)")
        _ <- ZIO.logInfo("  POST /info               - Get document info")
        _ <- ZIO.logInfo("  GET  /capabilities       - List capabilities")
        _ <- ZIO.logInfo("  GET  /health             - Health check")
        _ <- zio.http.Server.serve(Routes.all)
      yield ()

    program.forever.provide(
      ZLayer.succeed(
        zio.http.Server.Config.default
          .port(config.port)
          .enableRequestStreaming
      ),
      zio.http.Server.live
    )
