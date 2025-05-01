package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndMsExcel}

/** Bridge that converts XLS (Legacy Excel) spreadsheets to PDF using Aspose.Cells.
  */
object ExcelXlsToPdfAsposeBridge
    extends SimpleBridge[ApplicationVndMsExcel.type, ApplicationPdf.type]
    with ExcelXlsToPdfAsposeBridgeImpl
