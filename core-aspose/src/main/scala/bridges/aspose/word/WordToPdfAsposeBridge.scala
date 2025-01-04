package com.tjclp.xlcr
package bridges.aspose.word

import bridges.Bridge
import models.{FileContent, Model}
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationMsWord, ApplicationPdf}

import com.aspose.words.{Document => AsposeDocument}
import com.tjclp.xlcr.utils.aspose.AsposeLicense

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * A minimal model that carries Word document bytes from parse -> render.
 */
final case class WordDocModel(bytes: Array[Byte]) extends Model

/**
 * WordToPdfAsposeBridge is a Bridge from application/msword to application/pdf using Aspose.Words.
 */
object WordToPdfAsposeBridge extends Bridge[WordDocModel, ApplicationMsWord.type, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  override protected def inputParser: Parser[ApplicationMsWord.type, WordDocModel] =
    WordToPdfAsposeParser

  override protected def outputRenderer: Renderer[WordDocModel, ApplicationPdf.type] =
    WordToPdfAsposeRenderer

  /**
   * Parser that simply wraps the input bytes in a WordDocModel.
   */
  private object WordToPdfAsposeParser extends Parser[ApplicationMsWord.type, WordDocModel] {
    override def parse(input: FileContent[ApplicationMsWord.type]): WordDocModel = {
      // Initialize Aspose license if needed
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing Word file content to WordDocModel.")
      WordDocModel(input.data)
    }
  }

  /**
   * Renderer that uses Aspose.Words to convert WordDocModel -> PDF.
   */
  private object WordToPdfAsposeRenderer extends Renderer[WordDocModel, ApplicationPdf.type] {
    override def render(model: WordDocModel): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering WordDocModel to PDF using Aspose.Words.")

        // Load the Word document from bytes
        val inputStream = new ByteArrayInputStream(model.bytes)
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