package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.*

import utils.aspose.AsposeLicense
import compat.aspose.*

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

// Common conversion logic using a private trait
trait ExcelLegacyToPdfBridgeImpl[I <: MimeType] extends SimpleBridge[I, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  given ClassTag[M]                                   = summon[ClassTag[M]]
  given ClassTag[I]                                   = summon[ClassTag[I]]
  given ClassTag[ApplicationPdf.type]                 = summon[ClassTag[ApplicationPdf.type]]

  override protected def inputParser: Parser[I, M]          = ExcelLegacyParser.asInstanceOf[Parser[I, M]]
  override protected def outputRenderer: Renderer[M, ApplicationPdf.type] = ExcelLegacyRenderer

  private object ExcelLegacyParser extends Parser[MimeType, M] {
    override def parse(input: FileContent[MimeType]): M =
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing legacy Excel bytes for Aspose.Cells conversion …")
      input.asInstanceOf[M]
  }

  private object ExcelLegacyRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try
        AsposeLicense.initializeIfNeeded()
        logger.info("Converting legacy Excel to PDF using Aspose.Cells …")

        val wb = new AsposeWorkbook(new ByteArrayInputStream(model.data))

        val sheets = wb.getWorksheets
        (0 until sheets.getCount).foreach { i =>
          val ps = sheets.get(i).getPageSetup
          ps.setOrientation(AsposePageOrientationType.LANDSCAPE)
          ps.setPaperSize(AsposePaperSizeType.PAPER_A_4)
        }

        val baos = new ByteArrayOutputStream()
        wb.save(baos, new AsposePdfSaveOptions())
        val pdfBytes = baos.toByteArray
        baos.close()

        logger.info(s"Legacy Excel → PDF successful, size = ${pdfBytes.length} bytes")
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      catch
        case ex: Exception =>
          logger.error("Error during legacy Excel → PDF conversion", ex)
          throw RendererError("Legacy Excel to PDF conversion failed: " + ex.getMessage, Some(ex))
  }
}

// Concrete bridge singletons
object ExcelXlsToPdfAsposeBridge   extends ExcelLegacyToPdfBridgeImpl[ApplicationVndMsExcel.type]
object ExcelXlsmToPdfAsposeBridge  extends ExcelLegacyToPdfBridgeImpl[ApplicationVndMsExcelSheetMacroEnabled.type]
object ExcelXlsbToPdfAsposeBridge  extends ExcelLegacyToPdfBridgeImpl[ApplicationVndMsExcelSheetBinary.type]
