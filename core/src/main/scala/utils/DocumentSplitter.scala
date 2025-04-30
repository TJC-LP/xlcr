package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import scala.collection.concurrent.TrieMap

/** Metadata‑enriched chunk produced by a DocumentSplitter. */
case class DocChunk[T <: MimeType](
    content: FileContent[T],
    label: String, // human readable (e.g. Sheet name, "Page 3")
    index: Int, // 0‑based position within parent
    total: Int, // total number of chunks produced
    attrs: Map[String, String] = Map.empty
) extends Serializable

/** Splitting strategies supported for different document types. */
/** Splitting strategies supported for different document types. */
sealed trait SplitStrategy {

  /** Returns a clean, serialization-friendly string representation */
  def displayName: String
}

object SplitStrategy {
  case object Page extends SplitStrategy {
    override def displayName: String = "page"
  }
  case object Sheet extends SplitStrategy {
    override def displayName: String = "sheet"
  }
  case object Slide extends SplitStrategy {
    override def displayName: String = "slide"
  }
  case object Row extends SplitStrategy {
    override def displayName: String = "row"
  }
  case object Column extends SplitStrategy {
    override def displayName: String = "column"
  }
  case object Attachment extends SplitStrategy {
    override def displayName: String = "attachment"
  }
  case object Embedded extends SplitStrategy {
    override def displayName: String = "embedded"
  }
  case object Paragraph extends SplitStrategy {
    override def displayName: String = "paragraph"
  }
  case object Sentence extends SplitStrategy {
    override def displayName: String = "sentence"
  }
  case object Heading extends SplitStrategy {
    override def displayName: String = "heading"
  }
  case object Chunk extends SplitStrategy {
    override def displayName: String = "chunk"
  }

  /** Automatically selects the appropriate strategy based on the input MIME type */
  case object Auto extends SplitStrategy {
    override def displayName: String = "auto"
  }

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
    case "chunk"      => Some(Chunk)
    case "auto"       => Some(Auto)
    case _            => None
  }
}

/** Configuration for document splitting. */
case class SplitConfig(
    strategy: Option[SplitStrategy] = None,
    maxChars: Int = 8000,
    overlap: Int = 0,
    recursive: Boolean = false,
    maxRecursionDepth: Int = 5,
    maxTotalSize: Long = 1024 * 1024 * 100, // 100MB zipbomb protection

    // PDF to image conversion parameters
    outputFormat: Option[String] = None, // "pdf", "png", or "jpg"
    maxImageWidth: Int = 2000, // Max width in pixels
    maxImageHeight: Int = 2000, // Max height in pixels
    maxImageSizeBytes: Long = 1024 * 1024 * 5, // 5MB default limit
    imageDpi: Int = 300, // DPI for rendering
    jpegQuality: Float = 0.85f, // JPEG quality factor (0.0-1.0)

    // Excel and zip settings
    maxFileCount: Long = 1000L
) {

  /** Helper method to check if a strategy is set to a specific value */
  def hasStrategy(s: SplitStrategy): Boolean = strategy.contains(s)
}

object SplitConfig {

  /** Create a SplitConfig with a strategy automatically chosen from the input
    * MIME type. The other parameters default to the same values as the primary
    * case-class constructor so callers only specify what they need.
    */
  def autoForMime(
      mime: MimeType,
      recursive: Boolean = false,
      maxRecursionDepth: Int = 5
  ): SplitConfig =
    SplitConfig(
      strategy = Some(defaultStrategyForMime(mime)),
      recursive = recursive,
      maxRecursionDepth = maxRecursionDepth
    )

  /** Extracted from SplitStep – central place so both core and Spark code can
    * reuse it without duplication.
    */
  def defaultStrategyForMime(mime: MimeType): SplitStrategy = mime match {
    // Text files
    case MimeType("text", _, _) =>
      SplitStrategy.Chunk

    case MimeType.ApplicationPdf => SplitStrategy.Page

    // Excel
    case MimeType.ApplicationVndMsExcel |
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet |
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet =>
      SplitStrategy.Sheet

    // PowerPoint
    case MimeType.ApplicationVndMsPowerpoint |
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation =>
      SplitStrategy.Slide

    // Archives / containers
    case MimeType.ApplicationZip | MimeType.ApplicationGzip |
        MimeType.ApplicationSevenz | MimeType.ApplicationTar |
        MimeType.ApplicationBzip2 | MimeType.ApplicationXz =>
      SplitStrategy.Embedded

    // Emails
    case MimeType.MessageRfc822 | MimeType.ApplicationVndMsOutlook =>
      SplitStrategy.Attachment

    case _ => SplitStrategy.Page
  }
}

/** Generic trait for splitting a document. */
trait DocumentSplitter[I <: MimeType] {
  def split(
      content: FileContent[I],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]]
}

/** Registry + façade */
object DocumentSplitter {

  /** Thread‑safe registry */
  private val registry: TrieMap[MimeType, DocumentSplitter[_ <: MimeType]] =
    TrieMap.empty

  /* API ------------------------------------------------------------------ */

  def register[I <: MimeType](
      mime: MimeType,
      splitter: DocumentSplitter[I]
  ): Unit =
    registry.update(
      mime,
      splitter.asInstanceOf[DocumentSplitter[_ <: MimeType]]
    )

  def forMime(mime: MimeType): Option[DocumentSplitter[_ <: MimeType]] =
    registry.get(mime)

  /** Primary entry‑point returning enriched chunks. */
  def split(
      content: FileContent[_ <: MimeType],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    // If strategy is Auto or None, automatically select an appropriate strategy based on the MIME type
    val configToUse =
      if (cfg.strategy.isEmpty || cfg.strategy.contains(SplitStrategy.Auto)) {
        cfg.copy(strategy =
          Some(SplitConfig.defaultStrategyForMime(content.mimeType))
        )
      } else {
        cfg
      }

    forMime(content.mimeType)
      .map(
        _.asInstanceOf[DocumentSplitter[MimeType]].split(content, configToUse)
      )
      .getOrElse(
        Seq(DocChunk(content, label = "document", index = 0, total = 1))
      )
  }

  /** Convenience method for code that only needs the bytes. */
  def splitBytesOnly(
      content: FileContent[_ <: MimeType],
      cfg: SplitConfig
  ): Seq[FileContent[_ <: MimeType]] =
    split(content, cfg).map(_.content)

  /* Built‑ins ------------------------------------------------------------- */

  private def initBuiltIns(): Unit = {
    // PDF
    register(MimeType.ApplicationPdf, new PdfPageSplitter)

    // Excel
    val excelSplitter = new ExcelSheetSplitter
    register(MimeType.ApplicationVndMsExcel, excelSplitter)
    register(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      excelSplitter
    )
    
    // OpenDocument Spreadsheet (ODS)
    val odsSplitter = new OdsSheetSplitter
    register(MimeType.ApplicationVndOasisOpendocumentSpreadsheet, odsSplitter)

    // PowerPoint
    val pptSplitter = new PowerPointSlideSplitter
    register(
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      pptSplitter
    )

    // Word
    val wordSplitter = new WordHeadingSplitter
    register(
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
      wordSplitter
    )

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

    // Plain-text & CSV
    register(MimeType.TextPlain, new TextSplitter)
    register(MimeType.TextCsv, new CsvSplitter)
  }

  initBuiltIns()
}
