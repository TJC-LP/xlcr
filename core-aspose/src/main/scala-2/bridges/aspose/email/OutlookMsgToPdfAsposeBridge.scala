package com.tjclp.xlcr
package bridges.aspose.email

import bridges.aspose.HighPrioritySimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ApplicationVndMsOutlook}

import utils.aspose.AsposeLicense
import compat.aspose._

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

/**
 * OutlookMsgToPdfAsposeBridge converts Outlook .msg files (application/vnd.ms-outlook) to PDF.
 * It reuses the same Aspose.Email ➔ Aspose.Words pipeline as the EML variant.
 */
object OutlookMsgToPdfAsposeBridge extends HighPrioritySimpleBridge[ApplicationVndMsOutlook.type, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  // ClassTags required by Bridge
  override implicit val mTag: ClassTag[M] = implicitly[ClassTag[M]]
  implicit val iTag: ClassTag[ApplicationVndMsOutlook.type] = implicitly[ClassTag[ApplicationVndMsOutlook.type]]
  implicit val oTag: ClassTag[ApplicationPdf.type] = implicitly[ClassTag[ApplicationPdf.type]]

  override private[bridges] def inputParser: Parser[ApplicationVndMsOutlook.type, M] = MsgParser
  override private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] = MsgRenderer

  private object MsgParser extends Parser[ApplicationVndMsOutlook.type, M] {
    override def parse(input: M): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing Outlook MSG bytes for Aspose conversion …")
      input
    }
  }

  private object MsgRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering Outlook MSG to PDF via Aspose.Email + Aspose.Words …")

        // Load MSG via Aspose.Email
        val mailMessage = AsposeMailMessage.load(new ByteArrayInputStream(model.data))

        // Save to MHT
        val mhtOut = new ByteArrayOutputStream()
        mailMessage.save(mhtOut, new AsposeMhtSaveOptions())

        // Load MHT via Aspose.Words and save to PDF
        val doc = new AsposeDocument(new ByteArrayInputStream(mhtOut.toByteArray()), new AsposeLoadOptions())
        val pdfOut = new ByteArrayOutputStream()
        doc.save(pdfOut, AsposeWordsFormat.PDF)

        val pdf = pdfOut.toByteArray
        pdfOut.close(); mhtOut.close()

        logger.info(s"MSG → PDF successful, size = ${pdf.length} bytes")
        FileContent[ApplicationPdf.type](pdf, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error("MSG → PDF conversion failed", ex)
          throw RendererError("MSG to PDF conversion failed: " + ex.getMessage, Some(ex))
      }
    }
  }
}
