package com.tjclp.xlcr.v2.aspose

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.jdk.CollectionConverters.*
import scala.util.Using

import zio.{ Chunk, ZIO }

import com.tjclp.xlcr.v2.transform.{ DynamicSplitter, Splitter, TransformError }
import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, DynamicFragment, Fragment, Mime }
import com.tjclp.xlcr.utils.aspose.AsposeLicense
import com.tjclp.xlcr.compat.aspose.AsposeWorkbook
import com.tjclp.xlcr.utils.resource.ResourceWrappers.DisposableWrapper

/**
 * Pure given instances for Aspose-based document splitters.
 *
 * These are HIGH priority (100) and will be preferred over core splitters when both are available.
 *
 * Import these givens to enable Aspose splitters:
 * {{{
 * import com.tjclp.xlcr.v2.aspose.given
 * }}}
 */

// =============================================================================
// Excel Sheet Splitters
// =============================================================================

given asposeXlsxSheetSplitter: Splitter[Mime.Xlsx, Mime.Xlsx] with
  override def name = "Aspose.Cells.XlsxSheetSplitter"

  def split(input: Content[Mime.Xlsx]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Xlsx]]] =
    splitExcelWorkbook(input, Mime.xlsx, com.aspose.cells.FileFormatType.XLSX)

given asposeXlsSheetSplitter: Splitter[Mime.Xls, Mime.Xls] with
  override def name = "Aspose.Cells.XlsSheetSplitter"

  def split(input: Content[Mime.Xls]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Xls]]] =
    splitExcelWorkbook(input, Mime.xls, com.aspose.cells.FileFormatType.EXCEL_97_TO_2003)

given asposeXlsmSheetSplitter: Splitter[Mime.Xlsm, Mime.Xlsm] with
  override def name = "Aspose.Cells.XlsmSheetSplitter"

  def split(input: Content[Mime.Xlsm]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Xlsm]]] =
    splitExcelWorkbook(input, Mime.xlsm, com.aspose.cells.FileFormatType.XLSM)

given asposeXlsbSheetSplitter: Splitter[Mime.Xlsb, Mime.Xlsb] with
  override def name = "Aspose.Cells.XlsbSheetSplitter"

  def split(input: Content[Mime.Xlsb]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Xlsb]]] =
    splitExcelWorkbook(input, Mime.xlsb, com.aspose.cells.FileFormatType.XLSB)

given asposeOdsSheetSplitter: Splitter[Mime.Ods, Mime.Ods] with
  override def name = "Aspose.Cells.OdsSheetSplitter"

  def split(input: Content[Mime.Ods]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Ods]]] =
    splitExcelWorkbook(input, Mime.ods, com.aspose.cells.FileFormatType.ODS)

// Helper function for Excel splitting (options-aware)
private[aspose] def splitExcelWorkbook[M <: Mime](
  input: Content[M],
  outputMime: M,
  fileFormatType: Int,
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Chunk[Fragment[M]]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()

    Using.Manager { use =>
      val srcWb: AsposeWorkbook = loadCellsWorkbook(input, options)
      use(new DisposableWrapper(srcWb))

      // Calculate all formulas while all sheets are present so cross-sheet
      // references resolve correctly (e.g. =Data!A1 in a Summary sheet).
      safeCalculateFormulas(srcWb, options)

      val sheets = srcWb.getWorksheets
      val total  = sheets.getCount

      // Filter sheets based on options.
      // Precedence: excludeHidden is applied as a global filter even if a hidden sheet
      // is explicitly named via sheetNames. This means --sheet "HiddenSheet" --exclude-hidden
      // will still exclude HiddenSheet.
      val indicesToSplit = (0 until total).filter { idx =>
        val ws        = sheets.get(idx)
        val nameMatch = options.sheetNames.isEmpty || options.sheetNames.contains(ws.getName)
        val visMatch  = !options.excludeHidden || ws.isVisible
        nameMatch && visMatch
      }

      // Fragment indices are 0-based contiguous after filtering; the original sheet name
      // is preserved in Fragment.name for traceability.
      val fragments = indicesToSplit.zipWithIndex.map { case (idx, fragIdx) =>
        val srcSheet  = sheets.get(idx)
        val sheetName = srcSheet.getName

        Using.Manager { destUse =>
          val destWb = new AsposeWorkbook()
          destUse(new DisposableWrapper(destWb))

          val destSheets = destWb.getWorksheets
          destSheets.removeAt(0)

          val newIdx    = destSheets.add()
          val destSheet = destSheets.get(newIdx)
          destSheet.copy(srcSheet)
          destSheet.setName(sheetName)
          destSheet.setVisible(true)

          // Replace formula cells with their computed values so the split file
          // is self-contained (no broken cross-sheet references).
          val cellIter = destSheet.getCells.iterator()
          while cellIter.hasNext do
            val cell = cellIter.next().asInstanceOf[com.aspose.cells.Cell]
            if cell.getFormula != null && cell.getFormula.nonEmpty then
              try cell.putValue(cell.getValue)
              catch case _: Exception => ()

          // Strip styles inherited from the source workbook that aren't used by this
          // single sheet — significantly reduces split file sizes.
          destWb.removeUnusedStyles()

          val baos = destUse(new ByteArrayOutputStream())
          destWb.save(baos, fileFormatType)

          val content =
            Content.fromChunk(Chunk.fromArray(baos.toByteArray), outputMime, input.metadata)
          Fragment(content, fragIdx, Some(sheetName))
        }.get
      }

      Chunk.fromIterable(fragments)
    }.get
  }.mapError(TransformError.fromThrowable)

