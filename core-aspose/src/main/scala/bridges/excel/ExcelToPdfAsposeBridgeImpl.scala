package com.tjclp.xlcr
package bridges
package excel

import models.FileContent
import parsers.Parser
import renderers.{Renderer, SimpleRenderer}
import types.MimeType
import types.MimeType.ApplicationPdf
import utils.aspose.AsposeLicense

import com.aspose.cells.{
  PageOrientationType,
  PaperSizeType,
  PdfSaveOptions,
  Workbook
}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Generic Excel to PDF bridge implementation that works with both Scala 2 and Scala 3.
  * This trait contains the business logic for converting Excel files to PDF.
  *
  * This implementation handles all Excel formats using the same core conversion logic,
  * parameterized by the specific input MIME type.
  *
  * @tparam I The specific Excel input MimeType
  */
trait ExcelToPdfAsposeBridgeImpl[I <: MimeType]
    extends HighPrioritySimpleBridge[I, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def outputRenderer
      : Renderer[M, ApplicationPdf.type] =
    ExcelToPdfAsposeRenderer

  /** Renderer that performs any Excel format -> PDF conversion via Aspose.Cells.
    * This handles all Excel formats: XLSX, XLS, XLSM, XLSB, ODS, etc.
    */
  private object ExcelToPdfAsposeRenderer
      extends SimpleRenderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(
          s"Rendering ${model.mimeType.getClass.getSimpleName} to PDF using Aspose.Cells."
        )

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

        logger.info(
          s"Successfully converted Excel to PDF; output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during Excel -> PDF conversion with Aspose.Cells.",
            ex
          )
          throw RendererError(
            s"Excel to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
    }
  }
}
