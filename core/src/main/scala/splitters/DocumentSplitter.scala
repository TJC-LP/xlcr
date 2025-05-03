package com.tjclp.xlcr
package splitters

import models.FileContent
import spi.{SplitterInfo, SplitterProvider}
import types.{MimeType, Priority}
import utils.{Prioritized, PriorityRegistry}

import org.slf4j.LoggerFactory

import java.util.ServiceLoader
import scala.jdk.CollectionConverters._

/** Metadata‑enriched chunk produced by a DocumentSplitter. */
case class DocChunk[T <: MimeType](
    content: FileContent[T],
    label: String, // human readable (e.g. Sheet name, "Page 3")
    index: Int, // 0‑based position within parent
    total: Int, // total number of chunks produced
    attrs: Map[String, String] = Map.empty
) extends Serializable

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

    case _ => SplitStrategy.Page // Default fallback
  }
}

/** Generic trait for splitting a document. */
trait DocumentSplitter[I <: MimeType] extends Prioritized {
  def split(
      content: FileContent[I],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]]

  /**
   * Default priority for this DocumentSplitter implementation.
   * Override this in implementations to set a different priority.
   */
  override def priority: Priority = Priority.DEFAULT
}

/** Registry + façade for DocumentSplitters.
  * Uses ServiceLoader to discover SplitterProvider implementations.
  */
object DocumentSplitter {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Function to check if a mime type is a subtype of another */
  private val mimeTypeSubtypeFn: (MimeType, MimeType) => Boolean = {
    case (requestedMime, registeredMime) =>
      // Consider a match if base types and subtypes match
      requestedMime.baseType == registeredMime.baseType && 
      requestedMime.subType == registeredMime.subType
  }

  /** Thread‑safe registry based on PriorityRegistry */
  private lazy val registry: PriorityRegistry[MimeType, DocumentSplitter[_ <: MimeType]] = {
    logger.info("Initializing DocumentSplitter registry using ServiceLoader...")
    val reg = new PriorityRegistry[MimeType, DocumentSplitter[_ <: MimeType]](Some(mimeTypeSubtypeFn))
    val loader = ServiceLoader.load(classOf[SplitterProvider])

    loader.iterator().asScala.foreach { provider =>
      logger.info(s"Loading splitters from provider: ${provider.getClass.getName}")
      try {
        provider.getSplitters.foreach { info =>
          registerSplitterInfo(reg, info)
        }
      } catch {
        case e: Throwable =>
          logger.error(s"Failed to load splitters from provider ${provider.getClass.getName}: ${e.getMessage}", e)
      }
    }
    logger.info("DocumentSplitter registry initialization complete.")
    reg
  }

  // Helper to register a single SplitterInfo
  private def registerSplitterInfo(reg: PriorityRegistry[MimeType, DocumentSplitter[_ <: MimeType]], info: SplitterInfo): Unit = {
    logger.debug(
      s"Registering ${info.splitter.getClass.getSimpleName} for ${info.mime} with priority ${info.splitter.priority}"
    )
    // Ensure the splitter type matches the expected type for the registry
    reg.register(info.mime, info.splitter.asInstanceOf[DocumentSplitter[_ <: MimeType]])
  }

  /**
   * Explicitly trigger the lazy initialization.
   */
  def init(): Unit = {
    registry.size // Access the lazy val
  }

  /* API ------------------------------------------------------------------ */

  /**
   * Register a splitter for a MIME type. (Mainly for testing or manual registration)
   */
  def register[I <: MimeType](
      mime: MimeType,
      splitter: DocumentSplitter[I]
  ): Unit = {
     registerSplitterInfo(registry, SplitterInfo(mime, splitter))
  }

  /**
   * Find a splitter for a MIME type.
   * If multiple splitters are registered for the MIME type,
   * the one with the highest priority will be returned.
   * This method also considers subtypes using mimeTypeSubtypeFn.
   */
  def forMime(mime: MimeType): Option[DocumentSplitter[_ <: MimeType]] =
    registry.getWithSubtypes(mime)

  /**
   * Find all splitters registered for a MIME type, sorted by priority (highest first).
   * This method also considers subtypes using mimeTypeSubtypeFn.
   */
  def allForMime(mime: MimeType): List[DocumentSplitter[_ <: MimeType]] =
    registry.getAllWithSubtypes(mime)

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

    val splitterOpt = forMime(content.mimeType)
    logger.debug(s"Using splitter ${splitterOpt.map(_.getClass.getSimpleName).getOrElse("None")} for MIME type ${content.mimeType} with strategy ${configToUse.strategy}")

    splitterOpt
      .map(
        // We need to cast the content to the type the specific splitter expects.
        // This is inherently unsafe if the registry contains a splitter registered
        // for a supertype (e.g., application/*) but expects a subtype.
        // However, the registry logic should prioritize exact matches.
        // The cast to DocumentSplitter[MimeType] is a simplification; ideally,
        // we'd want to match I in DocumentSplitter[I] with content.mimeType.
        // But given the type erasure and registry structure, this is complex.
        // The current approach assumes the registered splitter can handle the provided content.
        splitter => splitter.asInstanceOf[DocumentSplitter[MimeType]].split(content.asInstanceOf[FileContent[MimeType]], configToUse)
      )
      .getOrElse {
        logger.warn(s"No specific splitter found for ${content.mimeType}, returning original content as a single chunk.")
        Seq(DocChunk(content, "document", 0, 1)) // Return original if no splitter found
      }
  }

  /** Convenience method for code that only needs the bytes. */
  def splitBytesOnly(
      content: FileContent[_ <: MimeType],
      cfg: SplitConfig
  ): Seq[FileContent[_ <: MimeType]] =
    split(content, cfg).map(_.content)

  // Built-ins are now registered via ServiceLoader (e.g., CoreSplitterProvider)
  // The initBuiltIns() method is removed.
}