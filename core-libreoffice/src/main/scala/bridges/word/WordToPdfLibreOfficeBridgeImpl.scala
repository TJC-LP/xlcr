package com.tjclp.xlcr
package bridges
package word

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
 * Common implementation for WordToPdfLibreOfficeBridge that works with both Scala 2 and Scala 3.
 * This trait contains all the business logic for the bridge using LibreOffice via JODConverter.
 *
 * Priority is set to DEFAULT (0) to act as a fallback when Aspose (HIGH priority) is not available.
 */
trait WordToPdfLibreOfficeBridgeImpl[I <: MimeType]
    extends SimpleBridge[I, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Set priority to DEFAULT for LibreOffice bridges (fallback to Aspose)
   */
  override def priority: Priority = Priority.DEFAULT

  private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    WordToPdfLibreOfficeRenderer

  /**
   * Renderer that uses LibreOffice (via JODConverter) to convert Word -> PDF.
   */
  private object WordToPdfLibreOfficeRenderer
      extends SimpleRenderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try {
        logger.info("Rendering Word document to PDF using LibreOffice.")

        val pdfBytes = convertWordToPdf(model.data)

        logger.info(
          s"Successfully converted Word to PDF using LibreOffice, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during Word -> PDF conversion with LibreOffice.",
            ex
          )
          throw RendererError(
            s"Word to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
  }

  /**
   * Convert a Word document to PDF using LibreOffice. Uses temporary files as JODConverter requires
   * file-based conversion.
   *
   * @param wordBytes
   *   The Word document as a byte array
   * @return
   *   The PDF document as a byte array
   */
  private def convertWordToPdf(wordBytes: Array[Byte]): Array[Byte] = {
    // Create temporary files for input and output
    val inputFile  = File.createTempFile("xlcr-word-input-", ".doc")
    val outputFile = File.createTempFile("xlcr-pdf-output-", ".pdf")

    try {
      // Write input Word document to temp file
      Files.write(inputFile.toPath, wordBytes)
      logger.debug(s"Wrote Word input to temp file: ${inputFile.getAbsolutePath}")

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
