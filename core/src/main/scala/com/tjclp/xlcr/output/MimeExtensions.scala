package com.tjclp.xlcr.output

import scala.annotation.targetName

import com.tjclp.xlcr.types.Mime

/**
 * Utilities for mapping MIME types to file extensions.
 *
 * Provides consistent extension lookup for fragment output files across CLI and Server.
 */
object MimeExtensions:

  /** Comprehensive mapping from MIME type strings to file extensions */
  private val mimeToExt: Map[String, String] = Map(
    // Text types
    "text/plain"                -> "txt",
    "text/html"                 -> "html",
    "text/markdown"             -> "md",
    "text/csv"                  -> "csv",
    "text/tab-separated-values" -> "tsv",
    "text/xml"                  -> "xml",
    "text/css"                  -> "css",
    "text/javascript"           -> "js",
    // Application types
    "application/json"            -> "json",
    "application/xml"             -> "xml",
    "application/pdf"             -> "pdf",
    "application/zip"             -> "zip",
    "application/gzip"            -> "gz",
    "application/x-7z-compressed" -> "7z",
    "application/x-tar"           -> "tar",
    "application/x-bzip2"         -> "bz2",
    "application/x-xz"            -> "xz",
    "application/vnd.rar"         -> "rar",
    "application/octet-stream"    -> "bin",
    "application/xhtml+xml"       -> "xhtml",
    "application/javascript"      -> "js",
    "application/rtf"             -> "rtf",
    // Microsoft Office legacy formats
    "application/msword"            -> "doc",
    "application/vnd.ms-excel"      -> "xls",
    "application/vnd.ms-powerpoint" -> "ppt",
    "application/vnd.ms-outlook"    -> "msg",
    // Microsoft Office Open XML formats
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"   -> "docx",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         -> "xlsx",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx",
    // Microsoft Office macro-enabled formats
    "application/vnd.ms-excel.sheet.macroenabled.12"             -> "xlsm",
    "application/vnd.ms-excel.sheet.binary.macroenabled.12"      -> "xlsb",
    "application/vnd.ms-word.document.macroenabled.12"           -> "docm",
    "application/vnd.ms-powerpoint.presentation.macroenabled.12" -> "pptm",
    // OpenDocument formats
    "application/vnd.oasis.opendocument.text"         -> "odt",
    "application/vnd.oasis.opendocument.spreadsheet"  -> "ods",
    "application/vnd.oasis.opendocument.presentation" -> "odp",
    "application/vnd.oasis.opendocument.graphics"     -> "odg",
    "application/vnd.oasis.opendocument.formula"      -> "odf",
    // Image types
    "image/jpeg"    -> "jpg",
    "image/png"     -> "png",
    "image/gif"     -> "gif",
    "image/bmp"     -> "bmp",
    "image/svg+xml" -> "svg",
    "image/tiff"    -> "tiff",
    "image/webp"    -> "webp",
    "image/x-icon"  -> "ico",
    "image/heic"    -> "heic",
    "image/avif"    -> "avif",
    // Message types
    "message/rfc822" -> "eml",
    // Audio types
    "audio/mpeg" -> "mp3",
    "audio/ogg"  -> "ogg",
    "audio/wav"  -> "wav",
    "audio/flac" -> "flac",
    "audio/aac"  -> "aac",
    "audio/webm" -> "webm",
    "audio/mp4"  -> "m4a",
    // Video types
    "video/mp4"        -> "mp4",
    "video/webm"       -> "webm",
    "video/ogg"        -> "ogv",
    "video/x-msvideo"  -> "avi",
    "video/quicktime"  -> "mov",
    "video/x-matroska" -> "mkv",
    // Font types
    "font/woff"  -> "woff",
    "font/woff2" -> "woff2",
    "font/ttf"   -> "ttf",
    "font/otf"   -> "otf"
  )

  /**
   * Get the file extension for a MIME type string.
   *
   * @param mimeType
   *   The MIME type string (e.g., "application/pdf")
   * @return
   *   Some(extension) if known, None otherwise
   */
  def getExtension(mimeType: String): Option[String] =
    val normalized = mimeType.toLowerCase.split(";").head.trim
    mimeToExt.get(normalized)

  /**
   * Get the file extension for a Mime type.
   *
   * @param mime
   *   The Mime type
   * @return
   *   Some(extension) if known, None otherwise
   */
  @targetName("getExtensionFromMime")
  def getExtension(mime: Mime): Option[String] =
    getExtension(mime.mimeType)

  /**
   * Get the file extension for a MIME type, with a fallback default.
   *
   * @param mimeType
   *   The MIME type string
   * @param default
   *   Default extension if MIME type is unknown (default: "bin")
   * @return
   *   The file extension
   */
  def getExtensionOrDefault(mimeType: String, default: String = "bin"): String =
    getExtension(mimeType).getOrElse(default)

  /**
   * Get the file extension for a Mime type, with a fallback default.
   *
   * @param mime
   *   The Mime type
   * @param default
   *   Default extension if MIME type is unknown
   * @return
   *   The file extension
   */
  @targetName("getExtensionOrDefaultFromMime")
  def getExtensionOrDefault(mime: Mime, default: String): String =
    getExtension(mime).getOrElse(default)

  /**
   * Get the file extension for a Mime type, defaulting to "bin".
   *
   * @param mime
   *   The Mime type
   * @return
   *   The file extension
   */
  @targetName("getExtensionOrBinFromMime")
  def getExtensionOrDefault(mime: Mime): String =
    getExtension(mime).getOrElse("bin")
end MimeExtensions
