package com.tjclp.xlcr
package splitters

import models.FileContent
import spi.{SplitterInfo, SplitterProvider}
import types.{MimeType, Priority}
import utils.{Prioritized, PriorityRegistry}

import org.slf4j.LoggerFactory

import java.util.ServiceLoader
import scala.jdk.CollectionConverters._

/** Generic trait for splitting a document. */
trait DocumentSplitter[I <: MimeType] extends Prioritized {
  def split(
      content: FileContent[I],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]]

  /** Default priority for this DocumentSplitter implementation.
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
  private lazy val registry
      : PriorityRegistry[MimeType, DocumentSplitter[_ <: MimeType]] = {
    logger.info("Initializing DocumentSplitter registry using ServiceLoader...")
    val reg = new PriorityRegistry[MimeType, DocumentSplitter[_ <: MimeType]](
      Some(mimeTypeSubtypeFn)
    )
    val loader = ServiceLoader.load(classOf[SplitterProvider])

    loader.iterator().asScala.foreach { provider =>
      logger
        .info(s"Loading splitters from provider: ${provider.getClass.getName}")
      try {
        provider.getSplitters.foreach { info =>
          registerSplitterInfo(reg, info)
        }
      } catch {
        case e: Throwable =>
          logger.error(
            s"Failed to load splitters from provider ${provider.getClass.getName}: ${e.getMessage}",
            e
          )
      }
    }
    logger.info("DocumentSplitter registry initialization complete.")
    reg
  }

  // Helper to register a single SplitterInfo
  private def registerSplitterInfo(
      reg: PriorityRegistry[MimeType, DocumentSplitter[_ <: MimeType]],
      info: SplitterInfo
  ): Unit = {
    logger.debug(
      s"Registering ${info.splitter.getClass.getSimpleName} for ${info.mime} with priority ${info.splitter.priority}"
    )
    // Ensure the splitter type matches the expected type for the registry
    reg.register(
      info.mime,
      info.splitter.asInstanceOf[DocumentSplitter[_ <: MimeType]]
    )
  }

  /** Explicitly trigger the lazy initialization.
    */
  def init(): Unit = {
    registry.size // Access the lazy val
  }

  /* API ------------------------------------------------------------------ */

  /** Register a splitter for a MIME type. (Mainly for testing or manual registration)
    */
  def register[I <: MimeType](
      mime: MimeType,
      splitter: DocumentSplitter[I]
  ): Unit = {
    registerSplitterInfo(registry, SplitterInfo(mime, splitter))
  }

  /** Find a splitter for a MIME type.
    * If multiple splitters are registered for the MIME type,
    * the one with the highest priority will be returned.
    * This method also considers subtypes using mimeTypeSubtypeFn.
    */
  def forMime(mime: MimeType): Option[DocumentSplitter[_ <: MimeType]] =
    registry.getWithSubtypes(mime)

  /** Find all splitters registered for a MIME type, sorted by priority (highest first).
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
    logger.debug(
      s"Using splitter ${splitterOpt.map(_.getClass.getSimpleName).getOrElse("None")} for MIME type ${content.mimeType} with strategy ${configToUse.strategy}"
    )

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
        splitter =>
          splitter
            .asInstanceOf[DocumentSplitter[MimeType]]
            .split(content.asInstanceOf[FileContent[MimeType]], configToUse)
      )
      .getOrElse {
        logger.warn(
          s"No specific splitter found for ${content.mimeType}, returning original content as a single chunk."
        )
        Seq(
          DocChunk(content, "document", 0, 1)
        ) // Return original if no splitter found
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
