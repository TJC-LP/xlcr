package com.tjclp.xlcr
package splitters.word

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import scala.util.Using
import com.aspose.words.{ DocSaveOptions, Document, OoxmlSaveOptions, SaveFormat }
import org.slf4j.{ Logger, LoggerFactory }
import models.FileContent
import splitters._
import types.MimeType
import utils.aspose.AsposeLicense

trait WordPageAsposeSplitter extends DocumentSplitter[MimeType] with SplitFailureHandler {
  override protected val logger: Logger = LoggerFactory.getLogger(getClass)

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
      // Initialize Aspose license on executor
      AsposeLicense.initializeIfNeeded()

      // Validate content is not empty
      if (content.data.isEmpty) {
        throw new EmptyDocumentException(
          content.mimeType.mimeType,
          "Word file is empty"
        )
      }

      Using(new ByteArrayInputStream(content.data)) { bis =>
        val document = new Document(bis)
        try {
          val chunks = splitDocumentByPages(document, cfg, content.mimeType)
          chunks
        } finally
          document.cleanup()
      }.get
    }
  }

  private def splitDocumentByPages(
    doc: Document,
    cfg: SplitConfig,
    mimeType: MimeType
  ): Seq[DocChunk[_ <: MimeType]] = {
    val pageCount = doc.getPageCount

    if (pageCount == 0) {
      throw new EmptyDocumentException(
        mimeType.mimeType,
        "Word document contains no pages"
      )
    }

    logger.info(s"Splitting Word document into $pageCount pages")

    // Determine which pages to extract based on configuration
    val pagesToExtract = cfg.chunkRange match {
      case Some(range) =>
        val validPages = range.filter(i => i >= 0 && i < pageCount)
        if (validPages.isEmpty) {
          throw new IllegalArgumentException(
            s"Requested page range ${range.start}-${range.end} is outside document bounds (0-${pageCount - 1})"
          )
        }
        validPages
      case None =>
        0 until pageCount
    }

    // Extract pages
    pagesToExtract.map { pageIndex =>
      // Extract single page using Aspose's extractPages method
      val pageDoc = doc.extractPages(pageIndex, 1)

      // Convert to bytes
      val baos = new ByteArrayOutputStream()
      val saveOptions = mimeType match {
        case MimeType.ApplicationMsWord =>
          val options = new DocSaveOptions()
          options.setSaveFormat(SaveFormat.DOC)
          options
        case MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument =>
          val options = new OoxmlSaveOptions()
          options.setSaveFormat(SaveFormat.DOCX)
          options
        case _ =>
          new OoxmlSaveOptions() // Default to DOCX
      }

      pageDoc.save(baos, saveOptions)
      pageDoc.cleanup()

      val pageData = baos.toByteArray
      DocChunk(
        FileContent(
          pageData,
          mimeType
        ),
        label = s"Page ${pageIndex + 1}",
        index = pageIndex,
        total = pageCount
      )
    }
  }
}
