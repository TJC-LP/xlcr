package com.tjclp.xlcr.server.routes

import com.tjclp.xlcr.cli.UnifiedTransforms
import com.tjclp.xlcr.output.DocumentInfo
import com.tjclp.xlcr.server.http.*
import com.tjclp.xlcr.server.json.*
import com.tjclp.xlcr.types.{Content, Mime}

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
      // Read raw body (don't trust Content-Type for /info — we detect it)
      body <- request.body.asChunk.mapError(err =>
        HttpError.badRequest(s"Failed to read request body: ${err.getMessage}")
      )
      _ <- ZIO.when(body.isEmpty)(
        ZIO.fail(HttpError.badRequest("Request body is empty"))
      )

      // Extract Tika metadata headers only (skips body text / OCR)
      // Also gives us detectedType via content inspection
      metadataRaw <- ZIO
        .attemptBlocking {
          DocumentInfo.extractMetadataOnly(body.toArray)
        }
        .mapError(err => HttpError.internalError(s"Metadata extraction failed: ${err.getMessage}"))

      // Use Tika-detected MIME as the authoritative type
      detectedMime = Mime.parse(
        metadataRaw.getOrElse("detectedType", "application/octet-stream").toString
      )
      content = Content.fromChunk(body, detectedMime)

      // Check split capability using detected type
      canSplit = UnifiedTransforms.canSplit(detectedMime)

      // Try to get fragment count if splittable (best effort)
      fragmentCount <- if canSplit then
        UnifiedTransforms
          .split(content)
          .map(frags => Some(frags.size))
          .catchAll(_ => ZIO.succeed(None))
      else
        ZIO.succeed(None)

      // Find available conversions using detected type
      availableConversions = findAvailableConversions(detectedMime)

      // Coerce metadata values to strings for JSON
      metadata = metadataRaw.map { case (k, v) =>
        k ->
          (v match
            case list: List[?] => list.mkString(", ")
            case other         => other.toString)
      }

      // Build response
      info = InfoResponse(
        mimeType = detectedMime.value,
        size = body.length.toLong,
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
