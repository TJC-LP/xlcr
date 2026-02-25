package com.tjclp.xlcr.base

import java.io.ByteArrayOutputStream

import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.ss.usermodel.{Row, Sheet, Workbook}
import org.apache.poi.xslf.usermodel.{XMLSlideShow, XSLFTextBox}
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import com.tjclp.xlcr.types.{Content, Mime}

/**
 * Generators for creating in-memory test documents.
 *
 * These helpers create minimal but valid documents for testing
 * transform operations without requiring actual files on disk.
 */
object V2DocumentGenerators:

  /**
   * Create a multi-sheet Excel workbook.
   *
   * @param sheetNames Names for the sheets to create
   * @return Content containing a valid XLSX file
   */
  def xlsxContent(sheetNames: String*): Content[Mime.Xlsx] =
    val workbook: Workbook = new XSSFWorkbook()
    try
      val names = if sheetNames.isEmpty then Seq("Sheet1") else sheetNames
      names.zipWithIndex.foreach { case (name, idx) =>
        val sheet: Sheet = workbook.createSheet(name)
        // Add some test data
        val row: Row = sheet.createRow(0)
        row.createCell(0).setCellValue(s"Data in $name")
        row.createCell(1).setCellValue(idx.toDouble)
      }

      val baos = new ByteArrayOutputStream()
      workbook.write(baos)
      Content(baos.toByteArray, Mime.xlsx)
    finally
      workbook.close()

  /**
   * Create an Excel workbook with specific data.
   *
   * @param sheetData Map of sheet name to rows (each row is a list of cell values)
   * @return Content containing a valid XLSX file
   */
  def xlsxContentWithData(sheetData: Map[String, Seq[Seq[Any]]]): Content[Mime.Xlsx] =
    val workbook: Workbook = new XSSFWorkbook()
    try
      sheetData.foreach { case (sheetName, rows) =>
        val sheet: Sheet = workbook.createSheet(sheetName)
        rows.zipWithIndex.foreach { case (rowData, rowIdx) =>
          val row: Row = sheet.createRow(rowIdx)
          rowData.zipWithIndex.foreach { case (cellValue, colIdx) =>
            val cell = row.createCell(colIdx)
            cellValue match
              case s: String  => cell.setCellValue(s)
              case d: Double  => cell.setCellValue(d)
              case i: Int     => cell.setCellValue(i.toDouble)
              case l: Long    => cell.setCellValue(l.toDouble)
              case b: Boolean => cell.setCellValue(b)
              case other      => cell.setCellValue(other.toString)
          }
        }
      }

      val baos = new ByteArrayOutputStream()
      workbook.write(baos)
      Content(baos.toByteArray, Mime.xlsx)
    finally
      workbook.close()

  /**
   * Create a PowerPoint presentation with slides.
   *
   * @param slideCount Number of slides to create
   * @return Content containing a valid PPTX file
   */
  def pptxContent(slideCount: Int): Content[Mime.Pptx] =
    val ppt = new XMLSlideShow()
    try
      (1 to slideCount).foreach { i =>
        val slide = ppt.createSlide()
        // Add a text box with slide number
        val textBox: XSLFTextBox = slide.createTextBox()
        textBox.setAnchor(new java.awt.Rectangle(50, 50, 400, 100))
        textBox.setText(s"Slide $i content")
      }

      val baos = new ByteArrayOutputStream()
      ppt.write(baos)
      Content(baos.toByteArray, Mime.pptx)
    finally
      ppt.close()

  /**
   * Create a PDF document with pages.
   *
   * @param pageCount Number of pages to create
   * @return Content containing a valid PDF file
   */
  def pdfContent(pageCount: Int): Content[Mime.Pdf] =
    val document = new PDDocument()
    try
      (1 to pageCount).foreach { i =>
        val page = new PDPage(PDRectangle.A4)
        document.addPage(page)

        // Add text to each page
        val contentStream = new PDPageContentStream(document, page)
        try
          contentStream.beginText()
          contentStream.setFont(new PDType1Font(FontName.HELVETICA), 12)
          contentStream.newLineAtOffset(100, 700)
          contentStream.showText(s"Page $i content")
          contentStream.endText()
        finally
          contentStream.close()
      }

      val baos = new ByteArrayOutputStream()
      document.save(baos)
      Content(baos.toByteArray, Mime.pdf)
    finally
      document.close()

  /**
   * Create plain text content.
   *
   * @param text The text content
   * @return Content containing plain text
   */
  def textContent(text: String): Content[Mime.Plain] =
    Content.fromString(text, Mime.plain)

  /**
   * Create plain text content with multiple paragraphs.
   *
   * @param paragraphs The paragraphs to include
   * @return Content containing plain text with blank lines between paragraphs
   */
  def textContentWithParagraphs(paragraphs: String*): Content[Mime.Plain] =
    Content.fromString(paragraphs.mkString("\n\n"), Mime.plain)

  /**
   * Create CSV content.
   *
   * @param rows Rows of data (each row is a sequence of cell values)
   * @return Content containing CSV text
   */
  def csvContent(rows: Seq[Seq[String]]): Content[Mime.Csv] =
    val csvText = rows.map { row =>
      row.map { cell =>
        // Quote cells containing commas, quotes, or newlines
        if cell.contains(",") || cell.contains("\"") || cell.contains("\n") then
          "\"" + cell.replace("\"", "\"\"") + "\""
        else
          cell
      }.mkString(",")
    }.mkString("\n")
    Content.fromString(csvText, Mime.csv)

  /**
   * Create CSV content with header.
   *
   * @param headers Column headers
   * @param rows Data rows
   * @return Content containing CSV text with header row
   */
  def csvContentWithHeader(headers: Seq[String], rows: Seq[Seq[String]]): Content[Mime.Csv] =
    csvContent(headers +: rows)

  /**
   * Create HTML content.
   *
   * @param title Document title
   * @param body Body HTML content
   * @return Content containing HTML
   */
  def htmlContent(title: String, body: String): Content[Mime.Html] =
    val html = s"""<!DOCTYPE html>
                  |<html>
                  |<head><title>$title</title></head>
                  |<body>$body</body>
                  |</html>""".stripMargin
    Content.fromString(html, Mime.html)

  /**
   * Create JSON content.
   *
   * @param json The JSON string
   * @return Content containing JSON
   */
  def jsonContent(json: String): Content[Mime.Json] =
    Content.fromString(json, Mime.json)

  /**
   * Create XML content.
   *
   * @param xml The XML string
   * @return Content containing XML
   */
  def xmlContent(xml: String): Content[Mime.Xml] =
    Content.fromString(xml, Mime.xml)

  /**
   * Create a minimal ZIP archive.
   *
   * @param entries Map of entry name to content bytes
   * @return Content containing a valid ZIP file
   */
  def zipContent(entries: Map[String, Array[Byte]]): Content[Mime.Zip] =
    import java.util.zip.{ZipEntry, ZipOutputStream}

    val baos = new ByteArrayOutputStream()
    val zos = new ZipOutputStream(baos)
    try
      entries.foreach { case (name, data) =>
        val entry = new ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
      }
    finally
      zos.close()

    Content(baos.toByteArray, Mime.zip)

  /**
   * Create a ZIP archive with text files.
   *
   * @param entries Map of filename to text content
   * @return Content containing a valid ZIP file
   */
  def zipContentWithTextFiles(entries: Map[String, String]): Content[Mime.Zip] =
    zipContent(entries.map { case (k, v) => k -> v.getBytes("UTF-8") })

  /**
   * Create binary content with arbitrary bytes.
   *
   * @param bytes The raw bytes
   * @param mime The MIME type
   * @return Content containing the bytes
   */
  def binaryContent[M <: Mime](bytes: Array[Byte], mime: M): Content[M] =
    Content(bytes, mime)

  /**
   * Create empty content of a specific type.
   *
   * @param mime The MIME type
   * @return Empty content of the specified type
   */
  def emptyContent[M <: Mime](mime: M): Content[M] =
    Content.empty(mime)
