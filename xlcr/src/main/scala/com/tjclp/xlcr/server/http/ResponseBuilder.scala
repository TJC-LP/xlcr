package com.tjclp.xlcr.server.http

import java.nio.charset.StandardCharsets

import zio._
import zio.http._

import com.tjclp.xlcr.output.ZipBuilder
import com.tjclp.xlcr.types.{ Content, DynamicFragment, Mime }
import com.tjclp.xlcr.server.json.{ Codecs, ErrorResponse }

/**
 * Utilities for building HTTP responses.
 */
object ResponseBuilder:

  /**
   * Build a binary response from Content.
   *
   * @param content
   *   The content to return
   * @return
   *   HTTP response with appropriate Content-Type
   */
  def fromContent(content: Content[Mime]): Response =
    Response(
      status = Status.Ok,
      headers = Headers(
        Header.ContentType(parseMediaType(content.mime)),
        Header.ContentLength(content.size.toLong)
      ),
      body = Body.fromChunk(content.data)
    )

  /**
   * Build a ZIP response from split fragments.
   *
   * Creates a ZIP archive containing all fragments with standardized naming:
   * {paddedIndex}__{sanitizedName}.{ext}
   *
   * Uses shared ZipBuilder utilities for consistent naming with CLI output.
   *
   * @param fragments
   *   The fragments to include in the ZIP
   * @param filename
   *   The filename for the ZIP (default: "split_output.zip")
   * @return
   *   HTTP response with ZIP content
   */
  def fromFragments(
    fragments: Chunk[DynamicFragment],
    filename: String = "split_output.zip"
  ): ZIO[Any, Nothing, Response] =
    ZIO.succeed {
      val zipBytes = Chunk.fromArray(ZipBuilder.buildZip(fragments))
      Response(
        status = Status.Ok,
        headers = Headers(
          Header.ContentType(MediaType.application.zip),
          Header.ContentLength(zipBytes.length.toLong),
          Header.ContentDisposition.attachment(filename)
        ),
        body = Body.fromChunk(zipBytes)
      )
    }

  /**
   * Build a JSON response.
   *
   * @param json
   *   The JSON string
   * @return
   *   HTTP response with application/json Content-Type
   */
  def json(json: String): Response =
    val jsonBytes = json.getBytes(StandardCharsets.UTF_8)
    Response(
      status = Status.Ok,
      headers = Headers(
        Header.ContentType(MediaType.application.json),
        Header.ContentLength(jsonBytes.length.toLong)
      ),
      body = Body.fromChunk(Chunk.fromArray(jsonBytes))
    )

  /**
   * Build an error response.
   *
   * @param error
   *   The HTTP error
   * @return
   *   HTTP response with error JSON body
   */
  def error(error: HttpError): Response =
    import zio.json._
    import Codecs.given
    val errorJson = ErrorResponse(
      error = error.message,
      details = error.details,
      status = error.status.code
    ).toJson
    Response(
      status = error.status,
      headers = Headers(
        Header.ContentType(MediaType.application.json)
      ),
      body = Body.fromString(errorJson)
    )

  /**
   * Build a health check response.
   */
  def health: Response =
    json("""{"status":"healthy"}""")

  // ============================================================================
  // Private helpers
  // ============================================================================

  /**
   * Parse a MIME string to ZIO HTTP MediaType.
   *
   * Falls back to application/octet-stream if parsing fails.
   */
  private def parseMediaType(mime: Mime): MediaType =
    MediaType.forContentType(mime.value).getOrElse(MediaType.application.`octet-stream`)
