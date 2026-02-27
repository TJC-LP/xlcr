package com.tjclp.xlcr.output

import java.io.ByteArrayInputStream

import scala.util.Using

import com.tjclp.xlcr.types.Mime

import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.*
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.sax.WriteOutContentHandler

/**
 * Utilities for extracting document metadata using Apache Tika.
 *
 * Provides consistent document info extraction across CLI and Server.
 */
object DocumentInfo:

  /** Lazy Tika instance for MIME detection */
  private lazy val tika = new Tika()

  /** Lazy parser for metadata extraction */
  private lazy val parser = new AutoDetectParser()

  /**
   * Extract metadata from document content.
   *
   * @param content
   *   Document content as byte array
   * @param mimeType
   *   Optional MIME type hint
   * @param filenameHint
   *   Optional filename hint for better detection
   * @return
   *   Map of metadata keys to values (single values as strings, multiple values as lists)
   */
  def extractMetadata(
    content: Array[Byte],
    mimeType: Option[String] = None,
    filenameHint: Option[String] = None
  ): Map[String, Any] =
    if content.isEmpty then Map.empty
    else
      try
        val metadata = new Metadata()
        mimeType.foreach(mt => metadata.set("Content-Type", mt))
        filenameHint.foreach(name => metadata.set(HttpHeaders.CONTENT_LOCATION, name))

        val handler = new BodyContentHandler(-1)
        Using.resource(TikaInputStream.get(new ByteArrayInputStream(content))) { stream =>
          parser.parse(stream, handler, metadata)
        }

        // Convert metadata to map, handling multi-value fields
        val metadataMap = metadata.names().map { name =>
          val values = metadata.getValues(name)
          name -> (if values.length == 1 then values.head else values.toList)
        }.toMap

        // Add computed fields
        metadataMap ++ Map(
          "fileSize"     -> content.length,
          "detectedType" -> tika.detect(content)
        )
      catch
        case _: Throwable =>
          // Return minimal info on error
          Map(
            "fileSize"     -> content.length,
            "detectedType" -> tika.detect(content)
          )

  /**
   * Extract metadata from a ZIO Chunk.
   *
   * @param content
   *   Document content as ZIO Chunk
   * @param mimeType
   *   Optional MIME type hint
   * @param filenameHint
   *   Optional filename hint for better detection
   * @return
   *   Map of metadata keys to values
   */
  def extractMetadata(
    content: zio.Chunk[Byte],
    mimeType: Option[String],
    filenameHint: Option[String]
  ): Map[String, Any] =
    extractMetadata(content.toArray, mimeType, filenameHint)

  /**
   * Extract metadata from document content with a Mime type.
   *
   * @param content
   *   Document content as byte array
   * @param mime
   *   MIME type
   * @return
   *   Map of metadata keys to values
   */
  def extractMetadata(content: Array[Byte], mime: Mime): Map[String, Any] =
    extractMetadata(content, Some(mime.value), None)

  /**
   * Extract only document metadata headers without parsing the full document body.
   *
   * Uses a no-op SAX handler so Tika reads structural metadata (page count, author, dates,
   * permissions, etc.) but skips text extraction and OCR. Much faster than `extractMetadata` for
   * large or scanned documents.
   *
   * @param content
   *   Document content as byte array
   * @param mimeType
   *   Optional MIME type hint
   * @return
   *   Map of metadata keys to string values
   */
  def extractMetadataOnly(
    content: Array[Byte],
    mimeType: Option[String] = None
  ): Map[String, Any] =
    if content.isEmpty then Map.empty
    else
      val metadata = new Metadata()
      mimeType.foreach(mt => metadata.set("Content-Type", mt))

      // WriteOutContentHandler(0) triggers WriteLimitReachedException on first
      // body character.  Tika populates metadata from document headers before
      // any body/OCR processing, so we get all metadata for free.
      val handler = new BodyContentHandler(new WriteOutContentHandler(0))
      try
        Using.resource(TikaInputStream.get(new ByteArrayInputStream(content))) { stream =>
          parser.parse(stream, handler, metadata)
        }
      catch
        // Expected — the zero-limit handler throws as soon as body content starts
        case _: Throwable => ()

      val metadataMap = metadata.names().map { name =>
        val values = metadata.getValues(name)
        name -> (if values.length == 1 then values.head else values.toList)
      }.toMap

      metadataMap ++ Map(
        "fileSize"     -> content.length,
        "detectedType" -> tika.detect(content)
      )

  /**
   * Extract only metadata headers with a Mime type hint.
   */
  def extractMetadataOnly(content: Array[Byte], mime: Mime): Map[String, Any] =
    extractMetadataOnly(content, Some(mime.value))

  /**
   * Detect the MIME type of content.
   *
   * @param content
   *   Document content as byte array
   * @return
   *   Detected MIME type string
   */
  def detectMimeType(content: Array[Byte]): String =
    if content.isEmpty then "application/octet-stream"
    else tika.detect(content)

  /**
   * Detect the MIME type of content as a Mime.
   *
   * @param content
   *   Document content as byte array
   * @return
   *   Detected Mime type
   */
  def detectMime(content: Array[Byte]): Mime =
    Mime.parse(detectMimeType(content))

  /**
   * Extract key document info as a structured result.
   *
   * @param content
   *   Document content as byte array
   * @param mimeType
   *   Optional MIME type hint
   * @param filenameHint
   *   Optional filename hint
   * @return
   *   DocumentInfoResult with detected type, size, and metadata
   */
  def extractInfo(
    content: Array[Byte],
    mimeType: Option[String] = None,
    filenameHint: Option[String] = None
  ): DocumentInfoResult =
    val metadata     = extractMetadata(content, mimeType, filenameHint)
    val detectedType = metadata.getOrElse("detectedType", "application/octet-stream").toString
    val size         = content.length

    // Extract commonly useful metadata fields
    val title   = metadata.get("dc:title").orElse(metadata.get("title")).map(_.toString)
    val author  = metadata.get("dc:creator").orElse(metadata.get("Author")).map(_.toString)
    val created =
      metadata.get("dcterms:created").orElse(metadata.get("Creation-Date")).map(_.toString)
    val modified =
      metadata.get("dcterms:modified").orElse(metadata.get("Last-Modified")).map(_.toString)
    val pageCount = metadata.get("xmpTPg:NPages").orElse(metadata.get("Page-Count")).map(v =>
      v.toString.toIntOption.getOrElse(0)
    )
    val wordCount = metadata.get("meta:word-count").orElse(metadata.get("Word-Count")).map(v =>
      v.toString.toIntOption.getOrElse(0)
    )

    DocumentInfoResult(
      detectedType = detectedType,
      size = size,
      title = title,
      author = author,
      created = created,
      modified = modified,
      pageCount = pageCount,
      wordCount = wordCount,
      rawMetadata = metadata
    )
  end extractInfo
end DocumentInfo

/**
 * Structured result from document info extraction.
 */
case class DocumentInfoResult(
  detectedType: String,
  size: Int,
  title: Option[String] = None,
  author: Option[String] = None,
  created: Option[String] = None,
  modified: Option[String] = None,
  pageCount: Option[Int] = None,
  wordCount: Option[Int] = None,
  rawMetadata: Map[String, Any] = Map.empty
)
