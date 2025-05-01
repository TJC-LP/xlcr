package com.tjclp.xlcr
package bridges.aspose.powerpoint

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

object PowerPointPptxToPdfAsposeBridge
    extends HighPrioritySimpleBridge[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  given ClassTag[M] = summon[ClassTag[M]]
  given ClassTag[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type] =
    summon[ClassTag[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type]]
  given ClassTag[ApplicationPdf.type] = summon[ClassTag[ApplicationPdf.type]]

  override protected def inputParser: Parser[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type, M] =
    PptxParser
  override protected def outputRenderer: Renderer[M, ApplicationPdf.type] = PptxRenderer

  private object PptxParser
      extends Parser[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type, M] {
    override def parse(input: M): M =
      AsposeLicense.initializeIfNeeded(); logger.info("Parsing PPTX bytes for Aspose.Slides …"); input
  }

  private object PptxRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering PPTX to PDF via Aspose.Slides …")
        val pres = new AsposePresentation(ByteArrayInputStream(model.data))
        val baos = ByteArrayOutputStream()
        pres.save(baos, AsposeSlidesFormat.Pdf)
        pres.dispose()
        val pdf = baos.toByteArray
        baos.close()
        logger.info(s"PPTX → PDF successful, size = ${pdf.length} bytes")
        FileContent[ApplicationPdf.type](pdf, ApplicationPdf)
      catch
        case ex: Exception =>
          logger.error("PPTX → PDF conversion failed", ex)
          throw RendererError("PPTX to PDF conversion failed: " + ex.getMessage, Some(ex))
  }
}
