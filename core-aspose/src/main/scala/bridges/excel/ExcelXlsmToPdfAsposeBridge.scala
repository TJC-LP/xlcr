package com.tjclp.xlcr
package bridges.excel

import types.MimeType.ApplicationVndMsExcelSheetMacroEnabled

/**
 * Bridge that converts XLSM (Macro-enabled Excel) spreadsheets to PDF using Aspose.Cells.
 */
object ExcelXlsmToPdfAsposeBridge
    extends ExcelToPdfAsposeBridgeImpl[
      ApplicationVndMsExcelSheetMacroEnabled.type
    ]
