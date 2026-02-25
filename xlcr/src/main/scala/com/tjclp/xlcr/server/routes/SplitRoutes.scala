package com.tjclp.xlcr.server.routes

import zio._
import zio.http._

import com.tjclp.xlcr.cli.UnifiedTransforms
import com.tjclp.xlcr.server.http.{ HttpError, RequestHandler, ResponseBuilder }
import com.tjclp.xlcr.transform.TransformError

/**
 * Routes for document splitting.
 *
 * POST /split
 *
 * Splits the request body into fragments and returns them as a ZIP archive.
 *
 * Request:
 *   - Body: Raw document bytes
 *   - Content-Type: MIME type of input (auto-detected if not provided)
 *
 * Response:
 *   - 200: ZIP archive containing fragments
 *     - Content-Type: application/zip
 *     - Content-Disposition: attachment; filename="split_output.zip"
 *     - ZIP entries named: {index}_{name}.{ext}
 *   - 400: Bad request (empty body)
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
      // Extract input content
      content <- RequestHandler.extractContent(request)

      // Check if splitting is supported
      _ <- ZIO.unless(UnifiedTransforms.canSplit(content.mime))(
        ZIO.fail(HttpError.unsupportedMediaType(
          s"Cannot split ${content.mime.value}"
        ))
      )

      // Perform split
      fragments <- UnifiedTransforms
        .split(content)
        .mapError(HttpError.fromTransformError)

      // Build ZIP response
      response <- ResponseBuilder.fromFragments(fragments)
    yield response
