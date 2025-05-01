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
    // Word -> PDF (DOC & DOCX)
    BridgeRegistry.register(ApplicationMsWord, ApplicationPdf, WordToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsWordprocessingmlDocument, ApplicationPdf, WordDocxToPdfAsposeBridge)

    // Excel -> PDF (modern and legacy)
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ApplicationPdf, ExcelToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsExcel, ApplicationPdf, excel.ExcelXlsToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsExcelSheetMacroEnabled, ApplicationPdf, excel.ExcelXlsmToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsExcelSheetBinary, ApplicationPdf, excel.ExcelXlsbToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndOasisOpendocumentSpreadsheet, ApplicationPdf, OdsToPdfAsposeBridge)

    // Email / Outlook -> PDF
    BridgeRegistry.register(MessageRfc822, ApplicationPdf, EmailToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsOutlook, ApplicationPdf, OutlookMsgToPdfAsposeBridge)

    // PowerPoint -> PDF (PPT & PPTX)
    BridgeRegistry.register(ApplicationVndMsPowerpoint, ApplicationPdf, PowerPointToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsPresentationmlPresentation, ApplicationPdf, PowerPointPptxToPdfAsposeBridge)
  }
}