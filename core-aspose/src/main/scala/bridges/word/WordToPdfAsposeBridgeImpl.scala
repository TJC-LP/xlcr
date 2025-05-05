package com.tjclp.xlcr
package bridges
package word

import models.FileContent
import parsers.Parser
import renderers.{Renderer, SimpleRenderer}
import types.MimeType.ApplicationPdf
import types.{MimeType, Priority}
import utils.aspose.AsposeLicense

import com.aspose.words.{Document, SaveFormat}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Common implementation for WordToPdfAsposeBridge that works with both Scala 2 and Scala 3.
  * This trait contains all the business logic for the bridge.
  */
trait WordToPdfAsposeBridgeImpl[I <: MimeType]
    extends HighPrioritySimpleBridge[I, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Set priority to HIGH for all Aspose bridges
    */
  override def priority: Priority = Priority.HIGH

  private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    WordToPdfAsposeRenderer

  /** Renderer that uses Aspose.Words to convert WordDocModel -> PDF.
    */
  private object WordToPdfAsposeRenderer
      extends SimpleRenderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering WordDocModel to PDF using Aspose.Words.")

        // Load the Word document from bytes
        val inputStream = new ByteArrayInputStream(model.data)

        // Use common implementation for PDF conversion
        val pdfOutput = convertDocToPdf(inputStream)

        val pdfBytes = pdfOutput.toByteArray
        pdfOutput.close()

        logger.info(
          s"Successfully converted Word to PDF, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during Word -> PDF conversion with Aspose.Words.",
            ex
          )
          throw RendererError(
            s"Word to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
    }
  }

  /** Convert a Word document to PDF.
    * Common implementation that works for both Scala 2 and Scala 3.
    */
  private def convertDocToPdf(
      inputStream: ByteArrayInputStream
  ): ByteArrayOutputStream = {
    val asposeDoc = new Document(inputStream)
    val pdfOutput = new ByteArrayOutputStream()
    asposeDoc.save(pdfOutput, SaveFormat.PDF)
    pdfOutput
  }
}
