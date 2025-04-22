package com.tjclp.xlcr
package bridges.aspose.word

import bridges.SimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType.*

import utils.aspose.AsposeLicense
import compat.aspose.*

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

object WordDocxToPdfAsposeBridge
    extends SimpleBridge[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  given ClassTag[M]                                   = summon[ClassTag[M]]
  given ClassTag[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type] =
    summon[ClassTag[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type]]
  given ClassTag[ApplicationPdf.type]                 = summon[ClassTag[ApplicationPdf.type]]

  override protected def inputParser: Parser[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type, M] =
    DocxParser
  override protected def outputRenderer: Renderer[M, ApplicationPdf.type] = DocxRenderer

  private object DocxParser
      extends Parser[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type, M] {
    override def parse(input: M): M =
      AsposeLicense.initializeIfNeeded(); logger.info("Parsing DOCX bytes for Aspose.Words …"); input
  }

  private object DocxRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering DOCX to PDF via Aspose.Words …")
        val doc  = new AsposeDocument(new ByteArrayInputStream(model.data))
        val baos = ByteArrayOutputStream()
        doc.save(baos, AsposeWordsFormat.PDF)
        val pdf = baos.toByteArray
        baos.close()
        logger.info(s"DOCX → PDF successful, size = ${pdf.length} bytes")
        FileContent[ApplicationPdf.type](pdf, ApplicationPdf)
      catch
        case ex: Exception =>
          logger.error("DOCX → PDF conversion failed", ex)
          throw RendererError("DOCX to PDF conversion failed: " + ex.getMessage, Some(ex))
  }
}
