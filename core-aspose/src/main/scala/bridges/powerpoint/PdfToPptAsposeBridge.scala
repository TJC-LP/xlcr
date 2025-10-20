package com.tjclp.xlcr
package bridges.powerpoint

import com.aspose.slides.SaveFormat

import types.MimeType.ApplicationVndMsPowerpoint

/**
 * Bridge that converts PDF documents to PPT (PowerPoint 97-2003) files using Aspose.Slides.
 * Each page in the PDF becomes a slide in the PowerPoint presentation.
 */
object PdfToPptAsposeBridge
    extends PdfToPowerPointAsposeBridgeImpl[ApplicationVndMsPowerpoint.type] {

  override protected def saveFormat: Int =
    SaveFormat.Ppt

  override protected def outputMimeType: ApplicationVndMsPowerpoint.type =
    ApplicationVndMsPowerpoint
}
