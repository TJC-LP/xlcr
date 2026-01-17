package com.tjclp.xlcr.v2.libreoffice

import zio.{Chunk, ZIO}

import com.tjclp.xlcr.v2.transform.{TransformError, UnsupportedConversion}
import com.tjclp.xlcr.v2.types.{Content, DynamicFragment, Mime}

/**
 * Stateless dispatch object for LibreOffice-based transforms.
 *
 * This object provides compile-time dispatch to LibreOffice conversion
 * implementations. No runtime registry initialization is required.
 *
 * LibreOffice must be installed on the system for these conversions to work.
 *
 * Usage:
 * {{{
 * import com.tjclp.xlcr.v2.libreoffice.LibreOfficeTransforms
 *
 * val result = LibreOfficeTransforms.convert(content, Mime.pdf)
 * }}}
 */
object LibreOfficeTransforms:

  // MIME type string constants for pattern matching
  private val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  private val DOC = "application/msword"
  private val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  private val XLS = "application/vnd.ms-excel"
  private val XLSM = "application/vnd.ms-excel.sheet.macroenabled.12"
  private val ODS = "application/vnd.oasis.opendocument.spreadsheet"
  private val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
  private val PPT = "application/vnd.ms-powerpoint"
  private val ODT = "application/vnd.oasis.opendocument.text"
  private val ODP = "application/vnd.oasis.opendocument.presentation"
  private val RTF = "application/rtf"
  private val PDF = "application/pdf"

  // ===========================================================================
  // Conversion dispatch
  // ===========================================================================

  /**
   * Convert content to a target MIME type using LibreOffice.
   *
   * @param input The input content to convert
   * @param to The target MIME type
   * @return The converted content or UnsupportedConversion error
   */
  def convert(input: Content[Mime], to: Mime): ZIO[Any, TransformError, Content[Mime]] =
    (input.mime.mimeType, to.mimeType) match
      // Word -> PDF
      case (DOCX, PDF) =>
        libreofficeDocxToPdf.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (DOC, PDF) =>
        libreofficeDocToPdf.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)

      // Excel -> PDF
      case (XLSX, PDF) =>
        libreofficeXlsxToPdf.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)
      case (XLS, PDF) =>
        libreofficeXlsToPdf.convert(input.asInstanceOf[Content[Mime.Xls]]).map(widen)
      case (XLSM, PDF) =>
        libreofficeXlsmToPdf.convert(input.asInstanceOf[Content[Mime.Xlsm]]).map(widen)
      case (ODS, PDF) =>
        libreofficeOdsToPdf.convert(input.asInstanceOf[Content[Mime.Ods]]).map(widen)

      // PowerPoint -> PDF
      case (PPTX, PDF) =>
        libreofficePptxToPdf.convert(input.asInstanceOf[Content[Mime.Pptx]]).map(widen)
      case (PPT, PDF) =>
        libreofficePptToPdf.convert(input.asInstanceOf[Content[Mime.Ppt]]).map(widen)

      // OpenDocument -> PDF (LibreOffice-only)
      case (ODT, PDF) =>
        libreofficeOdtToPdf.convert(input.asInstanceOf[Content[Mime.Odt]]).map(widen)
      case (ODP, PDF) =>
        libreofficeOdpToPdf.convert(input.asInstanceOf[Content[Mime.Odp]]).map(widen)

      // RTF -> PDF (LibreOffice-only)
      case (RTF, PDF) =>
        libreofficeRtfToPdf.convert(input.asInstanceOf[Content[Mime.Rtf]]).map(widen)

      case _ =>
        ZIO.fail(UnsupportedConversion(input.mime, to))

  /** Widen a Content[M] to Content[Mime] */
  private def widen[M <: Mime](c: Content[M]): Content[Mime] =
    Content.fromChunk(c.data, c.mime, c.metadata)

  /**
   * Check if a conversion is supported.
   */
  def canConvert(from: Mime, to: Mime): Boolean =
    supportedConversions.contains((from.mimeType, to.mimeType))

  private val supportedConversions: Set[(String, String)] = Set(
    // Word -> PDF
    (DOCX, PDF), (DOC, PDF),
    // Excel -> PDF
    (XLSX, PDF), (XLS, PDF), (XLSM, PDF), (ODS, PDF),
    // PowerPoint -> PDF
    (PPTX, PDF), (PPT, PDF),
    // OpenDocument -> PDF
    (ODT, PDF), (ODP, PDF),
    // RTF -> PDF
    (RTF, PDF)
  )

  // ===========================================================================
  // Splitter dispatch (LibreOffice doesn't have splitters)
  // ===========================================================================

  /**
   * Split content into fragments.
   *
   * LibreOffice does not support splitting. This always returns UnsupportedConversion.
   */
  def split(input: Content[Mime]): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    ZIO.fail(UnsupportedConversion(input.mime, input.mime))

  /**
   * Check if splitting is supported.
   *
   * LibreOffice does not support splitting.
   */
  def canSplit(mime: Mime): Boolean = false
