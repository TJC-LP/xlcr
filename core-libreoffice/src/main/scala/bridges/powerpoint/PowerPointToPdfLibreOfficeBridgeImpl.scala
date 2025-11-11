package com.tjclp.xlcr
package bridges
package powerpoint

import java.io.File
import java.nio.file.Files

import org.jodconverter.core.office.OfficeUtils
import org.slf4j.LoggerFactory

import config.LibreOfficeConfig
import models.FileContent
import renderers.{ Renderer, SimpleRenderer }
import types.MimeType.ApplicationPdf
import types.{ MimeType, Priority }

/**
 * Common implementation for PowerPointToPdfLibreOfficeBridge that works with both Scala 2 and Scala
 * 3. This trait contains all the business logic for the bridge using LibreOffice via JODConverter.
 *
 * Supports PowerPoint formats: PPT, PPTX Priority is set to DEFAULT (0) to act as a fallback when
 * Aspose (HIGH priority) is not available.
 */
trait PowerPointToPdfLibreOfficeBridgeImpl[I <: MimeType]
    extends SimpleBridge[I, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Set priority to DEFAULT for LibreOffice bridges (fallback to Aspose)
   */
  override def priority: Priority = Priority.DEFAULT

  private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    PowerPointToPdfLibreOfficeRenderer

  /**
   * Renderer that uses LibreOffice (via JODConverter) to convert PowerPoint -> PDF.
   */
  private object PowerPointToPdfLibreOfficeRenderer
      extends SimpleRenderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try {
        logger.info("Rendering PowerPoint presentation to PDF using LibreOffice.")

        val pdfBytes = convertPowerPointToPdf(model.data)

        logger.info(
          s"Successfully converted PowerPoint to PDF using LibreOffice, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during PowerPoint -> PDF conversion with LibreOffice.",
            ex
          )
          throw RendererError(
            s"PowerPoint to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
  }

  /**
   * Convert a PowerPoint presentation to PDF using LibreOffice. Uses temporary files as
   * JODConverter requires file-based conversion.
   *
   * @param pptBytes
   *   The PowerPoint document as a byte array
   * @return
   *   The PDF document as a byte array
   */
  private def convertPowerPointToPdf(pptBytes: Array[Byte]): Array[Byte] = {
    // Create temporary files for input and output
    val inputFile  = File.createTempFile("xlcr-ppt-input-", ".ppt")
    val outputFile = File.createTempFile("xlcr-pdf-output-", ".pdf")

    try {
      // Write input PowerPoint document to temp file
      Files.write(inputFile.toPath, pptBytes)
      logger.debug(s"Wrote PowerPoint input to temp file: ${inputFile.getAbsolutePath}")

      // Get the LibreOffice converter and perform conversion
      val converter = LibreOfficeConfig.createConverter()
      converter.convert(inputFile).to(outputFile).execute()

      // Read the PDF output
      val pdfBytes = Files.readAllBytes(outputFile.toPath)
      logger.debug(s"Read PDF output from temp file: ${outputFile.getAbsolutePath}")

      pdfBytes
    } finally {
      // Clean up temporary files
      OfficeUtils.stopQuietly(null) // No-op in local mode, but good practice
      if (inputFile.exists()) {
        val deleted = inputFile.delete()
        if (!deleted) {
          logger.warn(s"Failed to delete temp input file: ${inputFile.getAbsolutePath}")
        }
      }
      if (outputFile.exists()) {
        val deleted = outputFile.delete()
        if (!deleted) {
          logger.warn(s"Failed to delete temp output file: ${outputFile.getAbsolutePath}")
        }
      }
    }
  }
}
