package com.tjclp.xlcr
package bridges
package odf

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
 * Common implementation for ODF (OpenDocument Format) to PDF bridges. This trait handles conversion
 * of LibreOffice native formats (ODS, ODT, ODP) to PDF.
 *
 * Since these are LibreOffice's native formats, this bridge might provide better quality than
 * Aspose for these specific formats, though we still use DEFAULT priority to maintain consistency.
 *
 * Supported formats:
 *   - ODS (OpenDocument Spreadsheet) - Calc
 *   - ODT (OpenDocument Text) - Writer
 *   - ODP (OpenDocument Presentation) - Impress
 */
trait OdfToPdfLibreOfficeBridgeImpl[I <: MimeType]
    extends SimpleBridge[I, ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Set priority to DEFAULT for LibreOffice bridges. Note: Even though these are native LibreOffice
   * formats, we use DEFAULT priority to maintain consistent behavior with other LibreOffice
   * bridges.
   */
  override def priority: Priority = Priority.DEFAULT

  private[bridges] def outputRenderer: Renderer[M, ApplicationPdf.type] =
    OdfToPdfLibreOfficeRenderer

  /**
   * Renderer that uses LibreOffice (via JODConverter) to convert ODF formats -> PDF.
   */
  private object OdfToPdfLibreOfficeRenderer
      extends SimpleRenderer[M, ApplicationPdf.type] {
    override def render(model: M): FileContent[ApplicationPdf.type] =
      try {
        logger.info("Rendering OpenDocument format to PDF using LibreOffice.")

        val pdfBytes = convertOdfToPdf(model.data)

        logger.info(
          s"Successfully converted OpenDocument to PDF using LibreOffice, output size = ${pdfBytes.length} bytes."
        )
        FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)
      } catch {
        case ex: Exception =>
          logger.error(
            "Error during OpenDocument -> PDF conversion with LibreOffice.",
            ex
          )
          throw RendererError(
            s"OpenDocument to PDF conversion failed: ${ex.getMessage}",
            Some(ex)
          )
      }
  }

  /**
   * Convert an OpenDocument format file to PDF using LibreOffice. Uses temporary files as
   * JODConverter requires file-based conversion.
   *
   * @param odfBytes
   *   The OpenDocument file as a byte array
   * @return
   *   The PDF document as a byte array
   */
  private def convertOdfToPdf(odfBytes: Array[Byte]): Array[Byte] = {
    // Create temporary files for input and output
    // Use generic .odf extension - LibreOffice will auto-detect the specific format
    val inputFile  = File.createTempFile("xlcr-odf-input-", ".odf")
    val outputFile = File.createTempFile("xlcr-pdf-output-", ".pdf")

    try {
      // Write input OpenDocument to temp file
      Files.write(inputFile.toPath, odfBytes)
      logger.debug(s"Wrote OpenDocument input to temp file: ${inputFile.getAbsolutePath}")

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
