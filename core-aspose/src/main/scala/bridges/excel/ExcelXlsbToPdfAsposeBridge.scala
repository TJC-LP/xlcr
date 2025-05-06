package com.tjclp.xlcr
package bridges.excel

import types.MimeType.ApplicationVndMsExcelSheetBinary

/**
 * Bridge that converts XLSB (Binary Excel) spreadsheets to PDF using Aspose.Cells.
 */
object ExcelXlsbToPdfAsposeBridge
    extends ExcelToPdfAsposeBridgeImpl[ApplicationVndMsExcelSheetBinary.type]
