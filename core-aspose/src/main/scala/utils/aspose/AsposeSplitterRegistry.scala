package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.{MimeType, Priority}
import utils.{DocChunk, DocumentSplitter, SplitConfig}

/** Registers Aspose‑powered [[DocumentSplitter]] implementations with the global
  * [[DocumentSplitter]] registry.
  */
object AsposeSplitterRegistry {

  /** Perform the registration once at application start‑up. */
  def registerAll(): Unit = {
    // Mark all Aspose splitters with the ASPOSE priority
    // to ensure they're selected over core implementations
    
    // Create priority adapters for each splitter
    // PowerPoint splitters
    val pptSplitter = new PrioritizedSplitter(PowerPointSlideAsposeSplitter, Priority.ASPOSE)
    val pptxSplitter = new PrioritizedSplitter(PowerPointXSlideAsposeSplitter, Priority.ASPOSE)
    
    // Excel splitters
    val xlsSplitter = new PrioritizedSplitter(ExcelSheetXlsAsposeSplitter, Priority.ASPOSE)
    val xlsxSplitter = new PrioritizedSplitter(ExcelXSheetAsposeSplitter, Priority.ASPOSE)
    val xlsmSplitter = new PrioritizedSplitter(ExcelXlsmSheetAsposeSplitter, Priority.ASPOSE)
    val xlsbSplitter = new PrioritizedSplitter(ExcelXlsbSheetAsposeSplitter, Priority.ASPOSE)
    
    // Email splitters
    val emailSplitter = new PrioritizedSplitter(EmailAttachmentAsposeSplitter, Priority.ASPOSE)
    val msgSplitter = new PrioritizedSplitter(OutlookMsgAsposeSplitter, Priority.ASPOSE)
    
    // Archive splitters
    val zipSplitter = new PrioritizedSplitter(ZipArchiveAsposeSplitter, Priority.ASPOSE)
    val sevenZipSplitter = new PrioritizedSplitter(SevenZipArchiveAsposeSplitter, Priority.ASPOSE)
    
    // Register the prioritized splitters
    DocumentSplitter.register(
      MimeType.ApplicationVndMsPowerpoint,
      pptSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      pptxSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndMsExcel,
      xlsSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      xlsxSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndMsExcelSheetMacroEnabled,
      xlsmSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndMsExcelSheetBinary,
      xlsbSplitter
    )
    
    DocumentSplitter.register(
      MimeType.MessageRfc822,
      emailSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationVndMsOutlook,
      msgSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationZip,
      zipSplitter
    )
    
    DocumentSplitter.register(
      MimeType.ApplicationSevenz,
      sevenZipSplitter
    )
  }
  
  /**
   * Adapter class that wraps an existing splitter with a specific priority.
   * This allows us to set priority for existing splitter objects.
   */
  private class PrioritizedSplitter[I <: MimeType](
      val delegate: DocumentSplitter[I], 
      override val priority: Priority
  ) extends DocumentSplitter[I] {
    override def split(
      content: FileContent[I],
      cfg: SplitConfig
    ): Seq[DocChunk[_ <: MimeType]] = {
      delegate.split(content, cfg)
    }
  }
}