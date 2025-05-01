package com.tjclp.xlcr
package bridges.aspose.email

import bridges.aspose.HighPrioritySimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType.*

import utils.aspose.AsposeLicense
import compat.aspose.*

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

object OutlookMsgToPdfAsposeBridge
    extends HighPrioritySimpleBridge[ApplicationVndMsOutlook.type, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  given ClassTag[M]                                    = summon[ClassTag[M]]
  given ClassTag[ApplicationVndMsOutlook.type]         = summon[ClassTag[ApplicationVndMsOutlook.type]]
  given ClassTag[ApplicationPdf.type]                  = summon[ClassTag[ApplicationPdf.type]]

  override private[bridges] def inputParser: Parser[ApplicationVndMsOutlook.type, M] = MsgParser
  override private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] = MsgRenderer

  private object MsgParser extends Parser[ApplicationVndMsOutlook.type, M] {
    override def parse(input: M): M =
      AsposeLicense.initializeIfNeeded(); logger.info("Parsing MSG bytes for Aspose conversion …"); input
  }

  private object MsgRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering MSG to PDF via Aspose.Email + Aspose.Words …")

        val mail = AsposeMailMessage.load(new ByteArrayInputStream(model.data))
        val mhtOut = ByteArrayOutputStream()
        mail.save(mhtOut, new AsposeMhtSaveOptions())

        val doc   = new AsposeDocument(ByteArrayInputStream(mhtOut.toByteArray), new AsposeLoadOptions())
        val pdfOut = ByteArrayOutputStream()
        doc.save(pdfOut, AsposeWordsFormat.PDF)

        val pdf = pdfOut.toByteArray
        pdfOut.close(); mhtOut.close()
        logger.info(s"MSG → PDF successful, size = ${pdf.length} bytes")
        FileContent[ApplicationPdf.type](pdf, ApplicationPdf)
      catch
        case ex: Exception =>
          logger.error("MSG → PDF conversion failed", ex)
          throw RendererError("MSG to PDF conversion failed: " + ex.getMessage, Some(ex))
  }
}
