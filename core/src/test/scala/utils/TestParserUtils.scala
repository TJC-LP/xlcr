package com.tjclp.xlcr
package utils

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts

import java.io.FileOutputStream
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

object TestParserUtils {

  def createMinimalPdfFile(path: Path): Unit = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)

    val contentStream = new PDPageContentStream(doc, page)
    contentStream.beginText()
    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
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