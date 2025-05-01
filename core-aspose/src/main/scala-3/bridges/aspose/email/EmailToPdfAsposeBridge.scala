package com.tjclp.xlcr
package bridges.aspose.email

import bridges.Bridge
import bridges.aspose.HighPrioritySimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, MessageRfc822}
import utils.aspose.AsposeLicense

import com.aspose.email.{MailMessage, MhtSaveOptions}
import com.aspose.words.{LoadOptions, Document as AsposeDocument, SaveFormat as WordSaveFormat}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * EmailToPdfAsposeBridge converts message/rfc822 (EML or MSG) -> application/pdf.
 *
 * Using Aspose.Email to load the mail, then Aspose.Words to convert the MHT
 * output to PDF.
 */
object EmailToPdfAsposeBridge extends HighPrioritySimpleBridge[MessageRfc822.type, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def inputParser: Parser[MessageRfc822.type, M] =
    EmailToPdfAsposeParser

  override private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    EmailToPdfAsposeRenderer

  /**
   * Parser that simply wraps the input bytes in an EmailDocModel.
   */
  private object EmailToPdfAsposeParser extends Parser[MessageRfc822.type, M] {
    override def parse(input: FileContent[MessageRfc822.type]): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing Email content (message/rfc822) into EmailDocModel.")
      FileContent[MimeType.MessageRfc822.type](input.data, MimeType.MessageRfc822)
    }
  }

  /**
   * Renderer that uses Aspose.Email + Aspose.Words to convert EmailDocModel -> PDF.
   *
   * Approach:
   * 1) Load the MailMessage from EML/MSG bytes.
   * 2) Save it to MHT (in-memory).
   * 3) Load that MHT into Aspose.Words Document.
   * 4) Save the Document as PDF in-memory, returning that as FileContent.
   */
  private object EmailToPdfAsposeRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering EmailDocModel to PDF using Aspose.Email + Aspose.Words.")

        // Step 1: Load mail message from bytes
        val mailMessage = MailMessage.load(new ByteArrayInputStream(model.data))

        // Step 2: Save as MHT in-memory
        val mhtStream = new ByteArrayOutputStream()
        val mhtOptions = new MhtSaveOptions()
        // Optionally set formatting, such as DisplayAddressFields, etc.
        mailMessage.save(mhtStream, mhtOptions)
        val mhtBytes = mhtStream.toByteArray
        mhtStream.close()

        // Step 3: Load MHT with Aspose.Words
        val docStream = new ByteArrayInputStream(mhtBytes)
        val loadOpts = new LoadOptions()
        loadOpts.setLoadFormat(com.aspose.words.LoadFormat.MHTML)

        val asposeDoc = new AsposeDocument(docStream, loadOpts)
        docStream.close()

        // Step 4: Save Document as PDF in-memory
        val pdfOutput = new ByteArrayOutputStream()
        asposeDoc.save(pdfOutput, WordSaveFormat.PDF)
        val pdfBytes = pdfOutput.toByteArray
        pdfOutput.close()

        logger.info(s"Email -> PDF conversion successful, output size = ${pdfBytes.length} bytes.")
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error("Error during Email -> PDF conversion with Aspose.", ex)
          throw RendererError(s"Email to PDF conversion failed: ${ex.getMessage}", Some(ex))
      }
    }
  }
}