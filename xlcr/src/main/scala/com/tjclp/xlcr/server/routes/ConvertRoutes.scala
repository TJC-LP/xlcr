package com.tjclp.xlcr.server.routes

import com.tjclp.xlcr.cli.UnifiedTransforms
import com.tjclp.xlcr.cli.Commands.Backend
import com.tjclp.xlcr.config.LibreOfficeConfig
import com.tjclp.xlcr.server.http.*
import com.tjclp.xlcr.transform.TransformError

import zio.*
import zio.http.*

/**
 * Routes for document conversion.
 *
 * POST /convert?to=<mime>[&backend=<backend>][&detect=tika]
 *
 * Converts the request body to the specified target MIME type.
 *
 * Query parameters:
 *   - to: Target MIME type (required). Accepts either extension ("pdf") or full MIME type
 *     ("application/pdf")
 *   - backend: Optional backend override ("aspose", "libreoffice", "xlcr"). Default: auto-fallback
 *   - detect: Set to "tika" to force Tika content detection, ignoring Content-Type header
 *
 * Request:
 *   - Body: Raw document bytes
 *   - Content-Type: MIME type of input (optional, auto-detected via Tika if missing)
 *
 * Response:
 *   - 200: Converted document bytes with appropriate Content-Type
 *   - 400: Bad request (missing parameters, empty body, invalid backend)
 *   - 415: Unsupported conversion
 *   - 500: Internal server error
 */
object ConvertRoutes:

  val routes: Routes[Any, Response] = Routes(
    Method.POST / "convert" -> handler { (request: Request) =>
      handleConvert(request).catchAll { error =>
        ZIO.succeed(ResponseBuilder.error(error))
      }
    }
  )

  private def handleConvert(request: Request): ZIO[Any, HttpError, Response] =
    for
      // Extract input content (handles ?detect=tika)
      content <- RequestHandler.extractContent(request)

      // Parse target MIME type from query param
      targetMime <- RequestHandler.parseTargetMime(request)

      // Parse optional backend override
      backend <- RequestHandler.parseBackend(request)

      // Explicit LibreOffice backend requires runtime availability
      _ <- ZIO.when(backend.contains(Backend.LibreOffice) && !LibreOfficeConfig.isAvailable())(
        ZIO.fail(HttpError(
          Status.ServiceUnavailable,
          "LibreOffice backend is not available",
          Some("Install LibreOffice or set LIBREOFFICE_HOME")
        ))
      )

      // Check if conversion is supported
      _ <- ZIO.unless(UnifiedTransforms.canConvert(content.mime, targetMime, backend))(
        ZIO.fail(HttpError.unsupportedMediaType(
          s"Cannot convert ${content.mime.value} to ${targetMime.value}" +
            backend.fold("")(b => s" with backend ${b.toString.toLowerCase}")
        ))
      )

      // Perform conversion
      result <- UnifiedTransforms
        .convert(content, targetMime, backend = backend)
        .mapError(HttpError.fromTransformError)
    yield ResponseBuilder.fromContent(result)
end ConvertRoutes
