package com.tjclp.xlcr.v2.core

import zio.{Chunk, ZIO}

import com.tjclp.xlcr.v2.transform.{TransformError, UnsupportedConversion}
import com.tjclp.xlcr.v2.types.{Content, DynamicFragment, Mime}

/**
 * Stateless dispatch object for XLCR Core transforms.
 *
 * This object provides compile-time dispatch to core conversion and splitter
 * implementations using Apache Tika, Apache POI, Apache PDFBox, and Java standard
 * libraries. No external commercial dependencies (Aspose) or system installations
 * (LibreOffice) are required.
 *
 * Priority: XLCR Core (lowest) < LibreOffice < Aspose (highest)
 *
 * Key Features:
 * - Universal text extraction via Tika (any format → plain text or XML)
 * - XLSX → ODS conversion via POI + ODFDOM
 * - Document splitting for Excel, PowerPoint, Word, PDF, email, and archives
 *
 * Usage:
 * {{{
 * import com.tjclp.xlcr.v2.core.XlcrTransforms
 *
 * // Extract text from any document
 * val text = XlcrTransforms.convert(content, Mime.plain)
 *
 * // Split Excel into sheets
 * val sheets = XlcrTransforms.split(xlsxContent)
 * }}}
 */
object XlcrTransforms:

  // MIME type string constants for pattern matching
  private val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  private val DOC = "application/msword"
  private val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  private val XLS = "application/vnd.ms-excel"
  private val ODS = "application/vnd.oasis.opendocument.spreadsheet"
  private val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
  private val PPT = "application/vnd.ms-powerpoint"
  private val PDF = "application/pdf"
  private val PLAIN = "text/plain"
  private val XML = "application/xml"
  private val CSV = "text/csv"
  private val EML = "message/rfc822"
  private val MSG = "application/vnd.ms-outlook"
  private val ZIP = "application/zip"

  // ===========================================================================
  // Conversion dispatch
  // ===========================================================================

  /**
   * Convert content to a target MIME type using XLCR Core.
   *
   * Supported conversions:
   * - ANY → text/plain (Tika text extraction)
   * - ANY → application/xml (Tika XML extraction)
   * - XLSX → ODS (POI + ODFDOM)
   *
   * @param input The input content to convert
   * @param to The target MIME type
   * @return The converted content or UnsupportedConversion error
   */
  def convert(input: Content[Mime], to: Mime): ZIO[Any, TransformError, Content[Mime]] =
    to.mimeType match
      // Universal Tika conversions (catch-all for text/xml extraction)
      case PLAIN =>
        XlcrConversions.anyToPlainText.convert(input).map(widen)

      case XML =>
        XlcrConversions.anyToXml.convert(input).map(widen)

      // XLSX → ODS (POI + ODFDOM)
      case ODS if input.mime.mimeType == XLSX =>
        XlcrConversions.xlsxToOds.convert(input.asInstanceOf[Content[Mime.Xlsx]]).map(widen)

      case _ =>
        ZIO.fail(UnsupportedConversion(input.mime, to))

  /** Widen a Content[M] to Content[Mime] */
  private def widen[M <: Mime](c: Content[M]): Content[Mime] =
    Content.fromChunk(c.data, c.mime, c.metadata)

  /**
   * Check if a conversion is supported by XLCR Core.
   */
  def canConvert(from: Mime, to: Mime): Boolean =
    to.mimeType match
      // Tika can extract text/xml from any format
      case PLAIN | XML => true
      // XLSX → ODS is supported
      case ODS if from.mimeType == XLSX => true
      case _ => false

  // ===========================================================================
  // Splitter dispatch
  // ===========================================================================

  /**
   * Split content into fragments using XLCR Core.
   *
   * Supported splits:
   * - XLSX/XLS sheets (POI)
   * - ODS sheets (ODFDOM)
   * - PPTX slides (POI)
   * - DOCX sections (POI)
   * - PDF pages (PDFBox)
   * - Text paragraphs (built-in)
   * - CSV rows (built-in)
   * - EML attachments (Jakarta Mail)
   * - MSG attachments (POI HSMF)
   * - ZIP entries (java.util.zip)
   *
   * @param input The input content to split
   * @return Chunk of dynamic fragments or UnsupportedConversion error
   */
  def split(input: Content[Mime]): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    input.mime.mimeType match
      // Excel sheets (POI)
      case XLSX =>
        XlcrSplitters.xlsxSheetSplitter.split(input.asInstanceOf[Content[Mime.Xlsx]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      case XLS =>
        XlcrSplitters.xlsSheetSplitter.split(input.asInstanceOf[Content[Mime.Xls]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      case ODS =>
        XlcrSplitters.odsSheetSplitter.split(input.asInstanceOf[Content[Mime.Ods]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      // PowerPoint slides (POI)
      case PPTX =>
        XlcrSplitters.pptxSlideSplitter.split(input.asInstanceOf[Content[Mime.Pptx]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      // Word sections (POI)
      case DOCX =>
        XlcrSplitters.docxSectionSplitter.split(input.asInstanceOf[Content[Mime.Docx]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      // PDF pages (PDFBox)
      case PDF =>
        XlcrSplitters.pdfPageSplitter.split(input.asInstanceOf[Content[Mime.Pdf]])
          .map(_.map(f => DynamicFragment(widen(f.content), f.index, f.name)))

      // Text/CSV (built-in)
      case PLAIN =>
        XlcrSplitters.textSplitter.splitDynamic(input.asInstanceOf[Content[Mime.Plain]])

      case CSV =>
        XlcrSplitters.csvSplitter.splitDynamic(input.asInstanceOf[Content[Mime.Csv]])

      // Email attachments
      case EML =>
        XlcrSplitters.emlAttachmentSplitter.splitDynamic(input.asInstanceOf[Content[Mime.Eml]])

      case MSG =>
        XlcrSplitters.msgAttachmentSplitter.splitDynamic(input.asInstanceOf[Content[Mime.Msg]])

      // Archives
      case ZIP =>
        XlcrSplitters.zipEntrySplitter.splitDynamic(input.asInstanceOf[Content[Mime.Zip]])

      case _ =>
        ZIO.fail(UnsupportedConversion(input.mime, input.mime))

  /**
   * Check if splitting is supported by XLCR Core.
   */
  def canSplit(mime: Mime): Boolean =
    splittableMimeTypes.contains(mime.mimeType)

  private val splittableMimeTypes: Set[String] = Set(
    XLSX, XLS, ODS,
    PPTX,
    DOCX,
    PDF,
    PLAIN, CSV,
    EML, MSG,
    ZIP
  )
