package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet}
import utils.aspose.AsposeLicense
import compat.aspose._

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

/**
 * ExcelToPdfAsposeBridge converts XLSX (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)
 * to PDF (application/pdf) using Aspose.Cells.
 */
object ExcelToPdfAsposeBridge
  extends SimpleBridge[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  // For Scala 2.12 compatibility, provide required ClassTags
  implicit val mTag: ClassTag[M] = implicitly[ClassTag[M]]
  implicit val tTag: ClassTag[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] = 
    implicitly[ClassTag[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]]
  implicit val iTag: ClassTag[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] = 
    implicitly[ClassTag[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]]
  implicit val oTag: ClassTag[ApplicationPdf.type] = 
    implicitly[ClassTag[ApplicationPdf.type]]

  override protected def inputParser: Parser[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, M] =
    ExcelToPdfAsposeParser

  override protected def outputRenderer: Renderer[M, ApplicationPdf.type] =
    ExcelToPdfAsposeRenderer

  /**
   * Simple parser that just wraps XLSX bytes in a FileContent for direct usage.
   */
  private object ExcelToPdfAsposeParser
    extends Parser[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, M] {
    override def parse(input: FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing Excel XLSX bytes into a direct file model for Aspose.Cells conversion.")
      input
    }
  }

  /**
   * Renderer that performs the XLSX -> PDF conversion via Aspose.Cells.
   */
  private object ExcelToPdfAsposeRenderer
    extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering XLSX to PDF using Aspose.Cells.")

        // Load workbook from bytes
        val bais = new ByteArrayInputStream(model.data)
        val workbook = new AsposeWorkbook(bais)
        bais.close()

        // Optional page setup across all worksheets
        val worksheets = workbook.getWorksheets
        for (i <- 0 until worksheets.getCount) {
          val sheet = worksheets.get(i)
          val pageSetup = sheet.getPageSetup
          // Example: landscape orientation, A4 paper size
          pageSetup.setOrientation(AsposePageOrientationType.LANDSCAPE)
          pageSetup.setPaperSize(AsposePaperSizeType.PAPER_A_4)
        }
        // Optionally define a print area, if desired:
        // pageSetup.setPrintArea("A1:F50")

        // Create PDF save options (you can adjust if needed)
        val pdfOptions = new AsposePdfSaveOptions()

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