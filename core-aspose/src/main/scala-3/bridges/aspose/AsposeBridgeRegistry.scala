package com.tjclp.xlcr
package bridges.aspose

import bridges.BridgeRegistry
import bridges.aspose.email.EmailToPdfAsposeBridge
import bridges.aspose.excel.ExcelToPdfAsposeBridge
import bridges.aspose.powerpoint.PowerPointToPdfAsposeBridge
import bridges.aspose.word.WordToPdfAsposeBridge
import types.MimeType
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
    // Word -> PDF
    BridgeRegistry.register(ApplicationMsWord, ApplicationPdf, WordToPdfAsposeBridge)

    // Excel -> PDF
    BridgeRegistry.register(ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ApplicationPdf, ExcelToPdfAsposeBridge)

    // Email -> PDF
    BridgeRegistry.register(MessageRfc822, ApplicationPdf, EmailToPdfAsposeBridge)

    // PowerPoint -> PDF
    BridgeRegistry.register(ApplicationVndMsPowerpoint, ApplicationPdf, PowerPointToPdfAsposeBridge)
  }
}