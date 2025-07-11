package com.tjclp.xlcr
package splitters
package word

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.jdk.CollectionConverters._

import org.apache.poi.xwpf.usermodel.{ XWPFDocument, XWPFParagraph, XWPFTable }
import org.slf4j.LoggerFactory

import models.FileContent
import types.MimeType

/** Splits a DOCX on Heading 1 paragraphs. */
trait WordHeadingSplitter[T <: MimeType] extends DocumentSplitter[T]
    with SplitFailureHandler {

  override protected val logger: org.slf4j.Logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[T],
    cfg: SplitConfig
  ): Seq[DocChunk[T]] = {

    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Heading)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("heading")
      ).asInstanceOf[Seq[DocChunk[T]]]
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      val src   = new XWPFDocument(new ByteArrayInputStream(content.data))
      try {
        val elems = src.getBodyElements.asScala.toList

        // identify indices of Heading1
        val headingIndices = elems.zipWithIndex.collect {
          case (p: XWPFParagraph, idx)
              if Option(p.getStyle).exists(_.startsWith("Heading1")) =>
            idx
        }

        if (headingIndices.isEmpty) {
          throw new EmptyDocumentException(
            content.mimeType.toString,
            "Document contains no Heading 1 paragraphs"
          )
        }

        val boundaries = headingIndices :+ elems.length // sentinel end
        val total      = headingIndices.length

        // Determine which sections to extract based on configuration
        val sectionsToExtract = cfg.chunkRange match {
          case Some(range) =>
            // Filter to valid section indices
            range.filter(i => i >= 0 && i < total).toVector
          case None =>
            (0 until total).toVector
        }

        val chunks = sectionsToExtract.map { sectionIdx =>
          val start = boundaries(sectionIdx)
          val end   = boundaries(sectionIdx + 1)
          val dest  = new XWPFDocument()

          val chunkData = try {
            elems.slice(start, end).foreach {
              case p: XWPFParagraph =>
                val dp = dest.createParagraph()
                Option(p.getStyle).foreach(dp.setStyle)
                val dr = dp.createRun()
                dr.setText(p.getText)
              case _: XWPFTable => // simplistic: skip tables for now
              case _            => // ignore others
            }

            val baos = new ByteArrayOutputStream()
            try {
              dest.write(baos)
              baos.toByteArray
            } finally {
              baos.close()
            }
          } finally {
            dest.close()
          }

          val labelParagraph = elems(start).asInstanceOf[XWPFParagraph]
          val label = Option(labelParagraph.getText)
            .filter(_.nonEmpty)
            .getOrElse(s"Section ${sectionIdx + 1}")

          val fc = FileContent.fromBytes[T](chunkData)
          DocChunk(fc, label, sectionIdx, total)
        }

        chunks
      } finally {
        src.close()
      }
    }.asInstanceOf[Seq[DocChunk[T]]]
  }
}
