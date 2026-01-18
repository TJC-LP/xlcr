package com.tjclp.xlcr.v2.types

/**
 * A Fragment represents a piece of a split document with a known output MIME type.
 *
 * Used by typed Splitters where the output MIME type is known at compile time (e.g., splitting XLSX
 * into multiple XLSX sheets).
 *
 * @tparam M
 *   The MIME type of this fragment
 * @param content
 *   The content of this fragment
 * @param index
 *   Zero-based index of this fragment in the source document
 * @param name
 *   Optional name for this fragment (e.g., sheet name, page number)
 */
final case class Fragment[M <: Mime](
  content: Content[M],
  index: Int,
  name: Option[String] = None
):
  /** Get the MIME type of this fragment */
  def mime: M = content.mime

  /** Get the fragment's data */
  def data: zio.Chunk[Byte] = content.data

  /** Convenience method to get index as 1-based (for display) */
  def displayIndex: Int = index + 1

  /** Get the name or a default based on index */
  def nameOrDefault(prefix: String = "part"): String =
    name.getOrElse(s"$prefix-${displayIndex}")

/**
 * A DynamicFragment represents a piece of a split document where the output MIME type is determined
 * at runtime.
 *
 * Used by DynamicSplitters where each fragment can have a different MIME type (e.g., extracting
 * attachments from an email or entries from a ZIP archive).
 *
 * @param content
 *   The content of this fragment (with runtime MIME type)
 * @param index
 *   Zero-based index of this fragment in the source document
 * @param name
 *   Optional name for this fragment (e.g., filename, attachment name)
 */
final case class DynamicFragment(
  content: Content[Mime],
  index: Int,
  name: Option[String] = None
):
  /** Get the MIME type of this fragment */
  def mime: Mime = content.mime

  /** Get the fragment's data */
  def data: zio.Chunk[Byte] = content.data

  /** Convenience method to get index as 1-based (for display) */
  def displayIndex: Int = index + 1

  /** Get the name or a default based on index */
  def nameOrDefault(prefix: String = "part"): String =
    name.getOrElse(s"$prefix-${displayIndex}")

  /**
   * Attempt to narrow this fragment to a specific MIME type. Returns Some if the content's MIME
   * type matches, None otherwise.
   */
  def narrow[M <: Mime](expectedMime: M): Option[Fragment[M]] =
    if content.mime == expectedMime then
      Some(Fragment(content.asInstanceOf[Content[M]], index, name))
    else None

object Fragment:
  /** Create a Fragment with auto-populated metadata */
  def apply[M <: Mime](
    data: zio.Chunk[Byte],
    mime: M,
    index: Int,
    name: Option[String],
    total: Option[Int]
  ): Fragment[M] =
    val metadata = Map.newBuilder[String, String]
    metadata += (Content.MetadataKeys.Index -> index.toString)
    name.foreach(n => metadata += (Content.MetadataKeys.Filename -> n))
    total.foreach(t => metadata += (Content.MetadataKeys.Total -> t.toString))
    Fragment(Content(data, mime, metadata.result()), index, name)

  /** Create a Fragment from a byte array */
  def fromArray[M <: Mime](
    data: Array[Byte],
    mime: M,
    index: Int,
    name: Option[String] = None
  ): Fragment[M] =
    Fragment(Content(data, mime), index, name)

object DynamicFragment:
  /** Create a DynamicFragment with auto-populated metadata */
  def apply(
    data: zio.Chunk[Byte],
    mime: Mime,
    index: Int,
    name: Option[String],
    total: Option[Int]
  ): DynamicFragment =
    val metadata = Map.newBuilder[String, String]
    metadata += (Content.MetadataKeys.Index -> index.toString)
    name.foreach(n => metadata += (Content.MetadataKeys.Filename -> n))
    total.foreach(t => metadata += (Content.MetadataKeys.Total -> t.toString))
    DynamicFragment(Content(data, mime, metadata.result()), index, name)

  /** Create a DynamicFragment from a byte array */
  def fromArray(
    data: Array[Byte],
    mime: Mime,
    index: Int,
    name: Option[String] = None
  ): DynamicFragment =
    DynamicFragment(Content(data, mime), index, name)

  /** Convert a typed Fragment to a DynamicFragment */
  def from[M <: Mime](fragment: Fragment[M]): DynamicFragment =
    DynamicFragment(
      fragment.content.asInstanceOf[Content[Mime]],
      fragment.index,
      fragment.name
    )
