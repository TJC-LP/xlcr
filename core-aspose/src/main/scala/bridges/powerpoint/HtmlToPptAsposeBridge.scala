package com.tjclp.xlcr
package bridges.powerpoint

import com.aspose.slides.SaveFormat

import types.MimeType.ApplicationVndMsPowerpoint

/**
 * Bridge that converts HTML documents to PPT (PowerPoint 97-2003) files using Aspose.Slides.
 */
object HtmlToPptAsposeBridge
    extends HtmlToPowerPointAsposeBridgeImpl[ApplicationVndMsPowerpoint.type] {

  override protected def saveFormat: Int =
    SaveFormat.Ppt

  override protected def outputMimeType: ApplicationVndMsPowerpoint.type =
    ApplicationVndMsPowerpoint
}
