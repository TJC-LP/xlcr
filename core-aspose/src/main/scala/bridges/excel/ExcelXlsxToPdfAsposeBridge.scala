package com.tjclp.xlcr
package bridges.excel

import types.MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

/**
 * Bridge that converts XLSX (Excel Open XML) spreadsheets to PDF using Aspose.Cells.
 *
 * This specific implementation handles the modern Excel format (XLSX).
 */
object ExcelXlsxToPdfAsposeBridge
    extends ExcelToPdfAsposeBridgeImpl[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ]
