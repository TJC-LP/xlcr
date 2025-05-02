package com.tjclp.xlcr
package compat.aspose

/**
 * Helper class for working with Aspose libraries in a way that
 * avoids the package name conflicts in the Aspose libraries.
 * 
 * This approach uses fully qualified names for everything, avoiding
 * the import conflicts that cause errors in Scala 3.
 */
object AsposeBridge {
  
  // ===== CELLS =====
  object Cells {
    def createWorkbook(stream: java.io.InputStream): com.aspose.cells.Workbook = 
      new com.aspose.cells.Workbook(stream)
      
    def createWorkbook(stream: java.io.InputStream, options: com.aspose.cells.LoadOptions): com.aspose.cells.Workbook =
      new com.aspose.cells.Workbook(stream, options)
      
    def createLoadOptions(): com.aspose.cells.LoadOptions =
      new com.aspose.cells.LoadOptions()
      
    def createPdfSaveOptions(): com.aspose.cells.PdfSaveOptions =
      new com.aspose.cells.PdfSaveOptions()
      
    val LANDSCAPE_ORIENTATION: Int = com.aspose.cells.PageOrientationType.LANDSCAPE
    val PAPER_A4: Int = com.aspose.cells.PaperSizeType.PAPER_A_4
    val LOAD_FORMAT_ODS: Int = com.aspose.cells.LoadFormat.ODS
  }
  
  // ===== SLIDES =====
  object Slides {
    def createPresentation(stream: java.io.InputStream): com.aspose.slides.Presentation =
      new com.aspose.slides.Presentation(stream)
      
    val PDF_FORMAT: Int = com.aspose.slides.SaveFormat.Pdf
  }
  
  // ===== WORDS =====
  object Words {
    def createDocument(stream: java.io.InputStream): com.aspose.words.Document =
      new com.aspose.words.Document(stream)
    
    def createDocument(stream: java.io.InputStream, options: com.aspose.words.LoadOptions): com.aspose.words.Document =
      new com.aspose.words.Document(stream, options)
      
    def createLoadOptions(): com.aspose.words.LoadOptions =
      new com.aspose.words.LoadOptions()
      
    val MHTML_FORMAT: Int = com.aspose.words.LoadFormat.MHTML
    val PDF_FORMAT: Int = com.aspose.words.SaveFormat.PDF
  }
  
  // ===== PDF =====
  object Pdf {
    def createDocument(stream: java.io.InputStream): com.aspose.pdf.Document =
      new com.aspose.pdf.Document(stream)
      
    def createResolution(dpi: Int): com.aspose.pdf.devices.Resolution =
      new com.aspose.pdf.devices.Resolution(dpi)
      
    def createJpegDevice(resolution: com.aspose.pdf.devices.Resolution): com.aspose.pdf.devices.JpegDevice =
      new com.aspose.pdf.devices.JpegDevice(resolution)
      
    def createJpegDevice(resolution: com.aspose.pdf.devices.Resolution, quality: Int): com.aspose.pdf.devices.JpegDevice =
      new com.aspose.pdf.devices.JpegDevice(resolution, quality)
      
    def createPngDevice(resolution: com.aspose.pdf.devices.Resolution): com.aspose.pdf.devices.PngDevice =
      new com.aspose.pdf.devices.PngDevice(resolution)
  }
  
  // ===== EMAIL =====
  object Email {
    def loadMailMessage(stream: java.io.InputStream): com.aspose.email.MailMessage =
      com.aspose.email.MailMessage.load(stream)
      
    def createMhtSaveOptions(): com.aspose.email.MhtSaveOptions =
      new com.aspose.email.MhtSaveOptions()
  }
}