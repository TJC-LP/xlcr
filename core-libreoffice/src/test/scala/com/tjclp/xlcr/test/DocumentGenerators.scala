package com.tjclp.xlcr
package test

import java.io.ByteArrayOutputStream

/**
 * Utilities for programmatically generating test documents.
 * Creates minimal valid documents for testing without file system dependencies.
 */
object DocumentGenerators {

  /**
   * Create a minimal valid XLSX (Excel 2007+) workbook.
   * Contains one sheet with a single cell containing "Test".
   */
  def createMinimalXlsx(): Array[Byte] = {
    import org.apache.poi.xssf.usermodel.XSSFWorkbook

    val workbook = new XSSFWorkbook()
    try {
      val sheet = workbook.createSheet("Sheet1")
      val row = sheet.createRow(0)
      val cell = row.createCell(0)
      cell.setCellValue("Test")

      val baos = new ByteArrayOutputStream()
      workbook.write(baos)
      baos.toByteArray
    } finally {
      workbook.close()
    }
  }

  /**
   * Create a minimal valid XLSX with multiple cells and formulas.
   * More realistic test document with some complexity.
   */
  def createXlsxWithFormulas(): Array[Byte] = {
    import org.apache.poi.xssf.usermodel.XSSFWorkbook

    val workbook = new XSSFWorkbook()
    try {
      val sheet = workbook.createSheet("Data")

      // Header row
      val headerRow = sheet.createRow(0)
      headerRow.createCell(0).setCellValue("Number")
      headerRow.createCell(1).setCellValue("Doubled")
      headerRow.createCell(2).setCellValue("Sum")

      // Data rows
      for (i <- 1 to 3) {
        val row = sheet.createRow(i)
        row.createCell(0).setCellValue(i.toDouble)
        row.createCell(1).setCellFormula(s"A${i + 1}*2")
      }

      // Sum formula
      val sumRow = sheet.createRow(4)
      sumRow.createCell(0).setCellValue("Total")
      sumRow.createCell(2).setCellFormula("SUM(B2:B4)")

      val baos = new ByteArrayOutputStream()
      workbook.write(baos)
      baos.toByteArray
    } finally {
      workbook.close()
    }
  }

  /**
   * Create a minimal valid DOCX (Word 2007+) document.
   * Contains a single paragraph with text.
   */
  def createMinimalDocx(): Array[Byte] = {
    import org.apache.poi.xwpf.usermodel.XWPFDocument

    val document = new XWPFDocument()
    try {
      val paragraph = document.createParagraph()
      val run = paragraph.createRun()
      run.setText("This is a test document.")

      val baos = new ByteArrayOutputStream()
      document.write(baos)
      baos.toByteArray
    } finally {
      document.close()
    }
  }

  /**
   * Create a DOCX document with multiple paragraphs and formatting.
   * More realistic test document.
   */
  def createDocxWithFormatting(): Array[Byte] = {
    import org.apache.poi.xwpf.usermodel.XWPFDocument

    val document = new XWPFDocument()
    try {
      // Title
      val titlePara = document.createParagraph()
      val titleRun = titlePara.createRun()
      titleRun.setText("Test Document")
      titleRun.setBold(true)
      titleRun.setFontSize(16)

      // Content
      val contentPara = document.createParagraph()
      val contentRun = contentPara.createRun()
      contentRun.setText("This is a test document with some formatting.")

      // List items
      for (i <- 1 to 3) {
        val listPara = document.createParagraph()
        val listRun = listPara.createRun()
        listRun.setText(s"Item $i")
      }

      val baos = new ByteArrayOutputStream()
      document.write(baos)
      baos.toByteArray
    } finally {
      document.close()
    }
  }

  /**
   * Create a minimal valid PPTX (PowerPoint 2007+) presentation.
   * Contains one slide with a title.
   */
  def createMinimalPptx(): Array[Byte] = {
    import org.apache.poi.xslf.usermodel.XMLSlideShow

    val ppt = new XMLSlideShow()
    try {
      val slide = ppt.createSlide()
      val titleShape = slide.createTextBox()
      titleShape.setText("Test Slide")
      titleShape.setAnchor(new java.awt.Rectangle(50, 50, 400, 100))

      val baos = new ByteArrayOutputStream()
      ppt.write(baos)
      baos.toByteArray
    } finally {
      ppt.close()
    }
  }

  /**
   * Create a PPTX presentation with multiple slides.
   * More realistic test document.
   */
  def createPptxWithMultipleSlides(): Array[Byte] = {
    import org.apache.poi.xslf.usermodel.XMLSlideShow

    val ppt = new XMLSlideShow()
    try {
      // Title slide
      val titleSlide = ppt.createSlide()
      val titleBox = titleSlide.createTextBox()
      titleBox.setText("Presentation Title")
      titleBox.setAnchor(new java.awt.Rectangle(50, 100, 600, 100))

      // Content slides
      for (i <- 1 to 2) {
        val slide = ppt.createSlide()
        val textBox = slide.createTextBox()
        textBox.setText(s"Slide $i Content")
        textBox.setAnchor(new java.awt.Rectangle(50, 100, 600, 400))
      }

      val baos = new ByteArrayOutputStream()
      ppt.write(baos)
      baos.toByteArray
    } finally {
      ppt.close()
    }
  }

  /**
   * Create an invalid document (corrupted data) for error testing.
   * Just returns text that's not a valid document.
   */
  def createInvalidDocument(): Array[Byte] = {
    "This is not a valid document file".getBytes("UTF-8")
  }

  /**
   * Create a minimal valid document based on format.
   *
   * @param format The format ("xlsx", "docx", "pptx")
   * @return Byte array containing the document
   */
  def createMinimal(format: String): Array[Byte] = format.toLowerCase match {
    case "xlsx" => createMinimalXlsx()
    case "docx" => createMinimalDocx()
    case "pptx" => createMinimalPptx()
    case _ => throw new IllegalArgumentException(s"Unsupported format: $format")
  }
}
