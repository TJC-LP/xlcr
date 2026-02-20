package com.tjclp.xlcr.v2.aspose

import zio.{ Chunk, ZIO }

import com.tjclp.xlcr.v2.transform.{ TransformError, UnsupportedConversion }
import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, DynamicFragment, Mime }

/**
 * Stateless dispatch object for Aspose-based transforms.
 *
 * This object provides compile-time dispatch to Aspose conversion and splitter implementations. No
 * runtime registry initialization is required.
 *
 * Usage:
 * {{{
 * import com.tjclp.xlcr.v2.aspose.AsposeTransforms
 *
 * val result = AsposeTransforms.convert(content, Mime.pdf)
 * val fragments = AsposeTransforms.split(content)
 * }}}
 */
object AsposeTransforms:

  // MIME type string constants for pattern matching
  private val DOCX     = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  private val DOC      = "application/msword"
  private val XLSX     = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  private val XLS      = "application/vnd.ms-excel"
  private val XLSM     = "application/vnd.ms-excel.sheet.macroenabled.12"
  private val XLSB     = "application/vnd.ms-excel.sheet.binary.macroenabled.12"
  private val ODS      = "application/vnd.oasis.opendocument.spreadsheet"
  private val PPTX     = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
  private val PPT      = "application/vnd.ms-powerpoint"
  private val PDF      = "application/pdf"
  private val HTML     = "text/html"
  private val PNG      = "image/png"
  private val JPEG     = "image/jpeg"
  private val EML      = "message/rfc822"
  private val MSG      = "application/vnd.ms-outlook"
  private val DOCM     = "application/vnd.ms-word.document.macroenabled.12"
  private val PPTM     = "application/vnd.ms-powerpoint.presentation.macroenabled.12"
  private val ZIP      = "application/zip"
  private val SEVENZIP = "application/x-7z-compressed"

  // ===========================================================================
  // Conversion dispatch
  // ===========================================================================

  /**
   * Convert content to a target MIME type using Aspose.
   *
   * @param input
   *   The input content to convert
   * @param to
   *   The target MIME type
   * @return
   *   The converted content or UnsupportedConversion error
   */
  def convert(
    input: Content[Mime],
    to: Mime,
    options: ConvertOptions = ConvertOptions()
  ): ZIO[Any, TransformError, Content[Mime]] =
    (input.mime.mimeType, to.mimeType) match
      // Word -> PDF
      case (DOCX, PDF) =>
        asposeDocxToPdf.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (DOC, PDF) =>
        asposeDocToPdf.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)

      // Word format conversions
      case (DOC, DOCX) =>
        asposeDocToDocx.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)
      case (DOCX, DOC) =>
        asposeDocxToDoc.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)
      case (DOCM, DOCX) =>
        asposeDocmToDocx.convert(input.asInstanceOf[Content[Mime.Docm]]).map(widen)
      case (DOCM, DOC) =>
        asposeDocmToDoc.convert(input.asInstanceOf[Content[Mime.Docm]]).map(widen)
      case (DOC, DOCM) =>
        asposeDocToDocm.convert(input.asInstanceOf[Content[Mime.Doc]]).map(widen)
      case (DOCX, DOCM) =>
        asposeDocxToDocm.convert(input.asInstanceOf[Content[Mime.Docx]]).map(widen)

      // Excel -> PDF (options-aware)
      case (XLSX, PDF) =>
        convertWorkbookToPdf(input.asInstanceOf[Content[Mime.Xlsx]], Mime.pdf, options).map(widen)
      case (XLS, PDF) =>
        convertWorkbookToPdf(input.asInstanceOf[Content[Mime.Xls]], Mime.pdf, options).map(widen)
      case (XLSM, PDF) =>
        convertWorkbookToPdf(input.asInstanceOf[Content[Mime.Xlsm]], Mime.pdf, options).map(widen)
      case (XLSB, PDF) =>
        convertWorkbookToPdf(input.asInstanceOf[Content[Mime.Xlsb]], Mime.pdf, options).map(widen)
      case (ODS, PDF) =>
        convertWorkbookToPdf(input.asInstanceOf[Content[Mime.Ods]], Mime.pdf, options).map(widen)

      // Excel -> HTML (options-aware)
      case (XLSX, HTML) =>
        convertWorkbookToHtml(input.asInstanceOf[Content[Mime.Xlsx]], options).map(widen)
      case (XLS, HTML) =>
        convertWorkbookToHtml(input.asInstanceOf[Content[Mime.Xls]], options).map(widen)
      case (XLSM, HTML) =>
        convertWorkbookToHtml(input.asInstanceOf[Content[Mime.Xlsm]], options).map(widen)
      case (XLSB, HTML) =>
        convertWorkbookToHtml(input.asInstanceOf[Content[Mime.Xlsb]], options).map(widen)
      case (ODS, HTML) =>
        convertWorkbookToHtml(input.asInstanceOf[Content[Mime.Ods]], options).map(widen)

      // Excel format conversions
      case (XLS, XLSX) =>
        asposeXlsToXlsx.convert(input.asInstanceOf[Content[Mime.Xls]]).map(widen)
      case (XLSX, XLS) =>
        asposeXlsxToXls.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)
      case (XLSM, XLSX) =>
        asposeXlsmToXlsx.convert(input.asInstanceOf[Content[Mime.Xlsm]]).map(widen)
      case (XLSB, XLSX) =>
        asposeXlsbToXlsx.convert(input.asInstanceOf[Content[Mime.Xlsb]]).map(widen)
      case (XLSX, XLSM) =>
        asposeXlsxToXlsm.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)
      case (XLSX, XLSB) =>
        asposeXlsxToXlsb.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)
      case (ODS, XLSX) =>
        asposeOdsToXlsx.convert(input.asInstanceOf[Content[Mime.Ods]]).map(widen)
      case (XLSX, ODS) =>
        asposeXlsxToOds.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)

      // PowerPoint -> PDF
      case (PPTX, PDF) =>
        asposePptxToPdf.convert(input.asInstanceOf[Content[Mime.Pptx]]).map(widen)
      case (PPT, PDF) =>
        asposePptToPdf.convert(input.asInstanceOf[Content[Mime.Ppt]]).map(widen)

      // PowerPoint -> HTML (options-aware: strip-masters)
      case (PPTX, HTML) =>
        convertPresentationToHtml(input.asInstanceOf[Content[Mime.Pptx]], options).map(widen)
      case (PPT, HTML) =>
        convertPresentationToHtml(input.asInstanceOf[Content[Mime.Ppt]], options).map(widen)
      case (HTML, PPTX) =>
        asposeHtmlToPptx.convert(input.asInstanceOf[Content[Mime.Html]]).map(widen)
      case (HTML, PPT) =>
        asposeHtmlToPpt.convert(input.asInstanceOf[Content[Mime.Html]]).map(widen)

      // PowerPoint format conversions
      case (PPT, PPTX) =>
        asposePptToPptx.convert(input.asInstanceOf[Content[Mime.Ppt]]).map(widen)
      case (PPTX, PPT) =>
        asposePptxToPpt.convert(input.asInstanceOf[Content[Mime.Pptx]]).map(widen)
      case (PPTM, PPTX) =>
        asposePptmToPptx.convert(input.asInstanceOf[Content[Mime.Pptm]]).map(widen)
      case (PPTM, PPT) =>
        asposePptmToPpt.convert(input.asInstanceOf[Content[Mime.Pptm]]).map(widen)

      // PDF -> HTML/PowerPoint (options-aware)
      case (PDF, HTML) =>
        convertPdfToHtml(input.asInstanceOf[Content[Mime.Pdf]], options).map(widen)
      case (PDF, PPTX) =>
        asposePdfToPptx.convert(input.asInstanceOf[Content[Mime.Pdf]]).map(widen)
      case (PDF, PPT) =>
        asposePdfToPpt.convert(input.asInstanceOf[Content[Mime.Pdf]]).map(widen)

      // PDF <-> Images
      case (PDF, PNG) =>
        asposePdfToPng.convert(input.asInstanceOf[Content[Mime.Pdf]]).map(widen)
      case (PDF, JPEG) =>
        asposePdfToJpeg.convert(input.asInstanceOf[Content[Mime.Pdf]]).map(widen)
      case (PNG, PDF) =>
        asposePngToPdf.convert(input.asInstanceOf[Content[Mime.Png]]).map(widen)
      case (JPEG, PDF) =>
        asposeJpegToPdf.convert(input.asInstanceOf[Content[Mime.Jpeg]]).map(widen)

      // HTML -> PDF
      case (HTML, PDF) =>
        asposeHtmlToPdf.convert(input.asInstanceOf[Content[Mime.Html]]).map(widen)

      // Email -> PDF
      case (EML, PDF) =>
        asposeEmlToPdf.convert(input.asInstanceOf[Content[Mime.Eml]]).map(widen)
      case (MSG, PDF) =>
        asposeMsgToPdf.convert(input.asInstanceOf[Content[Mime.Msg]]).map(widen)

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
    // Word format conversions
    (DOC, DOCX),
    (DOCX, DOC),
    (DOCM, DOCX),
    (DOCM, DOC),
    (DOC, DOCM),
    (DOCX, DOCM),
    // Excel -> PDF
    (XLSX, PDF),
    (XLS, PDF),
    (XLSM, PDF),
    (XLSB, PDF),
    (ODS, PDF),
    // Excel -> HTML
    (XLSX, HTML),
    (XLS, HTML),
    (XLSM, HTML),
    (XLSB, HTML),
    (ODS, HTML),
    // Excel format conversions
    (XLS, XLSX),
    (XLSX, XLS),
    (XLSM, XLSX),
    (XLSB, XLSX),
    (XLSX, XLSM),
    (XLSX, XLSB),
    (ODS, XLSX),
    (XLSX, ODS),
    // PowerPoint -> PDF
    (PPTX, PDF),
    (PPT, PDF),
    // PowerPoint <-> HTML
    (PPTX, HTML),
    (PPT, HTML),
    (HTML, PPTX),
    (HTML, PPT),
    // PowerPoint format conversions
    (PPT, PPTX),
    (PPTX, PPT),
    (PPTM, PPTX),
    (PPTM, PPT),
    // PDF -> HTML/PowerPoint
    (PDF, HTML),
    (PDF, PPTX),
    (PDF, PPT),
    // PDF <-> Images
    (PDF, PNG),
    (PDF, JPEG),
    (PNG, PDF),
    (JPEG, PDF),
    // HTML -> PDF
    (HTML, PDF),
    // Email -> PDF
    (EML, PDF),
    (MSG, PDF)
  )

  // ===========================================================================
  // Splitter dispatch
  // ===========================================================================

  /**
   * Split content into fragments using Aspose.
   *
   * @param input
   *   The input content to split
   * @return
   *   Chunks of dynamic fragments or UnsupportedConversion error
   */
  def split(
    input: Content[Mime],
    options: ConvertOptions = ConvertOptions()
  ): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    input.mime.mimeType match
      // Excel sheets (options-aware: sheetNames, excludeHidden, password)
      case XLSX =>
        splitExcelWorkbook(
          input.asInstanceOf[Content[Mime.Xlsx]],
          Mime.xlsx,
          com.aspose.cells.FileFormatType.XLSX,
          options
        ).map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))
      case XLS =>
        splitExcelWorkbook(
          input.asInstanceOf[Content[Mime.Xls]],
          Mime.xls,
          com.aspose.cells.FileFormatType.EXCEL_97_TO_2003,
          options
        ).map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))
      case XLSM =>
        splitExcelWorkbook(
          input.asInstanceOf[Content[Mime.Xlsm]],
          Mime.xlsm,
          com.aspose.cells.FileFormatType.XLSM,
          options
        ).map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))
      case XLSB =>
        splitExcelWorkbook(
          input.asInstanceOf[Content[Mime.Xlsb]],
          Mime.xlsb,
          com.aspose.cells.FileFormatType.XLSB,
          options
        ).map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))
      case ODS =>
        splitExcelWorkbook(
          input.asInstanceOf[Content[Mime.Ods]],
          Mime.ods,
          com.aspose.cells.FileFormatType.ODS,
          options
        ).map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      // PowerPoint slides
      case PPTX =>
        asposePptxSlideSplitter.split(input.asInstanceOf[Content[Mime.Pptx]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))
      case PPT =>
        asposePptSlideSplitter.split(input.asInstanceOf[Content[Mime.Ppt]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      // PDF pages
      case PDF =>
        asposePdfPageSplitter.split(input.asInstanceOf[Content[Mime.Pdf]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      // Word sections
      case DOCX =>
        asposeDocxSectionSplitter.split(input.asInstanceOf[Content[Mime.Docx]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))
      case DOC =>
        asposeDocSectionSplitter.split(input.asInstanceOf[Content[Mime.Doc]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      // Archives (dynamic)
      case ZIP =>
        asposeZipArchiveSplitter.splitDynamic(input.asInstanceOf[Content[Mime.Zip]])
      case SEVENZIP =>
        asposeSevenZipArchiveSplitter.splitDynamic(input.asInstanceOf[Content[Mime.SevenZip]])

      // Email attachments (dynamic)
      case EML =>
        asposeEmlAttachmentSplitter.splitDynamic(input.asInstanceOf[Content[Mime.Eml]])
      case MSG =>
        asposeMsgAttachmentSplitter.splitDynamic(input.asInstanceOf[Content[Mime.Msg]])

      case _ =>
        ZIO.fail(UnsupportedConversion(input.mime, input.mime))

  /**
   * Check if splitting is supported.
   */
  def canSplit(mime: Mime): Boolean =
    splittableMimeTypes.contains(mime.mimeType)

  private val splittableMimeTypes: Set[String] = Set(
    XLSX,
    XLS,
    XLSM,
    XLSB,
    ODS,
    PPTX,
    PPT,
    PDF,
    DOCX,
    DOC,
    ZIP,
    SEVENZIP,
    EML,
    MSG
  )
