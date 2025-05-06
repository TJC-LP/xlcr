package com.tjclp.xlcr
package bridges.excel

import types.MimeType.ApplicationVndMsExcel

/**
 * Bridge that converts XLS (Legacy Excel) spreadsheets to PDF using Aspose.Cells.
 */
object ExcelXlsToPdfAsposeBridge
    extends ExcelToPdfAsposeBridgeImpl[ApplicationVndMsExcel.type]
