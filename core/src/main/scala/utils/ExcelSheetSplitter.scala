package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import org.apache.poi.ss.usermodel.WorkbookFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import scala.jdk.CollectionConverters._

class ExcelSheetSplitter extends DocumentSplitter[MimeType] {

  private val supported = Set(
    MimeType.ApplicationVndMsExcel,
    MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
  )

  override def split(
      content: FileContent[MimeType],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (!cfg.hasStrategy(SplitStrategy.Sheet) || !supported.exists(_ == content.mimeType))
      return Seq(DocChunk(content, "workbook", 0, 1))

    val tempWb = WorkbookFactory.create(new ByteArrayInputStream(content.data))
    val total  = tempWb.getNumberOfSheets
    val sheetNames = (0 until total).map(tempWb.getSheetName)
    tempWb.close()

    sheetNames.zipWithIndex.map { case (name, idx) =>
      val wb = WorkbookFactory.create(new ByteArrayInputStream(content.data))
      val cnt = wb.getNumberOfSheets
      (cnt - 1 to 0 by -1).foreach { i => if (i != idx) wb.removeSheetAt(i) }

      val baos = new ByteArrayOutputStream()
      wb.write(baos)
      wb.close()

      val fc = FileContent(baos.toByteArray, content.mimeType)
      DocChunk(fc, name, idx, total)
    }
  }
}
