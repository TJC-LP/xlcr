package com.tjclp.xlcr.pipeline.steps

import com.tjclp.xlcr.models.FileContent
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.utils.{DocumentSplitter, SplitConfig, SplitStrategy}
import com.tjclp.xlcr.pipeline.PipelineStep

/**
 * Split a document into multiple smaller `FileContent` chunks (pages, sheets,
 * slides, …) using the generic [[DocumentSplitter]] registry.
 *
 * Autodetection: if no explicit [[SplitStrategy]] was supplied we will choose
 * an appropriate one based on the *input* MIME type so that the caller does
 * not have to worry about the concrete document family.
 */
final case class SplitStep(
    strategyOverride: Option[SplitStrategy] = None,
    recursive: Boolean = false,
    maxRecursionDepth: Int = 5
) extends PipelineStep[FileContent[MimeType], List[FileContent[MimeType]]] {

  override def run(input: FileContent[MimeType]): List[FileContent[MimeType]] = {
    val strategy = strategyOverride.getOrElse(defaultStrategyForMime(input.mimeType))

    val cfg = SplitConfig(
      strategy = strategy,
      recursive = recursive,
      maxRecursionDepth = maxRecursionDepth
    )

    DocumentSplitter
      .splitBytesOnly(input, cfg)   // Seq[FileContent[_ <: MimeType]]
      .toList
      .map(_.asInstanceOf[FileContent[MimeType]])
  }

  /** Default strategy heuristics (copied from the existing Pipeline util). */
  private def defaultStrategyForMime(mime: MimeType): SplitStrategy = mime match {
    case MimeType.ApplicationPdf => SplitStrategy.Page

    // Excel family → sheet
    case MimeType.ApplicationVndMsExcel | MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet =>
      SplitStrategy.Sheet

    // PowerPoint family → slide
    case MimeType.ApplicationVndMsPowerpoint | MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation =>
      SplitStrategy.Slide

    // Archives → embedded entries
    case MimeType.ApplicationZip | MimeType.ApplicationGzip | MimeType.ApplicationSevenz |
        MimeType.ApplicationTar | MimeType.ApplicationBzip2 | MimeType.ApplicationXz => SplitStrategy.Embedded

    // Email containers → attachments
    case MimeType.MessageRfc822 | MimeType.ApplicationVndMsOutlook => SplitStrategy.Attachment

    case _ => SplitStrategy.Page
  }
}
