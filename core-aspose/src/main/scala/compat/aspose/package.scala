package com.tjclp.xlcr
package compat

/**
 * Compatibility layer for Aspose classes to handle specific issues with Aspose library usage. In
 * Scala 3, we use this to avoid name conflicts with the com.aspose packages.
 */
package object aspose:
  // Fully qualified references to avoid import conflicts
  // Cells
  type AsposeWorkbook       = com.aspose.cells.Workbook
  type AsposePdfSaveOptions = com.aspose.cells.PdfSaveOptions

  // For constants - use direct paths
  object AsposePageOrientationType:
    val LANDSCAPE = com.aspose.cells.PageOrientationType.LANDSCAPE

  object AsposePaperSizeType:
    val PAPER_A_4 = com.aspose.cells.PaperSizeType.PAPER_A_4

  // Slides
  type AsposePresentation = com.aspose.slides.Presentation

  object AsposeSlidesFormat:
    val Pdf = com.aspose.slides.SaveFormat.Pdf
    val Ppt = com.aspose.slides.SaveFormat.Ppt

  // PDF
  type AsposePdfDocument   = com.aspose.pdf.Document
  type AsposePdfResolution = com.aspose.pdf.devices.Resolution
  type AsposePdfJpegDevice = com.aspose.pdf.devices.JpegDevice
  type AsposePdfPngDevice  = com.aspose.pdf.devices.PngDevice

  object AsposePdfJpegDevice:
    def apply(resolution: AsposePdfResolution, quality: Int): AsposePdfJpegDevice =
      new com.aspose.pdf.devices.JpegDevice(resolution, quality)

  // Words
  type AsposeDocument    = com.aspose.words.Document
  type AsposeLoadOptions = com.aspose.words.LoadOptions

  object AsposeLoadFormat:
    val MHTML = com.aspose.words.LoadFormat.MHTML

  object AsposeWordsFormat:
    val PDF = com.aspose.words.SaveFormat.PDF

  // Email
  object AsposeMailMessage:
    def load(stream: java.io.InputStream): com.aspose.email.MailMessage =
      com.aspose.email.MailMessage.load(stream)

  type AsposeMhtSaveOptions = com.aspose.email.MhtSaveOptions
end aspose
