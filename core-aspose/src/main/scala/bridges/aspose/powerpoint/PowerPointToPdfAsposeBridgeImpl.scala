package com.tjclp.xlcr
package bridges.aspose.powerpoint

import bridges.aspose.HighPrioritySimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndMsPowerpoint,
  ApplicationVndOpenXmlFormatsPresentationmlPresentation
}
import utils.aspose.AsposeLicense

import com.aspose.slides.{Presentation, SaveFormat}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Common implementation for PowerPoint to PDF bridges that works with both Scala 2 and Scala 3.
  * This trait contains all the business logic for converting PowerPoint files to PDF using Aspose.Slides.
  *
  * @tparam I The specific PowerPoint input MimeType
  */
trait PowerPointToPdfAsposeBridgeImpl[I <: MimeType]
    extends HighPrioritySimpleBridge[I, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def inputParser: Parser[I, M] =
    PowerPointToPdfAsposeParser

  override private[bridges] def outputRenderer
      : Renderer[M, ApplicationPdf.type] =
    PowerPointToPdfAsposeRenderer

  /** Simple parser that just wraps PowerPoint bytes in a FileContent for direct usage.
    */
  private object PowerPointToPdfAsposeParser extends Parser[I, M] {
    override def parse(input: FileContent[I]): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info(
        s"Parsing ${input.mimeType.getClass.getSimpleName} bytes for Aspose.Slides conversion."
      )
      input
    }
  }

  /** Renderer that performs PowerPoint to PDF conversion via Aspose.Slides.
    * This works for both PPT and PPTX formats.
    */
  private object PowerPointToPdfAsposeRenderer
      extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(
          s"Rendering ${model.mimeType.getClass.getSimpleName} to PDF using Aspose.Slides."
        )

        val inputStream = new ByteArrayInputStream(model.data)
        val presentation = new Presentation(inputStream)
        inputStream.close()

        val pdfOutput = new ByteArrayOutputStream()
        presentation.save(pdfOutput, SaveFormat.Pdf)
        presentation.dispose()

        val pdfBytes = pdfOutput.toByteArray
        pdfOutput.close()

        logger.info(
          s"Successfully converted PowerPoint to PDF, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during PowerPoint -> PDF conversion with Aspose.Slides.",
            ex
          )
          throw RendererError(
            s"PowerPoint to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
    }
  }
}
