package com.tjclp.xlcr.v2.aspose

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.nio.file.Files

import scala.util.Using

import zio.ZIO

import com.tjclp.xlcr.v2.transform.{ Conversion, TransformError }
import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, Mime }
import com.tjclp.xlcr.utils.aspose.AsposeLicense
import com.tjclp.xlcr.utils.resource.ResourceWrappers.CleanupWrapper

/**
 * Pure given instances for Aspose-based document conversions.
 *
 * These are HIGH priority (100) and will be preferred over LibreOffice conversions when both are
 * available.
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
// Word format conversions (legacy <-> modern)
// =============================================================================

private def convertWordDoc[I <: Mime, O <: Mime](
  input: Content[I],
  saveFormat: Int,
  outputMime: O,
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[O]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()
    val loadOpts = new com.aspose.words.LoadOptions()
    options.password.foreach(loadOpts.setPassword)
    val doc = new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray), loadOpts)
    Using.resource(new CleanupWrapper(doc)) { wrapper =>
      val out = new ByteArrayOutputStream()
      wrapper.resource.save(out, saveFormat)
      Content[O](out.toByteArray, outputMime, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

given asposeDocToDocx: Conversion[Mime.Doc, Mime.Docx] with
  override def name = "Aspose.Words.DocToDocx"
  def convert(input: Content[Mime.Doc]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCX, Mime.docx)

given asposeDocxToDoc: Conversion[Mime.Docx, Mime.Doc] with
  override def name = "Aspose.Words.DocxToDoc"
  def convert(input: Content[Mime.Docx]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOC, Mime.doc)

given asposeDocmToDocx: Conversion[Mime.Docm, Mime.Docx] with
  override def name = "Aspose.Words.DocmToDocx"
  def convert(input: Content[Mime.Docm]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCX, Mime.docx)

given asposeDocmToDoc: Conversion[Mime.Docm, Mime.Doc] with
  override def name = "Aspose.Words.DocmToDoc"
  def convert(input: Content[Mime.Docm]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOC, Mime.doc)

given asposeDocToDocm: Conversion[Mime.Doc, Mime.Docm] with
  override def name = "Aspose.Words.DocToDocm"
  def convert(input: Content[Mime.Doc]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCM, Mime.docm)

given asposeDocxToDocm: Conversion[Mime.Docx, Mime.Docm] with
  override def name = "Aspose.Words.DocxToDocm"
  def convert(input: Content[Mime.Docx]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCM, Mime.docm)

// =============================================================================
// Excel -> PDF
// =============================================================================

given asposeXlsxToPdf: Conversion[Mime.Xlsx, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsxToPdf"

  def convert(input: Content[Mime.Xlsx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out      = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeXlsToPdf: Conversion[Mime.Xls, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsToPdf"

  def convert(input: Content[Mime.Xls]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out      = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeXlsmToPdf: Conversion[Mime.Xlsm, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsmToPdf"

  def convert(input: Content[Mime.Xlsm]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out      = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeXlsbToPdf: Conversion[Mime.Xlsb, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsbToPdf"

  def convert(input: Content[Mime.Xlsb]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out      = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeOdsToPdf: Conversion[Mime.Ods, Mime.Pdf] with
  override def name = "Aspose.Cells.OdsToPdf"

  def convert(input: Content[Mime.Ods]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicense.initializeIfNeeded()
      val workbook = new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
      val out      = new ByteArrayOutputStream()
      workbook.save(out, com.aspose.cells.SaveFormat.PDF)
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// Excel -> HTML
// =============================================================================

private def convertWorkbookToHtml[M <: Mime](
  input: Content[M],
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[Mime.Html]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()
    val loadOpts = new com.aspose.cells.LoadOptions()
    options.password.foreach(loadOpts.setPassword)
    val workbook =
      new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray), loadOpts)
    if options.evaluateFormulas then
      try workbook.calculateFormula()
      catch case _: Exception => ()
    val opts = new com.aspose.cells.HtmlSaveOptions()
    opts.setExportImagesAsBase64(options.embedResources)
    val out = new ByteArrayOutputStream()
    workbook.save(out, opts)
    Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
  }.mapError(TransformError.fromThrowable)

given asposeXlsxToHtml: Conversion[Mime.Xlsx, Mime.Html] with
  override def name                      = "Aspose.Cells.XlsxToHtml"
  def convert(input: Content[Mime.Xlsx]) = convertWorkbookToHtml(input)

given asposeXlsToHtml: Conversion[Mime.Xls, Mime.Html] with
  override def name                     = "Aspose.Cells.XlsToHtml"
  def convert(input: Content[Mime.Xls]) = convertWorkbookToHtml(input)

given asposeXlsmToHtml: Conversion[Mime.Xlsm, Mime.Html] with
  override def name                      = "Aspose.Cells.XlsmToHtml"
  def convert(input: Content[Mime.Xlsm]) = convertWorkbookToHtml(input)

given asposeXlsbToHtml: Conversion[Mime.Xlsb, Mime.Html] with
  override def name                      = "Aspose.Cells.XlsbToHtml"
  def convert(input: Content[Mime.Xlsb]) = convertWorkbookToHtml(input)

given asposeOdsToHtml: Conversion[Mime.Ods, Mime.Html] with
  override def name                     = "Aspose.Cells.OdsToHtml"
  def convert(input: Content[Mime.Ods]) = convertWorkbookToHtml(input)

// =============================================================================
// Excel format conversions (legacy <-> modern)
// =============================================================================

private def convertWorkbook[I <: Mime, O <: Mime](
  input: Content[I],
  saveFormat: Int,
  outputMime: O,
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[O]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()
    val loadOpts = new com.aspose.cells.LoadOptions()
    options.password.foreach(loadOpts.setPassword)
    val workbook =
      new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray), loadOpts)
    if options.evaluateFormulas then
      try workbook.calculateFormula()
      catch case _: Exception => ()
    val out = new ByteArrayOutputStream()
    workbook.save(out, saveFormat)
    Content[O](out.toByteArray, outputMime, input.metadata)
  }.mapError(TransformError.fromThrowable)

// Options-aware Excel -> PDF helper (used by dispatch layer)
private[aspose] def convertWorkbookToPdf[I <: Mime](
  input: Content[I],
  outputMime: Mime.Pdf,
  options: ConvertOptions
): ZIO[Any, TransformError, Content[Mime.Pdf]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()
    val loadOpts = new com.aspose.cells.LoadOptions()
    options.password.foreach(loadOpts.setPassword)
    val workbook =
      new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray), loadOpts)

    if options.evaluateFormulas then
      try workbook.calculateFormula()
      catch case _: Exception => ()

    val sheets = workbook.getWorksheets
    for i <- 0 until sheets.getCount do
      val ws        = sheets.get(i)
      val pageSetup = ws.getPageSetup

      options.paperSize.foreach(ps => pageSetup.setPaperSize(ps.asposeCellsValue))
      options.landscape.foreach { isLandscape =>
        pageSetup.setOrientation(
          if isLandscape then com.aspose.cells.PageOrientationType.LANDSCAPE
          else com.aspose.cells.PageOrientationType.PORTRAIT
        )
      }

    // Hide sheets not in the selection
    if options.sheetNames.nonEmpty then
      for i <- 0 until sheets.getCount do
        val ws = sheets.get(i)
        if !options.sheetNames.contains(ws.getName) then ws.setVisible(false)

    val pdfSaveOpts = new com.aspose.cells.PdfSaveOptions()
    if options.oneSheetPerPage then pdfSaveOpts.setOnePagePerSheet(true)

    val out = new ByteArrayOutputStream()
    workbook.save(out, pdfSaveOpts)
    Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
  }.mapError(TransformError.fromThrowable)

// Options-aware PowerPoint -> HTML helper (used by dispatch layer)
private[aspose] def convertPresentationToHtml[I <: Mime](
  input: Content[I],
  options: ConvertOptions
): ZIO[Any, TransformError, Content[Mime.Html]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()
    val loadOpts = new com.aspose.slides.LoadOptions()
    options.password.foreach(loadOpts.setPassword)
    val pres =
      new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray), loadOpts)
    try
      val target = if options.stripMasters then
        val blank = new com.aspose.slides.Presentation()
        if blank.getSlides.size() > 0 then blank.getSlides.removeAt(0)
        for i <- 0 until pres.getSlides.size() do
          blank.getSlides.addClone(pres.getSlides.get_Item(i))
        blank
      else
        pres

      val out = new ByteArrayOutputStream()
      target.save(out, com.aspose.slides.SaveFormat.Html)
      Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
    finally
      pres.dispose()
  }.mapError(TransformError.fromThrowable)

// Options-aware PDF -> HTML helper (used by dispatch layer)
private[aspose] def convertPdfToHtml(
  input: Content[Mime.Pdf],
  options: ConvertOptions
): ZIO[Any, TransformError, Content[Mime.Html]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()
    val document = options.password match
      case Some(pwd) =>
        new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray), pwd)
      case None =>
        new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray))
    try
      val saveOptions = new com.aspose.pdf.HtmlSaveOptions()
      saveOptions.setFixedLayout(!options.flowingLayout)
      if options.embedResources then
        saveOptions.setPartsEmbeddingMode(
          com.aspose.pdf.HtmlSaveOptions.PartsEmbeddingModes.EmbedAllIntoHtml
        )
        saveOptions.setRasterImagesSavingMode(
          com.aspose.pdf.HtmlSaveOptions.RasterImagesSavingModes.AsEmbeddedPartsOfPngPageBackground
        )
      val out = new ByteArrayOutputStream()
      document.save(out, saveOptions)
      Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
    finally
      document.close()
  }.mapError(TransformError.fromThrowable)

given asposeXlsToXlsx: Conversion[Mime.Xls, Mime.Xlsx] with
  override def name = "Aspose.Cells.XlsToXlsx"
  def convert(input: Content[Mime.Xls]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSX, Mime.xlsx)

given asposeXlsxToXls: Conversion[Mime.Xlsx, Mime.Xls] with
  override def name = "Aspose.Cells.XlsxToXls"
  def convert(input: Content[Mime.Xlsx]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.EXCEL_97_TO_2003, Mime.xls)

given asposeXlsmToXlsx: Conversion[Mime.Xlsm, Mime.Xlsx] with
  override def name = "Aspose.Cells.XlsmToXlsx"
  def convert(input: Content[Mime.Xlsm]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSX, Mime.xlsx)

given asposeXlsbToXlsx: Conversion[Mime.Xlsb, Mime.Xlsx] with
  override def name = "Aspose.Cells.XlsbToXlsx"
  def convert(input: Content[Mime.Xlsb]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSX, Mime.xlsx)

given asposeXlsxToXlsm: Conversion[Mime.Xlsx, Mime.Xlsm] with
  override def name = "Aspose.Cells.XlsxToXlsm"
  def convert(input: Content[Mime.Xlsx]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSM, Mime.xlsm)

given asposeXlsxToXlsb: Conversion[Mime.Xlsx, Mime.Xlsb] with
  override def name = "Aspose.Cells.XlsxToXlsb"
  def convert(input: Content[Mime.Xlsx]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSB, Mime.xlsb)

given asposeOdsToXlsx: Conversion[Mime.Ods, Mime.Xlsx] with
  override def name = "Aspose.Cells.OdsToXlsx"
  def convert(input: Content[Mime.Ods]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSX, Mime.xlsx)

given asposeXlsxToOds: Conversion[Mime.Xlsx, Mime.Ods] with
  override def name = "Aspose.Cells.XlsxToOds"
  def convert(input: Content[Mime.Xlsx]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.ODS, Mime.ods)

private def slidesFileExtension(saveFormat: Int): String =
  if saveFormat == com.aspose.slides.SaveFormat.Pptx then ".pptx"
  else if saveFormat == com.aspose.slides.SaveFormat.Ppt then ".ppt"
  else ".bin"

/**
 * Aspose.Slides stream saves are unstable in Graal native for some PPT/PPTX paths. Save to a temp
 * file and read bytes back to keep Aspose fidelity while avoiding that path.
 */
