package com.tjclp.xlcr.server.http

import com.tjclp.xlcr.cli.Commands.Backend
import com.tjclp.xlcr.types.*

import zio.*
import zio.http.*

/**
 * Utilities for extracting content from HTTP requests.
 */
object RequestHandler:

  /**
   * Extract content from an HTTP request.
   *
   * Reads the request body and determines the MIME type using one of two strategies:
   *   - Default: Content-Type header (if present), Tika content detection (fallback when missing)
   *   - `?detect=tika`: Always use Tika content inspection, ignore Content-Type header
   *
   * @param request
   *   The HTTP request
   * @return
   *   Content with detected MIME type, or HttpError
   */
  def extractContent(request: Request): ZIO[Any, HttpError, Content[Mime]] =
    val forceDetect = getQueryParam(request, "detect").exists(_.equalsIgnoreCase("tika"))
    for
      body <- request.body.asChunk.mapError(err =>
        HttpError.badRequest(s"Failed to read request body: ${err.getMessage}")
      )
      _ <- ZIO.when(body.isEmpty)(
        ZIO.fail(HttpError.badRequest("Request body is empty"))
      )
      mime = if forceDetect then Mime.detectFromContent(body)
      else detectMime(request, body)
    yield Content.fromChunk(body, mime)

  /**
   * Detect MIME type from request headers or content.
   *
   * Priority:
   *   1. Content-Type header 2. Tika content-based detection
   */
  private def detectMime(request: Request, body: Chunk[Byte]): Mime =
    request.header(Header.ContentType) match
      case Some(ct) =>
        // Parse the Content-Type header
        val mediaType = ct.mediaType
        Mime.parse(s"${mediaType.mainType}/${mediaType.subType}")
      case None =>
        // Fall back to Tika detection
        Mime.detectFromContent(body)

  /**
   * Parse the optional backend query parameter.
   *
   * @param request
   *   The HTTP request
   * @return
   *   Some(Backend) if specified, None for auto-fallback, or HttpError for invalid values
   */
  def parseBackend(request: Request): ZIO[Any, HttpError, Option[Backend]] =
    getQueryParam(request, "backend") match
      case None        => ZIO.succeed(None)
      case Some(value) =>
        value.toLowerCase match
          case "aspose"      => ZIO.succeed(Some(Backend.Aspose))
          case "libreoffice" => ZIO.succeed(Some(Backend.LibreOffice))
          case "xlcr"        => ZIO.succeed(Some(Backend.Xlcr))
          case other         =>
            ZIO.fail(
              HttpError.badRequest(
                s"Unknown backend: $other (expected: aspose, libreoffice, xlcr)"
              )
            )

  /**
   * Parse the target MIME type from query parameter.
   *
   * @param request
   *   The HTTP request
   * @param paramName
   *   Query parameter name (default: "to")
   * @return
   *   Target MIME type or HttpError
   */
  def parseTargetMime(request: Request, paramName: String = "to"): ZIO[Any, HttpError, Mime] =
    getQueryParam(request, paramName) match
      case Some(target) =>
        // Support multiple formats:
        // 1. Full MIME type: "application/pdf"
        // 2. File extension: "pdf", "txt", "xlsx"
        // 3. Common aliases: "plain", "text", "html"
        val mime = if target.contains("/") then
          Mime.parse(target)
        else
          resolveShorthand(target)
        ZIO.succeed(mime)
      case None =>
        ZIO.fail(HttpError.badRequest(s"Missing required query parameter: $paramName"))

  /**
   * Resolve shorthand format names to MIME types.
   *
   * Supports common aliases that aren't file extensions.
   */
  private def resolveShorthand(name: String): Mime =
    name.toLowerCase match
      // Common aliases that aren't extensions
      case "plain" | "text" => Mime.plain
      case "xml"            => Mime.xml // Could be text/xml or application/xml
      case "json"           => Mime.json
      // Fall through to extension-based lookup
      case ext => Mime.fromExtension(ext)

  /**
   * Get optional query parameter.
   */
  def getQueryParam(request: Request, name: String): Option[String] =
    request.url.queryParams.queryParam(name)

  /**
   * Get required query parameter.
   */
  def requireQueryParam(request: Request, name: String): ZIO[Any, HttpError, String] =
    getQueryParam(request, name) match
      case Some(value) => ZIO.succeed(value)
      case None        => ZIO.fail(HttpError.badRequest(s"Missing required query parameter: $name"))
end RequestHandler
