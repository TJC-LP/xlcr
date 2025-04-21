package com.tjclp.xlcr
package utils.aspose

import types.MimeType
import utils.DocumentSplitter

/** Registers Aspose‑powered [[DocumentSplitter]] implementations with the global
  * [[DocumentSplitter]] registry.
  */
object AsposeSplitterRegistry {

  /** Perform the registration once at application start‑up. */
  def registerAll(): Unit = {
    // Register PowerPoint splitters
    DocumentSplitter.register(
      MimeType.ApplicationVndMsPowerpoint,
      PowerPointSlideAsposeSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      PowerPointXSlideAsposeSplitter
    )
    
    // Register Excel splitters
    DocumentSplitter.register(
      MimeType.ApplicationVndMsExcel,
      ExcelSheetXlsAsposeSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      ExcelXSheetAsposeSplitter
    )
  }
}
