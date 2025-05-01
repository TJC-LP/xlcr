package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
}

/** Scala 2.12 implementation of ExcelToPdfAsposeBridge.
  * Extends the specific XLSX implementation and provides required ClassTags.
  */
object ExcelToPdfAsposeBridge
    extends SimpleBridge[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ApplicationPdf.type
    ]
    with ExcelXlsxToPdfAsposeBridgeImpl
