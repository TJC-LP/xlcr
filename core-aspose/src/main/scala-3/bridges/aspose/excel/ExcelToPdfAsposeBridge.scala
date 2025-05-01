package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.aspose.HighPrioritySimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet}

/**
 * Scala 3 implementation of ExcelToPdfAsposeBridge.
 * Simply extends the common implementation.
 */
object ExcelToPdfAsposeBridge
  extends HighPrioritySimpleBridge[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, ApplicationPdf.type]
  with ExcelToPdfAsposeBridgeImpl