package com.tjclp.xlcr
package bridges.aspose

import bridges.BridgeRegistry
import bridges.aspose.email.EmailToPdfAsposeBridge
import bridges.aspose.email.OutlookMsgToPdfAsposeBridge
import bridges.aspose.excel.{ExcelToPdfAsposeBridge, ExcelXlsToPdfAsposeBridge, ExcelXlsmToPdfAsposeBridge, ExcelXlsbToPdfAsposeBridge}
import bridges.aspose.powerpoint.{PowerPointToPdfAsposeBridge, PowerPointPptxToPdfAsposeBridge}
import bridges.aspose.word.{WordToPdfAsposeBridge, WordDocxToPdfAsposeBridge}
import types.MimeType
import types.MimeType._

/**
 * AsposeBridgeRegistry offers a method to register all Aspose-based bridging
 * into the core BridgeRegistry. This is called by BridgeRegistry.initAspose().
 */
object AsposeBridgeRegistry {

  /**
   * Register all Aspose-based bridging with the core BridgeRegistry.
   */
  def registerAll(): Unit = {
    // Word -> PDF
    BridgeRegistry.register(ApplicationMsWord, ApplicationPdf, WordToPdfAsposeBridge)

    // Excel -> PDF
    // Modern XLSX
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ApplicationPdf, ExcelToPdfAsposeBridge)

    // Legacy Excel formats
    BridgeRegistry.register(ApplicationVndMsExcel, ApplicationPdf, excel.ExcelXlsToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsExcelSheetMacroEnabled, ApplicationPdf, excel.ExcelXlsmToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsExcelSheetBinary, ApplicationPdf, excel.ExcelXlsbToPdfAsposeBridge)

    // Email -> PDF
    BridgeRegistry.register(MessageRfc822, ApplicationPdf, EmailToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndMsOutlook, ApplicationPdf, OutlookMsgToPdfAsposeBridge)

    // PowerPoint -> PDF (PPT & PPTX)
    BridgeRegistry.register(ApplicationVndMsPowerpoint, ApplicationPdf, PowerPointToPdfAsposeBridge)
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsPresentationmlPresentation, ApplicationPdf, powerpoint.PowerPointPptxToPdfAsposeBridge)

    // DOCX -> PDF
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsWordprocessingmlDocument, ApplicationPdf, word.WordDocxToPdfAsposeBridge)
  }
}