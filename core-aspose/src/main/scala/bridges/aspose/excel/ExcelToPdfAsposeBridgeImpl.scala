package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.aspose.HighPrioritySimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{
  ApplicationPdf, 
  ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, 
  ApplicationVndMsExcel,
  ApplicationVndMsExcelSheetMacroEnabled,
  ApplicationVndMsExcelSheetBinary,
  ApplicationVndOasisOpendocumentSpreadsheet
}
import utils.aspose.AsposeLicense

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import com.aspose.cells.{Workbook, PdfSaveOptions, PageOrientationType, PaperSizeType}

/**
 * Generic Excel to PDF bridge implementation that works with both Scala 2 and Scala 3.
 * This trait contains the business logic for converting Excel files to PDF.
 * 
 * This implementation handles all Excel formats using the same core conversion logic,
 * parameterized by the specific input MIME type.
 * 
 * @tparam I The specific Excel input MimeType
 */
trait ExcelToPdfAsposeBridgeImpl[I <: MimeType] extends HighPrioritySimpleBridge[I, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def inputParser: Parser[I, M] =
    ExcelToPdfAsposeParser

  override private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    ExcelToPdfAsposeRenderer

  /**
   * Simple parser that just wraps Excel bytes in a FileContent for direct usage.
   */
  private object ExcelToPdfAsposeParser extends Parser[I, M] {
    override def parse(input: FileContent[I]): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info(s"Parsing ${input.mimeType.getClass.getSimpleName} bytes into a direct file model for Aspose.Cells conversion.")
      input
    }
  }

  /**
   * Renderer that performs any Excel format -> PDF conversion via Aspose.Cells.
   * This handles all Excel formats: XLSX, XLS, XLSM, XLSB, ODS, etc.
   */
  private object ExcelToPdfAsposeRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(s"Rendering ${model.mimeType.getClass.getSimpleName} to PDF using Aspose.Cells.")

        // Load workbook from bytes
        val bais = new ByteArrayInputStream(model.data)
        val workbook = new Workbook(bais)
        bais.close()

        // Optional page setup across all worksheets
        val worksheets = workbook.getWorksheets
        for (i <- 0 until worksheets.getCount) {
          val sheet = worksheets.get(i)
          val pageSetup = sheet.getPageSetup
          // Example: landscape orientation, A4 paper size
          pageSetup.setOrientation(PageOrientationType.LANDSCAPE)
          pageSetup.setPaperSize(PaperSizeType.PAPER_A_4)
        }
        // Optionally define a print area, if desired:
        // pageSetup.setPrintArea("A1:F50")

        // Create PDF save options (you can adjust if needed)
        val pdfOptions = new PdfSaveOptions()

        // Perform save to PDF in-memory
        val pdfOutput = new ByteArrayOutputStream()
        workbook.save(pdfOutput, pdfOptions)
        val pdfBytes = pdfOutput.toByteArray
        pdfOutput.close()

        logger.info(s"Successfully converted Excel to PDF; output size = ${pdfBytes.length} bytes.")
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error("Error during Excel -> PDF conversion with Aspose.Cells.", ex)
          throw RendererError(s"Excel to PDF conversion failed: ${ex.getMessage}", Some(ex))
      }
    }
  }
}

// Format-specific implementations for each Excel format type
// These traits can be extended by the Scala 2 and Scala 3 concrete implementations

/**
 * Implementation specifically for XLSX files (Office Open XML)
 */
trait ExcelXlsxToPdfAsposeBridgeImpl extends 
  ExcelToPdfAsposeBridgeImpl[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]

/**
 * Implementation specifically for XLS files (Legacy Excel)
 */
trait ExcelXlsToPdfAsposeBridgeImpl extends 
  ExcelToPdfAsposeBridgeImpl[ApplicationVndMsExcel.type]

/**
 * Implementation specifically for XLSM files (Macro-enabled Excel)
 */
trait ExcelXlsmToPdfAsposeBridgeImpl extends 
  ExcelToPdfAsposeBridgeImpl[ApplicationVndMsExcelSheetMacroEnabled.type]

/**
 * Implementation specifically for XLSB files (Binary Excel)
 */
trait ExcelXlsbToPdfAsposeBridgeImpl extends 
  ExcelToPdfAsposeBridgeImpl[ApplicationVndMsExcelSheetBinary.type]

/**
 * Implementation specifically for ODS files (OpenDocument Spreadsheet)
 */
trait OdsToPdfAsposeBridgeImpl extends 
  ExcelToPdfAsposeBridgeImpl[ApplicationVndOasisOpendocumentSpreadsheet.type]