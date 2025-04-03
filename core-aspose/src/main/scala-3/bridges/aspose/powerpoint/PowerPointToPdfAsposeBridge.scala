package com.tjclp.xlcr
package bridges.aspose.powerpoint

import bridges.{Bridge, SimpleBridge}
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ApplicationVndMsPowerpoint}
import utils.aspose.AsposeLicense

import com.aspose.slides.{Presentation, SaveFormat}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * PowerPointToPdfAsposeBridge converts PowerPoint files (PPT/PPTX)
 * to PDF using Aspose.Slides.
 */
object PowerPointToPdfAsposeBridge extends SimpleBridge[ApplicationVndMsPowerpoint.type, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  override protected def inputParser: Parser[ApplicationVndMsPowerpoint.type, M] =
    PowerPointToPdfAsposeParser

  override protected def outputRenderer: Renderer[M, ApplicationPdf.type] =
    PowerPointToPdfAsposeRenderer

  /**
   * Parser that simply wraps the input bytes into a PptDocModel.
   */
  private object PowerPointToPdfAsposeParser extends Parser[ApplicationVndMsPowerpoint.type, M] {
    override def parse(input: M): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing PowerPoint file content into PptDocModel.")
      input
    }
  }

  /**
   * Renderer that uses Aspose.Slides to convert a PptDocModel to PDF.
   */
  private object PowerPointToPdfAsposeRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering PptDocModel to PDF using Aspose.Slides.")

        // Create an input stream from the PowerPoint bytes.
        val inputStream = new ByteArrayInputStream(model.data)
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