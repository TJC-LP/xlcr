package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndMsExcelSheetBinary}

/** Bridge that converts XLSB (Binary Excel) spreadsheets to PDF using Aspose.Cells.
  */
object ExcelXlsbToPdfAsposeBridge
    extends SimpleBridge[
      ApplicationVndMsExcelSheetBinary.type,
      ApplicationPdf.type
    ]
    with ExcelXlsbToPdfAsposeBridgeImpl
