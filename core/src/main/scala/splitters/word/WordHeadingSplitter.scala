package com.tjclp.xlcr
package splitters
package word

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.jdk.CollectionConverters._

import org.apache.poi.xwpf.usermodel.{ XWPFDocument, XWPFParagraph, XWPFTable }

import models.FileContent
import types.MimeType

/** Splits a DOCX on Heading 1 paragraphs. */
trait WordHeadingSplitter[T <: MimeType] extends DocumentSplitter[T] {

  override def split(
    content: FileContent[T],
    cfg: SplitConfig
  ): Seq[DocChunk[T]] = {

    if (!cfg.hasStrategy(SplitStrategy.Heading))
      return Seq(DocChunk(content, "document", 0, 1))

    val src   = new XWPFDocument(new ByteArrayInputStream(content.data))
    val elems = src.getBodyElements.asScala.toList

    // identify indices of Heading1
    val headingIndices = elems.zipWithIndex.collect {
      case (p: XWPFParagraph, idx)
          if Option(p.getStyle).exists(_.startsWith("Heading1")) =>
        idx
    }

    if (headingIndices.isEmpty) {
      src.close(); return Seq(DocChunk(content, "document", 0, 1))
    }

    val boundaries = headingIndices :+ elems.length // sentinel end
    val total      = headingIndices.length

    val chunks = boundaries
      .sliding(2)
      .zipWithIndex
      .map { case (List(start, end), sectionIdx) =>
        val dest = new XWPFDocument()

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
        dest.write(baos)
        dest.close()

        val labelParagraph = elems(start).asInstanceOf[XWPFParagraph]
        val label = Option(labelParagraph.getText)
          .filter(_.nonEmpty)
          .getOrElse(s"Section ${sectionIdx + 1}")

        val fc = FileContent.fromBytes[T](baos.toByteArray)
        DocChunk(fc, label, sectionIdx, total)
      }
      .toSeq

    src.close()
    chunks
  }
}
