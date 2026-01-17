package com.tjclp.xlcr.v2.libreoffice

import java.io.File
import java.nio.file.Files

import zio.{Chunk, ZIO}

import com.tjclp.xlcr.v2.transform.{Conversion, TransformError}
import com.tjclp.xlcr.v2.types.{Content, Mime}
import com.tjclp.xlcr.config.LibreOfficeConfig

/**
 * Pure given instances for LibreOffice-based document conversions.
 *
 * These are DEFAULT priority (0) and will be used as fallbacks
 * when Aspose conversions are not available.
 *
 * LibreOffice must be installed on the system for these to work.
 *
 * Import these givens to enable LibreOffice conversions:
 * {{{
 * import com.tjclp.xlcr.v2.libreoffice.given
 * }}}
 */

// =============================================================================
// Helper for LibreOffice conversions
// =============================================================================

private def convertWithLibreOffice(
    inputBytes: Array[Byte],
    inputExtension: String,
    outputExtension: String
): ZIO[Any, TransformError, Array[Byte]] =
  ZIO.attemptBlocking {
    val inputFile = File.createTempFile("xlcr-input-", s".$inputExtension")
    val outputFile = File.createTempFile("xlcr-output-", s".$outputExtension")

    try
      Files.write(inputFile.toPath, inputBytes)
      val converter = LibreOfficeConfig.createConverter()
      converter.convert(inputFile).to(outputFile).execute()
      Files.readAllBytes(outputFile.toPath)
    finally
      if inputFile.exists() then inputFile.delete()
      if outputFile.exists() then outputFile.delete()
  }.mapError(TransformError.fromThrowable)

// =============================================================================
// Word -> PDF
// =============================================================================

given libreofficeDocxToPdf: Conversion[Mime.Docx, Mime.Pdf] with
  override def name = "LibreOffice.DocxToPdf"

  def convert(input: Content[Mime.Docx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "docx", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeDocToPdf: Conversion[Mime.Doc, Mime.Pdf] with
  override def name = "LibreOffice.DocToPdf"

  def convert(input: Content[Mime.Doc]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "doc", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

// =============================================================================
// Excel -> PDF
// =============================================================================

given libreofficeXlsxToPdf: Conversion[Mime.Xlsx, Mime.Pdf] with
  override def name = "LibreOffice.XlsxToPdf"

  def convert(input: Content[Mime.Xlsx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "xlsx", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeXlsToPdf: Conversion[Mime.Xls, Mime.Pdf] with
  override def name = "LibreOffice.XlsToPdf"

  def convert(input: Content[Mime.Xls]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "xls", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeXlsmToPdf: Conversion[Mime.Xlsm, Mime.Pdf] with
  override def name = "LibreOffice.XlsmToPdf"

  def convert(input: Content[Mime.Xlsm]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "xlsm", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeOdsToPdf: Conversion[Mime.Ods, Mime.Pdf] with
  override def name = "LibreOffice.OdsToPdf"

  def convert(input: Content[Mime.Ods]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "ods", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

// =============================================================================
// PowerPoint -> PDF
// =============================================================================

given libreofficePptxToPdf: Conversion[Mime.Pptx, Mime.Pdf] with
  override def name = "LibreOffice.PptxToPdf"

  def convert(input: Content[Mime.Pptx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "pptx", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficePptToPdf: Conversion[Mime.Ppt, Mime.Pdf] with
  override def name = "LibreOffice.PptToPdf"

  def convert(input: Content[Mime.Ppt]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "ppt", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

// =============================================================================
// Additional LibreOffice conversions (not available in Aspose)
// =============================================================================

// ODT -> PDF
given libreofficeOdtToPdf: Conversion[Mime.Odt, Mime.Pdf] with
  override def name = "LibreOffice.OdtToPdf"

  def convert(input: Content[Mime.Odt]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "odt", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

// ODP -> PDF
given libreofficeOdpToPdf: Conversion[Mime.Odp, Mime.Pdf] with
  override def name = "LibreOffice.OdpToPdf"

  def convert(input: Content[Mime.Odp]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "odp", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

// RTF -> PDF
given libreofficeRtfToPdf: Conversion[Mime.Rtf, Mime.Pdf] with
  override def name = "LibreOffice.RtfToPdf"

  def convert(input: Content[Mime.Rtf]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "rtf", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))
