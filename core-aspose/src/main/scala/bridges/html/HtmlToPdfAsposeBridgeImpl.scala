package com.tjclp.xlcr
package bridges
package html

import models.FileContent
import parsers.Parser
import renderers.{Renderer, SimpleRenderer}
import types.MimeType.{ApplicationPdf, TextHtml}
import types.{MimeType, Priority}
import utils.aspose.AsposeLicense

import com.aspose.words.{Document, SaveFormat, LoadFormat, LoadOptions}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Common implementation for HtmlToPdfAsposeBridge that works with both Scala 2 and Scala 3.
  * This trait converts HTML documents to PDF using Aspose.Words for simpler HTML
  * or Aspose.PDF for more complex HTML with CSS.
  */
trait HtmlToPdfAsposeBridgeImpl
    extends HighPrioritySimpleBridge[TextHtml.type, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Set priority to HIGH for all Aspose bridges
    */
  override def priority: Priority = Priority.HIGH

  private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    HtmlToPdfAsposeRenderer

  /** Renderer that uses Aspose.Words to convert HTML -> PDF.
    * For simple HTML documents with basic formatting.
    */
  private object HtmlToPdfAsposeRenderer
      extends SimpleRenderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering HTML to PDF using Aspose.Words.")

        // Load the HTML document from bytes
        val inputStream = new ByteArrayInputStream(model.data)

        // Use common implementation for PDF conversion
        val pdfOutput = convertHtmlToPdf(inputStream)

        val pdfBytes = pdfOutput.toByteArray
        pdfOutput.close()

        logger.info(
          s"Successfully converted HTML to PDF, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during HTML -> PDF conversion with Aspose.Words.",
            ex
          )
          throw RendererError(
            s"HTML to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
    }
  }

  /** Convert an HTML document to PDF using Aspose.Words.
    * Common implementation that works for both Scala 2 and Scala 3.
    */
  private def convertHtmlToPdf(
      inputStream: ByteArrayInputStream
  ): ByteArrayOutputStream = {
    // Create load options and explicitly set format to HTML
    val loadOptions = new LoadOptions()
    loadOptions.setLoadFormat(LoadFormat.HTML)

    // Load the HTML document
    val asposeDoc = new Document(inputStream, loadOptions)
    val pdfOutput = new ByteArrayOutputStream()
    
    // Save as PDF
    asposeDoc.save(pdfOutput, SaveFormat.PDF)
    pdfOutput
  }
}