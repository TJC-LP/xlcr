package com.tjclp.xlcr
package types

import scala.util.matching.Regex

/** Enhanced MimeType class that supports parameters like charset and delimiter.
  *
  * @param baseType The primary type (e.g., "text", "application")
  * @param subType The subtype (e.g., "plain", "pdf")
  * @param parameters Additional parameters like charset, delimiter, etc.
  */
case class MimeType(
    baseType: String,
    subType: String,
    parameters: Map[String, String] = Map.empty
) extends Serializable {

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
    new MimeType(baseType, subType, parameters + (name.toLowerCase -> value))

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

  override def equals(obj: Any): Boolean = obj match {
    case other: MimeType =>
      baseType == other.baseType &&
        subType == other.subType &&
        parameters == other.parameters
    case _ => false
  }

  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + baseType.hashCode
    result = prime * result + subType.hashCode
    result = prime * result + parameters.hashCode
    result
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

  // Factory method to create a MimeType
  def apply(
      baseType: String,
      subType: String,
      parameters: Map[String, String] = Map.empty
  ): MimeType =
    new MimeType(
      baseType.toLowerCase,
      subType.toLowerCase,
      parameters.map { case (k, v) => k.toLowerCase -> v }
    )

  // Parse a MIME type string
  def parse(mimeTypeStr: String): MimeType = {
    mimeTypeStr match {
      case MimeTypeRegex(baseType, subType, null) =>
        // Simple MIME type without parameters
        MimeType(baseType, subType)

      case MimeTypeRegex(baseType, subType, paramStr) =>
        // MIME type with parameters
        val params = ParamRegex
          .findAllMatchIn(paramStr + ";")
          .map { m =>
            m.group(1).trim.toLowerCase -> m.group(2).trim
          }
          .toMap
        MimeType(baseType, subType, params)

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

    // If found, create a new instance with the same parameters
    baseMatch.map { base =>
      if (parsed.parameters.isEmpty) base
      else new MimeType(base.baseType, base.subType, parsed.parameters)
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
    val parsed = parse(mimeTypeStr)

    // Try to find a matching predefined type
    val baseMatch = values.find(_.matches(parsed))

    // If found, create a new instance with the same parameters
    baseMatch
      .map { base =>
        if (parsed.parameters.isEmpty) base
        else new MimeType(base.baseType, base.subType, parsed.parameters)
      }
      .getOrElse(parsed)
  }

  // Text MIME types
  val TextPlain: MimeType = MimeType("text", "plain")
  val TextHtml: MimeType = MimeType("text", "html")
  val TextMarkdown: MimeType = MimeType("text", "markdown")
  val TextCsv: MimeType = MimeType("text", "csv")
  val TextXml: MimeType = MimeType("text", "xml")
  val TextCss: MimeType = MimeType("text", "css")
  val TextJavascript: MimeType = MimeType("text", "javascript")

  // Application MIME types
  val ApplicationJson: MimeType = MimeType("application", "json")
  val ApplicationXml: MimeType = MimeType("application", "xml")
  val ApplicationPdf: MimeType = MimeType("application", "pdf")
  val ApplicationZip: MimeType = MimeType("application", "zip")
  val ApplicationGzip: MimeType = MimeType("application", "gzip")
  val ApplicationSevenz: MimeType = MimeType("application", "x-7z-compressed")
  val ApplicationTar: MimeType = MimeType("application", "x-tar")
  val ApplicationBzip2: MimeType = MimeType("application", "x-bzip2")
  val ApplicationXz: MimeType = MimeType("application", "x-xz")
  val ApplicationVndMsOutlook: MimeType =
    MimeType("application", "vnd.ms-outlook")
  val ApplicationOctet: MimeType = MimeType("application", "octet-stream")
  val ApplicationMsWord: MimeType = MimeType("application", "msword")
  val ApplicationVndMsExcel: MimeType = MimeType("application", "vnd.ms-excel")
  val ApplicationVndMsPowerpoint: MimeType =
    MimeType("application", "vnd.ms-powerpoint")
  val ApplicationVndOpenXmlFormatsWordprocessingmlDocument: MimeType =
    MimeType(
      "application",
      "vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
  val ApplicationVndOpenXmlFormatsSpreadsheetmlSheet: MimeType =
    MimeType(
      "application",
      "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
  val ApplicationVndMsExcelSheetMacroEnabled: MimeType =
    MimeType("application", "vnd.ms-excel.sheet.macroEnabled.12")
  val ApplicationVndMsExcelSheetBinary: MimeType =
    MimeType("application", "vnd.ms-excel.sheet.binary.macroEnabled.12")
  val ApplicationVndOpenXmlFormatsPresentationmlPresentation: MimeType =
    MimeType(
      "application",
      "vnd.openxmlformats-officedocument.presentationml.presentation"
    )
  val ApplicationJavascript: MimeType = MimeType("application", "javascript")
  val ApplicationXhtml: MimeType = MimeType("application", "xhtml+xml")

  // Image MIME types
  val ImageJpeg: MimeType = MimeType("image", "jpeg")
  val ImagePng: MimeType = MimeType("image", "png")
  val ImageGif: MimeType = MimeType("image", "gif")
  val ImageBmp: MimeType = MimeType("image", "bmp")
  val ImageSvgXml: MimeType = MimeType("image", "svg+xml")
  val ImageTiff: MimeType = MimeType("image", "tiff")
  val ImageWebp: MimeType = MimeType("image", "webp")

  // Message MIME types
  val MessageRfc822: MimeType = MimeType("message", "rfc822")

  // Audio MIME types
  val AudioMpeg: MimeType = MimeType("audio", "mpeg")
  val AudioOgg: MimeType = MimeType("audio", "ogg")
  val AudioWav: MimeType = MimeType("audio", "wav")

  // Video MIME types
  val VideoMp4: MimeType = MimeType("video", "mp4")
  val VideoWebm: MimeType = MimeType("video", "webm")
  val VideoOgg: MimeType = MimeType("video", "ogg")

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
}
