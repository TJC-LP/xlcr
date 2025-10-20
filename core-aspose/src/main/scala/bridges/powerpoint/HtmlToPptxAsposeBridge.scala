package com.tjclp.xlcr
package bridges.powerpoint

import com.aspose.slides.SaveFormat

import types.MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation

/**
 * Bridge that converts HTML documents to PPTX (PowerPoint Open XML) files using Aspose.Slides.
 */
object HtmlToPptxAsposeBridge
    extends HtmlToPowerPointAsposeBridgeImpl[
      ApplicationVndOpenXmlFormatsPresentationmlPresentation.type
    ] {

  override protected def saveFormat: Int =
    SaveFormat.Pptx

  override protected def outputMimeType: ApplicationVndOpenXmlFormatsPresentationmlPresentation.type =
    ApplicationVndOpenXmlFormatsPresentationmlPresentation
}
