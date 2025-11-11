package com.tjclp.xlcr
package bridges.powerpoint

import types.MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation

/**
 * Bridge that converts PowerPoint .pptx documents to PDF using LibreOffice.
 *
 * Priority: DEFAULT (fallback to Aspose if available)
 */
object PowerPointPptxToPdfLibreOfficeBridge
    extends PowerPointToPdfLibreOfficeBridgeImpl[
      ApplicationVndOpenXmlFormatsPresentationmlPresentation.type
    ]
