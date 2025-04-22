package com.tjclp.xlcr
package bridges.aspose.powerpoint

import bridges.SimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ApplicationVndOpenXmlFormatsPresentationmlPresentation}

import utils.aspose.AsposeLicense
import compat.aspose._

import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

/**
 * Converts PPTX (application/vnd.openxmlformats-officedocument.presentationml.presentation) → PDF via Aspose.Slides.
 */
object PowerPointPptxToPdfAsposeBridge
    extends SimpleBridge[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type, ApplicationPdf.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  implicit val mTag: ClassTag[M] = implicitly[ClassTag[M]]
  implicit val iTag: ClassTag[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type] =
    implicitly[ClassTag[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type]]
  implicit val oTag: ClassTag[ApplicationPdf.type] = implicitly[ClassTag[ApplicationPdf.type]]

  override protected def inputParser: Parser[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type, M] =
    PptxParser

  override protected def outputRenderer: Renderer[M, ApplicationPdf.type] = PptxRenderer

  private object PptxParser
      extends Parser[ApplicationVndOpenXmlFormatsPresentationmlPresentation.type, M] {
    override def parse(input: M): M = {
      AsposeLicense.initializeIfNeeded()
      logger.info("Parsing PPTX bytes for Aspose.Slides conversion …")
      input
    }
  }

  private object PptxRenderer extends Renderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] = {
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Rendering PPTX to PDF via Aspose.Slides …")

        val pres = new AsposePresentation(new ByteArrayInputStream(model.data))
        val baos = new ByteArrayOutputStream()
        pres.save(baos, AsposeSlidesFormat.Pdf)
        pres.dispose()
        val pdf = baos.toByteArray
        baos.close()
        logger.info(s"PPTX → PDF successful, size = ${pdf.length} bytes")
        FileContent[ApplicationPdf.type](pdf, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error("PPTX → PDF conversion failed", ex)
          throw RendererError("PPTX to PDF conversion failed: " + ex.getMessage, Some(ex))
      }
    }
  }
}
