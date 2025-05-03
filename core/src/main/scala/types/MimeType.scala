package com.tjclp.xlcr
package types

import scala.util.matching.Regex

/** Enhanced MimeType representation using sealed trait for better type safety and pattern matching.
  */
sealed trait MimeType extends Serializable {
  def baseType: String
  def subType: String
  def parameters: Map[String, String]

  /** The basic MIME type without parameters (e.g., "text/plain")
    */
  def mimeType: String = s"$baseType/$subType"

  /** Get a specific parameter value
    */
  def getParameter(name: String): Option[String] =
    parameters.get(name.toLowerCase)

  /** Get the charset parameter, if present
    */
  def charset: Option[String] = getParameter("charset")

  /** Get the delimiter parameter, if present
    */
  def delimiter: Option[String] = getParameter("delimiter")

  /** Create a new MimeType with an additional parameter
    */
  def withParameter(name: String, value: String): MimeType =
    MimeType.Custom(baseType, subType, parameters + (name.toLowerCase -> value))

  /** Create a new MimeType with a specific charset
    */
  def withCharset(charset: String): MimeType = withParameter("charset", charset)

  /** Create a new MimeType with a specific delimiter
    */
  def withDelimiter(delimiter: String): MimeType =
    withParameter("delimiter", delimiter)

  /** The full MIME type string including parameters
    */
  override def toString: String = {
    if (parameters.isEmpty) mimeType
    else {
      val params = parameters.map { case (k, v) => s"$k=$v" }.mkString("; ")
      s"$mimeType; $params"
    }
  }

  /** Checks if this MIME type matches another, ignoring parameters
    */
  def matches(other: MimeType): Boolean =
    baseType == other.baseType && subType == other.subType

  /** Checks if this MIME type is of a specific base type
    */
  def isType(baseType: String): Boolean =
    this.baseType.equalsIgnoreCase(baseType)

  /** Checks if this MIME type is text-based
    */
  def isText: Boolean = isType("text")

  /** Checks if this MIME type is application-based
    */
  def isApplication: Boolean = isType("application")

  /** Checks if this MIME type is image-based
    */
  def isImage: Boolean = isType("image")

  /** Checks if this MIME type is message-based
    */
  def isMessage: Boolean = isType("message")
}

object MimeType extends Serializable {

  // Regular expression for parsing MIME type strings
  private val MimeTypeRegex: Regex = """([^/]+)/([^;]+)(?:;\s*(.+))?""".r
  private val ParamRegex: Regex = """([^=]+)=([^;]+)(?:;\s*)?""".r

  // Case class for custom MIME types (user-defined)
  final case class Custom(
      baseType: String,
      subType: String,
      parameters: Map[String, String] = Map.empty
  ) extends MimeType {
    override def withParameter(name: String, value: String): MimeType =
      copy(parameters = parameters + (name.toLowerCase -> value))
  }

  /** Special MimeType that represents a wildcard, matching any input
    * Used for catch-all bridge implementations
    */
  case object Wildcard extends MimeType {
    val baseType: String = "*"
    val subType: String = "*"
    val parameters: Map[String, String] = Map.empty
  }

  // Factory method to create a MimeType
  def apply(
      baseType: String,
      subType: String,
      parameters: Map[String, String] = Map.empty
  ): MimeType = {
    val normalizedBase = baseType.toLowerCase
    val normalizedSub = subType.toLowerCase
    val normalizedParams = parameters.map { case (k, v) => k.toLowerCase -> v }

    // Try to match with a predefined type first
    values.find(m =>
      m.baseType == normalizedBase && m.subType == normalizedSub
    ) match {
      case Some(predefined) if normalizedParams.isEmpty =>
        predefined
      case Some(predefined) =>
        Custom(normalizedBase, normalizedSub, normalizedParams)
      case None =>
        Custom(normalizedBase, normalizedSub, normalizedParams)
    }
  }

  // Parse a MIME type string
  def parse(mimeTypeStr: String): MimeType = {
    mimeTypeStr match {
      case MimeTypeRegex(baseType, subType, null) =>
        // Simple MIME type without parameters
        apply(baseType, subType)

      case MimeTypeRegex(baseType, subType, paramStr) =>
        // MIME type with parameters
        val params = ParamRegex
          .findAllMatchIn(paramStr + ";")
          .map { m =>
            m.group(1).trim.toLowerCase -> m.group(2).trim
          }
          .toMap
        apply(baseType, subType, params)

      case _ =>
        // Invalid MIME type string, default to octet-stream
        ApplicationOctet
    }
  }

