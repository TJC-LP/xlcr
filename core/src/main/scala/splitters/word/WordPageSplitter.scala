package com.tjclp.xlcr
package splitters.word

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xwpf.usermodel.{ XWPFDocument, XWPFParagraph }
import org.slf4j.LoggerFactory

import models.FileContent
import splitters._
import types.MimeType
import utils.resource.ResourceWrappers._

trait WordPageSplitter extends DocumentSplitter[MimeType] with SplitFailureHandler {
  override protected val logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[MimeType],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    // Check strategy compatibility
    if (!cfg.hasStrategy(SplitStrategy.Page)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("page")
      )
    }

    // Wrap main splitting logic with failure handling
    withFailureHandling(content, cfg) {
      // Validate content is not empty
      if (content.data.isEmpty) {
        throw new EmptyDocumentException(
          content.mimeType.mimeType,
          "Word file is empty"
        )
      }

      val chunks = content.mimeType match {
        case MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument =>
          splitDocxDocument(content.data, cfg)
        case MimeType.ApplicationMsWord =>
          splitDocDocument(content.data, cfg)
        case _ =>
          throw new IllegalArgumentException(s"Unsupported MIME type: ${content.mimeType}")
      }

      chunks
    }
  }

  private def splitDocxDocument(data: Array[Byte], cfg: SplitConfig): Seq[DocChunk[_ <: MimeType]] =
    Using.Manager { use =>
      val bis = use(new ByteArrayInputStream(data))
      val pkg = OPCPackage.open(bis)
      use(new CloseableWrapper(pkg))
      val doc = new XWPFDocument(pkg)
      use(new CloseableWrapper(doc))

      val paragraphs = doc.getParagraphs.asScala.toList

      if (paragraphs.isEmpty) {
        throw new EmptyDocumentException(
          MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument.mimeType,
          "DOCX contains no paragraphs"
        )
      }

      // Group paragraphs by page breaks
      val pages = groupParagraphsByPageBreaks(paragraphs)

      logger.info(s"Splitting DOCX into ${pages.length} pages")

      // Determine which pages to extract based on configuration
      val pagesToExtract = cfg.chunkRange match {
        case Some(range) =>
          val validPages = range.filter(i => i >= 0 && i < pages.length)
          if (validPages.isEmpty) {
            throw new IllegalArgumentException(
              s"Requested page range ${range.start}-${range.end} is outside document bounds (0-${pages.length - 1})"
            )
          }
          validPages
        case None =>
          0 until pages.length
      }

      // Create chunks from selected pages
      pagesToExtract.map { idx =>
        val pageParagraphs = pages(idx)
        Using.Manager { pageUse =>
          val pageDoc = new XWPFDocument()
          pageUse(new CloseableWrapper(pageDoc))

          // Copy paragraphs to new document
          pageParagraphs.foreach { para =>
            val newPara = pageDoc.createParagraph()
            copyParagraphContent(para, newPara)
          }

          // Convert to bytes
          val baos = pageUse(new ByteArrayOutputStream())
          pageDoc.write(baos)

          val pageData = baos.toByteArray
          DocChunk(
            FileContent(
              pageData,
              MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument
            ),
            label = s"Page ${idx + 1}",
            index = idx,
            total = pages.length
          )
        }.get
      }
    }.get

  private def groupParagraphsByPageBreaks(paragraphs: List[XWPFParagraph])
    : List[List[XWPFParagraph]] = {
    if (paragraphs.isEmpty) return List.empty

    val pages       = scala.collection.mutable.ListBuffer[List[XWPFParagraph]]()
    val currentPage = scala.collection.mutable.ListBuffer[XWPFParagraph]()

    paragraphs.foreach { para =>
      currentPage += para

      // Check if paragraph contains a page break
      if (hasPageBreak(para)) {
        if (currentPage.nonEmpty) {
          pages += currentPage.toList
          currentPage.clear()
        }
      }
    }

    // Add remaining paragraphs as last page
    if (currentPage.nonEmpty) {
      pages += currentPage.toList
    }

    // If no page breaks were found, treat entire document as one page
    if (pages.isEmpty && paragraphs.nonEmpty) {
      pages += paragraphs
    }

    pages.toList
  }

  private def hasPageBreak(paragraph: XWPFParagraph): Boolean =
    paragraph.getRuns.asScala.exists { run =>
      // Check for explicit page breaks
      val hasExplicitBreak = run.getCTR != null && (
        (run.getCTR.getBrList != null && !run.getCTR.getBrList.isEmpty) ||
          (run.getCTR.getLastRenderedPageBreakList != null && !run.getCTR
            .getLastRenderedPageBreakList.isEmpty)
      )
      hasExplicitBreak
    }

  private def copyParagraphContent(source: XWPFParagraph, target: XWPFParagraph): Unit = {
    // Copy paragraph properties
    if (source.getCTP != null && source.getCTP.getPPr != null) {
      target.getCTP.setPPr(source.getCTP.getPPr)
    }

    // Copy runs
    source.getRuns.asScala.foreach { run =>
      val newRun = target.createRun()
      newRun.setText(run.text())

      // Copy basic formatting
      Option(run.getFontFamily).foreach(newRun.setFontFamily)
      Option(run.getFontSizeAsDouble).foreach(size => newRun.setFontSize(size))
      newRun.setBold(run.isBold)
      newRun.setItalic(run.isItalic)
      Option(run.getUnderline).foreach(newRun.setUnderline)
      Option(run.getColor).foreach(newRun.setColor)
    }
  }

  private def splitDocDocument(data: Array[Byte], cfg: SplitConfig): Seq[DocChunk[_ <: MimeType]] =
    Using(new ByteArrayInputStream(data)) { bis =>
      Using(new HWPFDocument(bis)) { doc =>
        val range = doc.getRange
        val text  = range.text()

        if (text.isEmpty) {
          throw new EmptyDocumentException(
            MimeType.ApplicationMsWord.mimeType,
            "DOC contains no text"
          )
        }

        // Split by page breaks (form feed character)
        val pageBreak = "\u000C"
        val pages     = text.split(pageBreak).toList.filter(_.nonEmpty)

        logger.info(s"Splitting DOC into ${pages.length} pages")

        // Determine which pages to extract based on configuration
        val pagesToExtract = cfg.chunkRange match {
          case Some(range) =>
            val validPages = range.filter(i => i >= 0 && i < pages.length)
            if (validPages.isEmpty) {
              throw new IllegalArgumentException(
                s"Requested page range ${range.start}-${range.end} is outside document bounds (0-${pages.length - 1})"
              )
            }
            validPages
          case None =>
            0 until pages.length
        }

        // Create chunks from selected pages
        pagesToExtract.map { idx =>
          val pageText = pages(idx)
          // For DOC format, we'll create a simple text representation
          // A full implementation would recreate a proper DOC file
          DocChunk(
            FileContent(
              pageText.getBytes("UTF-8"),
              MimeType.TextPlain
            ),
            label = s"Page ${idx + 1}",
            index = idx,
            total = pages.length
          )
        }
      }.get
    }.get
}
