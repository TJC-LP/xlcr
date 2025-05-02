package com.tjclp.xlcr
package bridges.powerpoint

import types.MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation

/** Bridge that converts PPTX (PowerPoint Open XML) files to PDF using Aspose.Slides.
  */
object PowerPointPptxToPdfAsposeBridge
    extends PowerPointToPdfAsposeBridgeImpl[
      ApplicationVndOpenXmlFormatsPresentationmlPresentation.type
    ]