  /** Get a MimeType from a string, using predefined constants if available
    * Returns an Option to maintain backward compatibility
    * Returns Some(MimeType) only if it matches a known predefined type
    */
  def fromString(mimeTypeStr: String): Option[MimeType] = {
    val parsed = parse(mimeTypeStr)

    // Try to find a matching predefined type
    val baseMatch = values.find(_.matches(parsed))

    // If found, return the predefined type or a custom one with parameters
    baseMatch.map {
      case predefined if parsed.parameters.isEmpty => predefined
      case predefined =>
        Custom(predefined.baseType, predefined.subType, parsed.parameters)
    }
  }

  /** Extract just the base MIME type without parameters.
    * Useful for consistent matching and lookups.
    *
    * @param mimeTypeStr Full MIME type string potentially with parameters
    * @return The base MIME type without parameters (e.g. "text/plain" from "text/plain; charset=utf-8")
    */
  def stripParameters(mimeTypeStr: String): String = {
    // Split on semicolon and take the first part (the base MIME type)
    mimeTypeStr.split(";", 2)(0).trim
  }

  /** Get a MimeType from a string, ignoring any parameters
    * This is useful for more reliable matching and bridge lookups
    *
    * @param mimeTypeStr Full MIME type string potentially with parameters
    * @return Option[MimeType] for the base type (if it matches a known type)
    */
  def fromStringNoParams(mimeTypeStr: String): Option[MimeType] = {
    // Extract just the base MIME type without parameters
    val baseTypeStr = stripParameters(mimeTypeStr)
    fromString(baseTypeStr)
  }

  /** Get a MimeType from a string, ignoring any parameters, with a fallback
    *
    * @param mimeTypeStr Full MIME type string potentially with parameters
    * @param fallback Fallback MimeType to use if no match is found
    * @return MimeType for the base type or the fallback
    */
  def fromStringNoParams(mimeTypeStr: String, fallback: MimeType): MimeType = {
    fromStringNoParams(mimeTypeStr).getOrElse(fallback)
  }

  /** Get a MimeType from a string without Option wrapping
    * Will return the parsed MimeType even if it doesn't match a predefined type
    */
  def fromStringDirect(mimeTypeStr: String): MimeType = {
    parse(mimeTypeStr)
  }

  // Text MIME types
  case object TextPlain extends MimeType {
    val baseType: String = "text"
    val subType: String = "plain"
    val parameters: Map[String, String] = Map.empty
  }

  case object TextHtml extends MimeType {
    val baseType: String = "text"
    val subType: String = "html"
    val parameters: Map[String, String] = Map.empty
  }

  case object TextMarkdown extends MimeType {
    val baseType: String = "text"
    val subType: String = "markdown"
    val parameters: Map[String, String] = Map.empty
  }

  case object TextCsv extends MimeType {
    val baseType: String = "text"
    val subType: String = "csv"
    val parameters: Map[String, String] = Map.empty
  }

  case object TextXml extends MimeType {
    val baseType: String = "text"
    val subType: String = "xml"
    val parameters: Map[String, String] = Map.empty
  }

  case object TextCss extends MimeType {
    val baseType: String = "text"
    val subType: String = "css"
    val parameters: Map[String, String] = Map.empty
  }

  case object TextJavascript extends MimeType {
    val baseType: String = "text"
    val subType: String = "javascript"
    val parameters: Map[String, String] = Map.empty
  }

