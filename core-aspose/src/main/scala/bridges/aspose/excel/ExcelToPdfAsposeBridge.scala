package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
}

/** Bridge that converts XLSX (Excel Open XML) spreadsheets to PDF using Aspose.Cells.
  *
  * This specific implementation handles the modern Excel format (XLSX).
  */
object ExcelToPdfAsposeBridge
    extends SimpleBridge[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ApplicationPdf.type
    ]
    with ExcelXlsxToPdfAsposeBridgeImpl
