package com.tjclp.xlcr.server.routes

import com.tjclp.xlcr.cli.UnifiedTransforms
import com.tjclp.xlcr.output.DocumentInfo
import com.tjclp.xlcr.server.http.*
import com.tjclp.xlcr.server.json.*
import com.tjclp.xlcr.types.Mime

import zio.*
import zio.http.*
import zio.json.*

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
      // Resolve MIME consistently with /convert and /split:
      // header hint by default, Tika only when missing/generic or ?detect=tika.
      content <- RequestHandler.extractContent(request)

      // Extract Tika metadata headers only (skips body text / OCR)
      // MIME hint follows the same semantics used by all request handlers.
      metadataRaw <- ZIO
        .attemptBlocking {
          DocumentInfo.extractMetadataOnly(content.data.toArray, Some(content.mime.value))
        }
        .mapError(err => HttpError.internalError(s"Metadata extraction failed: ${err.getMessage}"))

      // Check split capability using request MIME semantics
      canSplit = UnifiedTransforms.canSplit(content.mime)

      // Try to get fragment count if splittable (best effort)
      fragmentCount <- if canSplit then
        UnifiedTransforms
          .split(content)
          .map(frags => Some(frags.size))
          .catchAll(_ => ZIO.succeed(None))
      else
        ZIO.succeed(None)

      // Find available conversions using request MIME semantics
      availableConversions = findAvailableConversions(content.mime)

      // Coerce metadata values to strings for JSON
      metadata = metadataRaw.map { case (k, v) =>
        k ->
          (v match
            case list: List[?] => list.mkString(", ")
            case other         => other.toString)
      }

      // Build response
      info = InfoResponse(
        mimeType = content.mime.value,
        size = content.size.toLong,
        canSplit = canSplit,
        fragmentCount = fragmentCount,
        availableConversions = availableConversions,
        metadata = Some(metadata)
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
  end findAvailableConversions
end InfoRoutes
