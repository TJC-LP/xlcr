package com.tjclp.xlcr
package bridges.powerpoint

import types.MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation

/**
 * Bridge that converts PPTX (PowerPoint Open XML) files to HTML using Aspose.Slides.
 */
object PptxToHtmlAsposeBridge
    extends PowerPointToHtmlAsposeBridgeImpl[
      ApplicationVndOpenXmlFormatsPresentationmlPresentation.type
    ]
