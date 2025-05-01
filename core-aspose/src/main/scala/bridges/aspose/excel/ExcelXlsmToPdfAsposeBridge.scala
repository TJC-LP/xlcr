package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndMsExcelSheetMacroEnabled}

/** Bridge that converts XLSM (Macro-enabled Excel) spreadsheets to PDF using Aspose.Cells.
  */
object ExcelXlsmToPdfAsposeBridge
    extends SimpleBridge[
      ApplicationVndMsExcelSheetMacroEnabled.type,
      ApplicationPdf.type
    ]
    with ExcelXlsmToPdfAsposeBridgeImpl
