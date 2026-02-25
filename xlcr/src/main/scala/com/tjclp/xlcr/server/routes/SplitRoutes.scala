package com.tjclp.xlcr.server.routes

import com.tjclp.xlcr.cli.UnifiedTransforms
import com.tjclp.xlcr.server.http.*
import com.tjclp.xlcr.transform.TransformError

import zio.*
import zio.http.*

/**
 * Routes for document splitting.
 *
 * POST /split[?backend=<backend>][&detect=tika]
 *
 * Splits the request body into fragments and returns them as a ZIP archive.
 *
 * Query parameters:
 *   - backend: Optional backend override ("aspose", "libreoffice", "xlcr"). Default: auto-fallback
 *   - detect: Set to "tika" to force Tika content detection, ignoring Content-Type header
 *
 * Request:
 *   - Body: Raw document bytes
 *   - Content-Type: MIME type of input (optional, auto-detected via Tika if missing)
 *
 * Response:
 *   - 200: ZIP archive containing fragments
 *     - Content-Type: application/zip
 *     - Content-Disposition: attachment; filename="split_output.zip"
 *     - ZIP entries named: {index}_{name}.{ext}
 *   - 400: Bad request (empty body, invalid backend)
 *   - 415: Unsupported split operation
 *   - 422: Split failed (unprocessable content)
 *   - 500: Internal server error
 */
object SplitRoutes:

  val routes: Routes[Any, Response] = Routes(
    Method.POST / "split" -> handler { (request: Request) =>
      handleSplit(request).catchAll { error =>
        ZIO.succeed(ResponseBuilder.error(error))
      }
    }
  )

  private def handleSplit(request: Request): ZIO[Any, HttpError, Response] =
    for
      // Extract input content (handles ?detect=tika)
      content <- RequestHandler.extractContent(request)

      // Parse optional backend override
      backend <- RequestHandler.parseBackend(request)

      // Check if splitting is supported
      _ <- ZIO.unless(UnifiedTransforms.canSplit(content.mime, backend))(
        ZIO.fail(HttpError.unsupportedMediaType(
          s"Cannot split ${content.mime.value}" +
            backend.fold("")(b => s" with backend ${b.toString.toLowerCase}")
        ))
      )

      // Perform split
      fragments <- UnifiedTransforms
        .split(content, backend = backend)
        .mapError(HttpError.fromTransformError)

      // Build ZIP response
      response <- ResponseBuilder.fromFragments(fragments)
    yield response
end SplitRoutes
