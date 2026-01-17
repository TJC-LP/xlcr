package com.tjclp.xlcr.v2.types

/**
 * Opaque MIME type representation for v2 Transform algebra.
 *
 * Uses Scala 3 opaque types with subtyping for compile-time safety.
 * The underlying representation is a String, but the type system enforces that
 * only valid MIME types can be used.
 *
 * Each specific MIME type (Pdf, Html, etc.) is an opaque subtype of Mime,
 * enabling type-safe transformations where the compiler can verify that
 * a Conversion[Mime.Pdf, Mime.Html] cannot accept Content[Mime.Xlsx].
 */
opaque type Mime <: String = String

object Mime:
  /** Create a Mime from a string value */
  inline def apply(value: String): Mime = value

  extension (m: Mime)
    /** Get the raw string value */
    def value: String = m

    /** Get the base type (e.g., "application" from "application/pdf") */
    def baseType: String = m.takeWhile(_ != '/')

    /** Get the subtype without parameters (e.g., "pdf" from "application/pdf; charset=utf-8") */
    def subType: String = m.dropWhile(_ != '/').drop(1).takeWhile(_ != ';')

    /** Get full type without parameters (e.g., "application/pdf" from "application/pdf; charset=utf-8") */
    def mimeType: String = m.takeWhile(_ != ';').trim

    /** Check if this is a text-based MIME type */
    def isText: Boolean = baseType == "text"

    /** Check if this is an application MIME type */
    def isApplication: Boolean = baseType == "application"

    /** Check if this is an image MIME type */
    def isImage: Boolean = baseType == "image"

    /** Check if this is a message MIME type */
    def isMessage: Boolean = baseType == "message"

    /** Check if this is an audio MIME type */
    def isAudio: Boolean = baseType == "audio"

    /** Check if this is a video MIME type */
    def isVideo: Boolean = baseType == "video"

  // ============================================================================
  // Opaque subtypes for compile-time safety
  // Each type is a subtype of Mime, enabling type-safe transforms
  // ============================================================================

  // --- Text types ---
  opaque type Plain <: Mime = Mime
  opaque type Html <: Mime = Mime
  opaque type Markdown <: Mime = Mime
  opaque type Csv <: Mime = Mime
  opaque type Tsv <: Mime = Mime
  opaque type TextXml <: Mime = Mime
  opaque type Css <: Mime = Mime
  opaque type Javascript <: Mime = Mime

  // --- Application types ---
  opaque type Json <: Mime = Mime
  opaque type Xml <: Mime = Mime
  opaque type Pdf <: Mime = Mime
  opaque type Zip <: Mime = Mime
  opaque type Gzip <: Mime = Mime
  opaque type SevenZip <: Mime = Mime
  opaque type Tar <: Mime = Mime
  opaque type Bzip2 <: Mime = Mime
  opaque type Xz <: Mime = Mime
  opaque type Rar <: Mime = Mime
  opaque type Octet <: Mime = Mime
  opaque type Xhtml <: Mime = Mime
  opaque type AppJavascript <: Mime = Mime
  opaque type Rtf <: Mime = Mime

  // --- Microsoft Office legacy formats ---
  opaque type Doc <: Mime = Mime
  opaque type Xls <: Mime = Mime
  opaque type Ppt <: Mime = Mime
  opaque type Msg <: Mime = Mime

  // --- Microsoft Office Open XML formats ---
  opaque type Docx <: Mime = Mime
  opaque type Xlsx <: Mime = Mime
  opaque type Pptx <: Mime = Mime

  // --- Microsoft Office macro-enabled formats ---
  opaque type Xlsm <: Mime = Mime
  opaque type Xlsb <: Mime = Mime
  opaque type Docm <: Mime = Mime
  opaque type Pptm <: Mime = Mime

  // --- OpenDocument formats ---
  opaque type Odt <: Mime = Mime
  opaque type Ods <: Mime = Mime
  opaque type Odp <: Mime = Mime
  opaque type Odg <: Mime = Mime
  opaque type Odf <: Mime = Mime

  // --- Image types ---
  opaque type Jpeg <: Mime = Mime
  opaque type Png <: Mime = Mime
  opaque type Gif <: Mime = Mime
  opaque type Bmp <: Mime = Mime
  opaque type Svg <: Mime = Mime
  opaque type Tiff <: Mime = Mime
  opaque type Webp <: Mime = Mime
  opaque type Ico <: Mime = Mime
  opaque type Heic <: Mime = Mime
  opaque type Avif <: Mime = Mime

  // --- Message types ---
  opaque type Eml <: Mime = Mime

  // --- Audio types ---
  opaque type Mp3 <: Mime = Mime
  opaque type Ogg <: Mime = Mime
  opaque type Wav <: Mime = Mime
  opaque type Flac <: Mime = Mime
  opaque type Aac <: Mime = Mime
  opaque type Webm <: Mime = Mime
  opaque type M4a <: Mime = Mime

  // --- Video types ---
  opaque type Mp4 <: Mime = Mime
  opaque type VideoWebm <: Mime = Mime
  opaque type VideoOgg <: Mime = Mime
  opaque type Avi <: Mime = Mime
  opaque type Mov <: Mime = Mime
  opaque type Mkv <: Mime = Mime

  // --- Font types ---
  opaque type Woff <: Mime = Mime
  opaque type Woff2 <: Mime = Mime
  opaque type Ttf <: Mime = Mime
  opaque type Otf <: Mime = Mime

  // --- Multipart types ---
  opaque type MultipartFormData <: Mime = Mime
  opaque type MultipartMixed <: Mime = Mime

  // ============================================================================
  // Singleton values for runtime use
  // ============================================================================

  // --- Text types ---
  val plain: Plain = "text/plain"
  val html: Html = "text/html"
  val markdown: Markdown = "text/markdown"
  val csv: Csv = "text/csv"
  val tsv: Tsv = "text/tab-separated-values"
  val textXml: TextXml = "text/xml"
  val css: Css = "text/css"
  val javascript: Javascript = "text/javascript"

  // --- Application types ---
  val json: Json = "application/json"
  val xml: Xml = "application/xml"
  val pdf: Pdf = "application/pdf"
  val zip: Zip = "application/zip"
  val gzip: Gzip = "application/gzip"
  val sevenZip: SevenZip = "application/x-7z-compressed"
  val tar: Tar = "application/x-tar"
  val bzip2: Bzip2 = "application/x-bzip2"
  val xz: Xz = "application/x-xz"
  val rar: Rar = "application/vnd.rar"
  val octet: Octet = "application/octet-stream"
  val xhtml: Xhtml = "application/xhtml+xml"
  val appJavascript: AppJavascript = "application/javascript"
  val rtf: Rtf = "application/rtf"

  // --- Microsoft Office legacy formats ---
  val doc: Doc = "application/msword"
  val xls: Xls = "application/vnd.ms-excel"
  val ppt: Ppt = "application/vnd.ms-powerpoint"
  val msg: Msg = "application/vnd.ms-outlook"

  // --- Microsoft Office Open XML formats ---
  val docx: Docx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  val xlsx: Xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  val pptx: Pptx = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

  // --- Microsoft Office macro-enabled formats ---
  val xlsm: Xlsm = "application/vnd.ms-excel.sheet.macroenabled.12"
  val xlsb: Xlsb = "application/vnd.ms-excel.sheet.binary.macroenabled.12"
  val docm: Docm = "application/vnd.ms-word.document.macroenabled.12"
  val pptm: Pptm = "application/vnd.ms-powerpoint.presentation.macroenabled.12"

  // --- OpenDocument formats ---
  val odt: Odt = "application/vnd.oasis.opendocument.text"
  val ods: Ods = "application/vnd.oasis.opendocument.spreadsheet"
  val odp: Odp = "application/vnd.oasis.opendocument.presentation"
  val odg: Odg = "application/vnd.oasis.opendocument.graphics"
  val odf: Odf = "application/vnd.oasis.opendocument.formula"

  // --- Image types ---
  val jpeg: Jpeg = "image/jpeg"
  val png: Png = "image/png"
  val gif: Gif = "image/gif"
  val bmp: Bmp = "image/bmp"
  val svg: Svg = "image/svg+xml"
  val tiff: Tiff = "image/tiff"
  val webp: Webp = "image/webp"
  val ico: Ico = "image/x-icon"
  val heic: Heic = "image/heic"
  val avif: Avif = "image/avif"

  // --- Message types ---
  val eml: Eml = "message/rfc822"

  // --- Audio types ---
  val mp3: Mp3 = "audio/mpeg"
  val ogg: Ogg = "audio/ogg"
  val wav: Wav = "audio/wav"
  val flac: Flac = "audio/flac"
  val aac: Aac = "audio/aac"
  val audioWebm: Webm = "audio/webm"
  val m4a: M4a = "audio/mp4"

  // --- Video types ---
  val mp4: Mp4 = "video/mp4"
  val videoWebm: VideoWebm = "video/webm"
  val videoOgg: VideoOgg = "video/ogg"
  val avi: Avi = "video/x-msvideo"
  val mov: Mov = "video/quicktime"
  val mkv: Mkv = "video/x-matroska"

  // --- Font types ---
  val woff: Woff = "font/woff"
  val woff2: Woff2 = "font/woff2"
  val ttf: Ttf = "font/ttf"
  val otf: Otf = "font/otf"

  // --- Multipart types ---
  val multipartFormData: MultipartFormData = "multipart/form-data"
  val multipartMixed: MultipartMixed = "multipart/mixed"

  // ============================================================================
  // All known MIME types as a sequence (for iteration/lookup)
  // ============================================================================
  val values: Seq[Mime] = Seq(
    // Text
    plain, html, markdown, csv, tsv, textXml, css, javascript,
    // Application
    json, xml, pdf, zip, gzip, sevenZip, tar, bzip2, xz, rar, octet, xhtml, appJavascript, rtf,
    // MS Office legacy
    doc, xls, ppt, msg,
    // MS Office Open XML
    docx, xlsx, pptx,
    // MS Office macro-enabled
    xlsm, xlsb, docm, pptm,
    // OpenDocument
    odt, ods, odp, odg, odf,
    // Image
    jpeg, png, gif, bmp, svg, tiff, webp, ico, heic, avif,
    // Message
    eml,
    // Audio
    mp3, ogg, wav, flac, aac, audioWebm, m4a,
    // Video
    mp4, videoWebm, videoOgg, avi, mov, mkv,
    // Font
    woff, woff2, ttf, otf,
    // Multipart
    multipartFormData, multipartMixed
  )

  // ============================================================================
  // Extension-based detection
  // ============================================================================
  def fromExtension(ext: String): Mime =
    val normalized = ext.toLowerCase.stripPrefix(".")
    normalized match
      case "txt"                   => plain
      case "html" | "htm"          => html
      case "md" | "markdown"       => markdown
      case "csv"                   => csv
      case "tsv"                   => tsv
      case "xml"                   => xml
      case "css"                   => css
      case "js"                    => javascript
      case "json"                  => json
      case "pdf"                   => pdf
      case "zip"                   => zip
      case "gz" | "gzip"           => gzip
      case "7z"                    => sevenZip
      case "tar"                   => tar
      case "bz2"                   => bzip2
      case "xz"                    => xz
      case "rar"                   => rar
      case "rtf"                   => rtf
      case "doc"                   => doc
      case "xls"                   => xls
      case "ppt"                   => ppt
      case "msg"                   => msg
      case "docx"                  => docx
      case "xlsx"                  => xlsx
      case "pptx"                  => pptx
      case "xlsm"                  => xlsm
      case "xlsb"                  => xlsb
      case "docm"                  => docm
      case "pptm"                  => pptm
      case "odt"                   => odt
      case "ods"                   => ods
      case "odp"                   => odp
      case "odg"                   => odg
      case "odf"                   => odf
      case "jpg" | "jpeg"          => jpeg
      case "png"                   => png
      case "gif"                   => gif
      case "bmp"                   => bmp
      case "svg"                   => svg
      case "tif" | "tiff"          => tiff
      case "webp"                  => webp
      case "ico"                   => ico
      case "heic" | "heif"         => heic
      case "avif"                  => avif
      case "eml"                   => eml
      case "mp3"                   => mp3
      case "ogg"                   => ogg
      case "wav"                   => wav
      case "flac"                  => flac
      case "aac"                   => aac
      case "m4a"                   => m4a
      case "mp4"                   => mp4
      case "webm"                  => videoWebm
      case "avi"                   => avi
      case "mov"                   => mov
      case "mkv"                   => mkv
      case "woff"                  => woff
      case "woff2"                 => woff2
      case "ttf"                   => ttf
      case "otf"                   => otf
      case _                       => octet

  def fromFilename(filename: String): Mime =
    val lastDotIndex = filename.lastIndexOf('.')
    if lastDotIndex >= 0 && lastDotIndex < filename.length - 1 then
      fromExtension(filename.substring(lastDotIndex + 1))
    else
      octet

  /** Parse a MIME type string, stripping parameters */
  def parse(mimeTypeStr: String): Mime =
    Mime(mimeTypeStr.takeWhile(_ != ';').trim.toLowerCase)

  /** Find a known MIME type matching the given string */
  def fromString(mimeTypeStr: String): Option[Mime] =
    val parsed = parse(mimeTypeStr)
    values.find(_.mimeType == parsed.mimeType)
