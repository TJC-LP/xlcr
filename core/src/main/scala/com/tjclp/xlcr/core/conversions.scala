package com.tjclp.xlcr.core

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import zio.ZIO

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.{ AutoDetectParser, ParseContext }
import org.apache.tika.sax.{ BodyContentHandler, ToXMLContentHandler, WriteOutContentHandler }
import org.apache.poi.ss.usermodel.{ CellType, DateUtil, WorkbookFactory }
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument

import com.tjclp.xlcr.transform.{ Conversion, ParseError, RenderError }
import com.tjclp.xlcr.types.{ Content, Mime }

/**
 * Core XLCR conversions using Tika and POI with no external dependencies. These serve as the
 * lowest-priority fallback when Aspose and LibreOffice are unavailable.
 *
 * Priority: XLCR Core < LibreOffice < Aspose
 */
object XlcrConversions:

  // Priority for core transforms (lowest)
  private val CORE_PRIORITY = -100

  // ============================================================================
  // Tika Conversions - Universal Fallback
  // ============================================================================

  /**
   * Convert any document to plain text using Apache Tika.
   *
   * This is a catch-all conversion that works with any input format that Tika supports, making it
   * the universal fallback for text extraction.
   */
  val anyToPlainText: Conversion[Mime, Mime.Plain] =
    Conversion.withPriority[Mime, Mime.Plain](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        val parser   = new AutoDetectParser()
        val metadata = new Metadata()
        val context  = new ParseContext()
        val handler  = new BodyContentHandler(-1) // unlimited

        val stream = new ByteArrayInputStream(input.data.toArray)
        try
          parser.parse(stream, handler, metadata, context)
        finally
          stream.close()

        val text = handler.toString
        Content.fromString(text, Mime.plain)
      }.mapError { e =>
        ParseError(s"Tika text extraction failed: ${e.getMessage}", Some(e))
      }
    }

  /**
   * Convert any document to structured XML using Apache Tika.
   *
   * This is a catch-all conversion that extracts structured XML content, preserving document
   * structure when possible.
   */
  val anyToXml: Conversion[Mime, Mime.Xml] =
    Conversion.withPriority[Mime, Mime.Xml](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        val parser     = new AutoDetectParser()
        val metadata   = new Metadata()
        val context    = new ParseContext()
        val xmlHandler = new ToXMLContentHandler()
        val handler    = new WriteOutContentHandler(xmlHandler, -1) // unlimited

        val stream = new ByteArrayInputStream(input.data.toArray)
        try
          parser.parse(stream, handler, metadata, context)
        finally
          stream.close()

        val xml = handler.toString
        Content.fromString(xml, Mime.xml)
      }.mapError { e =>
        ParseError(s"Tika XML extraction failed: ${e.getMessage}", Some(e))
      }
    }

  // ============================================================================
  // POI Conversions
  // ============================================================================

  /**
   * Convert Excel XLSX to OpenDocument Spreadsheet (ODS) using POI + ODFDOM.
   *
   * This conversion preserves:
   *   - Sheet names and structure
   *   - Cell values (strings, numbers, dates, booleans)
   *   - Basic data types
   *
   * Note: Advanced formatting and formulas may not be perfectly preserved.
   */
  @scala.annotation.nowarn(
    "msg=deprecated"
  ) // ODFDOM getTableList/setDateValue — no clear replacement in 0.13.0
  val xlsxToOds: Conversion[Mime.Xlsx, Mime.Ods] =
    Conversion.withPriority[Mime.Xlsx, Mime.Ods](CORE_PRIORITY) { input =>
      ZIO.attemptBlocking {
        // Create a temporary file for Excel (POI WorkbookFactory works better with files)
        val tempFile = java.io.File.createTempFile("excel-", ".xlsx")
        try
          // Write input data to temp file
          val fos = new java.io.FileOutputStream(tempFile)
          try
            fos.write(input.data.toArray)
          finally
            fos.close()

          // Create ODS document
          val odsDocument = OdfSpreadsheetDocument.newSpreadsheetDocument()
          try
            // Load Excel workbook
            val excelWorkbook = WorkbookFactory.create(tempFile)
            try
              // Process each sheet
              for sheetIndex <- 0 until excelWorkbook.getNumberOfSheets do
                val excelSheet = excelWorkbook.getSheetAt(sheetIndex)
                val sheetName  = excelSheet.getSheetName

                // Get or create ODS sheet
                val odsSheet =
                  if sheetIndex == 0 then
                    // First sheet already exists in new document
                    odsDocument.getTableList.get(0)
                  else
                    // Create additional sheets
                    org.odftoolkit.odfdom.doc.table.OdfTable.newTable(odsDocument)

                odsSheet.setTableName(sheetName)

                // Copy rows and cells
                val rowIterator = excelSheet.rowIterator()
                while rowIterator.hasNext do
                  val excelRow = rowIterator.next()
                  val rowIndex = excelRow.getRowNum

                  val cellIterator = excelRow.cellIterator()
                  while cellIterator.hasNext do
                    val excelCell = cellIterator.next()
                    val colIndex  = excelCell.getColumnIndex
                    val odsCell   = odsSheet.getCellByPosition(colIndex, rowIndex)

                    // Transfer value based on cell type
                    excelCell.getCellType match
                      case CellType.STRING =>
                        odsCell.setStringValue(excelCell.getStringCellValue)

                      case CellType.NUMERIC =>
                        if DateUtil.isCellDateFormatted(excelCell) then
                          val date     = excelCell.getDateCellValue
                          val calendar = java.util.Calendar.getInstance()
                          calendar.setTime(date)
                          odsCell.setDateValue(calendar)
                        else
                          odsCell.setDoubleValue(excelCell.getNumericCellValue)

                      case CellType.BOOLEAN =>
                        odsCell.setBooleanValue(excelCell.getBooleanCellValue)

                      case CellType.FORMULA =>
                        // Get formula result as string (formulas themselves may not transfer)
                        try
                          odsCell.setStringValue(excelCell.toString)
                        catch
                          case _: Exception => odsCell.setStringValue("Formula")

                      case CellType.BLANK =>
                      // Skip blank cells

                      case _ =>
                        odsCell.setStringValue(excelCell.toString)
                    end match
                  end while
                end while
              end for
              // Save ODS to byte array
              val baos = new ByteArrayOutputStream()
              try
                odsDocument.save(baos)
                Content(baos.toByteArray, Mime.ods)
              finally
                baos.close()

            finally
              excelWorkbook.close()
          finally
            odsDocument.close()
        finally
          tempFile.delete()
      }.mapError { e =>
        RenderError(s"XLSX to ODS conversion failed: ${e.getMessage}", Some(e))
      }
    }