private def savePresentationToBytes(
  presentation: com.aspose.slides.Presentation,
  saveFormat: Int
): Array[Byte] =
  val tempPath = Files.createTempFile("xlcr-aspose-slides-", slidesFileExtension(saveFormat))
  try
    presentation.save(tempPath.toString, saveFormat)
    Files.readAllBytes(tempPath)
  finally
    Files.deleteIfExists(tempPath)

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
        val bytes = savePresentationToBytes(pres, com.aspose.slides.SaveFormat.Pptx)
        Content[Mime.Pptx](bytes, Mime.pptx, input.metadata)
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
        val bytes = savePresentationToBytes(pres, com.aspose.slides.SaveFormat.Ppt)
        Content[Mime.Ppt](bytes, Mime.ppt, input.metadata)
      finally
        pres.dispose()
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// PowerPoint format conversions (legacy <-> modern)
// =============================================================================

private def convertPresentation[I <: Mime, O <: Mime](
  input: Content[I],
  saveFormat: Int,
  outputMime: O,
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[O]] =
  ZIO.attempt {
    AsposeLicense.initializeIfNeeded()
    val loadOpts = new com.aspose.slides.LoadOptions()
    options.password.foreach(loadOpts.setPassword)
    val pres =
      new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray), loadOpts)
    try
      val bytes = savePresentationToBytes(pres, saveFormat)
      Content[O](bytes, outputMime, input.metadata)
    finally
      pres.dispose()
  }.mapError(TransformError.fromThrowable)

