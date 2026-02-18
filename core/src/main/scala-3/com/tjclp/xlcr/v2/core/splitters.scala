package com.tjclp.xlcr.v2.core

import java.io.{
  ByteArrayInputStream,
  ByteArrayOutputStream,
  File,
  FileInputStream,
  FileOutputStream
}
import java.util.Properties
import java.util.zip.{ ZipEntry, ZipInputStream }

import scala.jdk.CollectionConverters.*

import zio.{ Chunk, ZIO }

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.poi.hsmf.MAPIMessage
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument
import jakarta.mail.{ Multipart, Part, Session }
import jakarta.mail.internet.MimeMessage

import com.tjclp.xlcr.v2.transform.{ DynamicSplitter, SplitError, Splitter }
import com.tjclp.xlcr.v2.types.{ Content, DynamicFragment, Fragment, Mime }

/**
 * Core XLCR splitters using POI, PDFBox, and Java standard libraries. These serve as the
 * lowest-priority fallback when Aspose and LibreOffice are unavailable.
 *
 * Priority: XLCR Core < LibreOffice < Aspose
 */
object XlcrSplitters:

  // Priority for core transforms (lowest)
  private val CORE_PRIORITY = -100

  // ============================================================================
  // Excel Sheet Splitters (POI)
  // ============================================================================

  /**
   * Split XLSX workbook into individual sheets using Apache POI.
   */
  val xlsxSheetSplitter: Splitter[Mime.Xlsx, Mime.Xlsx] =
    Splitter.withPriority[Mime.Xlsx, Mime.Xlsx](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitExcelWorkbook(input.data.toArray, Mime.xlsx)
      }.mapError(e => SplitError(s"XLSX sheet split failed: ${e.getMessage}", Mime.xlsx, Some(e)))
    }

  /**
   * Split XLS workbook into individual sheets using Apache POI.
   */
  val xlsSheetSplitter: Splitter[Mime.Xls, Mime.Xls] =
    Splitter.withPriority[Mime.Xls, Mime.Xls](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitExcelWorkbook(input.data.toArray, Mime.xls)
      }.mapError(e => SplitError(s"XLS sheet split failed: ${e.getMessage}", Mime.xls, Some(e)))
    }

  /**
   * Split ODS spreadsheet into individual sheets using ODFDOM.
   */
  val odsSheetSplitter: Splitter[Mime.Ods, Mime.Ods] =
    Splitter.withPriority[Mime.Ods, Mime.Ods](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitOdsWorkbook(input.data.toArray)
      }.mapError(e => SplitError(s"ODS sheet split failed: ${e.getMessage}", Mime.ods, Some(e)))
    }

  private def splitExcelWorkbook[M <: Mime](data: Array[Byte], mimeType: M): Chunk[Fragment[M]] =
    try
      ZipSecureFile.setMaxFileCount(10000L)

      // First pass: get sheet count and names
      val tempWb = WorkbookFactory.create(new ByteArrayInputStream(data))
      val (total, sheetNames) =
        try
          val count = tempWb.getNumberOfSheets
          val names = (0 until count).map(tempWb.getSheetName)
          (count, names)
        finally
          tempWb.close()

      if total == 0 then
        throw new IllegalStateException("Workbook contains no sheets")

      // Second pass: extract each sheet
      val fragments = (0 until total).map { idx =>
        val bais = new ByteArrayInputStream(data)
        val wb   = WorkbookFactory.create(bais)
        try
          // Evaluate formulas on the target sheet BEFORE removing other sheets
          // so cross-sheet references (e.g. =Data!A1) resolve correctly.
          val evaluator = wb.getCreationHelper.createFormulaEvaluator()
          wb.getSheetAt(idx).rowIterator().asScala.foreach { row =>
            row.cellIterator().asScala.foreach { cell =>
              if cell.getCellType == org.apache.poi.ss.usermodel.CellType.FORMULA then
                try evaluator.evaluateInCell(cell)
                catch case _: Exception => ()
            }
          }

          val cnt = wb.getNumberOfSheets
          // Remove all sheets except the target
          (cnt - 1 to 0 by -1).foreach { i =>
            if i != idx then wb.removeSheetAt(i)
          }

          val baos = new ByteArrayOutputStream()
          wb.write(baos)

          val content = Content(baos.toByteArray, mimeType)
          Fragment(content, idx, Some(sheetNames(idx)))
        finally
          wb.close()
      }

      Chunk.fromIterable(fragments)
    finally
      ZipSecureFile.setMaxFileCount(1000L) // Reset to default

  private def splitOdsWorkbook(data: Array[Byte]): Chunk[Fragment[Mime.Ods]] =
    // First pass: get sheet info
    val tempDoc = OdfSpreadsheetDocument.loadDocument(new ByteArrayInputStream(data))
    val (total, sheetNames) =
      try
        val sheets = tempDoc.getTableList(false)
        val count  = sheets.size()
        val names  = (0 until count).map(idx => sheets.get(idx).getTableName)
        (count, names)
      finally
        tempDoc.close()

    if total == 0 then
      throw new IllegalStateException("ODS spreadsheet contains no sheets")

    // Extract each sheet
    val fragments = (0 until total).map { idx =>
      val doc = OdfSpreadsheetDocument.loadDocument(new ByteArrayInputStream(data))
      try
        val allSheets = doc.getTableList(false)
        // Remove all sheets except the target (in reverse order)
        val sheetsToRemove = (0 until allSheets.size()).filter(_ != idx)
        sheetsToRemove.sorted.reverse.foreach { i =>
          allSheets.get(i).remove()
        }

        val baos = new ByteArrayOutputStream()
        doc.save(baos)

        val content = Content(baos.toByteArray, Mime.ods)
        Fragment(content, idx, Some(sheetNames(idx)))
      finally
        doc.close()
    }

    Chunk.fromIterable(fragments)

  // ============================================================================
  // PowerPoint Slide Splitters (POI)
  // ============================================================================

  /**
   * Split PPTX presentation into individual slides using Apache POI.
   *
   * Note: POI requires file-based access for reliable PPTX handling, so we use temporary files
   * internally.
   */
  val pptxSlideSplitter: Splitter[Mime.Pptx, Mime.Pptx] =
    Splitter.withPriority[Mime.Pptx, Mime.Pptx](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitPptxPresentation(input.data.toArray)
      }.mapError(e => SplitError(s"PPTX slide split failed: ${e.getMessage}", Mime.pptx, Some(e)))
    }

  private def splitPptxPresentation(data: Array[Byte]): Chunk[Fragment[Mime.Pptx]] =
    // POI XMLSlideShow requires file-based access to avoid ZipEntry issues
    val tempFile = File.createTempFile("presentation", ".pptx")
    tempFile.deleteOnExit()
    try
      // Write data to temp file
      val fos = new FileOutputStream(tempFile)
      try
        fos.write(data)
      finally
        fos.close()

      // Open presentation from file
      val src = new XMLSlideShow(new FileInputStream(tempFile))
      try
        val slides = src.getSlides.asScala.toVector
        val total  = slides.size

        if total == 0 then
          throw new IllegalStateException("Presentation contains no slides")

        // Extract each slide
        val fragments = (0 until total).flatMap { idx =>
          val originalSlide = slides(idx)
          val title =
            Option(originalSlide.getTitle).filter(_.nonEmpty).getOrElse(s"Slide ${idx + 1}")

          try
            // Create temp file for destination
            val destFile = File.createTempFile(s"slide_${idx + 1}", ".pptx")
            destFile.deleteOnExit()
            try
              // Create new presentation with single slide
              val dest = new XMLSlideShow()
              try
                dest.createSlide().importContent(originalSlide)

                val destFos = new FileOutputStream(destFile)
                try
                  dest.write(destFos)
                finally
                  destFos.close()

                val destBytes = java.nio.file.Files.readAllBytes(destFile.toPath)
                val content   = Content(destBytes, Mime.pptx)
                Some(Fragment(content, idx, Some(title)))
              finally
                dest.close()
            finally
              destFile.delete()
          catch
            case _: Exception => None
        }

        Chunk.fromIterable(fragments)
      finally
        src.close()
    finally
      tempFile.delete()

  // ============================================================================
  // Word Section Splitters (POI)
  // ============================================================================

  /**
   * Split DOCX document into sections using Apache POI.
   *
   * This splits by section breaks in the document. If no section breaks exist, returns the entire
   * document as a single fragment.
   */
  val docxSectionSplitter: Splitter[Mime.Docx, Mime.Docx] =
    Splitter.withPriority[Mime.Docx, Mime.Docx](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitDocxDocument(input.data.toArray)
      }.mapError(e => SplitError(s"DOCX section split failed: ${e.getMessage}", Mime.docx, Some(e)))
    }

  private def splitDocxDocument(data: Array[Byte]): Chunk[Fragment[Mime.Docx]] =
    // Note: POI doesn't have great support for splitting DOCX by sections/pages
    // This is a simplified implementation that returns the whole document
    // A more sophisticated implementation would parse section breaks
    val doc = new XWPFDocument(new ByteArrayInputStream(data))
    try
      // For now, return the document as a single fragment
      // Full section splitting would require parsing body elements for section properties
      val content = Content(data, Mime.docx)
      Chunk.single(Fragment(content, 0, Some("Document")))
    finally
      doc.close()

  // ============================================================================
  // PDF Page Splitter (PDFBox)
  // ============================================================================

  /**
   * Split PDF document into individual pages using Apache PDFBox.
   */
  val pdfPageSplitter: Splitter[Mime.Pdf, Mime.Pdf] =
    Splitter.withPriority[Mime.Pdf, Mime.Pdf](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitPdfDocument(input.data.toArray)
      }.mapError(e => SplitError(s"PDF page split failed: ${e.getMessage}", Mime.pdf, Some(e)))
    }

  private def splitPdfDocument(data: Array[Byte]): Chunk[Fragment[Mime.Pdf]] =
    val original = PDDocument.load(data)
    try
      val total = original.getNumberOfPages

      if total == 0 then
        throw new IllegalStateException("PDF contains no pages")

      val fragments = (0 until total).map { idx =>
        val chunkDoc = new PDDocument()
        try
          val page = original.getPage(idx)
          chunkDoc.addPage(page)

          val baos = new ByteArrayOutputStream()
          chunkDoc.save(baos)

          val content = Content(baos.toByteArray, Mime.pdf)
          Fragment(content, idx, Some(s"Page ${idx + 1}"))
        finally
          chunkDoc.close()
      }

      Chunk.fromIterable(fragments)
    finally
      original.close()

  // ============================================================================
  // Text/CSV Splitters (Built-in)
  // ============================================================================

  /**
   * Split plain text into chunks.
   *
   * Default behavior: split by paragraphs (double newlines). Each paragraph becomes a fragment.
   */
  val textSplitter: DynamicSplitter[Mime.Plain] =
    DynamicSplitter.withPriority[Mime.Plain](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        val text = new String(input.data.toArray, "UTF-8")
        if text.isEmpty then Chunk.empty
        else
          // Split by paragraphs (double newlines)
          val paragraphs = text.split("\n\\s*\n").filter(_.trim.nonEmpty)
          val total      = paragraphs.length

          val fragments = paragraphs.zipWithIndex.map { case (para, idx) =>
            val content: Content[Mime] = Content.fromString(para.trim, Mime.plain)
            DynamicFragment(content, idx, Some(s"Paragraph ${idx + 1}"))
          }
          Chunk.fromIterable(fragments)
      }.mapError(e => SplitError(s"Text split failed: ${e.getMessage}", Mime.plain, Some(e)))
    }

  /**
   * Split CSV into row chunks, preserving header in each chunk.
   */
  val csvSplitter: DynamicSplitter[Mime.Csv] =
    DynamicSplitter.withPriority[Mime.Csv](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        val csv   = new String(input.data.toArray, "UTF-8")
        val lines = csv.split("\r?\n", -1).toVector
        if lines.isEmpty then Chunk.empty
        else
          val header = lines.head
          val rows   = lines.tail.filter(_.trim.nonEmpty)

          if rows.isEmpty then Chunk.empty
          else
            val total = rows.size
            val fragments = rows.zipWithIndex.map { case (row, idx) =>
              val chunkText              = s"$header\n$row"
              val content: Content[Mime] = Content.fromString(chunkText, Mime.csv)
              DynamicFragment(content, idx, Some(s"Row ${idx + 2}"))
            }
            Chunk.fromIterable(fragments)
      }.mapError(e => SplitError(s"CSV split failed: ${e.getMessage}", Mime.csv, Some(e)))
    }

  // ============================================================================
  // Email Attachment Splitters
  // ============================================================================

  /**
   * Split EML email into body + attachments using Jakarta Mail.
   */
  val emlAttachmentSplitter: DynamicSplitter[Mime.Eml] =
    DynamicSplitter.withPriority[Mime.Eml](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitEmlMessage(input.data.toArray)
      }.mapError(e =>
        SplitError(s"EML attachment split failed: ${e.getMessage}", Mime.eml, Some(e))
      )
    }

  private def splitEmlMessage(data: Array[Byte]): Chunk[DynamicFragment] =
    val session   = Session.getDefaultInstance(new Properties())
    val msg       = new MimeMessage(session, new ByteArrayInputStream(data))
    val fragments = scala.collection.mutable.ListBuffer.empty[DynamicFragment]

    var bodyCaptured = false

    def harvest(part: Part): Unit =
      if part.isMimeType("multipart/*") then
        part.getContent match
          case mp: Multipart =>
            (0 until mp.getCount).foreach(i => harvest(mp.getBodyPart(i)))
          case _ => // Skip non-multipart content
      else
        val disposition = Option(part.getDisposition).getOrElse("")
        val ctype       = part.getContentType.toLowerCase

        if Part.ATTACHMENT.equalsIgnoreCase(disposition) ||
          (disposition == "inline" && ctype.startsWith("image/"))
        then
          val bytes   = part.getInputStream.readAllBytes()
          val mimeStr = ctype.split(";")(0).trim
          val mime    = Mime.fromString(mimeStr).getOrElse(Mime.octet)
          val name    = Option(part.getFileName).getOrElse(s"attachment_${fragments.size}")
          val content = Content.fromChunk(Chunk.fromArray(bytes), mime)
          fragments += DynamicFragment(content, fragments.size, Some(name))
        else if !bodyCaptured && (part.isMimeType("text/plain") || part.isMimeType("text/html"))
        then
          val bytes   = part.getInputStream.readAllBytes()
          val mime    = if part.isMimeType("text/html") then Mime.html else Mime.plain
          val subj    = Option(msg.getSubject).getOrElse("body")
          val content = Content.fromChunk(Chunk.fromArray(bytes), mime)
          fragments += DynamicFragment(content, fragments.size, Some(subj))
          bodyCaptured = true

    harvest(msg)
    Chunk.fromIterable(fragments)

  /**
   * Split MSG Outlook message into body + attachments using POI HSMF.
   */
  val msgAttachmentSplitter: DynamicSplitter[Mime.Msg] =
    DynamicSplitter.withPriority[Mime.Msg](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitMsgMessage(input.data.toArray)
      }.mapError(e =>
        SplitError(s"MSG attachment split failed: ${e.getMessage}", Mime.msg, Some(e))
      )
    }

  private def splitMsgMessage(data: Array[Byte]): Chunk[DynamicFragment] =
    val msg       = new MAPIMessage(new ByteArrayInputStream(data))
    val fragments = scala.collection.mutable.ListBuffer.empty[DynamicFragment]

    // Extract body
    val bodyText = Option(msg.getTextBody).orElse(Option(msg.getHtmlBody)).getOrElse("")
    val hasBody  = bodyText.nonEmpty

    if hasBody then
      val mime    = if msg.getHtmlBody != null then Mime.html else Mime.plain
      val subj    = Option(msg.getSubject).getOrElse("body")
      val content = Content.fromString(bodyText, mime)
      fragments += DynamicFragment(content, fragments.size, Some(subj))

    // Extract attachments
    val atts = Option(msg.getAttachmentFiles).map(_.toIndexedSeq).getOrElse(IndexedSeq.empty)
    atts.foreach { att =>
      val name = Option(att.getAttachLongFileName)
        .map(_.toString).filter(_.nonEmpty)
        .orElse(Option(att.getAttachFileName).map(_.toString))
        .getOrElse(s"attachment_${fragments.size}")
      val bytes = Option(att.getEmbeddedAttachmentObject)
        .collect { case arr: Array[Byte] => arr }
        .getOrElse(Array.emptyByteArray)
      val ext     = name.split("\\.").lastOption.getOrElse("").toLowerCase
      val mime    = Mime.fromExtension(ext)
      val content = Content.fromChunk(Chunk.fromArray(bytes), mime)
      fragments += DynamicFragment(content, fragments.size, Some(name))
    }

    Chunk.fromIterable(fragments)

  // ============================================================================
  // Archive Splitter (java.util.zip)
  // ============================================================================

  /**
   * Split ZIP archive into individual entries.
   */
  val zipEntrySplitter: DynamicSplitter[Mime.Zip] =
    DynamicSplitter.withPriority[Mime.Zip](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        splitZipArchive(input.data.toArray)
      }.mapError(e => SplitError(s"ZIP split failed: ${e.getMessage}", Mime.zip, Some(e)))
    }

  private def splitZipArchive(data: Array[Byte]): Chunk[DynamicFragment] =
    val fragments      = scala.collection.mutable.ListBuffer.empty[DynamicFragment]
    val zipInputStream = new ZipInputStream(new ByteArrayInputStream(data))

    try
      var entry: ZipEntry = zipInputStream.getNextEntry()
      while entry != null do
        val entryName = entry.getName

        // Skip directories and macOS metadata
        if !entry.isDirectory && !isMacOsMetadata(entryName) then
          val baos   = new ByteArrayOutputStream()
          val buffer = new Array[Byte](8192)
          var len    = 0
          while { len = zipInputStream.read(buffer); len > 0 } do
            baos.write(buffer, 0, len)

          // Determine MIME type from extension
          val ext  = entryName.split("\\.").lastOption.getOrElse("").toLowerCase
          val mime = Mime.fromExtension(ext)

          // Clean entry name for display
          val displayName = entryName.split("/").last

          val content = Content.fromChunk(Chunk.fromArray(baos.toByteArray), mime)
          fragments += DynamicFragment(content, fragments.size, Some(displayName))

        zipInputStream.closeEntry()
        entry = zipInputStream.getNextEntry()
    finally
      zipInputStream.close()

    Chunk.fromIterable(fragments)

  private def isMacOsMetadata(path: String): Boolean =
    path.contains("__MACOSX") || path.contains(".DS_Store") || path.startsWith("._")
