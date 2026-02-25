package com.tjclp.xlcr.v2.libreoffice

import zio.{ Chunk, ZIO }

import com.tjclp.xlcr.v2.transform.{ TransformError, UnsupportedConversion }
import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, DynamicFragment, Mime }

/**
 * Stateless dispatch object for LibreOffice-based transforms.
 *
 * This object provides compile-time dispatch to LibreOffice conversion implementations. No runtime
 * registry initialization is required.
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
  private val DOC  = "application/msword"
  private val DOCM = "application/vnd.ms-word.document.macroenabled.12"
  private val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  private val XLS  = "application/vnd.ms-excel"
  private val XLSM = "application/vnd.ms-excel.sheet.macroenabled.12"
  private val XLSB = "application/vnd.ms-excel.sheet.binary.macroenabled.12"
  private val ODS  = "application/vnd.oasis.opendocument.spreadsheet"
  private val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
  private val PPT  = "application/vnd.ms-powerpoint"
  private val PPTM = "application/vnd.ms-powerpoint.presentation.macroenabled.12"
  private val ODT  = "application/vnd.oasis.opendocument.text"
  private val ODP  = "application/vnd.oasis.opendocument.presentation"
  private val ODG  = "application/vnd.oasis.opendocument.graphics"
  private val RTF  = "application/rtf"
  private val CSV  = "text/csv"
  private val HTML = "text/html"
  private val PDF  = "application/pdf"
  private val PNG  = "image/png"
  private val JPEG = "image/jpeg"
  private val SVG  = "image/svg+xml"

  // ===========================================================================
  // Conversion dispatch
  // ===========================================================================

  /**
   * Convert content to a target MIME type using LibreOffice.
   *
   * The `options` parameter is accepted for interface compatibility with BackendDispatch but is not
   * used by LibreOffice conversions. JODConverter's headless mode does not support fine-grained
   * options like password, sheet selection, or paper size. Non-default options are logged as
   * warnings at the dispatch layer.
   *
   * @param input
   *   The input content to convert
   * @param to
   *   The target MIME type
   * @param options
   *   Conversion options (unused — present for API compatibility)
   * @return
   *   The converted content or UnsupportedConversion error
   */
  def convert(
    input: Content[Mime],
    to: Mime,
    options: ConvertOptions = ConvertOptions()
  ): ZIO[Any, TransformError, Content[Mime]] =
    (input.mime.mimeType, to.mimeType) match
      // =======================================================================
      // Word -> PDF
      // =======================================================================
      case (DOCX, PDF) =>
        libreofficeDocxToPdf.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (DOC, PDF) =>
        libreofficeDocToPdf.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)
      case (DOCM, PDF) =>
        libreofficeDocmToPdf.convert(input.asInstanceOf[Content[Mime.Docm]]).map(widen)

      // =======================================================================
      // Excel -> PDF
      // =======================================================================
      case (XLSX, PDF) =>
        libreofficeXlsxToPdf.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)
      case (XLS, PDF) =>
        libreofficeXlsToPdf.convert(input.asInstanceOf[Content[Mime.Xls]]).map(widen)
      case (XLSM, PDF) =>
        libreofficeXlsmToPdf.convert(input.asInstanceOf[Content[Mime.Xlsm]]).map(widen)
      case (XLSB, PDF) =>
        libreofficeXlsbToPdf.convert(input.asInstanceOf[Content[Mime.Xlsb]]).map(widen)
      case (ODS, PDF) =>
        libreofficeOdsToPdf.convert(input.asInstanceOf[Content[Mime.Ods]]).map(widen)

      // =======================================================================
      // PowerPoint -> PDF
      // =======================================================================
      case (PPTX, PDF) =>
        libreofficePptxToPdf.convert(input.asInstanceOf[Content[Mime.Pptx]]).map(widen)
      case (PPT, PDF) =>
        libreofficePptToPdf.convert(input.asInstanceOf[Content[Mime.Ppt]]).map(widen)
      case (PPTM, PDF) =>
        libreofficePptmToPdf.convert(input.asInstanceOf[Content[Mime.Pptm]]).map(widen)

      // =======================================================================
      // OpenDocument / RTF -> PDF
      // =======================================================================
      case (ODT, PDF) =>
        libreofficeOdtToPdf.convert(input.asInstanceOf[Content[Mime.Odt]]).map(widen)
      case (ODP, PDF) =>
        libreofficeOdpToPdf.convert(input.asInstanceOf[Content[Mime.Odp]]).map(widen)
      case (ODG, PDF) =>
        libreofficeOdgToPdf.convert(input.asInstanceOf[Content[Mime.Odg]]).map(widen)
      case (RTF, PDF) =>
        libreofficeRtfToPdf.convert(input.asInstanceOf[Content[Mime.Rtf]]).map(widen)

      // =======================================================================
      // CSV conversions
      // =======================================================================
      case (CSV, PDF) =>
        libreofficeCsvToPdf.convert(input.asInstanceOf[Content[Mime.Csv]]).map(widen)
      case (CSV, XLSX) =>
        libreofficeCsvToXlsx.convert(input.asInstanceOf[Content[Mime.Csv]]).map(widen)
      case (CSV, ODS) =>
        libreofficeCsvToOds.convert(input.asInstanceOf[Content[Mime.Csv]]).map(widen)
      case (CSV, HTML) =>
        libreofficeCsvToHtml.convert(input.asInstanceOf[Content[Mime.Csv]]).map(widen)

      // =======================================================================
      // HTML conversions
      // =======================================================================
      case (HTML, PDF) =>
        libreofficeHtmlToPdf.convert(input.asInstanceOf[Content[Mime.Html]]).map(widen)
      case (HTML, DOCX) =>
        libreofficeHtmlToDocx.convert(input.asInstanceOf[Content[Mime.Html]]).map(widen)
      case (HTML, ODT) =>
        libreofficeHtmlToOdt.convert(input.asInstanceOf[Content[Mime.Html]]).map(widen)
      case (DOCX, HTML) =>
        libreofficeDocxToHtml.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (DOC, HTML) =>
        libreofficeDocToHtml.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)
      case (ODT, HTML) =>
        libreofficeOdtToHtml.convert(input.asInstanceOf[Content[Mime.Odt]]).map(widen)
      case (RTF, HTML) =>
        libreofficeRtfToHtml.convert(input.asInstanceOf[Content[Mime.Rtf]]).map(widen)

      // =======================================================================
      // ODG Drawing -> SVG / PNG
      // =======================================================================
      case (ODG, SVG) =>
        libreofficeOdgToSvg.convert(input.asInstanceOf[Content[Mime.Odg]]).map(widen)
      case (ODG, PNG) =>
        libreofficeOdgToPng.convert(input.asInstanceOf[Content[Mime.Odg]]).map(widen)

      // =======================================================================
      // Document -> Image (first page/slide)
      // =======================================================================
      case (DOCX, PNG) =>
        libreofficeDocxToPng.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (DOC, PNG) =>
        libreofficeDocToPng.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)
      case (PPTX, PNG) =>
        libreofficePptxToPng.convert(input.asInstanceOf[Content[Mime.Pptx]]).map(widen)
      case (PPT, PNG) =>
        libreofficePptToPng.convert(input.asInstanceOf[Content[Mime.Ppt]]).map(widen)
      case (DOCX, JPEG) =>
        libreofficeDocxToJpeg.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (DOC, JPEG) =>
        libreofficeDocToJpeg.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)
      case (PPTX, JPEG) =>
        libreofficePptxToJpeg.convert(input.asInstanceOf[Content[Mime.Pptx]]).map(widen)
      case (PPT, JPEG) =>
        libreofficePptToJpeg.convert(input.asInstanceOf[Content[Mime.Ppt]]).map(widen)

      // =======================================================================
      // Word format conversions
      // =======================================================================
      case (DOC, DOCX) =>
        libreofficeDocToDocx.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)
      case (DOCX, DOC) =>
        libreofficeDocxToDoc.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (DOCM, DOCX) =>
        libreofficeDocmToDocx.convert(input.asInstanceOf[Content[Mime.Docm]]).map(widen)
      case (DOCM, DOC) =>
        libreofficeDocmToDoc.convert(input.asInstanceOf[Content[Mime.Docm]]).map(widen)

      // =======================================================================
      // Excel format conversions
      // =======================================================================
      case (XLS, XLSX) =>
        libreofficeXlsToXlsx.convert(input.asInstanceOf[Content[Mime.Xls]]).map(widen)
      case (XLSX, XLS) =>
        libreofficeXlsxToXls.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)
      case (XLSB, XLSX) =>
        libreofficeXlsbToXlsx.convert(input.asInstanceOf[Content[Mime.Xlsb]]).map(widen)
      case (XLSM, XLSX) =>
        libreofficeXlsmToXlsx.convert(input.asInstanceOf[Content[Mime.Xlsm]]).map(widen)
      case (ODS, XLSX) =>
        libreofficeOdsToXlsx.convert(input.asInstanceOf[Content[Mime.Ods]]).map(widen)
      case (XLSX, ODS) =>
        libreofficeXlsxToOds.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)
      case (XLS, ODS) =>
        libreofficeXlsToOds.convert(input.asInstanceOf[Content[Mime.Xls]]).map(widen)
      case (ODS, XLS) =>
        libreofficeOdsToXls.convert(input.asInstanceOf[Content[Mime.Ods]]).map(widen)

      // =======================================================================
      // PowerPoint format conversions
      // =======================================================================
      case (PPT, PPTX) =>
        libreofficePptToPptx.convert(input.asInstanceOf[Content[Mime.Ppt]]).map(widen)
      case (PPTX, PPT) =>
        libreofficePptxToPpt.convert(input.asInstanceOf[Content[Mime.Pptx]]).map(widen)
      case (PPTM, PPTX) =>
        libreofficePptmToPptx.convert(input.asInstanceOf[Content[Mime.Pptm]]).map(widen)
      case (PPTM, PPT) =>
        libreofficePptmToPpt.convert(input.asInstanceOf[Content[Mime.Pptm]]).map(widen)

      // =======================================================================
      // OpenDocument cross-format conversions
      // =======================================================================
      case (ODT, DOCX) =>
        libreofficeOdtToDocx.convert(input.asInstanceOf[Content[Mime.Odt]]).map(widen)
      case (DOCX, ODT) =>
        libreofficeDocxToOdt.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (ODP, PPTX) =>
        libreofficeOdpToPptx.convert(input.asInstanceOf[Content[Mime.Odp]]).map(widen)
      case (PPTX, ODP) =>
        libreofficePptxToOdp.convert(input.asInstanceOf[Content[Mime.Pptx]]).map(widen)
      case (ODP, PPT) =>
        libreofficeOdpToPpt.convert(input.asInstanceOf[Content[Mime.Odp]]).map(widen)
      case (PPT, ODP) =>
        libreofficePptToOdp.convert(input.asInstanceOf[Content[Mime.Ppt]]).map(widen)
      case (ODT, RTF) =>
        libreofficeOdtToRtf.convert(input.asInstanceOf[Content[Mime.Odt]]).map(widen)
      case (RTF, DOCX) =>
        libreofficeRtfToDocx.convert(input.asInstanceOf[Content[Mime.Rtf]]).map(widen)

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
    (DOCX, PDF),
    (DOC, PDF),
    (DOCM, PDF),
    // Excel -> PDF
    (XLSX, PDF),
    (XLS, PDF),
    (XLSM, PDF),
    (XLSB, PDF),
    (ODS, PDF),
    // PowerPoint -> PDF
    (PPTX, PDF),
    (PPT, PDF),
    (PPTM, PDF),
    // OpenDocument / RTF -> PDF
    (ODT, PDF),
    (ODP, PDF),
    (ODG, PDF),
    (RTF, PDF),
    // CSV conversions
    (CSV, PDF),
    (CSV, XLSX),
    (CSV, ODS),
    (CSV, HTML),
    // HTML conversions
    (HTML, PDF),
    (HTML, DOCX),
    (HTML, ODT),
    (DOCX, HTML),
    (DOC, HTML),
    (ODT, HTML),
    (RTF, HTML),
    // ODG Drawing -> SVG / PNG
    (ODG, SVG),
    (ODG, PNG),
    // Document -> Image (first page/slide)
    (DOCX, PNG),
    (DOC, PNG),
    (PPTX, PNG),
    (PPT, PNG),
    (DOCX, JPEG),
    (DOC, JPEG),
    (PPTX, JPEG),
    (PPT, JPEG),
    // Word format conversions
    (DOC, DOCX),
    (DOCX, DOC),
    (DOCM, DOCX),
    (DOCM, DOC),
    // Excel format conversions
    (XLS, XLSX),
    (XLSX, XLS),
    (XLSB, XLSX),
    (XLSM, XLSX),
    (ODS, XLSX),
    (XLSX, ODS),
    (XLS, ODS),
    (ODS, XLS),
    // PowerPoint format conversions
    (PPT, PPTX),
    (PPTX, PPT),
    (PPTM, PPTX),
    (PPTM, PPT),
    // OpenDocument cross-format conversions
    (ODT, DOCX),
    (DOCX, ODT),
    (ODP, PPTX),
    (PPTX, ODP),
    (ODP, PPT),
    (PPT, ODP),
    (ODT, RTF),
    (RTF, DOCX)
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
