package com.tjclp.xlcr
package bridges.aspose.powerpoint

import bridges.SimpleBridge
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndOpenXmlFormatsPresentationmlPresentation
}

/** Bridge that converts PPTX (PowerPoint Open XML) files to PDF using Aspose.Slides.
  */
object PowerPointPptxToPdfAsposeBridge
    extends SimpleBridge[
      ApplicationVndOpenXmlFormatsPresentationmlPresentation.type,
      ApplicationPdf.type
    ]
    with PowerPointPptxToPdfAsposeBridgeImpl