given asposePptToPptx: Conversion[Mime.Ppt, Mime.Pptx] with
  override def name = "Aspose.Slides.PptToPptx"
  def convert(input: Content[Mime.Ppt]) =
    convertPresentation(input, com.aspose.slides.SaveFormat.Pptx, Mime.pptx)

given asposePptxToPpt: Conversion[Mime.Pptx, Mime.Ppt] with
  override def name = "Aspose.Slides.PptxToPpt"
  def convert(input: Content[Mime.Pptx]) =
    convertPresentation(input, com.aspose.slides.SaveFormat.Ppt, Mime.ppt)

given asposePptmToPptx: Conversion[Mime.Pptm, Mime.Pptx] with
  override def name = "Aspose.Slides.PptmToPptx"
  def convert(input: Content[Mime.Pptm]) =
    convertPresentation(input, com.aspose.slides.SaveFormat.Pptx, Mime.pptx)

given asposePptmToPpt: Conversion[Mime.Pptm, Mime.Ppt] with
  override def name = "Aspose.Slides.PptmToPpt"
  def convert(input: Content[Mime.Pptm]) =
    convertPresentation(input, com.aspose.slides.SaveFormat.Ppt, Mime.ppt)

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
        saveOptions.setPartsEmbeddingMode(
          com.aspose.pdf.HtmlSaveOptions.PartsEmbeddingModes.EmbedAllIntoHtml
        )
        saveOptions.setRasterImagesSavingMode(
          com.aspose.pdf.HtmlSaveOptions.RasterImagesSavingModes.AsEmbeddedPartsOfPngPageBackground
        )
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
        val bytes = savePresentationToBytes(pres, com.aspose.slides.SaveFormat.Pptx)
        Content[Mime.Pptx](bytes, Mime.pptx, input.metadata)
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
        val bytes = savePresentationToBytes(pres, com.aspose.slides.SaveFormat.Ppt)
        Content[Mime.Ppt](bytes, Mime.ppt, input.metadata)
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
        val pngDevice  = new com.aspose.pdf.devices.PngDevice(resolution)
        val out        = new ByteArrayOutputStream()
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
        val out        = new ByteArrayOutputStream()
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
      val page     = document.getPages.add()
      val image    = new com.aspose.pdf.Image()
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
      val page     = document.getPages.add()
      val image    = new com.aspose.pdf.Image()
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
      val document =
        new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray), htmlOptions)
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
