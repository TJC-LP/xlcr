package com.tjclp.xlcr
package bridges.aspose.word

import bridges.{Bridge, SimpleBridge}
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationMsWord, ApplicationPdf}
import utils.aspose.AsposeLicense

import com.aspose.words.Document as AsposeDocument
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * WordToPdfAsposeBridge is a Bridge from application/msword to application/pdf using Aspose.Words.
 */
object WordToPdfAsposeBridge extends SimpleBridge[ApplicationMsWord.type, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  override protected def inputParser: Parser[ApplicationMsWord.type, M] =
    WordToPdfAsposeParser

  override protected def outputRenderer: Renderer[M, ApplicationPdf.type] =
    WordToPdfAsposeRenderer

  /**
   * Parser that simply wraps the input bytes in a WordDocModel.
   */
  private object WordToPdfAsposeParser extends Parser[ApplicationMsWord.type, M] {
    override def parse(input: M): M = {
      // Initialize Aspose license if needed
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing Word file content to WordDocModel.")
      input
    }
  }

  /**
   * Renderer that uses Aspose.Words to convert WordDocModel -> PDF.
   */
  private object WordToPdfAsposeRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering WordDocModel to PDF using Aspose.Words.")

        // Load the Word document from bytes
        val inputStream = new ByteArrayInputStream(model.data)
        val asposeDoc = new AsposeDocument(inputStream)

        // Convert to PDF in-memory
        val pdfOutput = new ByteArrayOutputStream()
        asposeDoc.save(pdfOutput, com.aspose.words.SaveFormat.PDF)

        val pdfBytes = pdfOutput.toByteArray
        pdfOutput.close()

        logger.info(s"Successfully converted Word to PDF, output size = ${pdfBytes.length} bytes.")
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error("Error during Word -> PDF conversion with Aspose.Words.", ex)
          throw RendererError(s"Word to PDF conversion failed: ${ex.getMessage}", Some(ex))
      }
    }
  }
}