package com.tjclp.xlcr.v2.server.routes

import zio._
import zio.http._
import zio.json._

import com.tjclp.xlcr.v2.cli.UnifiedTransforms
import com.tjclp.xlcr.v2.server.http.{ HttpError, RequestHandler, ResponseBuilder }
import com.tjclp.xlcr.v2.server.json.{ Codecs, InfoResponse }
import com.tjclp.xlcr.v2.types.Mime

/**
 * Routes for document information.
 *
 * POST /info
 *
 * Returns metadata and capabilities for the uploaded document.
 *
 * Request:
 *   - Body: Raw document bytes
 *   - Content-Type: MIME type of input (auto-detected if not provided)
 *
 * Response:
 *   - 200: JSON with document info { "mimeType": "application/pdf", "size": 1048576, "canSplit":
 *     true, "fragmentCount": 5, "availableConversions": ["text/html", "text/plain", "image/png"] }
 *   - 400: Bad request (empty body)
 *   - 500: Internal server error
 */
object InfoRoutes:

  import Codecs.given

  val routes: Routes[Any, Response] = Routes(
    Method.POST / "info" -> handler { (request: Request) =>
      handleInfo(request).catchAll { error =>
        ZIO.succeed(ResponseBuilder.error(error))
      }
    }
  )

  private def handleInfo(request: Request): ZIO[Any, HttpError, Response] =
    for
      // Extract input content
      content <- RequestHandler.extractContent(request)

      // Check split capability
      canSplit = UnifiedTransforms.canSplit(content.mime)

      // Try to get fragment count if splittable (best effort)
      fragmentCount <- if canSplit then
        UnifiedTransforms
          .split(content)
          .map(frags => Some(frags.size))
          .catchAll(_ => ZIO.succeed(None))
      else
        ZIO.succeed(None)

      // Find available conversions
      availableConversions = findAvailableConversions(content.mime)

      // Build response
      info = InfoResponse(
        mimeType = content.mime.value,
        size = content.size.toLong,
        canSplit = canSplit,
        fragmentCount = fragmentCount,
        availableConversions = availableConversions
      )
    yield ResponseBuilder.json(info.toJson)

  /**
   * Find all MIME types that this input can be converted to.
   */
  private def findAvailableConversions(inputMime: Mime): List[String] =
    // Check against common output types
    val commonOutputs = List(
      Mime.plain,
      Mime.html,
      Mime.xml,
      Mime.pdf,
      Mime.docx,
      Mime.xlsx,
      Mime.pptx,
      Mime.ods,
      Mime.odt,
      Mime.odp,
      Mime.png,
      Mime.jpeg,
      Mime.csv
    )

    commonOutputs
      .filter(output => UnifiedTransforms.canConvert(inputMime, output))
      .map(_.value)
