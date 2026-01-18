package com.tjclp.xlcr.v2.types

import zio.Chunk

/**
 * Type-safe content wrapper for the v2 Transform algebra.
 *
 * Content associates binary data with a compile-time MIME type, enabling type-safe transformations
 * where the compiler can verify that a transform accepting `Content[Mime.Pdf]` cannot be called
 * with `Content[Mime.Html]`.
 *
 * @tparam M
 *   The MIME type of this content (a singleton literal type from Mime)
 * @param data
 *   The binary content as a ZIO Chunk
 * @param mime
 *   The runtime MIME type value (matches type parameter M)
 * @param metadata
 *   Optional metadata key-value pairs (e.g., filename, charset, page count)
 */
final case class Content[M <: Mime](
  data: Chunk[Byte],
  mime: M,
  metadata: Map[String, String] = Map.empty
):

  /** Get the size of the content in bytes */
  def size: Int = data.length

  /** Check if the content is empty */
  def isEmpty: Boolean = data.isEmpty

  /** Check if the content is non-empty */
  def nonEmpty: Boolean = data.nonEmpty

  /** Get a metadata value by key */
  def get(key: String): Option[String] = metadata.get(key)

  /** Add or update a metadata entry */
  def withMetadata(key: String, value: String): Content[M] =
    copy(metadata = metadata + (key -> value))

  /** Add multiple metadata entries */
  def withMetadata(entries: (String, String)*): Content[M] =
    copy(metadata = metadata ++ entries)

  /** Remove a metadata entry */
  def withoutMetadata(key: String): Content[M] =
    copy(metadata = metadata - key)

  /** Get the filename from metadata if present */
  def filename: Option[String] = metadata.get(Content.MetadataKeys.Filename)

  /** Get the charset from metadata if present */
  def charset: Option[String] = metadata.get(Content.MetadataKeys.Charset)

  /** Get the source path from metadata if present */
  def sourcePath: Option[String] = metadata.get(Content.MetadataKeys.SourcePath)

  /** Convert to a byte array (allocates new array) */
  def toArray: Array[Byte] = data.toArray

  /** Create a copy with different data but same MIME type and metadata */
  def withData(newData: Chunk[Byte]): Content[M] =
    copy(data = newData)

  /** Create a copy with different data from a byte array */
  def withData(newData: Array[Byte]): Content[M] =
    copy(data = Chunk.fromArray(newData))

object Content:

  /** Standard metadata keys */
  object MetadataKeys:
    val Filename   = "filename"
    val Charset    = "charset"
    val SourcePath = "source-path"
    val PageCount  = "page-count"
    val SheetCount = "sheet-count"
    val SlideCount = "slide-count"
    val Index      = "index"
    val Total      = "total"
    val Title      = "title"
    val Author     = "author"
    val CreatedAt  = "created-at"
    val ModifiedAt = "modified-at"

  /** Create Content from a byte array */
  def apply[M <: Mime](data: Array[Byte], mime: M): Content[M] =
    Content(Chunk.fromArray(data), mime)

  /** Create Content from a byte array with metadata */
  def apply[M <: Mime](
    data: Array[Byte],
    mime: M,
    metadata: Map[String, String]
  ): Content[M] =
    Content(Chunk.fromArray(data), mime, metadata)

  /** Create Content from a Chunk with metadata - explicit factory to help type inference */
  def fromChunk[M <: Mime](
    data: Chunk[Byte],
    mime: M,
    metadata: Map[String, String] = Map.empty
  ): Content[M] =
    new Content(data, mime, metadata)

  /** Create Content from a string (UTF-8 encoded) */
  def fromString[M <: Mime](text: String, mime: M): Content[M] =
    Content(Chunk.fromArray(text.getBytes("UTF-8")), mime)

  /** Create Content from a string with specified charset */
  def fromString[M <: Mime](
    text: String,
    mime: M,
    charset: String
  ): Content[M] =
    Content(
      Chunk.fromArray(text.getBytes(charset)),
      mime,
      Map(MetadataKeys.Charset -> charset)
    )

  /** Create empty Content of a specific MIME type */
  def empty[M <: Mime](mime: M): Content[M] =
    Content(Chunk.empty, mime)
