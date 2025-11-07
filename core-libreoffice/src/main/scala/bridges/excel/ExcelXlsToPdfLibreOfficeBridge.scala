package com.tjclp.xlcr
package bridges.excel

import types.MimeType.ApplicationVndMsExcel

/**
 * Bridge that converts Excel .xls documents to PDF using LibreOffice.
 *
 * Priority: DEFAULT (fallback to Aspose if available)
 */
object ExcelXlsToPdfLibreOfficeBridge
    extends ExcelToPdfLibreOfficeBridgeImpl[ApplicationVndMsExcel.type]
