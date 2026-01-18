package com.tjclp.xlcr.v2.server.http

import java.io.ByteArrayOutputStream
import java.util.zip.{ ZipEntry, ZipOutputStream }

import zio._
import zio.http._

import com.tjclp.xlcr.v2.types.{ Content, DynamicFragment, Mime }
import com.tjclp.xlcr.v2.server.json.{ Codecs, ErrorResponse }

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
   * Creates a ZIP archive containing all fragments with predictable naming: {index}_{name}.{ext}
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
      val baos = new ByteArrayOutputStream()
      val zos  = new ZipOutputStream(baos)

      fragments.foreach { frag =>
        val ext  = extensionFor(frag.mime)
        val name = s"${frag.index}_${sanitizeName(frag.nameOrDefault("part"))}.$ext"
        zos.putNextEntry(new ZipEntry(name))
        zos.write(frag.data.toArray)
        zos.closeEntry()
      }
      zos.close()

      val zipBytes = Chunk.fromArray(baos.toByteArray)
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
    Response(
      status = Status.Ok,
      headers = Headers(
        Header.ContentType(MediaType.application.json),
        Header.ContentLength(json.length.toLong)
      ),
      body = Body.fromString(json)
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

  /**
   * Get file extension for a MIME type.
   *
   * Reverse lookup from MIME to common extension.
   */
  private def extensionFor(mime: Mime): String =
    val mimeStr = mime.mimeType
    mimeStr match
      // Text
      case "text/plain"                => "txt"
      case "text/html"                 => "html"
      case "text/markdown"             => "md"
      case "text/csv"                  => "csv"
      case "text/tab-separated-values" => "tsv"
      case "text/xml"                  => "xml"
      case "text/css"                  => "css"
      case "text/javascript"           => "js"
      // Application
      case "application/json"            => "json"
      case "application/xml"             => "xml"
      case "application/pdf"             => "pdf"
      case "application/zip"             => "zip"
      case "application/gzip"            => "gz"
      case "application/x-7z-compressed" => "7z"
      case "application/x-tar"           => "tar"
      case "application/rtf"             => "rtf"
      // MS Office legacy
      case "application/msword"            => "doc"
      case "application/vnd.ms-excel"      => "xls"
      case "application/vnd.ms-powerpoint" => "ppt"
      case "application/vnd.ms-outlook"    => "msg"
      // MS Office Open XML
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"   => "docx"
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         => "xlsx"
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation" => "pptx"
      // MS Office macro-enabled
      case "application/vnd.ms-excel.sheet.macroenabled.12"             => "xlsm"
      case "application/vnd.ms-excel.sheet.binary.macroenabled.12"      => "xlsb"
      case "application/vnd.ms-word.document.macroenabled.12"           => "docm"
      case "application/vnd.ms-powerpoint.presentation.macroenabled.12" => "pptm"
      // OpenDocument
      case "application/vnd.oasis.opendocument.text"         => "odt"
      case "application/vnd.oasis.opendocument.spreadsheet"  => "ods"
      case "application/vnd.oasis.opendocument.presentation" => "odp"
      // Images
      case "image/jpeg"    => "jpg"
      case "image/png"     => "png"
      case "image/gif"     => "gif"
      case "image/svg+xml" => "svg"
      case "image/tiff"    => "tiff"
      case "image/webp"    => "webp"
      // Email
      case "message/rfc822" => "eml"
      // Default
      case _ => "bin"

  /**
   * Sanitize a name for use in ZIP entry.
   *
   * Removes/replaces characters that are problematic in filenames.
   */
  private def sanitizeName(name: String): String =
    name
      .replaceAll("[/\\\\:*?\"<>|]", "_")
      .replaceAll("\\s+", "_")
      .take(100) // Limit length
