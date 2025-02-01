package com.tjclp.xlcr
package bridges.aspose.ppt

import bridges.Bridge
import models.{FileContent, Model}
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ApplicationVndMsPowerpoint}
import utils.aspose.AsposeLicense

import com.aspose.slides.{Presentation, SaveFormat}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * Minimal model representing a PowerPoint document.
 */
final case class PptDocModel(bytes: Array[Byte]) extends Model

/**
 * PowerPointToPdfAsposeBridge converts PowerPoint files (PPT/PPTX)
 * to PDF using Aspose.Slides.
 */
object PowerPointToPdfAsposeBridge extends Bridge[PptDocModel, ApplicationVndMsPowerpoint.type, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  override protected def inputParser: Parser[ApplicationVndMsPowerpoint.type, PptDocModel] =
    PowerPointToPdfAsposeParser

  override protected def outputRenderer: Renderer[PptDocModel, ApplicationPdf.type] =
    PowerPointToPdfAsposeRenderer

  /**
   * Parser that simply wraps the input bytes into a PptDocModel.
   */
  private object PowerPointToPdfAsposeParser extends Parser[ApplicationVndMsPowerpoint.type, PptDocModel] {
    override def parse(input: FileContent[ApplicationVndMsPowerpoint.type]): PptDocModel = {
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing PowerPoint file content into PptDocModel.")
      PptDocModel(input.data)
    }
  }

  /**
   * Renderer that uses Aspose.Slides to convert a PptDocModel to PDF.
   */
  private object PowerPointToPdfAsposeRenderer extends Renderer[PptDocModel, ApplicationPdf.type] {
    override def render(model: PptDocModel): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering PptDocModel to PDF using Aspose.Slides.")

        // Create an input stream from the PowerPoint bytes.
        val inputStream = new ByteArrayInputStream(model.bytes)
        // Instantiate a Presentation from the input stream.
        val presentation = new Presentation(inputStream)
        inputStream.close()

        // Prepare an output stream to capture the PDF.
        val pdfOutput = new ByteArrayOutputStream()
        // Save the presentation as PDF using default options.
        presentation.save(pdfOutput, SaveFormat.Pdf)
        // Dispose the presentation to free resources.
        presentation.dispose()

        val pdfBytes = pdfOutput.toByteArray
        pdfOutput.close()

        logger.info(s"PowerPoint to PDF conversion successful, output size = ${pdfBytes.length} bytes.")
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error("Error during PowerPoint to PDF conversion with Aspose.Slides.", ex)
          throw RendererError(s"PowerPoint to PDF conversion failed: ${ex.getMessage}", Some(ex))
      }
    }
  }
}