package com.tjclp.xlcr
package bridges.excel

import java.io.ByteArrayOutputStream

import scala.util.Using

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument
import org.slf4j.LoggerFactory

import utils.resource.ResourceWrappers._
import bridges.SimpleBridge
import models.FileContent
import parsers.{ Parser, SimpleParser }
import renderers.{ Renderer, SimpleRenderer }
import types.MimeType.{
  ApplicationVndOasisOpendocumentSpreadsheet,
  ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
}

/**
 * A bridge to convert Excel XLSX files to OpenDocument Spreadsheet (ODS) format
 */
object ExcelToOdsBridge
    extends SimpleBridge[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ApplicationVndOasisOpendocumentSpreadsheet.type
    ] {
  private val logger = LoggerFactory.getLogger(getClass)

  override def inputParser: Parser[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, M] =
    ExcelToOdsParser

  override def outputRenderer: Renderer[M, ApplicationVndOasisOpendocumentSpreadsheet.type] =
    ExcelToOdsRenderer

  /**
   * Simple parser that just wraps Excel bytes in a FileContent for direct usage.
   */
  private object ExcelToOdsParser
      extends SimpleParser[
        ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
        M
      ] {
    override def parse(
      input: FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]
    ): M = {
      logger.info("Parsing Excel XLSX bytes for ODS conversion.")
      input
    }
  }

  /**
   * Renderer that performs the XLSX -> ODS conversion using Apache POI and ODFDOM
   */
  private object ExcelToOdsRenderer
      extends SimpleRenderer[
        M,
        ApplicationVndOasisOpendocumentSpreadsheet.type
      ] {
    override def render(
      model: M
    ): FileContent[ApplicationVndOasisOpendocumentSpreadsheet.type] = {
      try {
        logger.info("Converting Excel XLSX to ODS format.")

        // Create a temporary file to store the Excel content
        val tempFile         = java.io.File.createTempFile("excel-", ".xlsx")
        val tempFileResource = autoCloseable(tempFile.delete())

        val result = Using.Manager { use =>
          // Register temp file cleanup
          use(tempFileResource)

          // Write the Excel data to the temporary file
          val fos = use(new java.io.FileOutputStream(tempFile))
          fos.write(model.data)
          fos.close() // Close early so WorkbookFactory can read it

          // Create an ODS document
          val odsDocument = use(OdfSpreadsheetDocument.newSpreadsheetDocument())

          // Load the Excel workbook
          val excelWorkbook = use(WorkbookFactory.create(tempFile))

          // For each sheet in the Excel workbook
          for (sheetIndex <- 0 until excelWorkbook.getNumberOfSheets) {
            val excelSheet = excelWorkbook.getSheetAt(sheetIndex)
            val sheetName  = excelSheet.getSheetName

            // Create a sheet in the ODS document with the same name
            val odsSheet =
              if (sheetIndex == 0) {
                // The first sheet already exists in a new document
                odsDocument.getTableList.get(0)
              } else {
                // Create additional sheets
                org.odftoolkit.odfdom.doc.table.OdfTable.newTable(odsDocument)
              }

            // Set the sheet name
            odsSheet.setTableName(sheetName)

            // Copy rows and cells from Excel to ODS
            // Note: This is a simplified conversion that transfers cell values
            // but not all formatting and formulas may be perfectly converted
            val rowIterator = excelSheet.rowIterator()
            while (rowIterator.hasNext) {
              val excelRow = rowIterator.next()
              val rowIndex = excelRow.getRowNum

              val cellIterator = excelRow.cellIterator()
              while (cellIterator.hasNext) {
                val excelCell = cellIterator.next()
                val colIndex  = excelCell.getColumnIndex

                // Create or get the cell in the ODS document
                val odsCell = odsSheet.getCellByPosition(colIndex, rowIndex)

                // Transfer the value based on cell type
                import org.apache.poi.ss.usermodel.CellType
                excelCell.getCellType match {
                  case CellType.STRING =>
                    odsCell.setStringValue(excelCell.getStringCellValue)

                  case CellType.NUMERIC =>
                    // Check if it's a date
                    if (
                      org.apache.poi.ss.usermodel.DateUtil
                        .isCellDateFormatted(excelCell)
                    ) {
                      val date     = excelCell.getDateCellValue
                      val calendar = java.util.Calendar.getInstance()
                      calendar.setTime(date)
                      odsCell.setDateValue(calendar)
                    } else {
                      odsCell.setDoubleValue(excelCell.getNumericCellValue)
                    }

                  case CellType.BOOLEAN =>
                    odsCell.setBooleanValue(excelCell.getBooleanCellValue)

                  case CellType.FORMULA =>
                    // For simplicity, we're getting the formula result rather than the formula itself
                    try
                      odsCell.setStringValue(excelCell.toString)
                    catch {
                      case _: Exception =>
                        odsCell.setStringValue("Formula")
                    }

                  case CellType.BLANK =>
                  // Do nothing for blank cells

                  case _ =>
                    // For anything else, just convert to string
                    odsCell.setStringValue(excelCell.toString)
                }
              }
            }
          }

          // Save the ODS document to a byte array
          val odsOutput = use(new ByteArrayOutputStream())
          odsDocument.save(odsOutput)
          val odsBytes = odsOutput.toByteArray

          logger.info(
            s"Successfully converted Excel to ODS; output size = ${odsBytes.length} bytes."
          )
          FileContent[ApplicationVndOasisOpendocumentSpreadsheet.type](
            odsBytes,
            ApplicationVndOasisOpendocumentSpreadsheet
          )
        }
        result.get
      } catch {
        case ex: Exception =>
          logger.error("Error during Excel -> ODS conversion.", ex)
          throw RendererError(
            s"Excel to ODS conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
    }
  }
}
