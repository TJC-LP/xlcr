package com.tjclp.xlcr
package bridges.aspose

import bridges.BridgeRegistry
import bridges.aspose.email.{EmailToPdfAsposeBridge, OutlookMsgToPdfAsposeBridge}
import bridges.aspose.excel.{ExcelToPdfAsposeBridge, ExcelXlsToPdfAsposeBridge, ExcelXlsmToPdfAsposeBridge, ExcelXlsbToPdfAsposeBridge, OdsToPdfAsposeBridge}
import bridges.aspose.powerpoint.{PowerPointToPdfAsposeBridge, PowerPointPptxToPdfAsposeBridge}
import bridges.aspose.word.{WordToPdfAsposeBridge, WordDocxToPdfAsposeBridge}
import types.{MimeType, Priority}
import types.MimeType.*

/**
 * AsposeBridgeRegistry offers a method to register all Aspose-based bridging
 * into the core BridgeRegistry. This is called by BridgeRegistry.initAspose().
 */
object AsposeBridgeRegistry {

  /**
   * Register all Aspose-based bridging with the core BridgeRegistry.
   */
  def registerAll(): Unit = {
    // All Aspose bridges are registered with ASPOSE priority
    // to ensure they're selected over core implementations

    // Word -> PDF (DOC & DOCX)
    BridgeRegistry.register(ApplicationMsWord, ApplicationPdf, Priority.ASPOSE, WordToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsWordprocessingmlDocument, ApplicationPdf, Priority.ASPOSE, WordDocxToPdfAsposeBridge)

    // Excel -> PDF (modern and legacy)
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ApplicationPdf, Priority.ASPOSE, ExcelToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsExcel, ApplicationPdf, Priority.ASPOSE, excel.ExcelXlsToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsExcelSheetMacroEnabled, ApplicationPdf, Priority.ASPOSE, excel.ExcelXlsmToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsExcelSheetBinary, ApplicationPdf, Priority.ASPOSE, excel.ExcelXlsbToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndOasisOpendocumentSpreadsheet, ApplicationPdf, Priority.ASPOSE, OdsToPdfAsposeBridge)

    // Email / Outlook -> PDF
    BridgeRegistry.register(MessageRfc822, ApplicationPdf, Priority.ASPOSE, EmailToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsOutlook, ApplicationPdf, Priority.ASPOSE, OutlookMsgToPdfAsposeBridge)

    // PowerPoint -> PDF (PPT & PPTX)
    BridgeRegistry.register(ApplicationVndMsPowerpoint, ApplicationPdf, Priority.ASPOSE, PowerPointToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsPresentationmlPresentation, ApplicationPdf, Priority.ASPOSE, PowerPointPptxToPdfAsposeBridge)
  }
}