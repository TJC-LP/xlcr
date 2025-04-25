package com.tjclp.xlcr
package utils

import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import org.apache.pdfbox.pdmodel.font.PDType1Font

import java.nio.file.{Files, Path}

object TestParserUtils {

  def createMinimalPdfFile(path: Path): Unit = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)

    val contentStream = new PDPageContentStream(doc, page)
    contentStream.beginText()
    contentStream.setFont(PDType1Font.HELVETICA, 12)
    contentStream.newLineAtOffset(100, 700)
    contentStream.showText("Test PDF Content")
    contentStream.endText()
    contentStream.close()

    doc.save(path.toFile)
    doc.close()
  }

  def createJsonTestFile(path: Path, json: String): Unit = {
    Files.write(path, json.getBytes("UTF-8"))
  }

  def writeMarkdownTestFile(path: Path, markdown: String): Unit = {
    Files.write(path, markdown.getBytes("UTF-8"))
  }
}