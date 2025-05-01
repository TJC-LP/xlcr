package com.tjclp.xlcr
package bridges.aspose

import bridges.BridgeRegistry
import bridges.aspose.email.{
  EmailToPdfAsposeBridge,
  OutlookMsgToPdfAsposeBridge
}
import bridges.aspose.excel._
import bridges.aspose.powerpoint.{
  PowerPointPptxToPdfAsposeBridge,
  PowerPointToPdfAsposeBridge
}
import bridges.aspose.word.{WordDocxToPdfAsposeBridge, WordToPdfAsposeBridge}
import types.MimeType._

/** AsposeBridgeRegistry offers a method to register all Aspose-based bridging
  * into the core BridgeRegistry. This is called by BridgeRegistry.initAspose().
  */
object AsposeBridgeRegistry {

  /** Register all Aspose-based bridging with the core BridgeRegistry.
    */
  def registerAll(): Unit = {
    // Word -> PDF
    BridgeRegistry.register(
      ApplicationMsWord,
      ApplicationPdf,
      WordToPdfAsposeBridge
    )

    // Excel -> PDF
    // Modern XLSX
    BridgeRegistry.register(
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      ApplicationPdf,
      ExcelToPdfAsposeBridge
    )

    // Legacy Excel formats
    BridgeRegistry.register(
      ApplicationVndMsExcel,
      ApplicationPdf,
      ExcelXlsToPdfAsposeBridge
    )
    BridgeRegistry.register(
      ApplicationVndMsExcelSheetMacroEnabled,
      ApplicationPdf,
      ExcelXlsmToPdfAsposeBridge
    )
    BridgeRegistry.register(
      ApplicationVndMsExcelSheetBinary,
      ApplicationPdf,
      ExcelXlsbToPdfAsposeBridge
    )

    // OpenDocument Spreadsheet (ODS)
    BridgeRegistry.register(
      ApplicationVndOasisOpendocumentSpreadsheet,
      ApplicationPdf,
      OdsToPdfAsposeBridge
    )

    // Email -> PDF
    BridgeRegistry.register(
      MessageRfc822,
      ApplicationPdf,
      EmailToPdfAsposeBridge
    )
    BridgeRegistry.register(
      ApplicationVndMsOutlook,
      ApplicationPdf,
      OutlookMsgToPdfAsposeBridge
    )

    // PowerPoint -> PDF (PPT & PPTX)
    BridgeRegistry.register(
      ApplicationVndMsPowerpoint,
      ApplicationPdf,
      PowerPointToPdfAsposeBridge
    )
    BridgeRegistry.register(
      ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      ApplicationPdf,
      PowerPointPptxToPdfAsposeBridge
    )

    // DOCX -> PDF
    BridgeRegistry.register(
      ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
      ApplicationPdf,
      WordDocxToPdfAsposeBridge
    )
  }
}
