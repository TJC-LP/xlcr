package com.tjclp.xlcr
package bridges.aspose.word

import bridges.aspose.HighPrioritySimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ApplicationVndOpenXmlFormatsWordprocessingmlDocument}

import utils.aspose.AsposeLicense

import com.aspose.words.Document
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

/**
 * Converts DOCX (application/vnd.openxmlformats-officedocument.wordprocessingml.document) to PDF via Aspose.Words.
 */
object WordDocxToPdfAsposeBridge
    extends HighPrioritySimpleBridge[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  // ClassTags for SimpleBridge (Scala 2.12)
  override implicit val mTag: ClassTag[M] = implicitly[ClassTag[M]]
  implicit val iTag: ClassTag[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type] =
    implicitly[ClassTag[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type]]
  implicit val oTag: ClassTag[ApplicationPdf.type] = implicitly[ClassTag[ApplicationPdf.type]]

  override private[bridges] def inputParser: Parser[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type, M] =
    DocxParser

  override private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] = DocxRenderer

  private object DocxParser
      extends Parser[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type, M] {
    override def parse(input: M): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing DOCX bytes for Aspose.Words conversion …")
      input
    }
  }

  private object DocxRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering DOCX to PDF via Aspose.Words …")

        val doc = new Document(new ByteArrayInputStream(model.data))
        val baos = new ByteArrayOutputStream()
        doc.save(baos, com.aspose.words.SaveFormat.PDF)

        val pdf = baos.toByteArray
        baos.close()
        logger.info(s"DOCX → PDF successful, size = ${pdf.length} bytes")
        FileContent[ApplicationPdf.type](pdf, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error("DOCX → PDF conversion failed", ex)
          throw RendererError("DOCX to PDF conversion failed: " + ex.getMessage, Some(ex))
      }
    }
  }
}
