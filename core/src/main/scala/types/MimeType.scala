package com.tjclp.xlcr
package types

sealed trait MimeTypeCategory

sealed trait ApplicationMimeType extends MimeTypeCategory

sealed trait TextMimeType extends MimeTypeCategory

sealed trait ImageMimeType extends MimeTypeCategory

sealed trait MessageMimeType extends MimeTypeCategory

enum MimeType(val mimeType: String):
  // Application types
  case ApplicationMsWord extends MimeType("application/msword") with ApplicationMimeType
  case ApplicationVndOpenXmlFormatsWordprocessingmlDocument extends MimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document") with ApplicationMimeType
  case ApplicationVndMsExcel extends MimeType("application/vnd.ms-excel") with ApplicationMimeType
  case ApplicationVndMsPowerpoint extends MimeType("application/vnd.ms-powerpoint") with ApplicationMimeType
  case ApplicationPdf extends MimeType("application/pdf") with ApplicationMimeType
  case ApplicationZip extends MimeType("application/zip") with ApplicationMimeType
  case ApplicationXml extends MimeType("application/xml") with ApplicationMimeType
  case ApplicationJson extends MimeType("application/json")
  case ApplicationVndOpenXmlFormatsSpreadsheetmlSheet extends MimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") with ApplicationMimeType

  // Text types
  case TextPlain extends MimeType("text/plain") with TextMimeType
  case TextHtml extends MimeType("text/html") with TextMimeType
  case TextMarkdown extends MimeType("text/markdown") with TextMimeType

  // Image types
  case ImageJpeg extends MimeType("image/jpeg") with ImageMimeType
  case ImagePng extends MimeType("image/png") with ImageMimeType
  case ImageGif extends MimeType("image/gif") with ImageMimeType
  case ImageBmp extends MimeType("image/bmp") with ImageMimeType
  case ImageSvgXml extends MimeType("image/svg+xml") with ImageMimeType

  // Message types
  case MessageRfc822 extends MimeType("message/rfc822") with MessageMimeType

  def category: MimeTypeCategory = this.asInstanceOf[MimeTypeCategory]


object MimeType:
  // Extensions can register additional MIME types beyond the core ones
  private var extensionValues: Seq[MimeType] = Seq.empty
  
  /**
   * Registers additional MIME types from extension modules.
   * This allows other modules to add their own MIME types while
   * maintaining compatibility with the core lookup system.
   *
   * @param extensions Sequence of additional MIME types to register
   */
  def registerExtension(extensions: Seq[MimeType]): Unit =
    extensionValues ++= extensions
  
  /**
   * Creates a MimeType from a string representation, throwing if not found.
   *
   * @param str The MIME type string
   * @return The corresponding MimeType instance
   * @throws IllegalArgumentException if the MIME type is not recognized
   */
  def unsafeFromString(str: String): MimeType =
    fromString(str).getOrElse(
      throw new IllegalArgumentException(s"Invalid mime type: $str")
    )

  /**
   * Attempts to find a MimeType matching the given string.
   *
   * @param str The MIME type string to look up
   * @return Option containing the matching MimeType, or None if not found
   */
  def fromString(str: String): Option[MimeType] = {
    val normalizedStr = normalize(str)
    // First check the core values
    MimeType.values.find(_.mimeType == normalizedStr) match {
      case Some(mime) => Some(mime)
      case None => 
        // Then check the extension values
        extensionValues.find(_.mimeType == normalizedStr)
    }
  }

  /**
   * Normalizes a MIME type string for consistent comparison.
   *
   * @param str The MIME type string to normalize
   * @return The normalized string
   */
  def normalize(str: String): String = str.trim.toLowerCase
  
  /**
   * Returns all registered MIME types (core + extensions).
   *
   * @return Sequence of all known MIME types
   */
  def allValues: Seq[MimeType] = MimeType.values.toSeq ++ extensionValues