// =============================================================================
// PowerPoint Slide Splitters
// =============================================================================

given asposePptxSlideSplitter: Splitter[Mime.Pptx, Mime.Pptx] with
  override def name = "Aspose.Slides.PptxSlideSplitter"

  def split(input: Content[Mime.Pptx]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Pptx]]] =
    splitPowerPointPresentation(input, Mime.pptx, com.aspose.slides.SaveFormat.Pptx)

given asposePptSlideSplitter: Splitter[Mime.Ppt, Mime.Ppt] with
  override def name = "Aspose.Slides.PptSlideSplitter"

  def split(input: Content[Mime.Ppt]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Ppt]]] =
    splitPowerPointPresentation(input, Mime.ppt, com.aspose.slides.SaveFormat.Ppt)

// Helper function for PowerPoint splitting
private def splitPowerPointPresentation[M <: Mime](
  input: Content[M],
  outputMime: M,
  saveFormat: Int
): ZIO[Any, TransformError, Chunk[Fragment[M]]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()

    val srcPres = new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
    try
      val slides = srcPres.getSlides
      val total  = slides.size()

      val fragments = (0 until total).map { idx =>
        val srcSlide = slides.get_Item(idx)

        // Create a new presentation with just this slide
        val destPres = new com.aspose.slides.Presentation()
        try
          // Remove default empty slide
          destPres.getSlides.removeAt(0)
          // Clone the slide
          destPres.getSlides.addClone(srcSlide)

          val out = new ByteArrayOutputStream()
          destPres.save(out, saveFormat)
          val content =
            Content.fromChunk(Chunk.fromArray(out.toByteArray), outputMime, input.metadata)
          Fragment(content, idx, Some(s"Slide ${idx + 1}"))
        finally
          destPres.dispose()
      }

      Chunk.fromIterable(fragments)
    finally
      srcPres.dispose()
  }.mapError(TransformError.fromThrowable)

// =============================================================================
// PDF Page Splitter
// =============================================================================

given asposePdfPageSplitter: Splitter[Mime.Pdf, Mime.Pdf] with
  override def name = "Aspose.Pdf.PdfPageSplitter"

  def split(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Pdf]]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()

      val srcDoc = new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray))
      try
        val pages = srcDoc.getPages
        val total = pages.size()

        val fragments = (1 to total).map { pageNum =>
          // Create a new document with just this page
          val destDoc = new com.aspose.pdf.Document()
          destDoc.getPages.add(pages.get_Item(pageNum))

          val opts = new com.aspose.pdf.optimization.OptimizationOptions()
          opts.setRemoveUnusedStreams(true)
          opts.setRemoveUnusedObjects(true)
          opts.setAllowReusePageContent(true)
          destDoc.optimizeResources(opts)

          val out = new ByteArrayOutputStream()
          destDoc.save(out)
          destDoc.close()

          val content =
            Content.fromChunk[Mime.Pdf](Chunk.fromArray(out.toByteArray), Mime.pdf, input.metadata)
          Fragment(content, pageNum - 1, Some(s"Page $pageNum"))
        }

        Chunk.fromIterable(fragments)
      finally
        srcDoc.close()
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// Word Document Splitters (by section/page)
// =============================================================================

given asposeDocxSectionSplitter: Splitter[Mime.Docx, Mime.Docx] with
  override def name = "Aspose.Words.DocxSectionSplitter"

  def split(input: Content[Mime.Docx]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Docx]]] =
    splitWordDocument(input, Mime.docx, com.aspose.words.SaveFormat.DOCX)

given asposeDocSectionSplitter: Splitter[Mime.Doc, Mime.Doc] with
  override def name = "Aspose.Words.DocSectionSplitter"

  def split(input: Content[Mime.Doc]): ZIO[Any, TransformError, Chunk[Fragment[Mime.Doc]]] =
    splitWordDocument(input, Mime.doc, com.aspose.words.SaveFormat.DOC)

