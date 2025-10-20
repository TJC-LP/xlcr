package com.tjclp.xlcr
package bridges.powerpoint

import com.aspose.slides.SaveFormat

import types.MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation

/**
 * Bridge that converts PDF documents to PPTX (PowerPoint Open XML) files using Aspose.Slides.
 * Each page in the PDF becomes a slide in the PowerPoint presentation.
 */
object PdfToPptxAsposeBridge
    extends PdfToPowerPointAsposeBridgeImpl[
      ApplicationVndOpenXmlFormatsPresentationmlPresentation.type
    ] {

  override protected def saveFormat: Int =
    SaveFormat.Pptx

  override protected def outputMimeType: ApplicationVndOpenXmlFormatsPresentationmlPresentation.type =
    ApplicationVndOpenXmlFormatsPresentationmlPresentation
}
