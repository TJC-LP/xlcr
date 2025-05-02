package com.tjclp.xlcr
package bridges
package email

import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, MessageRfc822}
import utils.aspose.AsposeLicense
import com.aspose.email.{MailMessage, MhtSaveOptions}
import com.aspose.words.{Document, LoadFormat, LoadOptions, SaveFormat}

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/** Common implementation for Email to PDF bridges that works with both Scala 2 and Scala 3.
  * This trait contains all the business logic for converting email files to PDF using Aspose.Email.
  *
  * @tparam I The specific email input MimeType
  */
trait EmailToPdfAsposeBridgeImpl[I <: MimeType]
    extends HighPrioritySimpleBridge[I, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def inputParser: Parser[I, M] =
    EmailToPdfAsposeParser

  override private[bridges] def outputRenderer
      : Renderer[M, ApplicationPdf.type] =
    EmailToPdfAsposeRenderer

  /** Simple parser that just wraps email bytes in a FileContent for direct usage.
    */
  private object EmailToPdfAsposeParser extends Parser[I, M] {
    override def parse(input: FileContent[I]): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info(
        s"Parsing ${input.mimeType.getClass.getSimpleName} bytes for Aspose.Email conversion."
      )
      input
    }
  }

  /** Renderer that performs email to PDF conversion via Aspose.Email and Aspose.Words.
    * The conversion happens in two steps:
    * 1. Convert email to MHTML using Aspose.Email
    * 2. Convert MHTML to PDF using Aspose.Words
    */
  private object EmailToPdfAsposeRenderer
      extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(
          s"Rendering ${model.mimeType.getClass.getSimpleName} to PDF using Aspose.Email."
        )

        // Load the email message from bytes
        val mailMessage = loadEmail(model.data)

        // Convert email to MHTML format (intermediate step)
        val mhtBytes = convertEmailToMhtml(mailMessage)

        // Convert MHTML to PDF
        val pdfBytes = convertMhtmlToPdf(mhtBytes)

        logger.info(
          s"Successfully converted Email to PDF, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during Email -> PDF conversion with Aspose.Email/Words.",
            ex
          )
          throw RendererError(
            s"Email to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
    }

    /** Load the email message from bytes.
      * This method can be overridden by specific implementations if needed.
      */
    protected def loadEmail(data: Array[Byte]): MailMessage = {
      MailMessage.load(new ByteArrayInputStream(data))
    }

    /** Convert MailMessage to MHTML bytes.
      */
    private def convertEmailToMhtml(message: MailMessage): Array[Byte] = {
      val mhtStream = new ByteArrayOutputStream()

      // Create MHTML save options
      val mhtOptions = new MhtSaveOptions()
      // Set options - adjust as needed based on Aspose.Email version
      mhtOptions.setPreserveOriginalBoundaries(true)

      // Save as MHTML
      message.save(mhtStream, mhtOptions)
      val mhtBytes = mhtStream.toByteArray
      mhtStream.close()

      mhtBytes
    }

    /** Convert MHTML bytes to PDF bytes using Aspose.Words.
      */
    private def convertMhtmlToPdf(mhtBytes: Array[Byte]): Array[Byte] = {
      val docStream = new ByteArrayInputStream(mhtBytes)

      // Configure load options for MHTML
      val loadOpts = new LoadOptions()
      loadOpts.setLoadFormat(LoadFormat.MHTML)

      // Load MHTML into Aspose.Words
      val asposeDoc = new Document(docStream, loadOpts)
      docStream.close()

      // Convert to PDF
      val pdfStream = new ByteArrayOutputStream()
      asposeDoc.save(pdfStream, SaveFormat.PDF)
      val pdfBytes = pdfStream.toByteArray
      pdfStream.close()

      pdfBytes
    }
  }
}
