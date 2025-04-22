package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import scala.collection.concurrent.TrieMap

/** Metadata‑enriched chunk produced by a DocumentSplitter. */
case class DocChunk[T <: MimeType](
    content: FileContent[T],
    label: String,               // human readable (e.g. Sheet name, "Page 3")
    index: Int,                  // 0‑based position within parent
    total: Int,                  // total number of chunks produced
    attrs: Map[String, String] = Map.empty
)

/** Splitting strategies supported for different document types. */
sealed trait SplitStrategy
object SplitStrategy {
  case object Page       extends SplitStrategy
  case object Sheet      extends SplitStrategy
  case object Slide      extends SplitStrategy
  case object Row        extends SplitStrategy
  case object Column     extends SplitStrategy
  case object Attachment extends SplitStrategy
  case object Embedded   extends SplitStrategy
  case object Paragraph  extends SplitStrategy
  case object Sentence   extends SplitStrategy
  case object Heading    extends SplitStrategy

  def fromString(s: String): Option[SplitStrategy] = s.trim.toLowerCase match {
    case "page"       => Some(Page)
    case "sheet"      => Some(Sheet)
    case "slide"      => Some(Slide)
    case "row"        => Some(Row)
    case "column"     => Some(Column)
    case "attachment" => Some(Attachment)
    case "embedded"   => Some(Embedded)
    case "paragraph"  => Some(Paragraph)
    case "sentence"   => Some(Sentence)
    case "heading"    => Some(Heading)
    case _             => None
  }
}

/** Configuration for document splitting. */
case class SplitConfig(
    strategy: SplitStrategy,
    maxChars: Int = 8000,
    overlap: Int = 0,
    recursive: Boolean = false,
    maxRecursionDepth: Int = 5,
    maxTotalSize: Long = 1024 * 1024 * 100, // 100MB zipbomb protection
    
    // PDF to image conversion parameters
    outputFormat: Option[String] = None,       // "pdf", "png", or "jpg"
    maxImageWidth: Int = 2000,                // Max width in pixels
    maxImageHeight: Int = 2000,               // Max height in pixels
    maxImageSizeBytes: Long = 1024 * 1024 * 5, // 5MB default limit
    imageDpi: Int = 300,                      // DPI for rendering
    jpegQuality: Float = 0.85f                // JPEG quality factor (0.0-1.0)
)

/** Generic trait for splitting a document. */
trait DocumentSplitter[I <: MimeType] {
  def split(content: FileContent[I], cfg: SplitConfig): Seq[DocChunk[_ <: MimeType]]
}

/** Registry + façade */
object DocumentSplitter {
  /** Thread‑safe registry */
  private val registry: TrieMap[MimeType, DocumentSplitter[_ <: MimeType]] = TrieMap.empty

  /* API ------------------------------------------------------------------ */

  def register[I <: MimeType](mime: MimeType, splitter: DocumentSplitter[I]): Unit =
    registry.update(mime, splitter.asInstanceOf[DocumentSplitter[_ <: MimeType]])

  def forMime(mime: MimeType): Option[DocumentSplitter[_ <: MimeType]] = registry.get(mime)

  /** Primary entry‑point returning enriched chunks. */
  def split(content: FileContent[_ <: MimeType], cfg: SplitConfig): Seq[DocChunk[_ <: MimeType]] =
    forMime(content.mimeType)
      .map(_.asInstanceOf[DocumentSplitter[MimeType]].split(content.asInstanceOf[FileContent[MimeType]], cfg))
      .getOrElse(Seq(DocChunk(content, label = "document", index = 0, total = 1)))

  /** Convenience method for code that only needs the bytes. */
  def splitBytesOnly(content: FileContent[_ <: MimeType], cfg: SplitConfig): Seq[FileContent[_ <: MimeType]] =
    split(content, cfg).map(_.content)

  /* Built‑ins ------------------------------------------------------------- */

  private def initBuiltIns(): Unit = {
    // PDF
    register(MimeType.ApplicationPdf, new PdfPageSplitter)

    // Excel
    val excelSplitter = new ExcelSheetSplitter
    register(MimeType.ApplicationVndMsExcel, excelSplitter)
    register(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, excelSplitter)

    // PowerPoint
    val pptSplitter = new PowerPointSlideSplitter
    register(MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation, pptSplitter)

    // Word
    val wordSplitter = new WordHeadingSplitter
    register(MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument, wordSplitter)

    // Email (.eml)
    val emailSplitter = new EmailAttachmentSplitter
    register(MimeType.MessageRfc822, emailSplitter)

    // Outlook MSG
    val msgSplitter = new OutlookMsgSplitter
    register(MimeType.ApplicationVndMsOutlook, msgSplitter)

    // Archive formats
    val archiveSplitter = new ArchiveEntrySplitter
    register(MimeType.ApplicationZip, archiveSplitter)
    register(MimeType.ApplicationGzip, archiveSplitter)
    register(MimeType.ApplicationSevenz, archiveSplitter)
    register(MimeType.ApplicationTar, archiveSplitter)
    register(MimeType.ApplicationBzip2, archiveSplitter)
    register(MimeType.ApplicationXz, archiveSplitter)
  }

  initBuiltIns()
}
