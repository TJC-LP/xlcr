package com.tjclp.xlcr.aspose

import java.io.*

import com.tjclp.xlcr.transform.*
import com.tjclp.xlcr.types.*

import zio.ZIO
import zio.blocks.scope.Scope

/**
 * Pure given instances for Aspose-based document conversions.
 *
 * These are HIGH priority (100) and will be preferred over LibreOffice conversions when both are
 * available.
 *
 * Import these givens to enable Aspose conversions:
 * {{{
 * import com.tjclp.xlcr.aspose.given
 * }}}
 */

// =============================================================================
// Word -> PDF
// =============================================================================

given asposeDocxToPdf: Conversion[Mime.Docx, Mime.Pdf] with
  override def name = "Aspose.Words.DocxToPdf"

  def convert(input: Content[Mime.Docx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Words]
      Scope.global.scoped { scope =>
        import scope.*
        val doc = allocate(wordDocResource(
          new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(doc)(_.save(out, com.aspose.words.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeDocxToPdf

given asposeDocToPdf: Conversion[Mime.Doc, Mime.Pdf] with
  override def name = "Aspose.Words.DocToPdf"

  def convert(input: Content[Mime.Doc]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Words]
      Scope.global.scoped { scope =>
        import scope.*
        val doc = allocate(wordDocResource(
          new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(doc)(_.save(out, com.aspose.words.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeDocToPdf

// =============================================================================
// Word format conversions (legacy <-> modern)
// =============================================================================

private[aspose] def convertWordDoc[I <: Mime, O <: Mime](
  input: Content[I],
  saveFormat: Int,
  outputMime: O,
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[O]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Words]
    Scope.global.scoped { scope =>
      import scope.*
      val loadOpts = new com.aspose.words.LoadOptions()
      options.password.foreach(loadOpts.setPassword)
      val doc = allocate(wordDocResource(
        new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray), loadOpts)
      ))
      val out = new ByteArrayOutputStream()
      $(doc)(_.save(out, saveFormat))
      Content[O](out.toByteArray, outputMime, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

given asposeDocToDocx: Conversion[Mime.Doc, Mime.Docx] with
  override def name                     = "Aspose.Words.DocToDocx"
  def convert(input: Content[Mime.Doc]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCX, Mime.docx)

given asposeDocxToDoc: Conversion[Mime.Docx, Mime.Doc] with
  override def name                      = "Aspose.Words.DocxToDoc"
  def convert(input: Content[Mime.Docx]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOC, Mime.doc)

given asposeDocmToDocx: Conversion[Mime.Docm, Mime.Docx] with
  override def name                      = "Aspose.Words.DocmToDocx"
  def convert(input: Content[Mime.Docm]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCX, Mime.docx)

given asposeDocmToDoc: Conversion[Mime.Docm, Mime.Doc] with
  override def name                      = "Aspose.Words.DocmToDoc"
  def convert(input: Content[Mime.Docm]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOC, Mime.doc)

given asposeDocToDocm: Conversion[Mime.Doc, Mime.Docm] with
  override def name                     = "Aspose.Words.DocToDocm"
  def convert(input: Content[Mime.Doc]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCM, Mime.docm)

given asposeDocxToDocm: Conversion[Mime.Docx, Mime.Docm] with
  override def name                      = "Aspose.Words.DocxToDocm"
  def convert(input: Content[Mime.Docx]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCM, Mime.docm)

// =============================================================================
// Excel -> PDF
// =============================================================================

given asposeXlsxToPdf: Conversion[Mime.Xlsx, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsxToPdf"

  def convert(input: Content[Mime.Xlsx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Cells]
      Scope.global.scoped { scope =>
        import scope.*
        val workbook = allocate(cellsWorkbookResource(
          new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(workbook)(_.save(out, com.aspose.cells.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeXlsxToPdf

given asposeXlsToPdf: Conversion[Mime.Xls, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsToPdf"

  def convert(input: Content[Mime.Xls]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Cells]
      Scope.global.scoped { scope =>
        import scope.*
        val workbook = allocate(cellsWorkbookResource(
          new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(workbook)(_.save(out, com.aspose.cells.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeXlsToPdf

given asposeXlsmToPdf: Conversion[Mime.Xlsm, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsmToPdf"

  def convert(input: Content[Mime.Xlsm]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Cells]
      Scope.global.scoped { scope =>
        import scope.*
        val workbook = allocate(cellsWorkbookResource(
          new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(workbook)(_.save(out, com.aspose.cells.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeXlsmToPdf

given asposeXlsbToPdf: Conversion[Mime.Xlsb, Mime.Pdf] with
  override def name = "Aspose.Cells.XlsbToPdf"

  def convert(input: Content[Mime.Xlsb]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Cells]
      Scope.global.scoped { scope =>
        import scope.*
        val workbook = allocate(cellsWorkbookResource(
          new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(workbook)(_.save(out, com.aspose.cells.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeXlsbToPdf

given asposeOdsToPdf: Conversion[Mime.Ods, Mime.Pdf] with
  override def name = "Aspose.Cells.OdsToPdf"

  def convert(input: Content[Mime.Ods]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Cells]
      Scope.global.scoped { scope =>
        import scope.*
        val workbook = allocate(cellsWorkbookResource(
          new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(workbook)(_.save(out, com.aspose.cells.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeOdsToPdf

// =============================================================================
// Excel -> HTML
// =============================================================================

private def convertWorkbookToHtml[M <: Mime](
  input: Content[M],
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[Mime.Html]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Cells]
    Scope.global.scoped { scope =>
      import scope.*
      val workbook = allocate(cellsWorkbookResource(loadCellsWorkbook(input, options)))
      if options.evaluateFormulas then
        $(workbook) { wb =>
          try wb.calculateFormula()
          catch case _: Exception => ()
        }
      val opts = new com.aspose.cells.HtmlSaveOptions()
      opts.setExportImagesAsBase64(options.embedResources)
      val out = new ByteArrayOutputStream()
      $(workbook)(_.save(out, opts))
      Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
    }
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

private[aspose] def convertWorkbook[I <: Mime, O <: Mime](
  input: Content[I],
  saveFormat: Int,
  outputMime: O,
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[O]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Cells]
    Scope.global.scoped { scope =>
      import scope.*
      val workbook = allocate(cellsWorkbookResource(loadCellsWorkbook(input, options)))
      if options.evaluateFormulas then
        $(workbook) { wb =>
          try wb.calculateFormula()
          catch case _: Exception => ()
        }
      val out = new ByteArrayOutputStream()
      $(workbook)(_.save(out, saveFormat))
      Content[O](out.toByteArray, outputMime, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

// Options-aware Excel -> PDF helper (used by dispatch layer)
private[aspose] def convertWorkbookToPdf[I <: Mime](
  input: Content[I],
  outputMime: Mime.Pdf,
  options: ConvertOptions
): ZIO[Any, TransformError, Content[Mime.Pdf]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Cells]
    Scope.global.scoped { scope =>
      import scope.*
      val workbook = allocate(cellsWorkbookResource(loadCellsWorkbook(input, options)))
      if options.evaluateFormulas then
        $(workbook) { wb =>
          try wb.calculateFormula()
          catch case _: Exception => ()
        }

      $(workbook) { wb =>
        val sheets = wb.getWorksheets
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
      }

      val pdfSaveOpts = new com.aspose.cells.PdfSaveOptions()
      if options.oneSheetPerPage then pdfSaveOpts.setOnePagePerSheet(true)

      val out = new ByteArrayOutputStream()
      $(workbook)(_.save(out, pdfSaveOpts))
      Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

// Options-aware PowerPoint -> HTML helper (used by dispatch layer)
private[aspose] def convertPresentationToHtml[I <: Mime](
  input: Content[I],
  options: ConvertOptions
): ZIO[Any, TransformError, Content[Mime.Html]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Slides]
    Scope.global.scoped { scope =>
      import scope.*
      val loadOpts = new com.aspose.slides.LoadOptions()
      options.password.foreach(loadOpts.setPassword)
      val pres = allocate(presentationResource(
        new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray), loadOpts)
      ))
      val out = new ByteArrayOutputStream()
      if options.stripMasters then
        val blank = new com.aspose.slides.Presentation()
        scope.defer(blank.dispose())
        if blank.getSlides.size() > 0 then blank.getSlides.removeAt(0)
        $(pres) { p =>
          var i     = 0
          val count = p.getSlides.size()
          while i < count do
            blank.getSlides.addClone(p.getSlides.get_Item(i))
            i += 1
        }
        blank.save(out, com.aspose.slides.SaveFormat.Html)
      else
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Html))
      Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

// Options-aware PDF -> HTML helper (used by dispatch layer)
private[aspose] def convertPdfToHtml(
  input: Content[Mime.Pdf],
  options: ConvertOptions
): ZIO[Any, TransformError, Content[Mime.Html]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Pdf]
    Scope.global.scoped { scope =>
      import scope.*
      val stream   = new ByteArrayInputStream(input.data.toArray)
      val document = allocate(pdfDocResource(
        options.password match
          case Some(pwd) => new com.aspose.pdf.Document(stream, pwd)
          case None      => new com.aspose.pdf.Document(stream)
      ))
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
      $(document)(_.save(out, saveOptions))
      Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

given asposeXlsToXlsx: Conversion[Mime.Xls, Mime.Xlsx] with
  override def name                     = "Aspose.Cells.XlsToXlsx"
  def convert(input: Content[Mime.Xls]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSX, Mime.xlsx)

given asposeXlsxToXls: Conversion[Mime.Xlsx, Mime.Xls] with
  override def name                      = "Aspose.Cells.XlsxToXls"
  def convert(input: Content[Mime.Xlsx]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.EXCEL_97_TO_2003, Mime.xls)

given asposeXlsmToXlsx: Conversion[Mime.Xlsm, Mime.Xlsx] with
  override def name                      = "Aspose.Cells.XlsmToXlsx"
  def convert(input: Content[Mime.Xlsm]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSX, Mime.xlsx)

given asposeXlsbToXlsx: Conversion[Mime.Xlsb, Mime.Xlsx] with
  override def name                      = "Aspose.Cells.XlsbToXlsx"
  def convert(input: Content[Mime.Xlsb]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSX, Mime.xlsx)

given asposeXlsxToXlsm: Conversion[Mime.Xlsx, Mime.Xlsm] with
  override def name                      = "Aspose.Cells.XlsxToXlsm"
  def convert(input: Content[Mime.Xlsx]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSM, Mime.xlsm)

given asposeXlsxToXlsb: Conversion[Mime.Xlsx, Mime.Xlsb] with
  override def name                      = "Aspose.Cells.XlsxToXlsb"
  def convert(input: Content[Mime.Xlsx]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSB, Mime.xlsb)

given asposeOdsToXlsx: Conversion[Mime.Ods, Mime.Xlsx] with
  override def name                     = "Aspose.Cells.OdsToXlsx"
  def convert(input: Content[Mime.Ods]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.XLSX, Mime.xlsx)

given asposeXlsxToOds: Conversion[Mime.Xlsx, Mime.Ods] with
  override def name                      = "Aspose.Cells.XlsxToOds"
  def convert(input: Content[Mime.Xlsx]) =
    convertWorkbook(input, com.aspose.cells.SaveFormat.ODS, Mime.ods)

// =============================================================================
// PowerPoint -> PDF
// =============================================================================

given asposePptxToPdf: Conversion[Mime.Pptx, Mime.Pdf] with
  override def name = "Aspose.Slides.PptxToPdf"

  def convert(input: Content[Mime.Pptx]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Slides]
      Scope.global.scoped { scope =>
        import scope.*
        val pres = allocate(presentationResource(
          new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Pdf))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePptxToPdf

given asposePptToPdf: Conversion[Mime.Ppt, Mime.Pdf] with
  override def name = "Aspose.Slides.PptToPdf"

  def convert(input: Content[Mime.Ppt]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Slides]
      Scope.global.scoped { scope =>
        import scope.*
        val pres = allocate(presentationResource(
          new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Pdf))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePptToPdf

// =============================================================================
// PowerPoint <-> HTML
// =============================================================================

given asposePptxToHtml: Conversion[Mime.Pptx, Mime.Html] with
  override def name = "Aspose.Slides.PptxToHtml"

  def convert(input: Content[Mime.Pptx]): ZIO[Any, TransformError, Content[Mime.Html]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Slides]
      Scope.global.scoped { scope =>
        import scope.*
        val pres = allocate(presentationResource(
          new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Html))
        Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePptxToHtml

given asposePptToHtml: Conversion[Mime.Ppt, Mime.Html] with
  override def name = "Aspose.Slides.PptToHtml"

  def convert(input: Content[Mime.Ppt]): ZIO[Any, TransformError, Content[Mime.Html]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Slides]
      Scope.global.scoped { scope =>
        import scope.*
        val pres = allocate(presentationResource(
          new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray))
        ))
        val out = new ByteArrayOutputStream()
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Html))
        Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePptToHtml

given asposeHtmlToPptx: Conversion[Mime.Html, Mime.Pptx] with
  override def name = "Aspose.Slides.HtmlToPptx"

  def convert(input: Content[Mime.Html]): ZIO[Any, TransformError, Content[Mime.Pptx]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Slides]
      Scope.global.scoped { scope =>
        import scope.*
        val pres = allocate(presentationResource(new com.aspose.slides.Presentation()))
        $(pres) { p =>
          // Remove default empty slide
          if p.getSlides.size() > 0 then p.getSlides.removeAt(0)
          // Add slides from HTML
          p.getSlides.addFromHtml(new String(input.data.toArray, "UTF-8"))
        }
        val out = new ByteArrayOutputStream()
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Pptx))
        Content[Mime.Pptx](out.toByteArray, Mime.pptx, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeHtmlToPptx

given asposeHtmlToPpt: Conversion[Mime.Html, Mime.Ppt] with
  override def name = "Aspose.Slides.HtmlToPpt"

  def convert(input: Content[Mime.Html]): ZIO[Any, TransformError, Content[Mime.Ppt]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Slides]
      Scope.global.scoped { scope =>
        import scope.*
        val pres = allocate(presentationResource(new com.aspose.slides.Presentation()))
        $(pres) { p =>
          if p.getSlides.size() > 0 then p.getSlides.removeAt(0)
          p.getSlides.addFromHtml(new String(input.data.toArray, "UTF-8"))
        }
        val out = new ByteArrayOutputStream()
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Ppt))
        Content[Mime.Ppt](out.toByteArray, Mime.ppt, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeHtmlToPpt

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
    AsposeLicenseV2.require[Slides]
    Scope.global.scoped { scope =>
      import scope.*
      val loadOpts = new com.aspose.slides.LoadOptions()
      options.password.foreach(loadOpts.setPassword)
      val pres = allocate(presentationResource(
        new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray), loadOpts)
      ))
      val out = new ByteArrayOutputStream()
      $(pres)(_.save(out, saveFormat))
      Content[O](out.toByteArray, outputMime, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

given asposePptToPptx: Conversion[Mime.Ppt, Mime.Pptx] with
  override def name                     = "Aspose.Slides.PptToPptx"
  def convert(input: Content[Mime.Ppt]) =
    convertPresentation(input, com.aspose.slides.SaveFormat.Pptx, Mime.pptx)

given asposePptxToPpt: Conversion[Mime.Pptx, Mime.Ppt] with
  override def name                      = "Aspose.Slides.PptxToPpt"
  def convert(input: Content[Mime.Pptx]) =
    convertPresentation(input, com.aspose.slides.SaveFormat.Ppt, Mime.ppt)

given asposePptmToPptx: Conversion[Mime.Pptm, Mime.Pptx] with
  override def name                      = "Aspose.Slides.PptmToPptx"
  def convert(input: Content[Mime.Pptm]) =
    convertPresentation(input, com.aspose.slides.SaveFormat.Pptx, Mime.pptx)

given asposePptmToPpt: Conversion[Mime.Pptm, Mime.Ppt] with
  override def name                      = "Aspose.Slides.PptmToPpt"
  def convert(input: Content[Mime.Pptm]) =
    convertPresentation(input, com.aspose.slides.SaveFormat.Ppt, Mime.ppt)

// =============================================================================
// PDF -> HTML
// =============================================================================

given asposePdfToHtml: Conversion[Mime.Pdf, Mime.Html] with
  override def name = "Aspose.Pdf.PdfToHtml"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Html]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val document = allocate(pdfDocResource(
          new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray))
        ))
        val saveOptions = new com.aspose.pdf.HtmlSaveOptions()
        saveOptions.setPartsEmbeddingMode(
          com.aspose.pdf.HtmlSaveOptions.PartsEmbeddingModes.EmbedAllIntoHtml
        )
        saveOptions.setRasterImagesSavingMode(
          com.aspose.pdf.HtmlSaveOptions.RasterImagesSavingModes.AsEmbeddedPartsOfPngPageBackground
        )
        val out = new ByteArrayOutputStream()
        $(document)(_.save(out, saveOptions))
        Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePdfToHtml

// =============================================================================
// PDF -> PowerPoint
// =============================================================================

given asposePdfToPptx: Conversion[Mime.Pdf, Mime.Pptx] with
  override def name = "Aspose.Slides.PdfToPptx"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Pptx]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Slides]
      Scope.global.scoped { scope =>
        import scope.*
        val pres = allocate(presentationResource(new com.aspose.slides.Presentation()))
        $(pres) { p =>
          p.getSlides.removeAt(0)
          p.getSlides.addFromPdf(new ByteArrayInputStream(input.data.toArray))
        }
        val out = new ByteArrayOutputStream()
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Pptx))
        Content[Mime.Pptx](out.toByteArray, Mime.pptx, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePdfToPptx

given asposePdfToPpt: Conversion[Mime.Pdf, Mime.Ppt] with
  override def name = "Aspose.Slides.PdfToPpt"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Ppt]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Slides]
      Scope.global.scoped { scope =>
        import scope.*
        val pres = allocate(presentationResource(new com.aspose.slides.Presentation()))
        $(pres) { p =>
          p.getSlides.removeAt(0)
          p.getSlides.addFromPdf(new ByteArrayInputStream(input.data.toArray))
        }
        val out = new ByteArrayOutputStream()
        $(pres)(_.save(out, com.aspose.slides.SaveFormat.Ppt))
        Content[Mime.Ppt](out.toByteArray, Mime.ppt, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePdfToPpt

// =============================================================================
// PDF -> Images
// =============================================================================

given asposePdfToPng: Conversion[Mime.Pdf, Mime.Png] with
  override def name = "Aspose.Pdf.PdfToPng"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Png]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val document = allocate(pdfDocResource(
          new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray))
        ))
        val resolution = new com.aspose.pdf.devices.Resolution(300)
        val pngDevice  = new com.aspose.pdf.devices.PngDevice(resolution)
        val out        = new ByteArrayOutputStream()
        // Convert first page to PNG
        $(document) { d =>
          pngDevice.process(d.getPages.get_Item(1), out)
        }
        Content[Mime.Png](out.toByteArray, Mime.png, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePdfToPng

given asposePdfToJpeg: Conversion[Mime.Pdf, Mime.Jpeg] with
  override def name = "Aspose.Pdf.PdfToJpeg"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Jpeg]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val document = allocate(pdfDocResource(
          new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray))
        ))
        val resolution = new com.aspose.pdf.devices.Resolution(300)
        val jpegDevice = new com.aspose.pdf.devices.JpegDevice(resolution)
        val out        = new ByteArrayOutputStream()
        $(document) { d =>
          jpegDevice.process(d.getPages.get_Item(1), out)
        }
        Content[Mime.Jpeg](out.toByteArray, Mime.jpeg, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePdfToJpeg

// =============================================================================
// Images -> PDF
// =============================================================================

given asposePngToPdf: Conversion[Mime.Png, Mime.Pdf] with
  override def name = "Aspose.Pdf.PngToPdf"

  def convert(input: Content[Mime.Png]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val document = allocate(pdfDocResource(new com.aspose.pdf.Document()))
        $(document) { d =>
          val page  = d.getPages.add()
          val image = new com.aspose.pdf.Image()
          image.setImageStream(new ByteArrayInputStream(input.data.toArray))
          page.getParagraphs.add(image)
        }
        val out = new ByteArrayOutputStream()
        $(document)(_.save(out))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePngToPdf

given asposeJpegToPdf: Conversion[Mime.Jpeg, Mime.Pdf] with
  override def name = "Aspose.Pdf.JpegToPdf"

  def convert(input: Content[Mime.Jpeg]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val document = allocate(pdfDocResource(new com.aspose.pdf.Document()))
        $(document) { d =>
          val page  = d.getPages.add()
          val image = new com.aspose.pdf.Image()
          image.setImageStream(new ByteArrayInputStream(input.data.toArray))
          page.getParagraphs.add(image)
        }
        val out = new ByteArrayOutputStream()
        $(document)(_.save(out))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeJpegToPdf

// =============================================================================
// HTML -> PDF
// =============================================================================

given asposeHtmlToPdf: Conversion[Mime.Html, Mime.Pdf] with
  override def name = "Aspose.Pdf.HtmlToPdf"

  def convert(input: Content[Mime.Html]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val htmlOptions = new com.aspose.pdf.HtmlLoadOptions()
        val document    = allocate(pdfDocResource(
          new com.aspose.pdf.Document(new ByteArrayInputStream(input.data.toArray), htmlOptions)
        ))
        val out = new ByteArrayOutputStream()
        $(document)(_.save(out))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeHtmlToPdf

// =============================================================================
// Email -> PDF
// =============================================================================

given asposeEmlToPdf: Conversion[Mime.Eml, Mime.Pdf] with
  override def name = "Aspose.Email.EmlToPdf"

  def convert(input: Content[Mime.Eml]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.requireAll[(Email, Words)]
      Scope.global.scoped { scope =>
        import scope.*
        // Load email message
        val msg = com.aspose.email.MailMessage.load(new ByteArrayInputStream(input.data.toArray))
        // Convert to MHTML first
        val mhtmlStream = new ByteArrayOutputStream()
        msg.save(mhtmlStream, com.aspose.email.SaveOptions.getDefaultMhtml)
        // Then convert MHTML to PDF using Aspose.Words
        val doc = allocate(wordDocResource(
          new com.aspose.words.Document(new ByteArrayInputStream(mhtmlStream.toByteArray))
        ))
        val out = new ByteArrayOutputStream()
        $(doc)(_.save(out, com.aspose.words.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeEmlToPdf

given asposeMsgToPdf: Conversion[Mime.Msg, Mime.Pdf] with
  override def name = "Aspose.Email.MsgToPdf"

  def convert(input: Content[Mime.Msg]): ZIO[Any, TransformError, Content[Mime.Pdf]] =
    ZIO.attempt {
      AsposeLicenseV2.requireAll[(Email, Words)]
      Scope.global.scoped { scope =>
        import scope.*
        val msg = com.aspose.email.MapiMessage.load(new ByteArrayInputStream(input.data.toArray))
        val mhtmlStream = new ByteArrayOutputStream()
        msg.save(mhtmlStream, com.aspose.email.SaveOptions.getDefaultMhtml)
        val doc = allocate(wordDocResource(
          new com.aspose.words.Document(new ByteArrayInputStream(mhtmlStream.toByteArray))
        ))
        val out = new ByteArrayOutputStream()
        $(doc)(_.save(out, com.aspose.words.SaveFormat.PDF))
        Content[Mime.Pdf](out.toByteArray, Mime.pdf, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeMsgToPdf

// =============================================================================
// PDF -> DOCX / DOC (Aspose.PDF)
// =============================================================================

given asposePdfToDocx: Conversion[Mime.Pdf, Mime.Docx] with
  override def name = "Aspose.Pdf.PdfToDocx"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Docx]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val stream   = new ByteArrayInputStream(input.data.toArray)
        val document = allocate(pdfDocResource(
          new com.aspose.pdf.Document(stream)
        ))
        val saveOptions = new com.aspose.pdf.DocSaveOptions()
        saveOptions.setFormat(com.aspose.pdf.DocSaveOptions.DocFormat.DocX)
        val out = new ByteArrayOutputStream()
        $(document)(_.save(out, saveOptions))
        Content[Mime.Docx](out.toByteArray, Mime.docx, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePdfToDocx

given asposePdfToDoc: Conversion[Mime.Pdf, Mime.Doc] with
  override def name = "Aspose.Pdf.PdfToDoc"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Doc]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val stream   = new ByteArrayInputStream(input.data.toArray)
        val document = allocate(pdfDocResource(
          new com.aspose.pdf.Document(stream)
        ))
        val saveOptions = new com.aspose.pdf.DocSaveOptions()
        saveOptions.setFormat(com.aspose.pdf.DocSaveOptions.DocFormat.Doc)
        val out = new ByteArrayOutputStream()
        $(document)(_.save(out, saveOptions))
        Content[Mime.Doc](out.toByteArray, Mime.doc, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePdfToDoc

// =============================================================================
// PDF -> XLSX (Aspose.PDF)
// =============================================================================

given asposePdfToXlsx: Conversion[Mime.Pdf, Mime.Xlsx] with
  override def name = "Aspose.Pdf.PdfToXlsx"

  def convert(input: Content[Mime.Pdf]): ZIO[Any, TransformError, Content[Mime.Xlsx]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Pdf]
      Scope.global.scoped { scope =>
        import scope.*
        val stream   = new ByteArrayInputStream(input.data.toArray)
        val document = allocate(pdfDocResource(
          new com.aspose.pdf.Document(stream)
        ))
        val saveOptions = new com.aspose.pdf.ExcelSaveOptions()
        saveOptions.setFormat(com.aspose.pdf.ExcelSaveOptions.ExcelFormat.XLSX)
        val out = new ByteArrayOutputStream()
        $(document)(_.save(out, saveOptions))
        Content[Mime.Xlsx](out.toByteArray, Mime.xlsx, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposePdfToXlsx

// =============================================================================
// Word -> HTML (Aspose.Words)
// =============================================================================

private[aspose] def convertWordDocToHtml[I <: Mime](
  input: Content[I],
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[Mime.Html]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Words]
    Scope.global.scoped { scope =>
      import scope.*
      val loadOpts = new com.aspose.words.LoadOptions()
      options.password.foreach(loadOpts.setPassword)
      val doc = allocate(wordDocResource(
        new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray), loadOpts)
      ))
      val saveOpts = new com.aspose.words.HtmlSaveOptions(com.aspose.words.SaveFormat.HTML)
      if options.embedResources then
        saveOpts.setExportImagesAsBase64(true)
      val out = new ByteArrayOutputStream()
      $(doc)(_.save(out, saveOpts))
      Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

given asposeDocxToHtml: Conversion[Mime.Docx, Mime.Html] with
  override def name                      = "Aspose.Words.DocxToHtml"
  def convert(input: Content[Mime.Docx]) = convertWordDocToHtml(input)

given asposeDocToHtml: Conversion[Mime.Doc, Mime.Html] with
  override def name                     = "Aspose.Words.DocToHtml"
  def convert(input: Content[Mime.Doc]) = convertWordDocToHtml(input)

given asposeDocmToHtml: Conversion[Mime.Docm, Mime.Html] with
  override def name                      = "Aspose.Words.DocmToHtml"
  def convert(input: Content[Mime.Docm]) = convertWordDocToHtml(input)

// =============================================================================
// Word -> Plain Text (Aspose.Words)
// =============================================================================

given asposeDocxToPlain: Conversion[Mime.Docx, Mime.Plain] with
  override def name                      = "Aspose.Words.DocxToPlain"
  def convert(input: Content[Mime.Docx]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.TEXT, Mime.plain)

given asposeDocToPlain: Conversion[Mime.Doc, Mime.Plain] with
  override def name                     = "Aspose.Words.DocToPlain"
  def convert(input: Content[Mime.Doc]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.TEXT, Mime.plain)

// =============================================================================
// Word -> Markdown (Aspose.Words)
// =============================================================================

given asposeDocxToMarkdown: Conversion[Mime.Docx, Mime.Markdown] with
  override def name                      = "Aspose.Words.DocxToMarkdown"
  def convert(input: Content[Mime.Docx]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.MARKDOWN, Mime.markdown)

given asposeDocToMarkdown: Conversion[Mime.Doc, Mime.Markdown] with
  override def name                     = "Aspose.Words.DocToMarkdown"
  def convert(input: Content[Mime.Doc]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.MARKDOWN, Mime.markdown)

// =============================================================================
// Excel -> CSV (Aspose.Cells)
// =============================================================================

private[aspose] def convertWorkbookToCsv[I <: Mime](
  input: Content[I],
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[Mime.Csv]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Cells]
    Scope.global.scoped { scope =>
      import scope.*
      val workbook = allocate(cellsWorkbookResource(loadCellsWorkbook(input, options)))
      if options.evaluateFormulas then
        $(workbook) { wb =>
          try wb.calculateFormula()
          catch case _: Exception => ()
        }
      // If specific sheets are requested, hide the rest
      if options.sheetNames.nonEmpty then
        $(workbook) { wb =>
          val sheets = wb.getWorksheets
          for i <- 0 until sheets.getCount do
            val ws = sheets.get(i)
            if !options.sheetNames.contains(ws.getName) then ws.setVisible(false)
        }
      val out = new ByteArrayOutputStream()
      $(workbook)(_.save(out, com.aspose.cells.SaveFormat.CSV))
      Content[Mime.Csv](out.toByteArray, Mime.csv, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

given asposeXlsxToCsv: Conversion[Mime.Xlsx, Mime.Csv] with
  override def name                      = "Aspose.Cells.XlsxToCsv"
  def convert(input: Content[Mime.Xlsx]) = convertWorkbookToCsv(input)

given asposeXlsToCsv: Conversion[Mime.Xls, Mime.Csv] with
  override def name                     = "Aspose.Cells.XlsToCsv"
  def convert(input: Content[Mime.Xls]) = convertWorkbookToCsv(input)

given asposeXlsmToCsv: Conversion[Mime.Xlsm, Mime.Csv] with
  override def name                      = "Aspose.Cells.XlsmToCsv"
  def convert(input: Content[Mime.Xlsm]) = convertWorkbookToCsv(input)

given asposeXlsbToCsv: Conversion[Mime.Xlsb, Mime.Csv] with
  override def name                      = "Aspose.Cells.XlsbToCsv"
  def convert(input: Content[Mime.Xlsb]) = convertWorkbookToCsv(input)

given asposeOdsToCsv: Conversion[Mime.Ods, Mime.Csv] with
  override def name                     = "Aspose.Cells.OdsToCsv"
  def convert(input: Content[Mime.Ods]) = convertWorkbookToCsv(input)

// =============================================================================
// Excel -> JSON (Aspose.Cells)
// =============================================================================

given asposeXlsxToJson: Conversion[Mime.Xlsx, Mime.Json] with
  override def name = "Aspose.Cells.XlsxToJson"

  def convert(input: Content[Mime.Xlsx]): ZIO[Any, TransformError, Content[Mime.Json]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Cells]
      Scope.global.scoped { scope =>
        import scope.*
        val workbook = allocate(cellsWorkbookResource(loadCellsWorkbook(input, ConvertOptions())))
        val out      = new ByteArrayOutputStream()
        $(workbook)(_.save(out, com.aspose.cells.SaveFormat.JSON))
        Content[Mime.Json](out.toByteArray, Mime.json, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// Excel -> Markdown (Aspose.Cells)
// =============================================================================

given asposeXlsxToMarkdown: Conversion[Mime.Xlsx, Mime.Markdown] with
  override def name = "Aspose.Cells.XlsxToMarkdown"

  def convert(input: Content[Mime.Xlsx]): ZIO[Any, TransformError, Content[Mime.Markdown]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Cells]
      Scope.global.scoped { scope =>
        import scope.*
        val workbook = allocate(cellsWorkbookResource(loadCellsWorkbook(input, ConvertOptions())))
        val out      = new ByteArrayOutputStream()
        $(workbook)(_.save(out, com.aspose.cells.SaveFormat.MARKDOWN))
        Content[Mime.Markdown](out.toByteArray, Mime.markdown, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// RTF Support (Aspose.Words)
// =============================================================================

given asposeRtfToPdf: Conversion[Mime.Rtf, Mime.Pdf] with
  override def name                     = "Aspose.Words.RtfToPdf"
  def convert(input: Content[Mime.Rtf]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.PDF, Mime.pdf)

given asposeRtfToDocx: Conversion[Mime.Rtf, Mime.Docx] with
  override def name                     = "Aspose.Words.RtfToDocx"
  def convert(input: Content[Mime.Rtf]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.DOCX, Mime.docx)

given asposeRtfToHtml: Conversion[Mime.Rtf, Mime.Html] with
  override def name                     = "Aspose.Words.RtfToHtml"
  def convert(input: Content[Mime.Rtf]) = convertWordDocToHtml(input)

given asposeDocxToRtf: Conversion[Mime.Docx, Mime.Rtf] with
  override def name                      = "Aspose.Words.DocxToRtf"
  def convert(input: Content[Mime.Docx]) =
    convertWordDoc(input, com.aspose.words.SaveFormat.RTF, Mime.rtf)

// =============================================================================
// Email -> HTML (Aspose.Email + Words)
// =============================================================================

given asposeEmlToHtml: Conversion[Mime.Eml, Mime.Html] with
  override def name = "Aspose.Email.EmlToHtml"

  def convert(input: Content[Mime.Eml]): ZIO[Any, TransformError, Content[Mime.Html]] =
    ZIO.attempt {
      AsposeLicenseV2.requireAll[(Email, Words)]
      Scope.global.scoped { scope =>
        import scope.*
        val msg = com.aspose.email.MailMessage.load(new ByteArrayInputStream(input.data.toArray))
        val mhtmlStream = new ByteArrayOutputStream()
        msg.save(mhtmlStream, com.aspose.email.SaveOptions.getDefaultMhtml)
        val doc = allocate(wordDocResource(
          new com.aspose.words.Document(new ByteArrayInputStream(mhtmlStream.toByteArray))
        ))
        val saveOpts = new com.aspose.words.HtmlSaveOptions(com.aspose.words.SaveFormat.HTML)
        saveOpts.setExportImagesAsBase64(true)
        val out = new ByteArrayOutputStream()
        $(doc)(_.save(out, saveOpts))
        Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeEmlToHtml

given asposeMsgToHtml: Conversion[Mime.Msg, Mime.Html] with
  override def name = "Aspose.Email.MsgToHtml"

  def convert(input: Content[Mime.Msg]): ZIO[Any, TransformError, Content[Mime.Html]] =
    ZIO.attempt {
      AsposeLicenseV2.requireAll[(Email, Words)]
      Scope.global.scoped { scope =>
        import scope.*
        val msg = com.aspose.email.MapiMessage.load(new ByteArrayInputStream(input.data.toArray))
        val mhtmlStream = new ByteArrayOutputStream()
        msg.save(mhtmlStream, com.aspose.email.SaveOptions.getDefaultMhtml)
        val doc = allocate(wordDocResource(
          new com.aspose.words.Document(new ByteArrayInputStream(mhtmlStream.toByteArray))
        ))
        val saveOpts = new com.aspose.words.HtmlSaveOptions(com.aspose.words.SaveFormat.HTML)
        saveOpts.setExportImagesAsBase64(true)
        val out = new ByteArrayOutputStream()
        $(doc)(_.save(out, saveOpts))
        Content[Mime.Html](out.toByteArray, Mime.html, input.metadata)
      }
    }.mapError(TransformError.fromThrowable)
end asposeMsgToHtml

// =============================================================================
// EML <-> MSG (Aspose.Email)
// =============================================================================

given asposeEmlToMsg: Conversion[Mime.Eml, Mime.Msg] with
  override def name = "Aspose.Email.EmlToMsg"

  def convert(input: Content[Mime.Eml]): ZIO[Any, TransformError, Content[Mime.Msg]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Email]
      val msg     = com.aspose.email.MailMessage.load(new ByteArrayInputStream(input.data.toArray))
      val mapiMsg = com.aspose.email.MapiMessage.fromMailMessage(msg)
      val out     = new ByteArrayOutputStream()
      mapiMsg.save(out)
      Content[Mime.Msg](out.toByteArray, Mime.msg, input.metadata)
    }.mapError(TransformError.fromThrowable)

given asposeMsgToEml: Conversion[Mime.Msg, Mime.Eml] with
  override def name = "Aspose.Email.MsgToEml"

  def convert(input: Content[Mime.Msg]): ZIO[Any, TransformError, Content[Mime.Eml]] =
    ZIO.attempt {
      AsposeLicenseV2.require[Email]
      val mapiMsg = com.aspose.email.MapiMessage.load(new ByteArrayInputStream(input.data.toArray))
      val mailMsg = mapiMsg.toMailMessage(new com.aspose.email.MailConversionOptions())
      val out     = new ByteArrayOutputStream()
      mailMsg.save(out, com.aspose.email.SaveOptions.getDefaultEml)
      Content[Mime.Eml](out.toByteArray, Mime.eml, input.metadata)
    }.mapError(TransformError.fromThrowable)

// =============================================================================
// Word -> Images (Aspose.Words - first page rendering)
// =============================================================================

private[aspose] def convertWordDocToImage[I <: Mime, O <: Mime](
  input: Content[I],
  saveFormat: Int,
  outputMime: O,
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[O]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Words]
    Scope.global.scoped { scope =>
      import scope.*
      val loadOpts = new com.aspose.words.LoadOptions()
      options.password.foreach(loadOpts.setPassword)
      val doc = allocate(wordDocResource(
        new com.aspose.words.Document(new ByteArrayInputStream(input.data.toArray), loadOpts)
      ))
      val imgSaveOpts = new com.aspose.words.ImageSaveOptions(saveFormat)
      imgSaveOpts.setPageSet(new com.aspose.words.PageSet(0)) // First page only
      imgSaveOpts.setResolution(300f)
      val out = new ByteArrayOutputStream()
      $(doc)(_.save(out, imgSaveOpts))
      Content[O](out.toByteArray, outputMime, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

given asposeDocxToPng: Conversion[Mime.Docx, Mime.Png] with
  override def name                      = "Aspose.Words.DocxToPng"
  def convert(input: Content[Mime.Docx]) =
    convertWordDocToImage(input, com.aspose.words.SaveFormat.PNG, Mime.png)

given asposeDocxToJpeg: Conversion[Mime.Docx, Mime.Jpeg] with
  override def name                      = "Aspose.Words.DocxToJpeg"
  def convert(input: Content[Mime.Docx]) =
    convertWordDocToImage(input, com.aspose.words.SaveFormat.JPEG, Mime.jpeg)

// =============================================================================
// Slides -> Images (Aspose.Slides - first slide thumbnail)
// =============================================================================

private[aspose] def convertSlideToImage[I <: Mime, O <: Mime](
  input: Content[I],
  formatName: String,
  outputMime: O,
  options: ConvertOptions = ConvertOptions()
): ZIO[Any, TransformError, Content[O]] =
  ZIO.attempt {
    AsposeLicenseV2.require[Slides]
    Scope.global.scoped { scope =>
      import scope.*
      val loadOpts = new com.aspose.slides.LoadOptions()
      options.password.foreach(loadOpts.setPassword)
      val pres = allocate(presentationResource(
        new com.aspose.slides.Presentation(new ByteArrayInputStream(input.data.toArray), loadOpts)
      ))
      val out = new ByteArrayOutputStream()
      $(pres) { p =>
        val slide     = p.getSlides.get_Item(0)
        val scaleX    = 1920.0f / p.getSlideSize.getSize.getWidth.toFloat
        val scaleY    = 1080.0f / p.getSlideSize.getSize.getHeight.toFloat
        val image     = slide.getImage(scaleX, scaleY)
        val imgFormat = formatName match
          case "png"  => com.aspose.slides.ImageFormat.Png
          case "jpeg" => com.aspose.slides.ImageFormat.Jpeg
          case _      => com.aspose.slides.ImageFormat.Png
        image.save(out, imgFormat)
        image.dispose()
      }
      Content[O](out.toByteArray, outputMime, input.metadata)
    }
  }.mapError(TransformError.fromThrowable)

given asposePptxToPng: Conversion[Mime.Pptx, Mime.Png] with
  override def name                      = "Aspose.Slides.PptxToPng"
  def convert(input: Content[Mime.Pptx]) =
    convertSlideToImage(input, "png", Mime.png)

given asposePptxToJpeg: Conversion[Mime.Pptx, Mime.Jpeg] with
  override def name                      = "Aspose.Slides.PptxToJpeg"
  def convert(input: Content[Mime.Pptx]) =
    convertSlideToImage(input, "jpeg", Mime.jpeg)

given asposePptToPng: Conversion[Mime.Ppt, Mime.Png] with
  override def name                     = "Aspose.Slides.PptToPng"
  def convert(input: Content[Mime.Ppt]) =
    convertSlideToImage(input, "png", Mime.png)

given asposePptToJpeg: Conversion[Mime.Ppt, Mime.Jpeg] with
  override def name                     = "Aspose.Slides.PptToJpeg"
  def convert(input: Content[Mime.Ppt]) =
    convertSlideToImage(input, "jpeg", Mime.jpeg)
