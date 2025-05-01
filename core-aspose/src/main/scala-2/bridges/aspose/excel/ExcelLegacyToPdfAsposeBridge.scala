package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.aspose.HighPrioritySimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{
  ApplicationVndOasisOpendocumentSpreadsheet,
  ApplicationPdf,
  ApplicationVndMsExcel,
  ApplicationVndMsExcelSheetMacroEnabled,
  ApplicationVndMsExcelSheetBinary
}

import utils.aspose.AsposeLicense
import compat.aspose._

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

// Reusable implementation shared by the three legacy Excel mime‑types.
trait ExcelLegacyToPdfBridgeImpl[I <: MimeType]
    extends HighPrioritySimpleBridge[I, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def inputParser: Parser[I, M] =
    ExcelLegacyParser.asInstanceOf[Parser[I, M]]
  override private[bridges] def outputRenderer
      : Renderer[M, ApplicationPdf.type] = ExcelLegacyRenderer

  /* Thin parser – just forward bytes */
  private object ExcelLegacyParser extends Parser[MimeType, M] {
    override def parse(input: FileContent[MimeType]): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info("Received legacy Excel bytes for Aspose.Cells conversion …")
      input.asInstanceOf[M]
    }
  }

  /* Renderer: Aspose.Cells workbook → PDF */
  private object ExcelLegacyRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Converting legacy Excel to PDF using Aspose.Cells …")

        val workbook = new AsposeWorkbook(new ByteArrayInputStream(model.data))

        val sheets = workbook.getWorksheets
        for (i <- 0 until sheets.getCount) {
          val ps = sheets.get(i).getPageSetup
          ps.setOrientation(AsposePageOrientationType.LANDSCAPE)
          ps.setPaperSize(AsposePaperSizeType.PAPER_A_4)
        }

        val pdfOpts = new AsposePdfSaveOptions()
        val baos = new ByteArrayOutputStream()
        workbook.save(baos, pdfOpts)

        val pdf = baos.toByteArray
        baos.close()

        logger.info(
          s"Legacy Excel → PDF successful, size = ${pdf.length} bytes"
        )
        FileContent[ApplicationPdf.type](pdf, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error("Error during legacy Excel → PDF conversion", ex)
          throw RendererError(
            "Legacy Excel to PDF conversion failed: " + ex.getMessage,
            Some(ex)
          )
      }
    }
  }
}

// Concrete bridge singletons --------------------------------------------------

object ExcelXlsmToPdfAsposeBridge
    extends ExcelLegacyToPdfBridgeImpl[
      ApplicationVndMsExcelSheetMacroEnabled.type
    ]

object ExcelXlsbToPdfAsposeBridge
    extends ExcelLegacyToPdfBridgeImpl[ApplicationVndMsExcelSheetBinary.type]

object OdsToPdfAsposeBridge
    extends ExcelLegacyToPdfBridgeImpl[
      ApplicationVndOasisOpendocumentSpreadsheet.type
    ]
