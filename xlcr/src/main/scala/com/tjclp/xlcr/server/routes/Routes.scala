package com.tjclp.xlcr.server.routes

import com.tjclp.xlcr.server.http.ResponseBuilder
import com.tjclp.xlcr.server.json.*

import zio.*
import zio.http.*
import zio.json.*

/**
 * Composed routes for the XLCR HTTP server.
 *
 * Combines all route handlers:
 *   - POST /convert?to=<mime> - Convert document
 *   - POST /split - Split document into fragments (ZIP output)
 *   - POST /info - Get document metadata
 *   - GET /capabilities - List all supported operations
 *   - GET /health - Health check
 */
object Routes:

  import Codecs.given

  /**
   * Health check routes.
   */
  private val healthRoutes: zio.http.Routes[Any, Response] = zio.http.Routes(
    Method.GET / "health" -> handler { (_: Request) =>
      ZIO.succeed(ResponseBuilder.json(HealthResponse(status = "healthy").toJson))
    },

    // Root endpoint - return basic server info
    Method.GET / "" -> handler { (_: Request) =>
      ZIO.succeed(ResponseBuilder.json(
        """{
          |  "name": "XLCR Server",
          |  "version": "2.0.0",
          |  "endpoints": {
          |    "convert": "POST /convert?to=<mime>",
          |    "split": "POST /split",
          |    "info": "POST /info",
          |    "capabilities": "GET /capabilities",
          |    "health": "GET /health"
          |  }
          |}""".stripMargin
      ))
    }
  )

  /**
   * All routes for the server.
   */
  val all: zio.http.Routes[Any, Response] =
    healthRoutes ++ ConvertRoutes.routes ++ SplitRoutes.routes ++
      InfoRoutes
        .routes ++ CapabilitiesRoutes.routes
end Routes
