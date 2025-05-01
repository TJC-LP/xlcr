package com.tjclp.xlcr
package bridges.aspose.powerpoint

import bridges.Bridge
import bridges.aspose.HighPrioritySimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ApplicationVndMsPowerpoint}
import utils.aspose.AsposeLicense
import compat.aspose._

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

/**
 * PowerPointToPdfAsposeBridge converts PowerPoint files (PPT/PPTX)
 * to PDF using Aspose.Slides.
 */
object PowerPointToPdfAsposeBridge extends HighPrioritySimpleBridge[ApplicationVndMsPowerpoint.type, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  // For Scala 2.12 compatibility, provide required ClassTags
  override implicit val mTag: ClassTag[M] = implicitly[ClassTag[M]]
  implicit val tTag: ClassTag[ApplicationVndMsPowerpoint.type] = 
    implicitly[ClassTag[ApplicationVndMsPowerpoint.type]]
  implicit val iTag: ClassTag[ApplicationVndMsPowerpoint.type] = 
    implicitly[ClassTag[ApplicationVndMsPowerpoint.type]]
  implicit val oTag: ClassTag[ApplicationPdf.type] = 
    implicitly[ClassTag[ApplicationPdf.type]]

  override private[bridges] def inputParser: Parser[ApplicationVndMsPowerpoint.type, M] =
    PowerPointToPdfAsposeParser

  override private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
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
        val presentation = new AsposePresentation(inputStream)
        inputStream.close()

        // Prepare an output stream to capture the PDF.
        val pdfOutput = new ByteArrayOutputStream()
        // Save the presentation as PDF using default options.
        presentation.save(pdfOutput, AsposeSlidesFormat.Pdf)
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