package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet}

/**
 * Scala 3 implementation of ExcelToPdfAsposeBridge.
 * Simply extends the specific XLSX implementation.
 */
object ExcelToPdfAsposeBridge
  extends SimpleBridge[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, ApplicationPdf.type]
  with ExcelXlsxToPdfAsposeBridgeImpl