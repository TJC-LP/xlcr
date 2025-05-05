package com.tjclp.xlcr
package pipeline.steps

import models.FileContent
import pipeline.PipelineStep
import splitters.{DocumentSplitter, SplitConfig, SplitStrategy}
import types.MimeType

/** Split a document into multiple smaller `FileContent` chunks (pages, sheets,
  * slides, …) using the generic [[DocumentSplitter]] registry.
  *
  * Autodetection: if no explicit [[SplitStrategy]] was supplied we will choose
  * an appropriate one based on the *input* MIME type so that the caller does
  * not have to worry about the concrete document family.
  */
final case class SplitStep(
    strategyOverride: Option[SplitStrategy] = None,
    recursive: Boolean = false,
    maxRecursionDepth: Int = 5,
    useCloneForSlides: Boolean = true,
    preserveSlideNotes: Boolean = true
) extends PipelineStep[FileContent[MimeType], List[FileContent[MimeType]]] {

  override def run(
      input: FileContent[MimeType]
  ): List[FileContent[MimeType]] = {
    val strategy =
      strategyOverride.getOrElse(
        SplitConfig.defaultStrategyForMime(input.mimeType)
      )

    val cfg = SplitConfig(
      strategy = Some(strategy),
      recursive = recursive,
      maxRecursionDepth = maxRecursionDepth,
      useCloneForSlides = useCloneForSlides,
      preserveSlideNotes = preserveSlideNotes
    )

    DocumentSplitter
      .splitBytesOnly(input, cfg) // Seq[FileContent[_ <: MimeType]]
      .toList
  }
}
