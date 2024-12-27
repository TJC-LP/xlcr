package com.tjclp.xlcr
package parsers.excel

import types.MimeType
import com.tjclp.xlcr.models.{Content, SheetData}
import com.tjclp.xlcr.parsers.Parser

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory

import java.nio.file.Path
import scala.util.Try

object ExcelJsonParser extends ExcelParser:
  private val logger = LoggerFactory.getLogger(getClass)

  override def extractContent(input: Path): Try[Content] =
    Try {
      val workbook = WorkbookFactory.create(input.toFile)
      val evaluator = workbook.getCreationHelper.createFormulaEvaluator()
      
      try
        val sheets = (0 until workbook.getNumberOfSheets).map { idx =>
          val sheet = workbook.getSheetAt(idx)
          SheetData.fromSheet(sheet, evaluator)
        }.toList

        val jsonContent = SheetData.toJsonMultiple(sheets)
        
        Content(
          jsonContent.getBytes,
          MimeType.ApplicationJson.mimeType,
          Map("sheets" -> sheets.length.toString)
        )
      finally
        workbook.close()
    }

  override def outputType: MimeType = MimeType.ApplicationJson
