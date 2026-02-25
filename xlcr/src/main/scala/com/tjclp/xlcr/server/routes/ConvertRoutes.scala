package com.tjclp.xlcr.server.routes

import zio._
import zio.http._

import com.tjclp.xlcr.cli.UnifiedTransforms
import com.tjclp.xlcr.server.http.{ HttpError, RequestHandler, ResponseBuilder }
import com.tjclp.xlcr.transform.TransformError

/**
 * Routes for document conversion.
 *
 * POST /convert?to=<mime>
 *
 * Converts the request body to the specified target MIME type.
 *
 * Query parameters:
 *   - to: Target MIME type (required). Accepts either extension ("pdf") or full MIME type
 *     ("application/pdf")
 *
 * Request:
 *   - Body: Raw document bytes
 *   - Content-Type: MIME type of input (auto-detected if not provided)
 *
 * Response:
 *   - 200: Converted document bytes with appropriate Content-Type
 *   - 400: Bad request (missing parameters, empty body)
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
      // Extract input content
      content <- RequestHandler.extractContent(request)

      // Parse target MIME type from query param
      targetMime <- RequestHandler.parseTargetMime(request)

      // Check if conversion is supported
      _ <- ZIO.unless(UnifiedTransforms.canConvert(content.mime, targetMime))(
        ZIO.fail(HttpError.unsupportedMediaType(
          s"Cannot convert ${content.mime.value} to ${targetMime.value}"
        ))
      )

      // Perform conversion
      result <- UnifiedTransforms
        .convert(content, targetMime)
        .mapError(HttpError.fromTransformError)
    yield ResponseBuilder.fromContent(result)
