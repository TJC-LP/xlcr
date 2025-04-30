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
    
    DocumentSplitter.register(
      MimeType.ApplicationVndMsExcelSheetMacroEnabled,
      ExcelXlsmSheetAsposeSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndMsExcelSheetBinary,
      ExcelXlsbSheetAsposeSplitter
    )
    
    // Register Email splitters
    DocumentSplitter.register(
      MimeType.MessageRfc822,
      EmailAttachmentAsposeSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndMsOutlook,
      OutlookMsgAsposeSplitter
    )
    
    // Register Archive splitters using Aspose.ZIP
    DocumentSplitter.register(
      MimeType.ApplicationZip,
      ZipArchiveAsposeSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationSevenz,
      SevenZipArchiveAsposeSplitter
    )
  }
}
