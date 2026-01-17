package com.tjclp.xlcr.v2.aspose

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import scala.util.Using

import zio.{Chunk, ZIO}

import com.tjclp.xlcr.v2.transform.{Conversion, TransformError}
import com.tjclp.xlcr.v2.types.{Content, Mime}
import com.tjclp.xlcr.utils.aspose.AsposeLicense
import com.tjclp.xlcr.utils.resource.ResourceWrappers.CleanupWrapper

/**
 * Pure given instances for Aspose-based document conversions.
 *
 * These are HIGH priority (100) and will be preferred over LibreOffice
 * conversions when both are available.
 *
 * Import these givens to enable Aspose conversions:
 * {{{
 * import com.tjclp.xlcr.v2.aspose.given
 * }}}
 */

// =============================================================================
// Word -> PDF
// =============================================================================

given asposeDocxToPdf: Conversion[Mime.Docx, Mime.Pdf] with
  override def name = "Aspose.Words.DocxToPdf"

  def convert(input: Content[Mime.Docx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val doc = new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray))
      Using.resource(new CleanupWrapper(doc)) { wrapper =>
        val out = new ByteArrayOutputStream()
        wrapper.resource.save(out, com.aspose.words.SaveFormat.PDF)
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)

given asposeDocToPdf: Conversion[Mime.Doc, Mime.Pdf] with
  override def name = "Aspose.Words.DocToPdf"

  def convert(input: Content[Mime.Doc]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val doc = new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray))
      Using.resource(new CleanupWrapper(doc)) { wrapper =>
        val out = new ByteArrayOutputStream()
        wrapper.resource.save(out, com.aspose.words.SaveFormat.PDF)
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// Excel -> PDF
// =============================================================================

given asposeXlsxToPdf: Conversion[Mime.Xlsx, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsxToPdf"

  def convert(input: Content[Mime.Xlsx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeXlsToPdf: Conversion[Mime.Xls, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsToPdf"

  def convert(input: Content[Mime.Xls]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeXlsmToPdf: Conversion[Mime.Xlsm, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsmToPdf"

  def convert(input: Content[Mime.Xlsm]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeXlsbToPdf: Conversion[Mime.Xlsb, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsbToPdf"

  def convert(input: Content[Mime.Xlsb]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeOdsToPdf: Conversion[Mime.Ods, Mime.Pdf] with
  override def name = "Aspose.Cells.OdsToPdf"

  def convert(input: Content[Mime.Ods]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// PowerPoint -> PDF
// =============================================================================

given asposePptxToPdf: Conversion[Mime.Pptx, Mime.Pdf] with
  override def name = "Aspose.Slides.PptxToPdf"

  def convert(input: Content[Mime.Pptx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val pres = new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
      try
        val out = new ByteArrayOutputStream()
        pres.save(out, com.aspose.slides.SaveFormat.Pdf)
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

given asposePptToPdf: Conversion[Mime.Ppt, Mime.Pdf] with
  override def name = "Aspose.Slides.PptToPdf"

  def convert(input: Content[Mime.Ppt]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val pres = new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
      try
        val out = new ByteArrayOutputStream()
        pres.save(out, com.aspose.slides.SaveFormat.Pdf)
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// PowerPoint <-> HTML
// =============================================================================

given asposePptxToHtml: Conversion[Mime.Pptx, Mime.Html] with
  override def name = "Aspose.Slides.PptxToHtml"

  def convert(input: Content[Mime.Pptx]): ZIO[Any, TransformError, Content[Mime.Html]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val pres = new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
      try
        val out = new ByteArrayOutputStream()
        pres.save(out, com.aspose.slides.SaveFormat.Html)
        Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

given asposePptToHtml: Conversion[Mime.Ppt, Mime.Html] with
  override def name = "Aspose.Slides.PptToHtml"

  def convert(input: Content[Mime.Ppt]): ZIO[Any, TransformError, Content[Mime.Html]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val pres = new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
      try
        val out = new ByteArrayOutputStream()
        pres.save(out, com.aspose.slides.SaveFormat.Html)
        Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

given asposeHtmlToPptx: Conversion[Mime.Html, Mime.Pptx] with
  override def name = "Aspose.Slides.HtmlToPptx"

  def convert(input: Content[Mime.Html]): ZIO[Any, TransformError, Content[Mime.Pptx]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val pres = new com.aspose.slides.Presentation()
      try
        // Remove default empty slide
        if pres.getSlides.size() > 0 then
          pres.getSlides.removeAt(0)
        // Add slides from HTML
        pres.getSlides.addFromHtml(new String(input.data.toArray, "UTF-8"))
        val out = new ByteArrayOutputStream()
        pres.save(out, com.aspose.slides.SaveFormat.Pptx)
        Content[Mime.Pptx](out.toByteArray, Mime.pptx, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

given asposeHtmlToPpt: Conversion[Mime.Html, Mime.Ppt] with
  override def name = "Aspose.Slides.HtmlToPpt"

  def convert(input: Content[Mime.Html]): ZIO[Any, TransformError, Content[Mime.Ppt]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val pres = new com.aspose.slides.Presentation()
      try
        if pres.getSlides.size() > 0 then
          pres.getSlides.removeAt(0)
        pres.getSlides.addFromHtml(new String(input.data.toArray, "UTF-8"))
        val out = new ByteArrayOutputStream()
        pres.save(out, com.aspose.slides.SaveFormat.Ppt)
        Content[Mime.Ppt](out.toByteArray, Mime.ppt, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// PDF -> HTML
// =============================================================================

given asposePdfToHtml: Conversion[Mime.Pdf, Mime.Html] with
  override def name = "Aspose.Pdf.PdfToHtml"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Html]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val document = new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray))
      try
        val saveOptions = new com.aspose.pdf.HtmlSaveOptions()
        saveOptions.setPartsEmbeddingMode(com.aspose.pdf.HtmlSaveOptions.PartsEmbeddingModes.EmbedAllIntoHtml)
        saveOptions.setRasterImagesSavingMode(com.aspose.pdf.HtmlSaveOptions.RasterImagesSavingModes.AsEmbeddedPartsOfPngPageBackground)
        val out = new ByteArrayOutputStream()
        document.save(out, saveOptions)
        Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
      finally
        document.close()
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// PDF -> PowerPoint
// =============================================================================

given asposePdfToPptx: Conversion[Mime.Pdf, Mime.Pptx] with
  override def name = "Aspose.Slides.PdfToPptx"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Pptx]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val pres = new com.aspose.slides.Presentation()
      try
        pres.getSlides.removeAt(0)
        pres.getSlides.addFromPdf(new ByteArrayInputStream(input.data.toArray))
        val out = new ByteArrayOutputStream()
        pres.save(out, com.aspose.slides.SaveFormat.Pptx)
        Content[Mime.Pptx](out.toByteArray, Mime.pptx, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

given asposePdfToPpt: Conversion[Mime.Pdf, Mime.Ppt] with
  override def name = "Aspose.Slides.PdfToPpt"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Ppt]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val pres = new com.aspose.slides.Presentation()
      try
        pres.getSlides.removeAt(0)
        pres.getSlides.addFromPdf(new ByteArrayInputStream(input.data.toArray))
        val out = new ByteArrayOutputStream()
        pres.save(out, com.aspose.slides.SaveFormat.Ppt)
        Content[Mime.Ppt](out.toByteArray, Mime.ppt, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// PDF -> Images
// =============================================================================

given asposePdfToPng: Conversion[Mime.Pdf, Mime.Png] with
  override def name = "Aspose.Pdf.PdfToPng"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Png]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val document = new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray))
      try
        val resolution = new com.aspose.pdf.devices.Resolution(300)
        val pngDevice = new com.aspose.pdf.devices.PngDevice(resolution)
        val out = new ByteArrayOutputStream()
        // Convert first page to PNG
        pngDevice.process(document.getPages.get_Item(1), out)
        Content[Mime.Png](out.toByteArray, Mime.png, input.metadata)
      finally
        document.close()
    }.mapError(TransformError.fromThrowable)

given asposePdfToJpeg: Conversion[Mime.Pdf, Mime.Jpeg] with
  override def name = "Aspose.Pdf.PdfToJpeg"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Jpeg]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val document = new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray))
      try
        val resolution = new com.aspose.pdf.devices.Resolution(300)
        val jpegDevice = new com.aspose.pdf.devices.JpegDevice(resolution)
        val out = new ByteArrayOutputStream()
        jpegDevice.process(document.getPages.get_Item(1), out)
        Content[Mime.Jpeg](out.toByteArray, Mime.jpeg, input.metadata)
      finally
        document.close()
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// Images -> PDF
// =============================================================================

given asposePngToPdf: Conversion[Mime.Png, Mime.Pdf] with
  override def name = "Aspose.Pdf.PngToPdf"

  def convert(input: Content[Mime.Png]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val document = new com.aspose.pdf.Document()
      val page = document.getPages.add()
      val image = new com.aspose.pdf.Image()
      image.setImageStream(new ByteArrayInputStream(input.data.toArray))
      page.getParagraphs.add(image)
      val out = new ByteArrayOutputStream()
      document.save(out)
      document.close()
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeJpegToPdf: Conversion[Mime.Jpeg, Mime.Pdf] with
  override def name = "Aspose.Pdf.JpegToPdf"

  def convert(input: Content[Mime.Jpeg]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val document = new com.aspose.pdf.Document()
      val page = document.getPages.add()
      val image = new com.aspose.pdf.Image()
      image.setImageStream(new ByteArrayInputStream(input.data.toArray))
      page.getParagraphs.add(image)
      val out = new ByteArrayOutputStream()
      document.save(out)
      document.close()
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// HTML -> PDF
// =============================================================================

given asposeHtmlToPdf: Conversion[Mime.Html, Mime.Pdf] with
  override def name = "Aspose.Pdf.HtmlToPdf"

  def convert(input: Content[Mime.Html]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val htmlOptions = new com.aspose.pdf.HtmlLoadOptions()
      val document = new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray), htmlOptions)
      val out = new ByteArrayOutputStream()
      document.save(out)
      document.close()
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// Email -> PDF
// =============================================================================

given asposeEmlToPdf: Conversion[Mime.Eml, Mime.Pdf] with
  override def name = "Aspose.Email.EmlToPdf"

  def convert(input: Content[Mime.Eml]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      // Load email message
      val msg = com.aspose.email.MailMessage.load(new ByteArrayInputStream(input.data.toArray))
      // Convert to MHTML first
      val mhtmlStream = new ByteArrayOutputStream()
      msg.save(mhtmlStream, com.aspose.email.SaveOptions.getDefaultMhtml)
      // Then convert MHTML to PDF using Aspose.Words
      val doc = new com.aspose.words.Document(new ByteArrayInputStream(mhtmlStream.toByteArray))
      Using.resource(new CleanupWrapper(doc)) { wrapper =>
        val out = new ByteArrayOutputStream()
        wrapper.resource.save(out, com.aspose.words.SaveFormat.PDF)
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)

given asposeMsgToPdf: Conversion[Mime.Msg, Mime.Pdf] with
  override def name = "Aspose.Email.MsgToPdf"

  def convert(input: Content[Mime.Msg]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val msg = com.aspose.email.MapiMessage.load(new ByteArrayInputStream(input.data.toArray))
      val mhtmlStream = new ByteArrayOutputStream()
      msg.save(mhtmlStream, com.aspose.email.SaveOptions.getDefaultMhtml)
      val doc = new com.aspose.words.Document(new ByteArrayInputStream(mhtmlStream.toByteArray))
      Using.resource(new CleanupWrapper(doc)) { wrapper =>
        val out = new ByteArrayOutputStream()
        wrapper.resource.save(out, com.aspose.words.SaveFormat.PDF)
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
