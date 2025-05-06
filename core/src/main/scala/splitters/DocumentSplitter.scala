package com.tjclp.xlcr
package splitters

import org.slf4j.LoggerFactory

import models.FileContent
import types.{ MimeType, Prioritized, Priority }

/** Generic trait for splitting a document. */
trait DocumentSplitter[I <: MimeType] extends Prioritized {
  def split(
    content: FileContent[I],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]]

  /**
   * Default priority for this DocumentSplitter implementation. Override this in implementations to
   * set a different priority.
   */
  override def priority: Priority = Priority.DEFAULT
}

/**
 * Registry + façade for DocumentSplitters. Uses ServiceLoader to discover SplitterProvider
 * implementations.
 */
object DocumentSplitter {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Explicitly trigger the lazy initialization of the registry.
   */
  def init(): Unit =
    SplitterRegistry.init() // Delegate to the registry

  /* API ------------------------------------------------------------------ */

  /**
   * Register a splitter for a MIME type. (Mainly for testing or manual registration)
   */
  def register[I <: MimeType](
    mime: I,
    splitter: DocumentSplitter[I]
  ): Unit =
    SplitterRegistry.register(mime, splitter) // Delegate to the registry

  /**
   * Find a splitter for a MIME type with strong typing. If multiple splitters are registered for
   * the MIME type, the one with the highest priority will be returned. This method also considers
   * subtypes.
   *
   * @tparam T
   *   The mime type to find a splitter for
   * @param mime
   *   The mime type instance
   * @return
   *   An optional splitter that can handle the given mime type
   */
  def forMime[T <: MimeType](mime: T): Option[DocumentSplitter[T]] =
    SplitterRegistry.findSplitter[T](
      mime
    ) // Delegate to the registry's type-safe method

  /**
   * Find all splitters registered for a MIME type, sorted by priority (highest first). This method
   * also considers subtypes.
   *
   * @tparam T
   *   The mime type to find splitters for
   * @param mime
   *   The mime type instance
   * @return
   *   A list of splitters that can handle the given mime type
   */
  def allForMime[T <: MimeType](mime: T): List[DocumentSplitter[T]] =
    SplitterRegistry.findAllSplitters[T](
      mime
    ) // Delegate to the registry's type-safe method

  /**
   * Primary entry‑point returning enriched chunks with type safety.
   *
   * @tparam T
   *   The mime type of the content to split
   * @param content
   *   The file content to split
   * @param cfg
   *   The split configuration
   * @return
   *   A sequence of document chunks
   */
  def split[T <: MimeType](
    content: FileContent[T],
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

    // Use type-safe method
    val splitterOpt = SplitterRegistry.findSplitter(content.mimeType)
    logger.debug(
      s"Using splitter ${splitterOpt.map(_.getClass.getSimpleName).getOrElse("None")} for MIME type ${content
          .mimeType} with strategy ${configToUse.strategy}"
    )

    splitterOpt
      .map(
        // No cast needed since findSplitter[T] already returns DocumentSplitter[T]
        splitter => splitter.split(content, configToUse)
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
  def splitBytesOnly[T <: MimeType](
    content: FileContent[T],
    cfg: SplitConfig
  ): Seq[FileContent[_ <: MimeType]] =
    split(content, cfg).map(_.content)

  // Built-ins are now registered via ServiceLoader (e.g., CoreSplitterProvider)
  // The initBuiltIns() method is removed.
}