  // Application MIME types
  case object ApplicationJson extends MimeType {
    val baseType: String = "application"
    val subType: String = "json"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationXml extends MimeType {
    val baseType: String = "application"
    val subType: String = "xml"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationPdf extends MimeType {
    val baseType: String = "application"
    val subType: String = "pdf"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationZip extends MimeType {
    val baseType: String = "application"
    val subType: String = "zip"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationGzip extends MimeType {
    val baseType: String = "application"
    val subType: String = "gzip"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationSevenz extends MimeType {
    val baseType: String = "application"
    val subType: String = "x-7z-compressed"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationTar extends MimeType {
    val baseType: String = "application"
    val subType: String = "x-tar"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationBzip2 extends MimeType {
    val baseType: String = "application"
    val subType: String = "x-bzip2"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationXz extends MimeType {
    val baseType: String = "application"
    val subType: String = "x-xz"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndMsOutlook extends MimeType {
    val baseType: String = "application"
    val subType: String = "vnd.ms-outlook"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationOctet extends MimeType {
    val baseType: String = "application"
    val subType: String = "octet-stream"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationMsWord extends MimeType {
    val baseType: String = "application"
    val subType: String = "msword"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndMsExcel extends MimeType {
    val baseType: String = "application"
    val subType: String = "vnd.ms-excel"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndMsPowerpoint extends MimeType {
    val baseType: String = "application"
    val subType: String = "vnd.ms-powerpoint"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndOpenXmlFormatsWordprocessingmlDocument
      extends MimeType {
    val baseType: String = "application"
    val subType: String =
      "vnd.openxmlformats-officedocument.wordprocessingml.document"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndOpenXmlFormatsSpreadsheetmlSheet extends MimeType {
    val baseType: String = "application"
    val subType: String =
      "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndMsExcelSheetMacroEnabled extends MimeType {
    val baseType: String = "application"
    val subType: String = "vnd.ms-excel.sheet.macroEnabled.12"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndMsExcelSheetBinary extends MimeType {
    val baseType: String = "application"
    val subType: String = "vnd.ms-excel.sheet.binary.macroEnabled.12"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndOpenXmlFormatsPresentationmlPresentation
      extends MimeType {
    val baseType: String = "application"
    val subType: String =
      "vnd.openxmlformats-officedocument.presentationml.presentation"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationVndOasisOpendocumentSpreadsheet extends MimeType {
    val baseType: String = "application"
    val subType: String = "vnd.oasis.opendocument.spreadsheet"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationJavascript extends MimeType {
    val baseType: String = "application"
    val subType: String = "javascript"
    val parameters: Map[String, String] = Map.empty
  }

  case object ApplicationXhtml extends MimeType {
    val baseType: String = "application"
    val subType: String = "xhtml+xml"
    val parameters: Map[String, String] = Map.empty
  }

  // Image MIME types
  case object ImageJpeg extends MimeType {
    val baseType: String = "image"
    val subType: String = "jpeg"
    val parameters: Map[String, String] = Map.empty
  }

  case object ImagePng extends MimeType {
    val baseType: String = "image"
    val subType: String = "png"
    val parameters: Map[String, String] = Map.empty
  }

  case object ImageGif extends MimeType {
    val baseType: String = "image"
    val subType: String = "gif"
    val parameters: Map[String, String] = Map.empty
  }

  case object ImageBmp extends MimeType {
    val baseType: String = "image"
    val subType: String = "bmp"
    val parameters: Map[String, String] = Map.empty
  }

  case object ImageSvgXml extends MimeType {
    val baseType: String = "image"
    val subType: String = "svg+xml"
    val parameters: Map[String, String] = Map.empty
  }

  case object ImageTiff extends MimeType {
    val baseType: String = "image"
    val subType: String = "tiff"
    val parameters: Map[String, String] = Map.empty
  }

  case object ImageWebp extends MimeType {
    val baseType: String = "image"
    val subType: String = "webp"
    val parameters: Map[String, String] = Map.empty
  }

  // Message MIME types
  case object MessageRfc822 extends MimeType {
    val baseType: String = "message"
    val subType: String = "rfc822"
    val parameters: Map[String, String] = Map.empty
  }

  // Audio MIME types
  case object AudioMpeg extends MimeType {
    val baseType: String = "audio"
    val subType: String = "mpeg"
    val parameters: Map[String, String] = Map.empty
  }

  case object AudioOgg extends MimeType {
    val baseType: String = "audio"
    val subType: String = "ogg"
    val parameters: Map[String, String] = Map.empty
  }

  case object AudioWav extends MimeType {
    val baseType: String = "audio"
    val subType: String = "wav"
    val parameters: Map[String, String] = Map.empty
  }

  // Video MIME types
  case object VideoMp4 extends MimeType {
    val baseType: String = "video"
    val subType: String = "mp4"
    val parameters: Map[String, String] = Map.empty
  }

  case object VideoWebm extends MimeType {
    val baseType: String = "video"
    val subType: String = "webm"
    val parameters: Map[String, String] = Map.empty
  }

  case object VideoOgg extends MimeType {
    val baseType: String = "video"
    val subType: String = "ogg"
    val parameters: Map[String, String] = Map.empty
  }

  // Get all MimeType values
  val values: Seq[MimeType] = Seq(
    TextPlain,
    TextHtml,
    TextMarkdown,
    TextCsv,
    TextXml,
    TextCss,
    TextJavascript,
    ApplicationJson,
    ApplicationXml,
    ApplicationPdf,
    ApplicationZip,
    ApplicationGzip,
    ApplicationSevenz,
    ApplicationTar,
    ApplicationBzip2,
    ApplicationXz,
    ApplicationOctet,
    ApplicationVndMsOutlook,
    ApplicationMsWord,
    ApplicationVndMsExcel,
    ApplicationVndMsPowerpoint,
    ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
    ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
    ApplicationVndMsExcelSheetMacroEnabled,
    ApplicationVndMsExcelSheetBinary,
    ApplicationVndOpenXmlFormatsPresentationmlPresentation,
    ApplicationVndOasisOpendocumentSpreadsheet,
    ApplicationJavascript,
    ApplicationXhtml,
    ImageJpeg,
    ImagePng,
    ImageGif,
    ImageBmp,
    ImageSvgXml,
    ImageTiff,
    ImageWebp,
    MessageRfc822,
    AudioMpeg,
    AudioOgg,
    AudioWav,
    VideoMp4,
    VideoWebm,
    VideoOgg
  )

  // Helper methods for common operations

  /** Determine MIME type from file extension
    */
  def fromExtension(extension: String): MimeType = {
    val ext = extension.toLowerCase.stripPrefix(".")
    ext match {
      case "txt"             => TextPlain
      case "html" | "htm"    => TextHtml
      case "md" | "markdown" => TextMarkdown
      case "csv"             => TextCsv
      case "xml"             => ApplicationXml
      case "json"            => ApplicationJson
      case "pdf"             => ApplicationPdf
      case "zip"             => ApplicationZip
      case "gz" | "gzip"     => ApplicationGzip
      case "7z"              => ApplicationSevenz
      case "tar"             => ApplicationTar
      case "bz2"             => ApplicationBzip2
      case "xz"              => ApplicationXz
      case "msg"             => ApplicationVndMsOutlook
      case "doc"             => ApplicationMsWord
      case "docx"            => ApplicationVndOpenXmlFormatsWordprocessingmlDocument
      case "xls"             => ApplicationVndMsExcel
      case "xlsx"            => ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
      case "xlsm"            => ApplicationVndMsExcelSheetMacroEnabled
      case "xlsb"            => ApplicationVndMsExcelSheetBinary
      case "ppt"             => ApplicationVndMsPowerpoint
      case "pptx"            => ApplicationVndOpenXmlFormatsPresentationmlPresentation
      case "ods"             => ApplicationVndOasisOpendocumentSpreadsheet
      case "jpg" | "jpeg"    => ImageJpeg
      case "png"             => ImagePng
      case "gif"             => ImageGif
      case "bmp"             => ImageBmp
      case "svg"             => ImageSvgXml
      case "tif" | "tiff"    => ImageTiff
      case "webp"            => ImageWebp
      case "eml"             => MessageRfc822
      case "mp3"             => AudioMpeg
      case "ogg"             => AudioOgg
      case "wav"             => AudioWav
      case "mp4"             => VideoMp4
      case "webm"            => VideoWebm
      case _                 => ApplicationOctet
    }
  }

  /** Determine MIME type from filename
    */
  def fromFilename(filename: String): MimeType = {
    val lastDotIndex = filename.lastIndexOf('.')
    if (lastDotIndex >= 0 && lastDotIndex < filename.length - 1) {
      val extension = filename.substring(lastDotIndex + 1)
      fromExtension(extension)
    } else {
      ApplicationOctet
    }
  }

  /** Group MIME types by category
    */
  val textTypes: Seq[MimeType] = values.filter(_.isText)
  val applicationTypes: Seq[MimeType] = values.filter(_.isApplication)
  val imageTypes: Seq[MimeType] = values.filter(_.isImage)
  val messageTypes: Seq[MimeType] = values.filter(_.isMessage)

  /** Backward compatibility with the old API
    */
  def fromString(mimeTypeStr: String, fallback: MimeType): MimeType = {
    fromString(mimeTypeStr).getOrElse(fallback)
  }

  /** For backward compatibility with old case class approach */
  @deprecated("Use case objects or Custom directly instead")
  def unapply(
      mimeType: MimeType
  ): Option[(String, String, Map[String, String])] = {
    Some((mimeType.baseType, mimeType.subType, mimeType.parameters))
  }
}
