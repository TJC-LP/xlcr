package com.tjclp.xlcr.libreoffice

import java.io.File
import java.nio.file.Files

import zio.ZIO

import com.tjclp.xlcr.transform.{ Conversion, TransformError }
import com.tjclp.xlcr.types.{ Content, Mime }
import com.tjclp.xlcr.config.LibreOfficeConfig

/**
 * Pure given instances for LibreOffice-based document conversions.
 *
 * These are DEFAULT priority (0) and will be used as fallbacks when Aspose conversions are not
 * available.
 *
 * LibreOffice must be installed on the system for these to work.
 *
 * Import these givens to enable LibreOffice conversions:
 * {{{
 * import com.tjclp.xlcr.libreoffice.given
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
    val inputFile  = File.createTempFile("xlcr-input-", s".$inputExtension")
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

given libreofficeDocmToPdf: Conversion[Mime.Docm, Mime.Pdf] with
  override def name = "LibreOffice.DocmToPdf"

  def convert(input: Content[Mime.Docm]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "docm", "pdf")
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

given libreofficeXlsbToPdf: Conversion[Mime.Xlsb, Mime.Pdf] with
  override def name = "LibreOffice.XlsbToPdf"

  def convert(input: Content[Mime.Xlsb]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "xlsb", "pdf")
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

given libreofficePptmToPdf: Conversion[Mime.Pptm, Mime.Pdf] with
  override def name = "LibreOffice.PptmToPdf"

  def convert(input: Content[Mime.Pptm]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "pptm", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

// =============================================================================
// OpenDocument / RTF -> PDF (LibreOffice-only)
// =============================================================================

given libreofficeOdtToPdf: Conversion[Mime.Odt, Mime.Pdf] with
  override def name = "LibreOffice.OdtToPdf"

  def convert(input: Content[Mime.Odt]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "odt", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeOdpToPdf: Conversion[Mime.Odp, Mime.Pdf] with
  override def name = "LibreOffice.OdpToPdf"

  def convert(input: Content[Mime.Odp]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "odp", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeOdgToPdf: Conversion[Mime.Odg, Mime.Pdf] with
  override def name = "LibreOffice.OdgToPdf"

  def convert(input: Content[Mime.Odg]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "odg", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeRtfToPdf: Conversion[Mime.Rtf, Mime.Pdf] with
  override def name = "LibreOffice.RtfToPdf"

  def convert(input: Content[Mime.Rtf]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    convertWithLibreOffice(input.data.toArray, "rtf", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

// =============================================================================
// CSV -> PDF / spreadsheet formats
// =============================================================================

given libreofficeCsvToPdf: Conversion[Mime.Csv, Mime.Pdf] with
  override def name = "LibreOffice.CsvToPdf"
  def convert(input: Content[Mime.Csv]) =
    convertWithLibreOffice(input.data.toArray, "csv", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeCsvToXlsx: Conversion[Mime.Csv, Mime.Xlsx] with
  override def name = "LibreOffice.CsvToXlsx"
  def convert(input: Content[Mime.Csv]) =
    convertWithLibreOffice(input.data.toArray, "csv", "xlsx")
      .map(bytes => Content[Mime.Xlsx](bytes, Mime.xlsx, input.metadata))

given libreofficeCsvToOds: Conversion[Mime.Csv, Mime.Ods] with
  override def name = "LibreOffice.CsvToOds"
  def convert(input: Content[Mime.Csv]) =
    convertWithLibreOffice(input.data.toArray, "csv", "ods")
      .map(bytes => Content[Mime.Ods](bytes, Mime.ods, input.metadata))

given libreofficeCsvToHtml: Conversion[Mime.Csv, Mime.Html] with
  override def name = "LibreOffice.CsvToHtml"
  def convert(input: Content[Mime.Csv]) =
    convertWithLibreOffice(input.data.toArray, "csv", "html")
      .map(bytes => Content[Mime.Html](bytes, Mime.html, input.metadata))

// =============================================================================
// HTML conversions
// =============================================================================

given libreofficeHtmlToPdf: Conversion[Mime.Html, Mime.Pdf] with
  override def name = "LibreOffice.HtmlToPdf"
  def convert(input: Content[Mime.Html]) =
    convertWithLibreOffice(input.data.toArray, "html", "pdf")
      .map(bytes => Content[Mime.Pdf](bytes, Mime.pdf, input.metadata))

given libreofficeHtmlToDocx: Conversion[Mime.Html, Mime.Docx] with
  override def name = "LibreOffice.HtmlToDocx"
  def convert(input: Content[Mime.Html]) =
    convertWithLibreOffice(input.data.toArray, "html", "docx")
      .map(bytes => Content[Mime.Docx](bytes, Mime.docx, input.metadata))

given libreofficeHtmlToOdt: Conversion[Mime.Html, Mime.Odt] with
  override def name = "LibreOffice.HtmlToOdt"
  def convert(input: Content[Mime.Html]) =
    convertWithLibreOffice(input.data.toArray, "html", "odt")
      .map(bytes => Content[Mime.Odt](bytes, Mime.odt, input.metadata))

given libreofficeDocxToHtml: Conversion[Mime.Docx, Mime.Html] with
  override def name = "LibreOffice.DocxToHtml"
  def convert(input: Content[Mime.Docx]) =
    convertWithLibreOffice(input.data.toArray, "docx", "html")
      .map(bytes => Content[Mime.Html](bytes, Mime.html, input.metadata))

given libreofficeDocToHtml: Conversion[Mime.Doc, Mime.Html] with
  override def name = "LibreOffice.DocToHtml"
  def convert(input: Content[Mime.Doc]) =
    convertWithLibreOffice(input.data.toArray, "doc", "html")
      .map(bytes => Content[Mime.Html](bytes, Mime.html, input.metadata))

given libreofficeOdtToHtml: Conversion[Mime.Odt, Mime.Html] with
  override def name = "LibreOffice.OdtToHtml"
  def convert(input: Content[Mime.Odt]) =
    convertWithLibreOffice(input.data.toArray, "odt", "html")
      .map(bytes => Content[Mime.Html](bytes, Mime.html, input.metadata))

given libreofficeRtfToHtml: Conversion[Mime.Rtf, Mime.Html] with
  override def name = "LibreOffice.RtfToHtml"
  def convert(input: Content[Mime.Rtf]) =
    convertWithLibreOffice(input.data.toArray, "rtf", "html")
      .map(bytes => Content[Mime.Html](bytes, Mime.html, input.metadata))

// =============================================================================
// ODG Drawing -> SVG / PNG
// =============================================================================

given libreofficeOdgToSvg: Conversion[Mime.Odg, Mime.Svg] with
  override def name = "LibreOffice.OdgToSvg"
  def convert(input: Content[Mime.Odg]) =
    convertWithLibreOffice(input.data.toArray, "odg", "svg")
      .map(bytes => Content[Mime.Svg](bytes, Mime.svg, input.metadata))

given libreofficeOdgToPng: Conversion[Mime.Odg, Mime.Png] with
  override def name = "LibreOffice.OdgToPng"
  def convert(input: Content[Mime.Odg]) =
    convertWithLibreOffice(input.data.toArray, "odg", "png")
      .map(bytes => Content[Mime.Png](bytes, Mime.png, input.metadata))

// =============================================================================
// Document -> Image (first page/slide only)
// =============================================================================

given libreofficeDocxToPng: Conversion[Mime.Docx, Mime.Png] with
  override def name = "LibreOffice.DocxToPng"
  def convert(input: Content[Mime.Docx]) =
    convertWithLibreOffice(input.data.toArray, "docx", "png")
      .map(bytes => Content[Mime.Png](bytes, Mime.png, input.metadata))

given libreofficeDocToPng: Conversion[Mime.Doc, Mime.Png] with
  override def name = "LibreOffice.DocToPng"
  def convert(input: Content[Mime.Doc]) =
    convertWithLibreOffice(input.data.toArray, "doc", "png")
      .map(bytes => Content[Mime.Png](bytes, Mime.png, input.metadata))

given libreofficePptxToPng: Conversion[Mime.Pptx, Mime.Png] with
  override def name = "LibreOffice.PptxToPng"
  def convert(input: Content[Mime.Pptx]) =
    convertWithLibreOffice(input.data.toArray, "pptx", "png")
      .map(bytes => Content[Mime.Png](bytes, Mime.png, input.metadata))

given libreofficePptToPng: Conversion[Mime.Ppt, Mime.Png] with
  override def name = "LibreOffice.PptToPng"
  def convert(input: Content[Mime.Ppt]) =
    convertWithLibreOffice(input.data.toArray, "ppt", "png")
      .map(bytes => Content[Mime.Png](bytes, Mime.png, input.metadata))

given libreofficeDocxToJpeg: Conversion[Mime.Docx, Mime.Jpeg] with
  override def name = "LibreOffice.DocxToJpeg"
  def convert(input: Content[Mime.Docx]) =
    convertWithLibreOffice(input.data.toArray, "docx", "jpg")
      .map(bytes => Content[Mime.Jpeg](bytes, Mime.jpeg, input.metadata))

given libreofficeDocToJpeg: Conversion[Mime.Doc, Mime.Jpeg] with
  override def name = "LibreOffice.DocToJpeg"
  def convert(input: Content[Mime.Doc]) =
    convertWithLibreOffice(input.data.toArray, "doc", "jpg")
      .map(bytes => Content[Mime.Jpeg](bytes, Mime.jpeg, input.metadata))

given libreofficePptxToJpeg: Conversion[Mime.Pptx, Mime.Jpeg] with
  override def name = "LibreOffice.PptxToJpeg"
  def convert(input: Content[Mime.Pptx]) =
    convertWithLibreOffice(input.data.toArray, "pptx", "jpg")
      .map(bytes => Content[Mime.Jpeg](bytes, Mime.jpeg, input.metadata))

given libreofficePptToJpeg: Conversion[Mime.Ppt, Mime.Jpeg] with
  override def name = "LibreOffice.PptToJpeg"
  def convert(input: Content[Mime.Ppt]) =
    convertWithLibreOffice(input.data.toArray, "ppt", "jpg")
      .map(bytes => Content[Mime.Jpeg](bytes, Mime.jpeg, input.metadata))

// =============================================================================
// Format conversions (legacy <-> modern)
// =============================================================================

// Word
given libreofficeDocToDocx: Conversion[Mime.Doc, Mime.Docx] with
  override def name = "LibreOffice.DocToDocx"
  def convert(input: Content[Mime.Doc]) =
    convertWithLibreOffice(input.data.toArray, "doc", "docx")
      .map(bytes => Content[Mime.Docx](bytes, Mime.docx, input.metadata))

given libreofficeDocxToDoc: Conversion[Mime.Docx, Mime.Doc] with
  override def name = "LibreOffice.DocxToDoc"
  def convert(input: Content[Mime.Docx]) =
    convertWithLibreOffice(input.data.toArray, "docx", "doc")
      .map(bytes => Content[Mime.Doc](bytes, Mime.doc, input.metadata))

given libreofficeDocmToDocx: Conversion[Mime.Docm, Mime.Docx] with
  override def name = "LibreOffice.DocmToDocx"
  def convert(input: Content[Mime.Docm]) =
    convertWithLibreOffice(input.data.toArray, "docm", "docx")
      .map(bytes => Content[Mime.Docx](bytes, Mime.docx, input.metadata))

given libreofficeDocmToDoc: Conversion[Mime.Docm, Mime.Doc] with
  override def name = "LibreOffice.DocmToDoc"
  def convert(input: Content[Mime.Docm]) =
    convertWithLibreOffice(input.data.toArray, "docm", "doc")
      .map(bytes => Content[Mime.Doc](bytes, Mime.doc, input.metadata))

// Excel
given libreofficeXlsToXlsx: Conversion[Mime.Xls, Mime.Xlsx] with
  override def name = "LibreOffice.XlsToXlsx"
  def convert(input: Content[Mime.Xls]) =
    convertWithLibreOffice(input.data.toArray, "xls", "xlsx")
      .map(bytes => Content[Mime.Xlsx](bytes, Mime.xlsx, input.metadata))

given libreofficeXlsxToXls: Conversion[Mime.Xlsx, Mime.Xls] with
  override def name = "LibreOffice.XlsxToXls"
  def convert(input: Content[Mime.Xlsx]) =
    convertWithLibreOffice(input.data.toArray, "xlsx", "xls")
      .map(bytes => Content[Mime.Xls](bytes, Mime.xls, input.metadata))

given libreofficeXlsbToXlsx: Conversion[Mime.Xlsb, Mime.Xlsx] with
  override def name = "LibreOffice.XlsbToXlsx"
  def convert(input: Content[Mime.Xlsb]) =
    convertWithLibreOffice(input.data.toArray, "xlsb", "xlsx")
      .map(bytes => Content[Mime.Xlsx](bytes, Mime.xlsx, input.metadata))

given libreofficeXlsmToXlsx: Conversion[Mime.Xlsm, Mime.Xlsx] with
  override def name = "LibreOffice.XlsmToXlsx"
  def convert(input: Content[Mime.Xlsm]) =
    convertWithLibreOffice(input.data.toArray, "xlsm", "xlsx")
      .map(bytes => Content[Mime.Xlsx](bytes, Mime.xlsx, input.metadata))

given libreofficeOdsToXlsx: Conversion[Mime.Ods, Mime.Xlsx] with
  override def name = "LibreOffice.OdsToXlsx"
  def convert(input: Content[Mime.Ods]) =
    convertWithLibreOffice(input.data.toArray, "ods", "xlsx")
      .map(bytes => Content[Mime.Xlsx](bytes, Mime.xlsx, input.metadata))

given libreofficeXlsxToOds: Conversion[Mime.Xlsx, Mime.Ods] with
  override def name = "LibreOffice.XlsxToOds"
  def convert(input: Content[Mime.Xlsx]) =
    convertWithLibreOffice(input.data.toArray, "xlsx", "ods")
      .map(bytes => Content[Mime.Ods](bytes, Mime.ods, input.metadata))

given libreofficeXlsToOds: Conversion[Mime.Xls, Mime.Ods] with
  override def name = "LibreOffice.XlsToOds"
  def convert(input: Content[Mime.Xls]) =
    convertWithLibreOffice(input.data.toArray, "xls", "ods")
      .map(bytes => Content[Mime.Ods](bytes, Mime.ods, input.metadata))

given libreofficeOdsToXls: Conversion[Mime.Ods, Mime.Xls] with
  override def name = "LibreOffice.OdsToXls"
  def convert(input: Content[Mime.Ods]) =
    convertWithLibreOffice(input.data.toArray, "ods", "xls")
      .map(bytes => Content[Mime.Xls](bytes, Mime.xls, input.metadata))

// PowerPoint
given libreofficePptToPptx: Conversion[Mime.Ppt, Mime.Pptx] with
  override def name = "LibreOffice.PptToPptx"
  def convert(input: Content[Mime.Ppt]) =
    convertWithLibreOffice(input.data.toArray, "ppt", "pptx")
      .map(bytes => Content[Mime.Pptx](bytes, Mime.pptx, input.metadata))

given libreofficePptxToPpt: Conversion[Mime.Pptx, Mime.Ppt] with
  override def name = "LibreOffice.PptxToPpt"
  def convert(input: Content[Mime.Pptx]) =
    convertWithLibreOffice(input.data.toArray, "pptx", "ppt")
      .map(bytes => Content[Mime.Ppt](bytes, Mime.ppt, input.metadata))

given libreofficePptmToPptx: Conversion[Mime.Pptm, Mime.Pptx] with
  override def name = "LibreOffice.PptmToPptx"
  def convert(input: Content[Mime.Pptm]) =
    convertWithLibreOffice(input.data.toArray, "pptm", "pptx")
      .map(bytes => Content[Mime.Pptx](bytes, Mime.pptx, input.metadata))

given libreofficePptmToPpt: Conversion[Mime.Pptm, Mime.Ppt] with
  override def name = "LibreOffice.PptmToPpt"
  def convert(input: Content[Mime.Pptm]) =
    convertWithLibreOffice(input.data.toArray, "pptm", "ppt")
      .map(bytes => Content[Mime.Ppt](bytes, Mime.ppt, input.metadata))

// OpenDocument cross-format
given libreofficeOdtToDocx: Conversion[Mime.Odt, Mime.Docx] with
  override def name = "LibreOffice.OdtToDocx"
  def convert(input: Content[Mime.Odt]) =
    convertWithLibreOffice(input.data.toArray, "odt", "docx")
      .map(bytes => Content[Mime.Docx](bytes, Mime.docx, input.metadata))

given libreofficeDocxToOdt: Conversion[Mime.Docx, Mime.Odt] with
  override def name = "LibreOffice.DocxToOdt"
  def convert(input: Content[Mime.Docx]) =
    convertWithLibreOffice(input.data.toArray, "docx", "odt")
      .map(bytes => Content[Mime.Odt](bytes, Mime.odt, input.metadata))

given libreofficeOdpToPptx: Conversion[Mime.Odp, Mime.Pptx] with
  override def name = "LibreOffice.OdpToPptx"
  def convert(input: Content[Mime.Odp]) =
    convertWithLibreOffice(input.data.toArray, "odp", "pptx")
      .map(bytes => Content[Mime.Pptx](bytes, Mime.pptx, input.metadata))

given libreofficePptxToOdp: Conversion[Mime.Pptx, Mime.Odp] with
  override def name = "LibreOffice.PptxToOdp"
  def convert(input: Content[Mime.Pptx]) =
    convertWithLibreOffice(input.data.toArray, "pptx", "odp")
      .map(bytes => Content[Mime.Odp](bytes, Mime.odp, input.metadata))

given libreofficeOdpToPpt: Conversion[Mime.Odp, Mime.Ppt] with
  override def name = "LibreOffice.OdpToPpt"
  def convert(input: Content[Mime.Odp]) =
    convertWithLibreOffice(input.data.toArray, "odp", "ppt")
      .map(bytes => Content[Mime.Ppt](bytes, Mime.ppt, input.metadata))

given libreofficePptToOdp: Conversion[Mime.Ppt, Mime.Odp] with
  override def name = "LibreOffice.PptToOdp"
  def convert(input: Content[Mime.Ppt]) =
    convertWithLibreOffice(input.data.toArray, "ppt", "odp")
      .map(bytes => Content[Mime.Odp](bytes, Mime.odp, input.metadata))

given libreofficeOdtToRtf: Conversion[Mime.Odt, Mime.Rtf] with
  override def name = "LibreOffice.OdtToRtf"
  def convert(input: Content[Mime.Odt]) =
    convertWithLibreOffice(input.data.toArray, "odt", "rtf")
      .map(bytes => Content[Mime.Rtf](bytes, Mime.rtf, input.metadata))

given libreofficeRtfToDocx: Conversion[Mime.Rtf, Mime.Docx] with
  override def name = "LibreOffice.RtfToDocx"
  def convert(input: Content[Mime.Rtf]) =
    convertWithLibreOffice(input.data.toArray, "rtf", "docx")
      .map(bytes => Content[Mime.Docx](bytes, Mime.docx, input.metadata))
