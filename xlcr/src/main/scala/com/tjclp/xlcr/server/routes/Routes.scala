package com.tjclp.xlcr.server.routes

import com.tjclp.xlcr.config.LibreOfficeConfig
import com.tjclp.xlcr.server.http.ResponseBuilder
import com.tjclp.xlcr.server.json.*

import scala.util.Try

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
    Method.GET / "health" -> handler { (request: Request) =>
      val probeLibreOffice = request.url.queryParams
        .queryParam("check")
        .exists(_.equalsIgnoreCase("libreoffice"))

      ZIO
        .attemptBlocking {
          val available = LibreOfficeConfig.isAvailable()
          val cfg       = LibreOfficeConfig.currentConfig
          val ready     = if probeLibreOffice then
            available && Try {
              LibreOfficeConfig.getOfficeManager()
              true
            }.getOrElse(false)
          else
            true

          val loStatus =
            LibreOfficeStatus(
              available = available,
              running = LibreOfficeConfig.isRunning(),
              instances = cfg.instances,
              maxTasksPerProcess = cfg.maxTasksPerProcess,
              ready = if probeLibreOffice then Some(ready) else None
            )
          val healthStatus = if probeLibreOffice && !ready then "unhealthy" else "healthy"
          HealthResponse(status = healthStatus, libreoffice = Some(loStatus))
        }
        .catchAll { _ =>
          ZIO
            .attemptBlocking {
              val cfg = LibreOfficeConfig.currentConfig
              HealthResponse(
                status = if probeLibreOffice then "unhealthy" else "healthy",
                libreoffice = Some(LibreOfficeStatus(
                  available = LibreOfficeConfig.isAvailable(),
                  running = LibreOfficeConfig.isRunning(),
                  instances = cfg.instances,
                  maxTasksPerProcess = cfg.maxTasksPerProcess,
                  ready = if probeLibreOffice then Some(false) else None
                ))
              )
            }
            .orElseSucceed(HealthResponse(status =
              if probeLibreOffice then "unhealthy" else "healthy"
            ))
        }
        .map(resp => ResponseBuilder.json(resp.toJson))
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
