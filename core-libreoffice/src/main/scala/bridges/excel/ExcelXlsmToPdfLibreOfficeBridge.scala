package com.tjclp.xlcr
package bridges.excel

import types.MimeType.ApplicationVndMsExcelSheetMacroEnabled

/**
 * Bridge that converts Excel .xlsm (macro-enabled) documents to PDF using LibreOffice.
 *
 * Priority: DEFAULT (fallback to Aspose if available)
 */
object ExcelXlsmToPdfLibreOfficeBridge
    extends ExcelToPdfLibreOfficeBridgeImpl[ApplicationVndMsExcelSheetMacroEnabled.type]