// Helper function for Word splitting
private def splitWordDocument[M <: Mime](
  input: Content[M],
  outputMime: M,
  saveFormat: Int
): ZIO[Any, TransformError, Chunk[Fragment[M]]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()

    val srcDoc   = new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray))
    val sections = srcDoc.getSections
    val total    = sections.getCount

    val fragments = (0 until total).map { idx =>
      val srcSection = sections.get(idx)

      // Create a new document with just this section
      val destDoc = new com.aspose.words.Document()
      // Clear default content
      destDoc.removeAllChildren()
      // Import the section
      val importedSection = destDoc.importNode(srcSection, true)
      destDoc.appendChild(importedSection)

      val out = new ByteArrayOutputStream()
      destDoc.save(out, saveFormat)

      val content = Content.fromChunk(Chunk.fromArray(out.toByteArray), outputMime, input.metadata)
      Fragment(content, idx, Some(s"Section ${idx + 1}"))
    }

    Chunk.fromIterable(fragments)
  }.mapError(TransformError.fromThrowable)

// =============================================================================
// Archive Splitters (Dynamic - multiple MIME types)
// =============================================================================

given asposeZipArchiveSplitter: DynamicSplitter[Mime.Zip] with
  override def name = "Aspose.Zip.ZipArchiveSplitter"

  def splitDynamic(input: Content[Mime.Zip]): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()

      val archive = new com.aspose.zip.Archive(new ByteArrayInputStream(input.data.toArray))
      try
        val entries = archive.getEntries
        val fragments = entries.asScala.zipWithIndex.flatMap { case (entry, idx) =>
          if !entry.isDirectory then
            val out = new ByteArrayOutputStream()
            entry.extract(out)
            val entryName = entry.getName
            val mime      = Mime.fromFilename(entryName)
            val content = Content.fromChunk(
              Chunk.fromArray(out.toByteArray),
              mime,
              Map("filename" -> entryName)
            )
            Some(DynamicFragment(content, idx, Some(entryName)))
          else
            None
        }
        Chunk.fromIterable(fragments.toSeq)
      finally
        archive.close()
    }.mapError(TransformError.fromThrowable)

given asposeSevenZipArchiveSplitter: DynamicSplitter[Mime.SevenZip] with
  override def name = "Aspose.Zip.SevenZipArchiveSplitter"

  def splitDynamic(input: Content[Mime.SevenZip])
    : ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()

      val archive = new com.aspose.zip.SevenZipArchive(new ByteArrayInputStream(input.data.toArray))
      try
        val entries = archive.getEntries
        val fragments = entries.asScala.zipWithIndex.flatMap { case (entry, idx) =>
          if !entry.isDirectory then
            val out = new ByteArrayOutputStream()
            entry.extract(out)
            val entryName = entry.getName
            val mime      = Mime.fromFilename(entryName)
            val content = Content.fromChunk(
              Chunk.fromArray(out.toByteArray),
              mime,
              Map("filename" -> entryName)
            )
            Some(DynamicFragment(content, idx, Some(entryName)))
          else
            None
        }
        Chunk.fromIterable(fragments.toSeq)
      finally
        archive.close()
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// Email Attachment Splitters (Dynamic - multiple MIME types)
// =============================================================================

given asposeEmlAttachmentSplitter: DynamicSplitter[Mime.Eml] with
  override def name = "Aspose.Email.EmlAttachmentSplitter"

  def splitDynamic(input: Content[Mime.Eml]): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()

      val msg = com.aspose.email.MailMessage.load(new ByteArrayInputStream(input.data.toArray))
      val attachments = msg.getAttachments
      val fragments = attachments.asScala.zipWithIndex.map { case (attachment, idx) =>
        val out = new ByteArrayOutputStream()
        attachment.save(out)
        val filename = attachment.getName
        val contentType =
          Option(attachment.getContentType).map(_.toString).getOrElse("application/octet-stream")
        val mime = Mime.parse(contentType)
        val content =
          Content.fromChunk(Chunk.fromArray(out.toByteArray), mime, Map("filename" -> filename))
        DynamicFragment(content, idx, Some(filename))
      }
      Chunk.fromIterable(fragments.toSeq)
    }.mapError(TransformError.fromThrowable)

given asposeMsgAttachmentSplitter: DynamicSplitter[Mime.Msg] with
  override def name = "Aspose.Email.MsgAttachmentSplitter"

  def splitDynamic(input: Content[Mime.Msg]): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()

      val msg = com.aspose.email.MapiMessage.load(new ByteArrayInputStream(input.data.toArray))
      val attachments = msg.getAttachments
      val fragments = attachments.asScala.zipWithIndex.map { case (attachment, idx) =>
        val data     = attachment.getBinaryData
        val filename = attachment.getDisplayName
        val mime     = Mime.fromFilename(filename)
        val content  = Content.fromChunk(Chunk.fromArray(data), mime, Map("filename" -> filename))
        DynamicFragment(content, idx, Some(filename))
      }
      Chunk.fromIterable(fragments.toSeq)
    }.mapError(TransformError.fromThrowable)
